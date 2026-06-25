package com.hero.ziggymusic.ext

import android.content.Context
import com.hero.ziggymusic.playback.PlaybackQueueSource
import com.hero.ziggymusic.view.main.MainActivity
import dagger.hilt.android.internal.managers.FragmentComponentManager

fun Context.playMusic(
    id: String,
    queueSource: PlaybackQueueSource = PlaybackQueueSource.MUSIC_LIST
) {
    val activity = FragmentComponentManager.findActivity(this)

    (activity as? MainActivity)?.playMusic(
        id = id,
        queueSource = queueSource
    )
}
