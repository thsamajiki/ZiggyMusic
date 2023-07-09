package com.hero.ziggymusic.database.local

import android.app.Application
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.music.dao.MusicFileDao
import com.hero.ziggymusic.database.music.dao.PlaylistMusicDao
import com.hero.ziggymusic.database.music.entity.MusicModel
import javax.inject.Inject

class MusicLocalDataSourceImpl @Inject constructor(
    private val application: Application,
    private val musicFileDao: MusicFileDao,
    private val playlistMusicDao: PlaylistMusicDao
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

        // 3. 콘텐츠 리졸버에 해당 데이터 요청 (음원 목록에 있는 0번째 줄을 가리킴)
        val cursor = application.contentResolver.query(
            musicListUri,
            projection,
            null,
            null,
            null
        )

        // 4. 커서로 전달된 데이터를 꺼내서 저장
        val musicList = mutableListOf<MusicModel>()

        while (cursor?.moveToNext() == true) {
            val id = cursor.getString(0)
            val title = cursor.getString(1)
            val artist = cursor.getString(2)
            val albumId = cursor.getString(3)
            val albumTitle = cursor.getString(4) //  (if (cursor.getString(4) == null) "Unknown Album" else cursor.getString(4))
            val duration = cursor.getLong(5)

            val music = MusicModel(id, title, artist, albumId, albumTitle, duration)
            musicList.add(music)
        }

        musicFileDao.insertAll(musicList)

        cursor?.close()
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