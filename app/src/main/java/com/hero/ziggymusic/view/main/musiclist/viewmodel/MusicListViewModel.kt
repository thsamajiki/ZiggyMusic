package com.hero.ziggymusic.view.main.musiclist.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.text.Typography.dagger

// ViewModel은 DB에 직접 접근하지 않아야함. Repository 에서 데이터 통신.
@HiltViewModel
class MusicListViewModel @Inject constructor(
    application: Application,
    private val musicRepository : MusicRepository
) : AndroidViewModel(application) {

    private val myPlaylist = musicRepository.getMyPlaylistMusics()
    private val myPlaylistObserver: (List<MusicModel>) -> Unit = {

    }

    init {
        viewModelScope.launch {
            musicRepository.loadMusics()
        }

        myPlaylist.observeForever(myPlaylistObserver)
    }

    fun getAllMusics(): LiveData<List<MusicModel>> {
        return musicRepository.getAllMusic()
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