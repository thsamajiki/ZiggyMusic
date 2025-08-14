package com.hero.ziggymusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
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
import java.io.IOException
import javax.inject.Inject
import kotlin.system.exitProcess
import com.squareup.otto.Subscribe
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MusicService : MediaLibraryService() {
    @Inject
    lateinit var player: ExoPlayer

    private val playerModel: PlayerModel = PlayerModel.getInstance()

    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var remoteNotificationLayout: RemoteViews
    private lateinit var remoteNotificationExtendedLayout: RemoteViews

    override fun onCreate() {
        super.onCreate()

        EventBus.getInstance().register(this)

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player,
            object : MediaLibrarySession.Callback {
        }).build()

        // ExoPlayer 상태 변화 감지 리스너 등록
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // 재생/일시정지 상태가 바뀔 때마다 Notification 갱신
                updateNotification()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // 곡이 바뀔 때도 Notification 갱신
                updateNotification()
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel() // 알림 채널 생성

        // Notification 에 사용할 RemoteViews 생성
        remoteNotificationLayout = RemoteViews(this.packageName, R.layout.notification_player)
        remoteNotificationExtendedLayout = RemoteViews(this.packageName, R.layout.notification_player_extended)

        Log.d("MusicServiceAction", "action = ${intent?.action}")

        // 예외 발생 시에도 반드시 임시 알림으로 startForeground 호출
        val notification = try {
            createNotification()
        } catch (e: Exception) {
            Log.e("MusicService", "createNotification() 실패, 기본 알림으로 대체", e)
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Ziggy Music")
                .setContentText("음악 재생 중")
                .setSmallIcon(R.drawable.ic_music_note)
                .build()
        }
        startForeground(1, notification) // Foreground 에서 실행

        when(intent?.action) {
            PLAY -> { // Notification 에서 재생 / 일시 정지 버튼을 누를 시
                Log.d("onStartCommand", "PLAY: ${player.isPlaying}")
                EventBus.getInstance().post(Event("PLAY"))
            }
            PAUSE -> { // Notification 에서 재생 / 일시 정지 버튼을 누를 시
                Log.d("onStartCommand", "PAUSE: ${player.isPlaying}")
                EventBus.getInstance().post(Event("PAUSE"))
            }
            SKIP_PREV -> { // Notification 에서 이전 곡 버튼을 누를 시
                Log.d("SKIP_PREV", "onStartCommand: $player")
                EventBus.getInstance().post(Event("SKIP_PREV"))
            }
            SKIP_NEXT -> { // Notification 에서 다음 곡 버튼을 누를 시
                EventBus.getInstance().post(Event("SKIP_NEXT"))
            }
            CLOSE -> { // Notification 에서 닫기 버튼 누를 시
                stopSelf()
                exitProcess(0)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateNotification() {
        val notification = try {
            createNotification()
        } catch (e: Exception) {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Ziggy Music")
                .setContentText("음악 재생 중")
                .setSmallIcon(R.drawable.ic_music_note)
                .build()
        }

        // 음악 재생 상태 변화 등 이벤트가 발생할 때 알림 UI가 최신 상태로 유지
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
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

            }
            "SKIP_NEXT" -> {

            }
        }
    }

    // 알림 채널을 생성
    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "musicPlayerChannel",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    // 알림을 생성
    private fun createNotification(): Notification {
        // 각 action 에 해당하는 PendingIntent 생성
        val prevIntent = Intent(this, MusicService::class.java).run {
            action = SKIP_PREV
            PendingIntent.getService(this@MusicService, 0, this, PendingIntent.FLAG_IMMUTABLE)
        }
        val playIntent = Intent(this, MusicService::class.java).run {
            action = PLAY
            PendingIntent.getService(this@MusicService, 1, this, PendingIntent.FLAG_IMMUTABLE)
        }
        val pauseIntent = Intent(this, MusicService::class.java).run {
            action = PAUSE
            PendingIntent.getService(this@MusicService, 2, this, PendingIntent.FLAG_IMMUTABLE)
        }
        val nextIntent = Intent(this, MusicService::class.java).run {
            action = SKIP_NEXT
            PendingIntent.getService(this@MusicService, 3, this, PendingIntent.FLAG_IMMUTABLE)
        }
        val closeIntent = Intent(this, MusicService::class.java).run {
            action = CLOSE
            PendingIntent.getService(this@MusicService, 4, this, PendingIntent.FLAG_IMMUTABLE)
        }
        val notificationTouchIntent = Intent(this, MainActivity::class.java).run {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            PendingIntent.getActivity(this@MusicService, 5, this, PendingIntent.FLAG_IMMUTABLE)
        }

        val isPlaying = player.isPlaying

        // Play/Pause 버튼 이미지와 PendingIntent를 상태에 따라 동적으로 설정
        setPlayPauseButtonState(isPlaying)

        // setOnClickPendingIntent()를 이용해서 클릭 리스너를 달아준다.
        remoteNotificationLayout.setOnClickPendingIntent(
            R.id.btnNotificationPrev,
            prevIntent
        )
        remoteNotificationLayout.setOnClickPendingIntent(
            R.id.btnNotificationNext,
            nextIntent
        )
        remoteNotificationExtendedLayout.setOnClickPendingIntent(
            R.id.btnNotificationExtendedPrev,
            prevIntent
        )
        remoteNotificationExtendedLayout.setOnClickPendingIntent(
            R.id.btnNotificationExtendedNext,
            nextIntent
        )
        remoteNotificationExtendedLayout.setOnClickPendingIntent(
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
            .setCustomContentView(remoteNotificationLayout)
            .setCustomBigContentView(remoteNotificationExtendedLayout)
            .setContentIntent(notificationTouchIntent)
            .setOngoing(true)
            .build()
    }

    private fun setPlayPauseButtonState(isPlaying: Boolean) {
        if (isPlaying) {
            remoteNotificationLayout.setImageViewResource(R.id.btnNotificationPlay, R.drawable.ic_pause_button)
            remoteNotificationLayout.setOnClickPendingIntent(R.id.btnNotificationPlay, getPauseIntent())
            remoteNotificationExtendedLayout.setImageViewResource(R.id.btnNotificationExtendedPlay, R.drawable.ic_pause_button)
            remoteNotificationExtendedLayout.setOnClickPendingIntent(R.id.btnNotificationExtendedPlay, getPauseIntent())
        } else {
            remoteNotificationLayout.setImageViewResource(R.id.btnNotificationPlay, R.drawable.ic_play_button)
            remoteNotificationLayout.setOnClickPendingIntent(R.id.btnNotificationPlay, getPlayIntent())
            remoteNotificationExtendedLayout.setImageViewResource(R.id.btnNotificationExtendedPlay, R.drawable.ic_play_button)
            remoteNotificationExtendedLayout.setOnClickPendingIntent(R.id.btnNotificationExtendedPlay, getPlayIntent())
        }
    }

    private fun getPlayIntent(): PendingIntent {
        val playIntent = Intent(this, MusicService::class.java).apply { action = PLAY }
        return PendingIntent.getService(this, 1, playIntent, PendingIntent.FLAG_IMMUTABLE)
    }
    private fun getPauseIntent(): PendingIntent {
        val pauseIntent = Intent(this, MusicService::class.java).apply { action = PAUSE }
        return PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setMusicInNotification(music: MusicModel?) {
        var bitmap: Bitmap? = null
        val albumUri = music?.getAlbumUri() ?: Uri.parse("")

        try {
            bitmap = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // P 이상인 경우
                val source = ImageDecoder.createSource(contentResolver, albumUri)
                ImageDecoder.decodeBitmap(source)

            } else { // 그 이하인 경우
                BitmapFactory.decodeStream(contentResolver.openInputStream(albumUri))
            }
        } catch (e: IOException) { // 음원의 앨범 아트 Uri 에 해당하는 파일이 없는 경우 예외가 발생하고 이 경우 bitmap 에 null 을 담는다.
            e.printStackTrace()
            bitmap = null
        } finally {
            if (bitmap != null) { // null 이 아닌 경우 다시 말해 앨범 아트 Uri 에 파일이 있는 경우
                remoteNotificationLayout.setImageViewBitmap(R.id.ivNotificationAlbum, bitmap)
                remoteNotificationExtendedLayout.setImageViewBitmap(
                    R.id.ivNotificationExtendedAlbum,
                    bitmap
                )
            } else { // 앨범 아트 Uri 에 파일이 없는 경우 기본 앨범 아트를 사용
                remoteNotificationLayout.setImageViewResource(
                    R.id.ivNotificationAlbum,
                    R.drawable.ic_no_album_image
                )
                remoteNotificationExtendedLayout.setImageViewResource(
                    R.id.ivNotificationExtendedAlbum,
                    R.drawable.ic_no_album_image
                )
            }
        }

        remoteNotificationLayout.setTextViewText(R.id.tvNotificationTitle, music?.title)
        remoteNotificationLayout.setTextViewText(R.id.tvNotificationArtist, music?.artist)
        remoteNotificationLayout.setImageViewResource(R.id.btnNotificationPrev, R.drawable.ic_prev_button)
        remoteNotificationLayout.setImageViewResource(R.id.btnNotificationNext, R.drawable.ic_next_button)

        remoteNotificationExtendedLayout.setTextViewText(R.id.tvNotificationExtendedTitle, music?.title)
        remoteNotificationExtendedLayout.setTextViewText(R.id.tvNotificationExtendedArtist, music?.artist)
        remoteNotificationExtendedLayout.setImageViewResource(
            R.id.btnNotificationExtendedPrev,
            R.drawable.ic_prev_button
        )
        remoteNotificationExtendedLayout.setImageViewResource(
            R.id.btnNotificationExtendedNext,
            R.drawable.ic_next_button
        )
        remoteNotificationExtendedLayout.setImageViewResource(
            R.id.btnNotificationExtendedClose,
            R.drawable.ic_white_close
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    // 서비스가 완전히 종료되면
    override fun onDestroy() {
        player.stop() // 플레이어 중단

        if (::mediaLibrarySession.isInitialized) {
            mediaLibrarySession.release()
        }

        EventBus.getInstance().unregister(this)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "MusicChannel" // 알림 채널 ID

        const val PLAY = "com.hero.ziggymusic.PLAY" // Notification 에서 재생 버튼을 누를 시
        const val PAUSE = "com.hero.ziggymusic.PAUSE" // Notification 에서 일시 정지 버튼을 누를 시
        const val SKIP_PREV = "com.hero.ziggymusic.SKIP_PREV" // Notification 에서 이전 곡 버튼을 누를 시
        const val SKIP_NEXT = "com.hero.ziggymusic.SKIP_NEXT" // Notification 에서 다음 곡 버튼을 누를 시
        const val CLOSE = "com.hero.ziggymusic.CLOSE" // 닫기 버튼을 누를 시
    }
}