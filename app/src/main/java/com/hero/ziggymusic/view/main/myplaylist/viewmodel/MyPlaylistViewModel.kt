package com.hero.ziggymusic.view.main.myplaylist.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.hero.ziggymusic.R
import com.hero.ziggymusic.common.SingleEvent
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MyPlaylistUiState {
    object Idle : MyPlaylistUiState()
    data class Content(val data: List<MusicModel>) : MyPlaylistUiState()
    object Empty : MyPlaylistUiState()
    object Error : MyPlaylistUiState()
}

@HiltViewModel
class MyPlaylistViewModel @Inject constructor(
    application: Application,
    private val musicRepository: MusicRepository
) : AndroidViewModel(application) {
    val myPlaylist : LiveData<List<MusicModel>> = musicRepository.getMyPlaylistMusics()

    private val _uiState = MutableLiveData<MyPlaylistUiState>(MyPlaylistUiState.Idle)
    val uiState: LiveData<MyPlaylistUiState>
        get() = _uiState

    private val _emptyStateMessage = MutableLiveData("")
    val emptyStateMessage: LiveData<String>
        get() = _emptyStateMessage

    private val _toastEvent = MutableLiveData<SingleEvent<String>>()
    val toastEvent: LiveData<SingleEvent<String>>
        get() = _toastEvent

    private val myPlaylistObserver = Observer<List<MusicModel>> { musics ->
        updateMyPlaylistUiState(musics)
    }

    init {
        myPlaylist.observeForever(myPlaylistObserver)
    }

    private fun updateMyPlaylistUiState(musics: List<MusicModel>) {
        if (musics.isEmpty()) {
            _emptyStateMessage.value =
                getApplication<Application>().getString(R.string.no_music_found)
            _uiState.value = MyPlaylistUiState.Empty
        } else {
            _emptyStateMessage.value = ""
            _uiState.value = MyPlaylistUiState.Content(musics)
        }
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
