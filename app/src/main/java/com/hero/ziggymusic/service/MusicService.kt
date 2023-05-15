package com.hero.ziggymusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.common.eventbus.Subscribe
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.entity.PlayerModel
import com.hero.ziggymusic.event.Event
import com.hero.ziggymusic.event.EventBus
import com.hero.ziggymusic.view.main.player.PlayerFragment
import java.io.IOException

class MusicService : Service() {
    private var musicPlayer: ExoPlayer? = null
    private var playerModel: PlayerModel = PlayerModel()

    companion object {
        const val CHANNEL_ID = "MusicChannel" //알림 채널 ID

        const val PLAY_OR_PAUSE = "com.hero.ziggymusic.PLAY_OR_PAUSE" // Notification 에서 재생 / 일시 정지 버튼을 누를 시
        const val SKIP_PREV = "com.hero.ziggymusic.SKIP_PREV" // Notification 에서 이전 곡 버튼을 누를 시
        const val SKIP_NEXT = "com.hero.ziggymusic.SKIP_NEXT" // Notification 에서 다음 곡 버튼을 누를 시
        const val CLOSE = "com.hero.ziggymusic.CLOSE" // 닫기 버튼을 누를 시
    }

    private lateinit var remoteNotificationLayout: RemoteViews
    private lateinit var remoteNotificationExtendedLayout: RemoteViews

