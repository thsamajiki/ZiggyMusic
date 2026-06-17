package com.hero.ziggymusic.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.hero.ziggymusic.database.music.entity.MusicModel

// Player 큐에 등록된 mediaId 목록을 비교할 때 사용한다.
fun Player.currentMediaIds(): List<String> {
    return (0 until mediaItemCount).map { index ->
        getMediaItemAt(index).mediaId
    }
}

// 선택한 음원이 현재 Player 큐의 몇 번째 항목인지 찾는다.
fun Player.findMediaItemIndexById(mediaId: String): Int {
    return (0 until mediaItemCount).firstOrNull { index ->
        getMediaItemAt(index).mediaId == mediaId
    } ?: -1
}

// MusicModel을 Media3에서 재생 가능한 MediaItem으로 변환한다.
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
    // 앱의 기본 DataSource 설정을 유지하면서 로컬 음원 MediaSource를 생성한다.
    val dataSourceFactory = DefaultDataSource.Factory(context)

    return ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(toMediaItem())
}
