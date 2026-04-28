package com.hero.ziggymusic

import android.app.Application
import android.app.NotificationManager
import com.hero.ziggymusic.audio.AudioDspChainHolder
import com.hero.ziggymusic.service.MusicService
import com.hero.ziggymusic.service.MusicServiceState
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZiggyMusicApp : Application() {
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()

        instance = this

        // 이전 비정상 종료로 남아 있을 수 있는 stale Notification 정리
        cancelStaleMusicNotification()

        // 프로세스가 죽기 직전에 Notification 제거 시도
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                cancelStaleMusicNotification()
            }
            defaultExceptionHandler?.uncaughtException(thread, throwable)
        }

        runCatching { AudioDspChainHolder.ensureNativeDspChainInitialized(this) }
    }

    override fun onTerminate() {
        super.onTerminate()
        // 에뮬/디버그에서만 호출될 수 있지만, 정리 루틴은 두는 편이 안전
        AudioDspChainHolder.releaseNativeChain()
    }

    private fun cancelStaleMusicNotification() {
        MusicServiceState.reset()
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(MusicService.NOTIFICATION_ID)
    }

    companion object {
        lateinit var instance : ZiggyMusicApp

        fun getInstance() : Application {
            return instance
        }
    }
}
