package com.hero.ziggymusic.domain.music.repository

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.music.entity.MusicModel
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun getMusicList(): List<MusicModel>

    suspend fun replaceCachedMusicList(musicList: List<MusicModel>)

    suspend fun getMusicCount(): Int

    suspend fun getMusic(id: String): MusicModel?

    fun getAllMusic(): LiveData<List<MusicModel>>

    fun getFavorites(): LiveData<List<MusicModel>>

    fun getFavoriteMusicIdList(): LiveData<List<String>>

    fun observeMusicChanges(): Flow<Unit>

    suspend fun addMusicToFavorites(id: String)

    suspend fun removeMusicFromFavorites(id: String)
}
