package com.hero.ziggymusic

import android.app.Application
import com.hero.ziggymusic.audio.AudioDspChainHolder
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZiggyMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()

        instance = this

        runCatching { AudioDspChainHolder.ensureNativeDspChainInitialized(this) }
    }

    override fun onTerminate() {
        super.onTerminate()
        // 에뮬/디버그에서만 호출될 수 있지만, 정리 루틴은 두는 편이 안전
        AudioDspChainHolder.releaseNativeChain()
    }

    companion object {
        lateinit var instance : ZiggyMusicApp

        fun getInstance() : Application {
            return instance
        }
    }
}