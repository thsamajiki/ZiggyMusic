package com.hero.ziggymusic.data.music.repository

import androidx.lifecycle.LiveData
import com.hero.ziggymusic.data.music.source.MusicLocalDataSource
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MusicRepositoryImpl @Inject constructor(
    private val musicLocalDataSource: MusicLocalDataSource
) : MusicRepository {
    override suspend fun getMusicTracksFromMediaStore(): List<MusicTrackEntity> {
        return musicLocalDataSource.getMusicTracksFromMediaStore()
    }

    override suspend fun replaceCachedMusicTracks(
        trackList: List<MusicTrackEntity>
    ) {
        return musicLocalDataSource.replaceCachedMusicTracks(trackList)
    }

    override suspend fun getMusicTrackCount(): Int {
        return musicLocalDataSource.getMusicTrackCount()
    }

    override suspend fun getMusicTrack(id: String): MusicTrackEntity? {
        return musicLocalDataSource.getMusicTrack(id)
    }

    override fun observeMusicTracks(): LiveData<List<MusicTrackEntity>> {
        return musicLocalDataSource.observeMusicTracks()
    }

    override fun observeFavoriteMusicTracks(): LiveData<List<MusicTrackEntity>> {
        return musicLocalDataSource.observeFavoriteMusicTracks()
    }

    override fun observeFavoriteTrackIdList(): LiveData<List<String>> {
        return musicLocalDataSource.getFavoriteMusicTrackIdList()
    }

    override fun observeMediaStoreMusicChanges(): Flow<Unit> = musicLocalDataSource.observeMediaStoreMusicChanges()

    override suspend fun addMusicTrackToFavorites(id: String) {
        musicLocalDataSource.addMusicTrackToFavorites(id)
    }

    override suspend fun removeMusicTrackFromFavorites(id: String) {
        musicLocalDataSource.removeMusicTrackFromFavorites(id)
    }
}
