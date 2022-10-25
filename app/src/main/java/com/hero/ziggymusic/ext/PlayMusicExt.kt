package com.hero.ziggymusic.ext

import android.content.Context
import com.hero.ziggymusic.view.main.MainActivity
import dagger.hilt.android.internal.managers.FragmentComponentManager

fun Context.playMusic(musicId: String) {
    val activity = FragmentComponentManager.findActivity(this)

    (activity as? MainActivity)?.playMusic(musicId)
}