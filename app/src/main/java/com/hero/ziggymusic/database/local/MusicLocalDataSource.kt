package com.hero.ziggymusic.database.local

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.music.entity.MusicTrackEntity
import kotlinx.coroutines.flow.Flow

interface MusicLocalDataSource {
    // MediaStore에서 현재 음원 목록 조회
    suspend fun getMusicTracksFromMediaStore(): List<MusicTrackEntity>

    // Room 캐시를 최신 목록으로 교체
    suspend fun replaceCachedMusicTracks(musicTrackList: List<MusicTrackEntity>)

    suspend fun getMusicTrackCount(): Int

    suspend fun getMusicTrack(id: String) : MusicTrackEntity?

    fun observeMusicTracks(): LiveData<List<MusicTrackEntity>>

    fun observeFavoriteMusicTracks(): LiveData<List<MusicTrackEntity>>

    fun getFavoriteMusicTrackIdList(): LiveData<List<String>>

    fun observeMediaStoreMusicChanges(): Flow<Unit>

    suspend fun addMusicTrackToFavorites(id: String)

    suspend fun removeMusicTrackFromFavorites(id: String)
}
