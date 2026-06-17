package com.hero.ziggymusic.domain.music.repository

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.music.entity.MusicModel
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun loadMusics()

    suspend fun getMusicCount(): Int

    suspend fun getMusic(key: String): MusicModel?

    fun getAllMusic(): LiveData<List<MusicModel>>

    fun getFavorites(): LiveData<List<MusicModel>>

    fun observeMusicChanges(): Flow<Unit>

    suspend fun addMusicToFavorites(musicModel: MusicModel)

    suspend fun removeMusicFromFavorites(musicModel: MusicModel)
}
