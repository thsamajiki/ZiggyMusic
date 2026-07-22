package com.hero.ziggymusic.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity

data class FavoriteMusicTrackWithAddedAt(
    @Embedded
    val track: MusicTrackEntity,

    @ColumnInfo(name = "added_to_favorites_at")
    val addedToFavoritesAt: Long,
)
