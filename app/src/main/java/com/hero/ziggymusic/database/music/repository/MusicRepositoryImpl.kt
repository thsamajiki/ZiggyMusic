package com.hero.ziggymusic.database.music.repository

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.data.music.local.MusicLocalDataSource
import com.hero.ziggymusic.database.music.entity.MusicModel
import javax.inject.Inject

// 앱에서 사용하는 데이터와 그 데이터 통신을 하는 역할

class MusicRepositoryImpl @Inject constructor(
    private val musicLocalDataSource: MusicLocalDataSource
) : MusicRepository {

    override suspend fun loadMusics() {
        return musicLocalDataSource.loadMusics()
    }

    override suspend fun getMusic(key: String): MusicModel? {
        return musicLocalDataSource.getMusic(key)
    }

    override fun getAllMusic(): LiveData<List<MusicModel>> {
        return musicLocalDataSource.getAllMusic()
    }

    override fun getMyPlaylistMusics(): LiveData<List<MusicModel>> {
        return musicLocalDataSource.getMyPlaylistMusics()
    }

    override suspend fun addMusicToMyPlaylist(musicModel: MusicModel) {
        musicLocalDataSource.addMusicToMyPlaylist(musicModel)
    }

    override suspend fun deleteMusicFromMyPlaylist(musicModel: MusicModel) {
        musicLocalDataSource.deleteMusicFromMyPlaylist(musicModel)
    }
}