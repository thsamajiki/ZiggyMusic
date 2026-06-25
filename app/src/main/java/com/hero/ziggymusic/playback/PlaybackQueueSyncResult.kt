package com.hero.ziggymusic.playback

data class PlaybackQueueSyncResult(
    val selectedMediaId: String?,
    val queueChanged: Boolean
)
