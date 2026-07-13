package com.hero.ziggymusic.data.music.source

import android.app.Application
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import com.hero.ziggymusic.data.local.mediastore.MediaStoreMusicObserver
import com.hero.ziggymusic.data.local.db.AppMusicTrackDatabase
import com.hero.ziggymusic.data.local.dao.MusicTrackDao
import com.hero.ziggymusic.data.local.dao.FavoriteMusicTracksDao
import com.hero.ziggymusic.data.local.entity.FavoriteMusicTrackEntity
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MusicLocalDataSourceImpl @Inject constructor(
    private val application: Application,
    private val appMusicTrackDatabase: AppMusicTrackDatabase,
    private val musicTrackDao: MusicTrackDao,
    private val favoriteMusicTracksDao: FavoriteMusicTracksDao,
    private val mediaStoreMusicObserver: MediaStoreMusicObserver,
) : MusicLocalDataSource {
    override suspend fun getMusicTracksFromMediaStore(): List<MusicTrackEntity> =
        withContext(Dispatchers.IO) {
            val musicTrackUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            // 1. 가져올 데이터 컬럼을 정의
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            // 2. 콘텐츠 리졸버에 해당 데이터 요청 (음원 목록에 있는 0번째 줄을 가리킴)
            // 3. 커서로 전달된 데이터를 꺼내서 저장
            val musicTrackList = mutableListOf<MusicTrackEntity>()

            application.contentResolver.query(
                musicTrackUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val albumTitleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val musicTrack = MusicTrackEntity(
                        id = cursor.getString(idColumn),
                        title = cursor.getString(titleColumn),
                        artist = cursor.getString(artistColumn),
                        albumId = cursor.getString(albumIdColumn),
                        album = cursor.getString(albumTitleColumn),
                        duration = cursor.getLong(durationColumn),
                        dateAdded = cursor.getLong(dateAddedColumn)
                    )

                    musicTrackList.add(musicTrack)
                }
            }

            musicTrackList
        }

    override suspend fun replaceCachedMusicTracks(
        musicTrackList: List<MusicTrackEntity>
    ) = withContext(Dispatchers.IO) {
        appMusicTrackDatabase.withTransaction {
            musicTrackDao.insertAllMusicTracks(musicTrackList)

            val musicTrackIdList = musicTrackList.map { it.id }

            if (musicTrackIdList.isEmpty()) {
                musicTrackDao.clearAll()
                favoriteMusicTracksDao.clearAll()
            } else {
                musicTrackDao.deleteMusicTracksExcept(musicTrackIdList)
                favoriteMusicTracksDao.deleteFavoriteTracksExcept(musicTrackIdList)
            }
        }
    }

    override suspend fun getMusicTrackCount(): Int = withContext(Dispatchers.IO) {
        musicTrackDao.getMusicTrackCount()
    }

    override suspend fun getMusicTrack(id: String): MusicTrackEntity? {
        return musicTrackDao.getMusicTrack(id)
    }

    override fun observeMusicTracks(): LiveData<List<MusicTrackEntity>> {
        return musicTrackDao.getMusicTracksFromMediaStore()
    }

    override fun observeFavoriteMusicTracks(): LiveData<List<MusicTrackEntity>> {
        return favoriteMusicTracksDao.getFavoriteMusicTracks()
    }

    override fun getFavoriteMusicTrackIdList(): LiveData<List<String>> {
        return favoriteMusicTracksDao.getFavoriteMusicTrackIdList()
    }

    override fun observeMediaStoreMusicChanges(): Flow<Unit> = mediaStoreMusicObserver.observeMusicChanges()

    override suspend fun addMusicTrackToFavorites(id: String) {
        favoriteMusicTracksDao.insertFavoriteMusicTrack(FavoriteMusicTrackEntity(id = id))
    }

    override suspend fun removeMusicTrackFromFavorites(id: String) {
        favoriteMusicTracksDao.deleteFavoriteMusicTrack(id)
    }
}
