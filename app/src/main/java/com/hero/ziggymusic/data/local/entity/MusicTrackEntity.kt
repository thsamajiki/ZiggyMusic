package com.hero.ziggymusic.data.local.entity

import android.net.Uri
import android.os.Parcelable
import android.provider.MediaStore
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import androidx.core.net.toUri

@Parcelize
@Entity(tableName = "music_tracks")
data class MusicTrackEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,    // 음원 자체의 ID
    @ColumnInfo(name = "title")
    val title: String? = "",   // 음원 제목
    @ColumnInfo(name = "artist")
    val artist: String? = "",  // 음원 아티스트
    @ColumnInfo(name = "album_id")
    val albumId: String? = "",    // 앨범 이미지 ID
    @ColumnInfo(name = "album")
    val album: String? = "",    // 앨범명
    @ColumnInfo(name = "duration")
    val duration: Long? = 0,     // 음원 재생 시간
    @ColumnInfo(name = "date_added")
    val dateAdded: Long,    // MediaStore에 추가된 시각(초 단위)
    @ColumnInfo(name = "is_playing")
    val isPlaying: Boolean = false,
) : Parcelable {
    fun getMusicTrackUri(): Uri {
        return Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }

    fun getAlbumArtUri(): Uri? {
        if (albumId.isNullOrBlank()) return null
        return "content://media/external/audio/albumart/${albumId}".toUri()
    }
}
