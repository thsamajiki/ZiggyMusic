package com.hero.ziggymusic.database.local

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.music.entity.MusicModel

interface MusicLocalDataSource {
    suspend fun loadMusics()

    suspend fun getMusicCount(): Int

    suspend fun getMusic(key: String) : MusicModel?

    fun getAllMusic(): LiveData<List<MusicModel>>

    fun getFavorites(): LiveData<List<MusicModel>>

    suspend fun addMusicToFavorites(musicModel: MusicModel)

    suspend fun removeMusicFromFavorites(musicModel: MusicModel)
}
