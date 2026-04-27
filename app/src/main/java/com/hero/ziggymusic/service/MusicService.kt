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
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import android.view.View
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
    private var currentAlbumArtMediaId: String? = null
    private var loadingAlbumArtMediaId: String? = null
    private val albumArtCache = object : LruCache<String, Bitmap>(20) {}

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isExiting) return

            val mediaId = player.currentMediaItem?.mediaId
            if (mediaId != null && playerModel.currentMusic?.id != mediaId) {
                playerModel.changedMusic(mediaId)
            }

            updateNotification()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (isExiting) return

            val newMediaId = mediaItem?.mediaId
            if (newMediaId != null) {
                playerModel.changedMusic(newMediaId)
            } else {
                syncCurrentMusicFromPlayer()
            }

            refreshAlbumArt()
        }
    }

    private lateinit var mediaLibrarySession: MediaLibrarySession

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForegroundCompat()
        MusicServiceState.onForegroundEntered()

        EventBus.getInstance().register(this)

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player,
            object : MediaLibrarySession.Callback {
            }).build()

        player.addListener(playerListener)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (!MusicServiceState.isForegroundStarted) {
            startForegroundCompat()
            MusicServiceState.onForegroundEntered()
        }

        Log.d("MusicServiceAction", "action = ${intent?.action}")

        when (intent?.action) {
            PLAY -> {
                if (!player.playWhenReady) {
                    Log.d("onStartCommand", "PLAY")
                    player.play()
                }
                updateNotification()
            }

            PAUSE -> {
                if (player.playWhenReady) {
                    Log.d("onStartCommand", "PAUSE")
                    player.pause()
                }
                updateNotification()
            }

            SKIP_PREV -> {
                Log.d("onStartCommand", "SKIP_PREV")
                player.seekToPrevious()
            }

            SKIP_NEXT -> {
                Log.d("onStartCommand", "SKIP_NEXT")
                player.seekToNext()
            }

            ACTION_REFRESH_NOTIFICATION, null -> {
                val mediaId = intent?.getStringExtra(EXTRA_MEDIA_ID)
                    ?: player.currentMediaItem?.mediaId

                updateAlbumArt(mediaId)
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ziggy Music")
            .setContentText("음악 재생 준비 중")
            .setSmallIcon(R.drawable.ic_music_note)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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

    private fun syncCurrentMusicFromPlayer(): MusicModel? {
        val mediaId = player.currentMediaItem?.mediaId ?: return playerModel.currentMusic
        playerModel.changedMusic(mediaId)
        return playerModel.currentMusic
    }

    private fun updateAlbumArt(mediaId: String?) {
        albumArtLoadJob?.cancel()

        if (mediaId == null) {
            loadingAlbumArtMediaId = null
            currentAlbumArtBitmap = null
            currentAlbumArtMediaId = null
            updateNotification()
            return
        }

        playerModel.changedMusic(mediaId)
        val currentMusic = playerModel.currentMusic
        val albumUri = currentMusic?.getAlbumUri()

        if (albumUri == null) {
            loadingAlbumArtMediaId = null
            currentAlbumArtBitmap = null
            currentAlbumArtMediaId = null
            updateNotification()
            return
        }

        val cachedBitmap = albumArtCache.get(mediaId)
        if (cachedBitmap != null) {
            loadingAlbumArtMediaId = null
            currentAlbumArtBitmap = cachedBitmap
            currentAlbumArtMediaId = mediaId
            updateNotification()
            return
        }

        loadingAlbumArtMediaId = mediaId
        currentAlbumArtBitmap = null
        currentAlbumArtMediaId = null
        updateNotification()

        albumArtLoadJob = serviceScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeAlbumArtForNotification(albumUri)
            }

            if (isExiting) return@launch

            val latestMediaId = player.currentMediaItem?.mediaId
            if (mediaId != latestMediaId) {
                if (loadingAlbumArtMediaId == mediaId) {
                    loadingAlbumArtMediaId = null
                }
                return@launch
            }

            loadingAlbumArtMediaId = null

            if (bitmap != null) {
                albumArtCache.put(mediaId, bitmap)
                currentAlbumArtBitmap = bitmap
                currentAlbumArtMediaId = mediaId
            } else {
                currentAlbumArtBitmap = null
                currentAlbumArtMediaId = null
            }

            updateNotification()
        }
    }

    private fun refreshAlbumArt() {
        val currentMusic = syncCurrentMusicFromPlayer()
        val newMediaId = currentMusic?.id ?: player.currentMediaItem?.mediaId
        updateAlbumArt(newMediaId)
    }

    private fun decodeAlbumArtForNotification(albumUri: Uri?): Bitmap? {
        if (albumUri == null) return null

        return try {
            val targetSizePx = 256

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, albumUri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val srcWidth = info.size.width
                    val srcHeight = info.size.height

                    val longestSide = maxOf(srcWidth, srcHeight)
                    if (longestSide > targetSizePx) {
                        val scale = targetSizePx.toFloat() / longestSide.toFloat()
                        val targetWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
                        val targetHeight = (srcHeight * scale).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetWidth, targetHeight)
                    }
                }
            } else {
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                contentResolver.openInputStream(albumUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, boundsOptions)
                }

                val sampleSize = calculateInSampleSize(
                    width = boundsOptions.outWidth,
                    height = boundsOptions.outHeight,
                    reqWidth = targetSizePx,
                    reqHeight = targetSizePx
                )

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }

                contentResolver.openInputStream(albumUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOptions)
                }
            }
        } catch (e: Exception) {
            Log.e("MusicService", "album art decode failed", e)
            null
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        if (width <= 0 || height <= 0) return 1

        var inSampleSize = 1
        val halfWidth = width / 2
        val halfHeight = height / 2

        while ((halfWidth / inSampleSize) >= reqWidth &&
            (halfHeight / inSampleSize) >= reqHeight
        ) {
            inSampleSize *= 2
        }

        return inSampleSize.coerceAtLeast(1)
    }

    private fun updateNotification() {
        if (isExiting || !MusicServiceState.isForegroundStarted) return

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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("MusicService", "startForeground로 알림 갱신 실패", e)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }
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

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "musicPlayerChannel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val collapsedNotificationView =
            RemoteViews(this.packageName, R.layout.notification_player_collapsed)
        val expandedNotificationView =
            RemoteViews(this.packageName, R.layout.notification_player_extended)

        val prevIntent = Intent(this, MusicService::class.java).run {
            action = SKIP_PREV
            PendingIntent.getService(
                this@MusicService,
                REQ_CODE_PREV,
                this,
                PendingIntent.FLAG_IMMUTABLE
            )
        }
        val playIntent = Intent(this, MusicService::class.java).run {
            action = PLAY
            PendingIntent.getService(
                this@MusicService,
                REQ_CODE_PLAY,
                this,
                PendingIntent.FLAG_IMMUTABLE
            )
        }
        val pauseIntent = Intent(this, MusicService::class.java).run {
            action = PAUSE
            PendingIntent.getService(
                this@MusicService,
                REQ_CODE_PAUSE,
                this,
                PendingIntent.FLAG_IMMUTABLE
            )
        }
        val nextIntent = Intent(this, MusicService::class.java).run {
            action = SKIP_NEXT
            PendingIntent.getService(
                this@MusicService,
                REQ_CODE_NEXT,
                this,
                PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notificationTouchIntent = Intent(this, MainActivity::class.java).run {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            PendingIntent.getActivity(
                this@MusicService,
                REQ_CODE_NOTIFICATION_TOUCH,
                this,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        val isPlaying = player.playWhenReady
        setPlayPauseButtonState(
            expandedNotificationView = expandedNotificationView,
            isPlaying = isPlaying,
            playIntent = playIntent,
            pauseIntent = pauseIntent
        )

        expandedNotificationView.setOnClickPendingIntent(R.id.btnNotificationExtendedPrev, prevIntent)
        expandedNotificationView.setOnClickPendingIntent(R.id.btnNotificationExtendedNext, nextIntent)

        val currentMusic = playerModel.currentMusic
        setMusicInNotification(
            collapsedNotificationView = collapsedNotificationView,
            expandedNotificationView = expandedNotificationView,
            music = currentMusic
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setShowWhen(false)
            .setSmallIcon(R.drawable.ic_music_note)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setColor(ContextCompat.getColor(this, R.color.dark_black))
            .setColorized(true)
            .setCustomContentView(collapsedNotificationView)
            .setCustomBigContentView(expandedNotificationView)
            .setContentIntent(notificationTouchIntent)
            .setOngoing(true)
            .build()
    }

    private fun setPlayPauseButtonState(
        expandedNotificationView: RemoteViews,
        isPlaying: Boolean,
        playIntent: PendingIntent,
        pauseIntent: PendingIntent
    ) {
        if (isPlaying) {
            expandedNotificationView.setImageViewResource(
                R.id.btnNotificationExtendedPlay,
                R.drawable.ic_pause_button
            )
            expandedNotificationView.setOnClickPendingIntent(
                R.id.btnNotificationExtendedPlay,
                pauseIntent
            )
        } else {
            expandedNotificationView.setImageViewResource(
                R.id.btnNotificationExtendedPlay,
                R.drawable.ic_play_button
            )
            expandedNotificationView.setOnClickPendingIntent(
                R.id.btnNotificationExtendedPlay,
                playIntent
            )
        }
    }

    private fun setMusicInNotification(
        collapsedNotificationView: RemoteViews,
        expandedNotificationView: RemoteViews,
        music: MusicModel?
    ) {
        val currentMusicId = music?.id
        val albumBitmap = if (currentAlbumArtMediaId == currentMusicId) {
            currentAlbumArtBitmap
        } else {
            null
        }
        val isAlbumArtLoading = loadingAlbumArtMediaId == currentMusicId

        when {
            albumBitmap != null -> {
                collapsedNotificationView.setViewVisibility(R.id.ivNotificationAlbum, View.VISIBLE)
                expandedNotificationView.setViewVisibility(R.id.ivNotificationExtendedAlbum, View.VISIBLE)

                collapsedNotificationView.setImageViewBitmap(R.id.ivNotificationAlbum, albumBitmap)
                expandedNotificationView.setImageViewBitmap(
                    R.id.ivNotificationExtendedAlbum,
                    albumBitmap
                )
            }

            isAlbumArtLoading -> {
                collapsedNotificationView.setViewVisibility(R.id.ivNotificationAlbum, View.VISIBLE)
                expandedNotificationView.setViewVisibility(R.id.ivNotificationExtendedAlbum, View.VISIBLE)

                collapsedNotificationView.setImageViewResource(
                    R.id.ivNotificationAlbum,
                    android.R.color.transparent
                )
                expandedNotificationView.setImageViewResource(
                    R.id.ivNotificationExtendedAlbum,
                    android.R.color.transparent
                )
            }

            else -> {
                collapsedNotificationView.setViewVisibility(R.id.ivNotificationAlbum, View.VISIBLE)
                expandedNotificationView.setViewVisibility(R.id.ivNotificationExtendedAlbum, View.VISIBLE)

                collapsedNotificationView.setImageViewResource(
                    R.id.ivNotificationAlbum,
                    R.drawable.ic_no_album_image
                )
                expandedNotificationView.setImageViewResource(
                    R.id.ivNotificationExtendedAlbum,
                    R.drawable.ic_no_album_image
                )
            }
        }

        collapsedNotificationView.setTextViewText(R.id.tvNotificationTitle, music?.title ?: "")
        collapsedNotificationView.setTextViewText(R.id.tvNotificationArtist, music?.artist ?: "")

        expandedNotificationView.setTextViewText(
            R.id.tvNotificationExtendedTitle,
            music?.title ?: ""
        )
        expandedNotificationView.setTextViewText(
            R.id.tvNotificationExtendedArtist,
            music?.artist ?: ""
        )
        expandedNotificationView.setImageViewResource(
            R.id.btnNotificationExtendedPrev,
            R.drawable.ic_prev_button
        )
        expandedNotificationView.setImageViewResource(
            R.id.btnNotificationExtendedNext,
            R.drawable.ic_next_button
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        exitPlayer()
        super.onTaskRemoved(rootIntent)
    }

    private fun exitPlayer() {
        if (isExiting) return
        isExiting = true

        player.removeListener(playerListener)
        player.pause()
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()

        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)

        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (task in am.appTasks) {
                task.finishAndRemoveTask()
            }
        } catch (e: Exception) {
            Log.e("MusicService", "finishAndRemoveTask 실패", e)
        }

        stopSelf()
    }

    override fun onDestroy() {
        MusicServiceState.reset()

        albumArtLoadJob?.cancel()
        serviceScope.cancel()
        currentAlbumArtBitmap = null
        currentAlbumArtMediaId = null

        stopForeground(STOP_FOREGROUND_REMOVE)

        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)

        if (::mediaLibrarySession.isInitialized) {
            mediaLibrarySession.release()
        }

        EventBus.getInstance().unregister(this)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "MusicChannel"
        const val NOTIFICATION_ID = 1

        const val PLAY = "com.hero.ziggymusic.PLAY"
        const val PAUSE = "com.hero.ziggymusic.PAUSE"
        const val SKIP_PREV = "com.hero.ziggymusic.SKIP_PREV"
        const val SKIP_NEXT = "com.hero.ziggymusic.SKIP_NEXT"
        const val ACTION_REFRESH_NOTIFICATION = "com.hero.ziggymusic.REFRESH_NOTIFICATION"
        const val EXTRA_MEDIA_ID = "extra_media_id"

        const val REQ_CODE_PREV = 100
        const val REQ_CODE_PLAY = 101
        const val REQ_CODE_PAUSE = 102
        const val REQ_CODE_NEXT = 103
        const val REQ_CODE_CLOSE = 104
        const val REQ_CODE_NOTIFICATION_TOUCH = 105
    }
}
