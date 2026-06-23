package com.hero.ziggymusic.database.music.repository

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.local.MusicLocalDataSource
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MusicRepositoryImpl @Inject constructor(
    private val musicLocalDataSource: MusicLocalDataSource
) : MusicRepository {
    override suspend fun loadMusics() {
        return musicLocalDataSource.loadMusics()
    }

    override suspend fun getMusicCount(): Int {
        return musicLocalDataSource.getMusicCount()
    }

    override suspend fun getMusic(id: String): MusicModel? {
        return musicLocalDataSource.getMusic(id)
    }

    override fun getAllMusic(): LiveData<List<MusicModel>> {
        return musicLocalDataSource.getAllMusic()
    }

    override fun getFavorites(): LiveData<List<MusicModel>> {
        return musicLocalDataSource.getFavorites()
    }

    override fun getFavoriteMusicIdList(): LiveData<List<String>> {
        return musicLocalDataSource.getFavoriteMusicIdList()
    }

    override fun observeMusicChanges(): Flow<Unit> = musicLocalDataSource.observeMusicChanges()

    override suspend fun addMusicToFavorites(id: String) {
        musicLocalDataSource.addMusicToFavorites(id)
    }

    override suspend fun removeMusicFromFavorites(id: String) {
        musicLocalDataSource.removeMusicFromFavorites(id)
    }
}
