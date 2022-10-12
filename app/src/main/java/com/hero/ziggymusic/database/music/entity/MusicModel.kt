package com.hero.ziggymusic.database.music.entity

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "music_table")
data class MusicModel (
    @PrimaryKey(autoGenerate = true)
    val id: String,    // 음원 자체의 ID

    var musicTitle: String? = "",
    var musicArtist: String? = "",
    val albumId: String? = "",    // 앨범 이미지 ID
    val duration: Long? = 0
)