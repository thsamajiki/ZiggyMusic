package com.hero.ziggymusic.view.main.musiclist.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

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
        // Observer 는 항상 활성 상태로 간주되므로 항상 수정 관련 알림을 받는다.
        myPlaylist.observeForever(myPlaylistObserver)
    }

    fun addMusicToMyPlaylist(musicModel: MusicModel) {
        viewModelScope.launch {
            musicRepository.addMusicToMyPlaylist(musicModel)
        }
    }

    fun isContainedInMyPlayList(musicId: String) : Boolean {
        return myPlaylist.value.orEmpty().any { it.id == musicId }
    }

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