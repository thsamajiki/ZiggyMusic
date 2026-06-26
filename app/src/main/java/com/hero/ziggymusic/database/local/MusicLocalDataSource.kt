package com.hero.ziggymusic.database.local

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.music.entity.MusicTrackEntity
import kotlinx.coroutines.flow.Flow

interface MusicLocalDataSource {
    // MediaStore에서 현재 음원 목록 조회
    suspend fun getMusicList(): List<MusicTrackEntity>

    // Room 캐시를 최신 목록으로 교체
    suspend fun replaceCachedMusicList(musicList: List<MusicTrackEntity>)

    suspend fun getMusicCount(): Int

    suspend fun getMusic(id: String) : MusicTrackEntity?

    fun getAllMusic(): LiveData<List<MusicTrackEntity>>

    fun getFavorites(): LiveData<List<MusicTrackEntity>>

    fun getFavoriteMusicIdList(): LiveData<List<String>>

    fun observeMusicChanges(): Flow<Unit>

    suspend fun addMusicToFavorites(id: String)

    suspend fun removeMusicFromFavorites(id: String)
}
