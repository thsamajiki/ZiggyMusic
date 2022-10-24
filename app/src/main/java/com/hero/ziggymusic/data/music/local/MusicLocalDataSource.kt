package com.hero.ziggymusic.data.music.local

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.music.entity.MusicModel

interface MusicLocalDataSource {
    suspend fun loadMusics()

    suspend fun getMusic(key: String) : MusicModel?

    suspend fun getAllMusic(): LiveData<List<MusicModel>>

    suspend fun getMyPlayListMusics(): LiveData<List<MusicModel>>
}