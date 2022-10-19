package com.hero.ziggymusic.database.music.repository

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.data.music.local.MusicLocalDataSource
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.domain.music.repository.MusicRepository

// 앱에서 사용하는 데이터와 그 데이터 통신을 하는 역할
class MusicRepositoryImpl(
    private val musicLocalDataSource: MusicLocalDataSource
) : MusicRepository {

    companion object {
        private var instance: MusicRepository? = null

        fun getInstance(musicLocalDataSource: MusicLocalDataSource): MusicRepository {
            if (instance == null) {
                instance = MusicRepositoryImpl(musicLocalDataSource)
            }

            return instance!!
        }
    }

    override suspend fun loadMusics() {
        return musicLocalDataSource.loadMusics()
    }

    override suspend fun getMusic(key: String): MusicModel? {
        return musicLocalDataSource.getMusic(key)
    }

    override suspend fun getAllMusic(): LiveData<List<MusicModel>> {
        return musicLocalDataSource.getAllMusic()
    }
}