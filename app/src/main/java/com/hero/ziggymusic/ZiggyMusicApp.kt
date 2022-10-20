package com.hero.ziggymusic

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZiggyMusicApp : Application() {

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