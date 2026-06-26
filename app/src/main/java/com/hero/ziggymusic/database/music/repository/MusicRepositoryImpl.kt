package com.hero.ziggymusic.database.music.repository

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.local.MusicLocalDataSource
import com.hero.ziggymusic.database.music.entity.MusicTrackEntity
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MusicRepositoryImpl @Inject constructor(
    private val musicLocalDataSource: MusicLocalDataSource
) : MusicRepository {
    override suspend fun getMusicList(): List<MusicTrackEntity> {
        return musicLocalDataSource.getMusicList()
    }

    override suspend fun replaceCachedMusicList(
        musicList: List<MusicTrackEntity>
    ) {
        return musicLocalDataSource.replaceCachedMusicList(musicList)
    }

    override suspend fun getMusicCount(): Int {
        return musicLocalDataSource.getMusicCount()
    }

    override suspend fun getMusic(id: String): MusicTrackEntity? {
        return musicLocalDataSource.getMusic(id)
    }

    override fun getAllMusic(): LiveData<List<MusicTrackEntity>> {
        return musicLocalDataSource.getAllMusic()
    }

    override fun getFavorites(): LiveData<List<MusicTrackEntity>> {
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
