package com.hero.ziggymusic.domain.music.model

import com.hero.ziggymusic.data.local.entity.MusicTrackEntity

data class FavoriteMusicTrack(
    val track: MusicTrackEntity,
    val addedToFavoritesAt: Long,
)
