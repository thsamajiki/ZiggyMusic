package com.hero.ziggymusic.playback.queue

data class PlaybackQueueSyncResult(
    val selectedMediaId: String?,
    val queueChanged: Boolean
)
