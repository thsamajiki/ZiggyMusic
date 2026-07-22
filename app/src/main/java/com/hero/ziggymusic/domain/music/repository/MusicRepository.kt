package com.hero.ziggymusic.domain.music.repository

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity
import com.hero.ziggymusic.domain.music.model.FavoriteMusicTrack
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun getMusicTracksFromMediaStore(): List<MusicTrackEntity>

    suspend fun replaceCachedMusicTracks(trackList: List<MusicTrackEntity>)

    suspend fun getMusicTrackCount(): Int

    suspend fun getMusicTrack(id: String): MusicTrackEntity?

    fun observeMusicTracks(): LiveData<List<MusicTrackEntity>>

    fun observeFavoriteMusicTracks(): LiveData<List<FavoriteMusicTrack>>

    fun observeFavoriteTrackIdList(): LiveData<List<String>>

    fun observeMediaStoreMusicChanges(): Flow<Unit>

    suspend fun addMusicTrackToFavorites(id: String)

    suspend fun removeMusicTrackFromFavorites(id: String)
}
