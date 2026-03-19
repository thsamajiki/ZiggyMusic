package com.hero.ziggymusic.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.entity.PlayerModel
import com.hero.ziggymusic.event.Event
import com.hero.ziggymusic.event.EventBus
import com.hero.ziggymusic.view.main.MainActivity
import javax.inject.Inject
import com.squareup.otto.Subscribe
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MusicService : MediaLibraryService() {
    @Inject
    lateinit var player: ExoPlayer

    private val playerModel: PlayerModel = PlayerModel.getInstance()

    @Volatile
    private var isExiting: Boolean = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var albumArtLoadJob: Job? = null
    private var currentAlbumArtBitmap: Bitmap? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isExiting) return
            updateNotification()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (isExiting) return

            currentAlbumArtBitmap = null
            updateNotification()

            val music = playerModel.currentMusic
            val albumUri = music?.getAlbumUri()

            albumArtLoadJob?.cancel()
            albumArtLoadJob = serviceScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        if (albumUri == null) {
                            null
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = ImageDecoder.createSource(contentResolver, albumUri)
                            ImageDecoder.decodeBitmap(source)
                        } else {
                            contentResolver.openInputStream(albumUri)?.use { input ->
                                BitmapFactory.decodeStream(input)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MusicService", "album art decode failed", e)
                        null
                    }
                }

                if (isExiting) return@launch
                if (music?.getAlbumUri() != playerModel.currentMusic?.getAlbumUri()) return@launch

                currentAlbumArtBitmap = bitmap
                updateNotification()
            }
        }
    }

    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var collapsedNotificationView: RemoteViews
    private lateinit var expandedNotificationView: RemoteViews

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForegroundCompat()
        isForegroundStarted = true
        MusicServiceLauncher.onForegroundEntered()

        EventBus.getInstance().register(this)

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player,
            object : MediaLibrarySession.Callback {
        }).build()

        // ExoPlayer 상태 변화 감지 리스너 등록
        player.addListener(playerListener)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (!isForegroundStarted) {
            startForegroundCompat()
            isForegroundStarted = true
            MusicServiceLauncher.onForegroundEntered()
        }

        if (!::collapsedNotificationView.isInitialized) {
            collapsedNotificationView = RemoteViews(this.packageName, R.layout.notification_player)
            expandedNotificationView = RemoteViews(this.packageName, R.layout.notification_player_extended)
        }

        Log.d("MusicServiceAction", "action = ${intent?.action}")

        when (intent?.action) {
            PLAY -> {
                if (!player.isPlaying) {
                    Log.d("onStartCommand", "PLAY")
                    player.play()
                }
                updateNotification()
            }

            PAUSE -> {
                if (player.isPlaying) {
                    Log.d("onStartCommand", "PAUSE")
                    player.pause()
                }
                updateNotification()
            }

            SKIP_PREV -> {
                Log.d("onStartCommand", "SKIP_PREV")
                player.seekToPrevious()
                updateNotification()
            }

            SKIP_NEXT -> {
                Log.d("onStartCommand", "SKIP_NEXT")
                player.seekToNext()
                updateNotification()
            }

            CLOSE -> {
                exitPlayer()
            }

            ACTION_REFRESH_NOTIFICATION, null -> {
                currentAlbumArtBitmap = null
                updateNotification()

                val music = playerModel.currentMusic
                val albumUri = music?.getAlbumUri()

                albumArtLoadJob?.cancel()
                albumArtLoadJob = serviceScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            if (albumUri == null) {
                                null
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                val source = ImageDecoder.createSource(contentResolver, albumUri)
                                ImageDecoder.decodeBitmap(source)
                            } else {
                                contentResolver.openInputStream(albumUri)?.use { input ->
                                    BitmapFactory.decodeStream(input)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MusicService", "album art decode failed", e)
                            null
                        }
                    }

                    if (isExiting) return@launch
                    if (music?.getAlbumUri() != playerModel.currentMusic?.getAlbumUri()) return@launch

                    currentAlbumArtBitmap = bitmap
                    updateNotification()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ziggy Music")
            .setContentText("음악 재생 준비 중")
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        if (isExiting || !isForegroundStarted) return

        val notification = try {
            createNotification()
        } catch (e: Exception) {
            Log.e("MusicService", "updateNotification() 실패, 기본 알림으로 대체", e)
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Ziggy Music")
                .setContentText("음악 재생 중")
                .setSmallIcon(R.drawable.ic_music_note)
                .setOngoing(true)
                .build()
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    @Subscribe
    fun onEvent(event: Event) {
        when (event.getEvent()) {
            "PLAY" -> {
                player.play()
            }
            "PAUSE" -> {
                player.pause()
            }
            "SKIP_PREV" -> {
                player.seekToPrevious()
            }
            "SKIP_NEXT" -> {
                player.seekToNext()
            }
        }
    }

    // 알림 채널을 생성
    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "musicPlayerChannel",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(serviceChannel)
        }
    }

    // 알림을 생성
    private fun createNotification(): Notification {
        // 각 action 에 해당하는 PendingIntent 생성
        val prevIntent = Intent(this, MusicService::class.java).run {
            action = SKIP_PREV
            PendingIntent.getService(this@MusicService, REQ_CODE_PREV, this, PendingIntent.FLAG_IMMUTABLE)
        }
        val playIntent = Intent(this, MusicService::class.java).run {
            action = PLAY
            PendingIntent.getService(this@MusicService, REQ_CODE_PLAY, this, PendingIntent.FLAG_IMMUTABLE)
        }
        val pauseIntent = Intent(this, MusicService::class.java).run {
            action = PAUSE
            PendingIntent.getService(this@MusicService, REQ_CODE_PAUSE, this, PendingIntent.FLAG_IMMUTABLE)
        }
        val nextIntent = Intent(this, MusicService::class.java).run {
            action = SKIP_NEXT
            PendingIntent.getService(this@MusicService, REQ_CODE_NEXT, this, PendingIntent.FLAG_IMMUTABLE)
        }
        val closeIntent = Intent(this, MusicService::class.java).run {
            action = CLOSE
            PendingIntent.getService(this@MusicService, REQ_CODE_CLOSE, this, PendingIntent.FLAG_IMMUTABLE)
        }
        val notificationTouchIntent = Intent(this, MainActivity::class.java).run {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            PendingIntent.getActivity(this@MusicService, REQ_CODE_NOTIFICATION_TOUCH, this, PendingIntent.FLAG_IMMUTABLE)
        }

        val isPlaying = player.isPlaying

        // Play/Pause 버튼 이미지와 PendingIntent를 상태에 따라 동적으로 설정
        setPlayPauseButtonState(isPlaying)

        // setOnClickPendingIntent()를 이용해서 클릭 리스너를 달아준다.
        collapsedNotificationView.setOnClickPendingIntent(
            R.id.btnNotificationPrev,
            prevIntent
        )
        collapsedNotificationView.setOnClickPendingIntent(
            R.id.btnNotificationNext,
            nextIntent
        )
        expandedNotificationView.setOnClickPendingIntent(
            R.id.btnNotificationExtendedPrev,
            prevIntent
        )
        expandedNotificationView.setOnClickPendingIntent(
            R.id.btnNotificationExtendedNext,
            nextIntent
        )
        expandedNotificationView.setOnClickPendingIntent(
            R.id.btnNotificationExtendedClose,
            closeIntent
        )

        val currentMusic = playerModel.currentMusic

        // 알림으로 사용할 레이아웃에 음원 정보를 설정
        setMusicInNotification(currentMusic)

        // 알림 생성
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setShowWhen(false)
            .setSmallIcon(R.drawable.ic_music_note)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setColor(ContextCompat.getColor(this, R.color.dark_black))
            .setColorized(true)
            .setCustomContentView(collapsedNotificationView)
            .setCustomBigContentView(expandedNotificationView)
            .setContentIntent(notificationTouchIntent)
            .setOngoing(true)
            .build()
    }

    private fun setPlayPauseButtonState(isPlaying: Boolean) {
        if (isPlaying) {
            collapsedNotificationView.setImageViewResource(R.id.btnNotificationPlay, R.drawable.ic_pause_button)
            collapsedNotificationView.setOnClickPendingIntent(R.id.btnNotificationPlay, getPauseIntent())
            expandedNotificationView.setImageViewResource(R.id.btnNotificationExtendedPlay, R.drawable.ic_pause_button)
            expandedNotificationView.setOnClickPendingIntent(R.id.btnNotificationExtendedPlay, getPauseIntent())
        } else {
            collapsedNotificationView.setImageViewResource(R.id.btnNotificationPlay, R.drawable.ic_play_button)
            collapsedNotificationView.setOnClickPendingIntent(R.id.btnNotificationPlay, getPlayIntent())
            expandedNotificationView.setImageViewResource(R.id.btnNotificationExtendedPlay, R.drawable.ic_play_button)
            expandedNotificationView.setOnClickPendingIntent(R.id.btnNotificationExtendedPlay, getPlayIntent())
        }
    }

    private fun getPlayIntent(): PendingIntent {
        val playIntent = Intent(this, MusicService::class.java).apply { action = PLAY }
        return PendingIntent.getService(this, REQ_CODE_PLAY, playIntent, PendingIntent.FLAG_IMMUTABLE)
    }
    private fun getPauseIntent(): PendingIntent {
        val pauseIntent = Intent(this, MusicService::class.java).apply { action = PAUSE }
        return PendingIntent.getService(this, REQ_CODE_PAUSE, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setMusicInNotification(music: MusicModel?) {
        if (currentAlbumArtBitmap != null) {
            collapsedNotificationView.setImageViewBitmap(R.id.ivNotificationAlbum, currentAlbumArtBitmap)
            expandedNotificationView.setImageViewBitmap(
                R.id.ivNotificationExtendedAlbum,
                currentAlbumArtBitmap
            )
        } else {
            collapsedNotificationView.setImageViewResource(
                R.id.ivNotificationAlbum,
                R.drawable.ic_no_album_image
            )
            expandedNotificationView.setImageViewResource(
                R.id.ivNotificationExtendedAlbum,
                R.drawable.ic_no_album_image
            )
        }

        collapsedNotificationView.setTextViewText(R.id.tvNotificationTitle, music?.title)
        collapsedNotificationView.setTextViewText(R.id.tvNotificationArtist, music?.artist)
        collapsedNotificationView.setImageViewResource(R.id.btnNotificationPrev, R.drawable.ic_prev_button)
        collapsedNotificationView.setImageViewResource(R.id.btnNotificationNext, R.drawable.ic_next_button)

        expandedNotificationView.setTextViewText(R.id.tvNotificationExtendedTitle, music?.title)
        expandedNotificationView.setTextViewText(R.id.tvNotificationExtendedArtist, music?.artist)
        expandedNotificationView.setImageViewResource(
            R.id.btnNotificationExtendedPrev,
            R.drawable.ic_prev_button
        )
        expandedNotificationView.setImageViewResource(
            R.id.btnNotificationExtendedNext,
            R.drawable.ic_next_button
        )
        expandedNotificationView.setImageViewResource(
            R.id.btnNotificationExtendedClose,
            R.drawable.ic_white_close
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 사용자가 최근 앱 목록에서 앱을 제거(백그라운드 종료)해도
        // 음악 재생은 유지되는 것이 일반적인 음악 플레이어의 동작.
        //
        // 또한 여기서 stopSelf()를 호출하면 서비스가 파괴되며(onDestroy),
        // DI로 공유 중인 ExoPlayer(@Singleton)가 release()되는 순간
        // 같은 프로세스에서 다시 앱을 실행했을 때(재실행) 재생이 불가능해짐.
        // (UI/Fragment 전환은 되지만, player 인스턴스는 이미 release 상태)
        //
        // 따라서 Task 제거 이벤트에서는 서비스를 강제 종료하지 않음.
        exitPlayer()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * 사용자 종료(백그라운드 종료 / 알림 X) 공통 처리
     */
    private fun exitPlayer() {
        if (isExiting) return
        isExiting = true

        // 종료 중에는 Notification 갱신이 절대 발생하면 안 되므로, 리스너를 먼저 제거
        player.removeListener(playerListener)

        // 자동 재생처럼 보이는 문제를 막기 위해 상태를 확실히 끊는다.
        player.pause()
        player.playWhenReady = false

        // 필요하면 플레이리스트도 끊어준다(원치 않으면 이 줄은 제거 가능)
        player.stop()
        player.clearMediaItems()

        // stopForeground 전에 cancel을 먼저 때려서 "큰뷰/작은뷰 토글" 깜빡임을 방지
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)

        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            // 현재 프로세스에 속한 task들을 최근 앱 목록에서 제거
            // (백그라운드에 Activity가 살아있든, task만 남아있든 모두 처리)
            for (task in am.appTasks) {
                task.finishAndRemoveTask()
            }
        } catch (e: Exception) {
            Log.e("MusicService", "finishAndRemoveTask 실패", e)
        }

        stopSelf()
    }

    // 서비스가 완전히 종료되면
    override fun onDestroy() {
        isForegroundStarted = false
        MusicServiceLauncher.onServiceDestroyed()

        albumArtLoadJob?.cancel()
        serviceScope.cancel()
        currentAlbumArtBitmap = null

        // ExoPlayer가 Hilt @Singleton 으로 제공되어
        // Activity/Fragment/Service가 같은 인스턴스를 공유.
        //
        // 여기서 player.release()를 호출하면, 앱을 종료했다가 다시 실행하는 케이스에서
        // (프로세스는 유지된 채 Activity만 재생성되는 경우가 흔함)
        // DI가 동일한 ExoPlayer 인스턴스를 재주입하게 되고,
        // 이미 release 된 player로 인해 "재생/seek/prepare"가 전부 동작하지 않게 됨.
        //
        // 서비스가 내려가더라도 프로세스가 살아있는 동안은 player를 release 하지 않음.
        // 프로세스가 종료되면 시스템이 리소스를 회수하므로 별도 release는 불필요함.
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (::mediaLibrarySession.isInitialized) {
            mediaLibrarySession.release()
        }

        EventBus.getInstance().unregister(this)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "MusicChannel" // 알림 채널 ID
        const val NOTIFICATION_ID = 1 // Foreground 알림 ID

        const val PLAY = "com.hero.ziggymusic.PLAY" // Notification 에서 재생 버튼을 누를 시
        const val PAUSE = "com.hero.ziggymusic.PAUSE" // Notification 에서 일시 정지 버튼을 누를 시
        const val SKIP_PREV = "com.hero.ziggymusic.SKIP_PREV" // Notification 에서 이전 곡 버튼을 누를 시
        const val SKIP_NEXT = "com.hero.ziggymusic.SKIP_NEXT" // Notification 에서 다음 곡 버튼을 누를 시
        const val CLOSE = "com.hero.ziggymusic.CLOSE" // 닫기 버튼을 누를 시
        const val ACTION_REFRESH_NOTIFICATION = "com.hero.ziggymusic.REFRESH_NOTIFICATION"

        @Volatile
        var isForegroundStarted: Boolean = false

        // PendingIntent Request 코드
        const val REQ_CODE_PREV = 100
        const val REQ_CODE_PLAY = 101
        const val REQ_CODE_PAUSE = 102
        const val REQ_CODE_NEXT = 103
        const val REQ_CODE_CLOSE = 104
        const val REQ_CODE_NOTIFICATION_TOUCH = 105
    }
}