package com.hero.ziggymusic.database.music.repository

import android.app.Application
import android.provider.MediaStore
import androidx.core.content.ContentProviderCompat
import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.AppMusicDatabase
import com.hero.ziggymusic.database.music.dao.MusicFileDao
import com.hero.ziggymusic.database.music.entity.MusicModel

class MusicRepository(application: Application) {
    private var appMusicDatabase : AppMusicDatabase
    private var musicFileDao : MusicFileDao
    private var musicList : LiveData<List<MusicModel>>

    init {
        appMusicDatabase = AppMusicDatabase.getInstance(application)
        musicFileDao = appMusicDatabase.musicFileDao()
        musicList = musicFileDao.getAllFiles()
    }

    fun getDataList(): LiveData<List<MusicModel>> {
        // 콘텐츠 리졸버로 음원 목록 가져오기
        // 1. 데이터 테이블 주소
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
        val cursor = ContentProviderCompat.requireContext().contentResolver.query(musicListUri, projection, null, null, null)

        // 4. 커서로 전달된 데이터를 꺼내서 저장


        while (cursor?.moveToNext() == true) {
            val id = cursor.getString(0)
            val title = cursor.getString(1)
            val artist = cursor.getString(2)
            val albumId = cursor.getString(3)
            val duration = cursor.getLong(4)

            val music = MusicModel(id, title, artist, albumId, duration)
            musicList.add(music)
        }

        return musicList
    }
}