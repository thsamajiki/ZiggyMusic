package com.hero.ziggymusic

import android.app.Application


class ZiggyMusicApp : Application() {

    init {
        instance = this
    }

    companion object {
        lateinit var instance : ZiggyMusicApp
    }

    override fun onCreate() {
        super.onCreate()
    }

    fun getInstance() : Application {
        return instance
    }
}