package com.hero.ziggymusic.domain.music.entity

import android.os.Parcelable
import androidx.room.PrimaryKey
import com.hero.ziggymusic.database.music.entity.MusicModel
import kotlinx.parcelize.Parcelize

@Parcelize
data class MusicFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: String,    // 음원 자체의 ID

    var musicTitle: String? = "",
    var musicArtist: String? = "",
    val albumId: String? = "",    // 앨범 이미지 ID
    val duration: Long? = 0
) : Parcelable {

    fun toEntity(): MusicFileEntity? {
        return MusicFileEntity(id,
            musicTitle,
            musicArtist,
            albumId,
            duration)
    }

    fun toData(musicFileEntity: MusicFileEntity): MusicModel {
        return MusicModel(
            musicFileEntity.id,
            musicFileEntity.musicTitle,
            musicFileEntity.musicArtist,
            musicFileEntity.albumId,
            musicFileEntity.duration
        )
    }
}