package com.hero.ziggymusic.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.entity.PlayerModel
import com.hero.ziggymusic.view.main.MainActivity
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MusicService : MediaLibraryService() {
    @Inject
    lateinit var player: ExoPlayer

    private val playerModel: PlayerModel = PlayerModel.getInstance()

    @Volatile
    private var isExiting: Boolean = false

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

            updateNotification()
        }
    }

    private lateinit var mediaLibrarySession: MediaLibrarySession

    override fun onCreate() {
        super.onCreate()

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player,
            object : MediaLibrarySession.Callback {
            }).build()

        createNotificationChannel()

        startForegroundCompat()
        MusicServiceState.onForegroundEntered()

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
            ACTION_REFRESH_NOTIFICATION, null -> {
                val mediaId = intent?.getStringExtra(EXTRA_MEDIA_ID)
                    ?: player.currentMediaItem?.mediaId

                mediaId?.let(playerModel::changedMusic)
                updateNotification()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notification = createNotification()

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
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("MusicService", "Notification 갱신 실패", e)
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
        val prevIntent = mediaButtonPendingIntent(KeyEvent.KEYCODE_MEDIA_PREVIOUS, REQ_CODE_PREV)
        val playIntent = mediaButtonPendingIntent(KeyEvent.KEYCODE_MEDIA_PLAY, REQ_CODE_PLAY)
        val pauseIntent = mediaButtonPendingIntent(KeyEvent.KEYCODE_MEDIA_PAUSE, REQ_CODE_PAUSE)
        val nextIntent = mediaButtonPendingIntent(KeyEvent.KEYCODE_MEDIA_NEXT, REQ_CODE_NEXT)
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
        val currentMusic = playerModel.currentMusic ?: syncCurrentMusicFromPlayer()
        val title = player.currentMediaItem?.mediaMetadata?.title
            ?: currentMusic?.title
            ?: getString(R.string.app_name)
        val artist = player.currentMediaItem?.mediaMetadata?.artist
            ?: currentMusic?.artist
            ?: ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setShowWhen(false)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(artist)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaLibrarySession)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(R.drawable.ic_prev_button, "Previous", prevIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause_button else R.drawable.ic_play_button,
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) pauseIntent else playIntent
            )
            .addAction(R.drawable.ic_next_button, "Next", nextIntent)
            .setColor(ContextCompat.getColor(this, R.color.dark_black))
            .setColorized(true)
            .setContentIntent(notificationTouchIntent)
            .setOngoing(true)
            .build()
    }

    private fun mediaButtonPendingIntent(keyCode: Int, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setClass(this@MusicService, MusicMediaButtonReceiver::class.java)
            putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            )
        }

        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE
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

        stopForeground(STOP_FOREGROUND_REMOVE)

        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)

        if (::mediaLibrarySession.isInitialized) {
            mediaLibrarySession.release()
        }

        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "MusicChannel"
        const val NOTIFICATION_ID = 1

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
