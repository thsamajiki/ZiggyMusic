package com.hero.ziggymusic.domain.music.repository

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.music.entity.MusicTrackEntity
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun getMusicTracksFromMediaStore(): List<MusicTrackEntity>

    suspend fun replaceCachedMusicTracks(trackList: List<MusicTrackEntity>)

    suspend fun getMusicTrackCount(): Int

    suspend fun getMusicTrack(id: String): MusicTrackEntity?

    fun observeMusicTracks(): LiveData<List<MusicTrackEntity>>

    fun observeFavoriteMusicTracks(): LiveData<List<MusicTrackEntity>>

    fun observeFavoriteTrackIdList(): LiveData<List<String>>

    fun observeMediaStoreMusicChanges(): Flow<Unit>

    suspend fun addMusicTrackToFavorites(id: String)

    suspend fun removeMusicTrackFromFavorites(id: String)
}
