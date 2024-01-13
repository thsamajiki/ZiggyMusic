package com.hero.ziggymusic

import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZiggyMusicApp : Application() {

    val exoPlayer by lazy { ExoPlayer.Builder(this).build() }

    companion object {
        lateinit var instance : ZiggyMusicApp

        fun getInstance() : Application {
            return instance
        }
    }

    override fun onCreate() {
        super.onCreate()

        instance = this
    }
}