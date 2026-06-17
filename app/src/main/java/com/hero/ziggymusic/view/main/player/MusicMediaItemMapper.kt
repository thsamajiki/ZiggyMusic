package com.hero.ziggymusic.view.main.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.hero.ziggymusic.database.music.entity.MusicModel

fun Player.currentMediaIds(): List<String> {
    return (0 until mediaItemCount).map { index ->
        getMediaItemAt(index).mediaId
    }
}

fun Player.findMediaItemIndexById(mediaId: String): Int {
    return (0 until mediaItemCount).firstOrNull { index ->
        getMediaItemAt(index).mediaId == mediaId
    } ?: -1
}

fun MusicModel.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(getMusicFileUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(getAlbumUri())
                .build()
        )
        .build()
}

@OptIn(UnstableApi::class)
fun MusicModel.toProgressiveMediaSource(context: Context): ProgressiveMediaSource {
    val dataSourceFactory = DefaultDataSource.Factory(context)

    return ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(toMediaItem())
}
