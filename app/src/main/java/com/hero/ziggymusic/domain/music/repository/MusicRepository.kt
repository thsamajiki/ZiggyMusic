package com.hero.ziggymusic.domain.music.repository

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.music.entity.MusicModel

interface MusicRepository {
    suspend fun loadMusics()

    suspend fun getMusic(key: String): MusicModel?

    fun getAllMusic(): LiveData<List<MusicModel>>

    fun getMyPlaylistMusics(): LiveData<List<MusicModel>>

    suspend fun addMusicToMyPlaylist(musicModel: MusicModel)

    suspend fun deleteMusicFromMyPlaylist(musicModel: MusicModel)
}