    override fun onCreate() {
        super.onCreate()
        EventBus.getInstance().register(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        musicPlayer = ExoPlayer.Builder(this).build()

        createNotificationChannel() // 알림 채널 생성

        // Notification 에 사용할 RemoteViews 생성
        remoteNotificationLayout = RemoteViews(this.packageName, R.layout.notification_player)
        remoteNotificationExtendedLayout = RemoteViews(this.packageName, R.layout.notification_player_extended)

        Log.d("MusicServiceAction", "action = ${intent?.action}")

        when(intent?.action) {
            null -> {
                val notification = createNotification() // Notification 생성
                startForeground(1, notification) // Foreground 에서 실행
            }
            PLAY_OR_PAUSE -> { // Notification 에서 재생 / 일시 정지 버튼을 누를 시
                if(musicPlayer?.isPlaying == true) { musicPlayer?.pause() } else { musicPlayer?.play() }
            }
            SKIP_PREV -> { // Notification 에서 이전 곡 버튼을 누를 시
                musicPlayer?.run {
                    val prevIndex = if (currentMediaItemIndex - 1 in 0 until mediaItemCount) {
                        currentMediaItemIndex - 1
                    } else {
                        0 // 0번에서 뒤로 갈때
                    }

                    seekTo(prevIndex, 0)
                }
            }
            SKIP_NEXT -> { // Notification 에서 다음 곡 버튼을 누를 시
                musicPlayer?.run {
                    val nextIndex = if (currentMediaItemIndex + 1 in 0 until mediaItemCount) {
                        currentMediaItemIndex + 1
                    } else {
                        0
                    }

                    seekTo(nextIndex, 0)
                }
            }
            CLOSE -> { // Notification 에서 닫기 버튼 누를 시
                stopSelf()
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }



    override fun onBind(intent: Intent): IBinder {
        return Binder()
    }

    @Subscribe
    fun doEvent(event: Event) {
        when (event.getEvent()) {
            // 다른 곳에서 플레이어가 정지될 수 있기 때문에 넣어줌
            "STOP" -> { stopSelf() }
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
            action = PLAY_OR_PAUSE
            PendingIntent.getService(this@MusicService, 1, this, PendingIntent.FLAG_IMMUTABLE)
        }
        val nextIntent = Intent(this, MusicService::class.java).run {
            action = SKIP_NEXT
            PendingIntent.getService(this@MusicService, 2, this, PendingIntent.FLAG_IMMUTABLE)
        }
        val closeIntent = Intent(this, MusicService::class.java).run {
            action = CLOSE
            PendingIntent.getService(this@MusicService, 3, this, PendingIntent.FLAG_IMMUTABLE)
        }
        val notificationTouchIntent = Intent(this, PlayerFragment::class.java).run {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            PendingIntent.getActivity(this@MusicService, 4, this, PendingIntent.FLAG_IMMUTABLE)
        }

        // setOnClickPendingIntent()를 이용해서 클릭 리스너를 달아준다.
        remoteNotificationLayout.setOnClickPendingIntent(R.id.btnNotificationPrev, prevIntent)
        remoteNotificationLayout.setOnClickPendingIntent(R.id.btnNotificationPlay, playIntent)
        remoteNotificationLayout.setOnClickPendingIntent(R.id.btnNotificationNext, nextIntent)

        remoteNotificationExtendedLayout.setOnClickPendingIntent(
            R.id.btnNotificationExtendedPrev,
            prevIntent
        )
        remoteNotificationExtendedLayout.setOnClickPendingIntent(
            R.id.btnNotificationExtendedPlay,
            playIntent
        )
        remoteNotificationExtendedLayout.setOnClickPendingIntent(
            R.id.btnNotificationExtendedNext,
            nextIntent
        )
        remoteNotificationExtendedLayout.setOnClickPendingIntent(
            R.id.btnNotificationExtendedClose,
            closeIntent
        )

        val music = playerModel.currentMusic

        // 알림으로 사용할 레이아웃에 음원 정보를 설정
        setMusicInNotification(music)

        // 알림 생성
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setShowWhen(false)
            .setSmallIcon(R.drawable.ic_music_note)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteNotificationLayout)
            .setCustomBigContentView(remoteNotificationExtendedLayout)
            .setContentIntent(notificationTouchIntent)
            .build()
    }

    private fun setMusicInNotification(music: MusicModel?) {
        var bitmap: Bitmap? = null

        try {
            bitmap = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // P 이상인 경우
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, music?.getAlbumUri() ?: Uri.parse("")))

            } else { // 그 이하인 경우
                // API 29부터 MediaStore.Images.Media.getBitmap()은 deprecated 됨
                MediaStore.Images.Media.getBitmap(contentResolver, music?.getAlbumUri()!!)
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
                    R.drawable.ic_default_album_art
                )
                remoteNotificationExtendedLayout.setImageViewResource(
                    R.id.ivNotificationExtendedAlbum,
                    R.drawable.ic_default_album_art
                )
            }
        }

        // 시작 / 일시 정지 버튼 설정
        if(musicPlayer?.isPlaying == true) {
            remoteNotificationLayout.setImageViewResource(R.id.btnNotificationPlay, R.drawable.ic_pause_button)
            remoteNotificationExtendedLayout.setImageViewResource(
                R.id.btnNotificationExtendedPlay,
                R.drawable.ic_pause_button
            )
        } else {
            remoteNotificationLayout.setImageViewResource(R.id.btnNotificationPlay, R.drawable.ic_play_button)
            remoteNotificationExtendedLayout.setImageViewResource(
                R.id.btnNotificationExtendedPlay,
                R.drawable.ic_play_button
            )
        }

        remoteNotificationLayout.setTextViewText(R.id.tvNotificationTitle, music?.musicTitle)
        remoteNotificationLayout.setTextViewText(R.id.tvNotificationArtist, music?.musicArtist)
        remoteNotificationLayout.setImageViewResource(R.id.btnNotificationPrev, R.drawable.ic_previous_button)
        remoteNotificationLayout.setImageViewResource(R.id.btnNotificationNext, R.drawable.ic_next_button)

        remoteNotificationExtendedLayout.setTextViewText(R.id.tvNotificationExtendedTitle, music?.musicTitle)
        remoteNotificationExtendedLayout.setTextViewText(R.id.tvNotificationExtendedArtist, music?.musicArtist)
        remoteNotificationExtendedLayout.setImageViewResource(
            R.id.btnNotificationExtendedPrev,
            R.drawable.ic_previous_button
        )
        remoteNotificationExtendedLayout.setImageViewResource(
            R.id.btnNotificationExtendedNext,
            R.drawable.ic_next_button
        )
        remoteNotificationExtendedLayout.setImageViewResource(
            R.id.btnNotificationExtendedClose,
            R.drawable.ic_close
        )
    }

    // 서비스가 완전히 종료되면
    override fun onDestroy() {
        musicPlayer?.stop() // 플레이어 중단
        EventBus.getInstance().unregister(this)
        super.onDestroy()
    }
}