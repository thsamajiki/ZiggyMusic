package com.hero.ziggymusic

import android.app.Application


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