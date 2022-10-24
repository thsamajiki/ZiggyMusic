package com.hero.ziggymusic.database.music.entity

import android.net.Uri
import android.os.Parcelable
import android.provider.MediaStore
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "music_table")
data class MusicModel (
    @PrimaryKey
    val id: String,    // 음원 자체의 ID
    @ColumnInfo(name = "title")
    val musicTitle: String? = "",   // 음원 제목
    @ColumnInfo(name = "artist")
    val musicArtist: String? = "",  // 음원 아티스트
    @ColumnInfo(name = "album_id")
    val albumId: String? = "",    // 앨범 이미지 ID
    @ColumnInfo(name = "duration")
    val duration: Long? = 0,     // 음원 재생 시간
    @ColumnInfo(name = "is_playing")
    val isPlaying: Boolean = false
) : Parcelable {

    fun getMusicFileUri(): Uri {
        return Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }

    fun getAlbumUri(): Uri {
        return Uri.parse("content://media/external/audio/albumart/${albumId}")
    }
}