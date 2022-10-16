package com.hero.ziggymusic.view.main.myplaylist.viewmodel

import android.app.Application
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.repository.MusicRepository

class MyPlayListViewModel(application: Application) : AndroidViewModel(application) {

    private val musicRepository : MusicRepository
    private var musicItems : LiveData<List<MusicModel>>

    val myPlayListLiveData = MutableLiveData<List<MusicModel>>()
    val musicList : LiveData<List<MusicModel>>
        get() = myPlayListLiveData

    init {
        musicRepository = MusicRepository(application)
        musicItems = musicRepository.getDataList()
    }


    private fun getMyPlayList() : List<MusicModel> {
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
        val cursor = getApplication<Application>().applicationContext.contentResolver.query(musicListUri, projection, null, null, null)

        // 4. 커서로 전달된 데이터를 꺼내서 저장
        val musicList = mutableListOf<MusicModel>()

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

    override fun onCleared() {
        super.onCleared()
    }
}