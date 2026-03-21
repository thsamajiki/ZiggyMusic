package com.hero.ziggymusic.database.local

import android.app.Application
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.music.dao.MusicFileDao
import com.hero.ziggymusic.database.music.dao.PlaylistMusicDao
import com.hero.ziggymusic.database.music.entity.MusicModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MusicLocalDataSourceImpl @Inject constructor(
    private val application: Application,
    private val musicFileDao: MusicFileDao,
    private val playlistMusicDao: PlaylistMusicDao,
) : MusicLocalDataSource {
    override suspend fun loadMusics() {
        val musicListUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // 2. 가져올 데이터 컬럼을 정의
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        // 3. 콘텐츠 리졸버에 해당 데이터 요청 (음원 목록에 있는 0번째 줄을 가리킴)
        // 4. 커서로 전달된 데이터를 꺼내서 저장
        val musicList = mutableListOf<MusicModel>()

        application.contentResolver.query(
            musicListUri,
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

            while (cursor.moveToNext()) {
                val music = MusicModel(
                    id = cursor.getString(idColumn),
                    title = cursor.getString(titleColumn),
                    artist = cursor.getString(artistColumn),
                    albumId = cursor.getString(albumIdColumn),
                    album = cursor.getString(albumTitleColumn),
                    duration = cursor.getLong(durationColumn)
                )
                musicList.add(music)
            }
        }

        musicFileDao.insertAll(musicList)
    }

    override suspend fun getMusicCount(): Int = withContext(Dispatchers.IO) {
        musicFileDao.getMusicCount()
    }

    override suspend fun getMusic(key: String): MusicModel? {
        return musicFileDao.getMusicFileFromKey(key)
    }

    override fun getAllMusic(): LiveData<List<MusicModel>> {
        return musicFileDao.getAllFiles()
    }

    override fun getMyPlaylistMusics(): LiveData<List<MusicModel>> {
        return playlistMusicDao.getAllFiles()
    }

    override suspend fun addMusicToMyPlaylist(musicModel: MusicModel) {
        playlistMusicDao.insertMusic(musicModel)
    }

    override suspend fun deleteMusicFromMyPlaylist(musicModel: MusicModel) {
        playlistMusicDao.deleteMusic(musicModel)
    }
}
