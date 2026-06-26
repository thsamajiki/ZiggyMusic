package com.hero.ziggymusic.presentation.common.ext

import android.content.Context
import com.hero.ziggymusic.playback.queue.PlaybackQueueSource
import com.hero.ziggymusic.presentation.main.MainActivity
import dagger.hilt.android.internal.managers.FragmentComponentManager

fun Context.playMusic(
    id: String,
    queueSource: PlaybackQueueSource = PlaybackQueueSource.MUSIC_TRACKS
) {
    val activity = FragmentComponentManager.findActivity(this)

    (activity as? MainActivity)?.playMusic(
        id = id,
        queueSource = queueSource
    )
}
