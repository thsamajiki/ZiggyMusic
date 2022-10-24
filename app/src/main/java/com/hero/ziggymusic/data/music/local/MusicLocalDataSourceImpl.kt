package com.hero.ziggymusic.data.music.local

import android.app.Application
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.music.dao.MusicFileDao
import com.hero.ziggymusic.database.music.entity.MusicModel
import javax.inject.Inject

class MusicLocalDataSourceImpl @Inject constructor(
    private val application: Application,
    private val musicFileDao: MusicFileDao
) : MusicLocalDataSource{

    override suspend fun loadMusics() {
        val musicListUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // 2. 가져올 데이터 컬럼을 정의
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
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
//            val id = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.))
            val id = cursor.getString(0)
            val title = cursor.getString(1)
            val artist = cursor.getString(2)
            val albumId = cursor.getString(3)
            val duration = cursor.getLong(4)

            val music = MusicModel(id, title, artist, albumId, duration)
            musicList.add(music)
        }

        musicFileDao.insertAll(musicList)
    }

    override suspend fun getMusic(key: String): MusicModel? {
        return musicFileDao.getMusicFileFromKey(key)
    }

    override suspend fun getAllMusic(): LiveData<List<MusicModel>> {
        return musicFileDao.getAllFiles()
    }

    override suspend fun getMyPlayListMusics(): LiveData<List<MusicModel>> {
        TODO("Not yet implemented")
    }
}