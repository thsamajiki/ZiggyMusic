package com.hero.ziggymusic.view.main.musiclist.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ViewModel은 DB에 직접 접근하지 않아야함. Repository 에서 데이터 통신.
@HiltViewModel
class MusicListViewModel @Inject constructor(
    application: Application,
    private val musicRepository : MusicRepository
) : AndroidViewModel(application) {

    private val myPlaylist = musicRepository.getMyPlaylistMusics()
    private val myPlaylistObserver: (List<MusicModel>) -> Unit = {

    }

    val allMusics = musicRepository.getAllMusic()

    init {
        viewModelScope.launch {
            musicRepository.loadMusics()
        }
        // Observer는 항상 활성 상태로 간주되므로 항상 수정 관련 알림을 받는다.
        myPlaylist.observeForever(myPlaylistObserver)
    }

//    fun getAllMusics(): LiveData<List<MusicModel>> {
//        return musicRepository.getAllMusic()
//    }

    // ViewModel에서 플레이리스트에 추가 작업 요청
    fun addMusicToMyPlaylist(musicModel: MusicModel) {
        viewModelScope.launch {
            musicRepository.addMusicToMyPlaylist(musicModel)
        }
    }

    fun isContainedInMyPlayList(musicId: String) : Boolean {
        // orEmpty() : null이 아니면 배열을 리턴, null이면 빈 배열을 리턴
        // any : 하나라도 만족하는 원소가 있는지 확인
        return myPlaylist.value.orEmpty().any { it.id == musicId }
    }

    // ViewModel에서 플레이리스트에서 제거 작업 요청
    fun deleteMusicFromMyPlaylist(musicModel: MusicModel) {
        viewModelScope.launch {
            musicRepository.deleteMusicFromMyPlaylist(musicModel)
        }
    }

    override fun onCleared() {
        myPlaylist.removeObserver(myPlaylistObserver)
        super.onCleared()
    }
}