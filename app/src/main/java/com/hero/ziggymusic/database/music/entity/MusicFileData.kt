package com.hero.ziggymusic.database.music.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "music_table")
data class MusicFileData(
    @PrimaryKey(autoGenerate = true)
    val id: String?,    // 음원 자체의 ID
    @ColumnInfo(name = "musicTitle")
    val musicTitle: String? = "",
    @ColumnInfo(name = "musicArtist")
    val musicArtist: String? = "",
    @ColumnInfo(name = "albumId")
    val albumId: String? = "",    // 앨범 이미지 ID
    @ColumnInfo(name = "duration")
    val duration: Long? = 0
) : Parcelable {
    constructor(): this(null, "", "", "", null)
}

//class MusicFileData(id : String, title : String, artist : String, albumId : String, duration : Long) : Parcelable
//{
//    @PrimaryKey(autoGenerate = true)
//    var id: String = ""    // 음원 자체의 ID
//
//    var musicTitle: String = ""
//    var musicArtist: String = ""
//    var albumId: String = ""    // 앨범 이미지 ID
//    var duration: Long = 0
////    var musicAddedDate: LocalDate = LocalDate.now()
////    var musicModifiedDate: LocalDate = LocalDate.now()
//
//    constructor(parcel: Parcel) : this(parcel.readString().toString(),
//        parcel.readString().toString(), parcel.readString().toString(), parcel.readString().toString(), parcel.readLong()) {
//        parcel.run {
//            id = parcel.readString().toString()
//            musicTitle = parcel.readString().toString()
//            musicArtist = parcel.readString().toString()
//            albumId = parcel.readString().toString()
//            duration = parcel.readLong()
//        }
//
//    }
//
//    fun getMusicFileUri(): Uri {
//        return Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id) // Base Uri
//    }
//
//    fun getAlbumUri(): Uri {
//        // return MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
//        return Uri.parse("content://media/external/audio/albumart/$albumId")
//    }
//
//    override fun writeToParcel(parcel: Parcel, flags: Int) {
//        parcel.writeString(id)
//        parcel.writeString(musicTitle)
//        parcel.writeString(musicArtist)
//        parcel.writeString(albumId)
//        parcel.writeValue(duration)
//    }
//
//    override fun describeContents(): Int {
//        return 0
//    }
//
//    companion object CREATOR : Parcelable.Creator<MusicFileData> {
//        override fun createFromParcel(parcel: Parcel): MusicFileData {
//            return MusicFileData(parcel)
//        }
//
//        override fun newArray(size: Int): Array<MusicFileData?> {
//            return arrayOfNulls(size)
//        }
//    }
//}