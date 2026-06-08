package com.hero.ziggymusic.view.main.favorites.viewmodel

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

sealed class FavoritesUiState {
    object Idle : FavoritesUiState()
    data class Content(val data: List<MusicModel>) : FavoritesUiState()
    object Empty : FavoritesUiState()
    object Error : FavoritesUiState()
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    application: Application,
    private val musicRepository: MusicRepository
) : AndroidViewModel(application) {
    val favorites : LiveData<List<MusicModel>> = musicRepository.getFavorites()

    private val _uiState = MutableLiveData<FavoritesUiState>(FavoritesUiState.Idle)
    val uiState: LiveData<FavoritesUiState>
        get() = _uiState

    private val _emptyStateMessage = MutableLiveData("")
    val emptyStateMessage: LiveData<String>
        get() = _emptyStateMessage

    private val _toastEvent = MutableLiveData<SingleEvent<String>>()
    val toastEvent: LiveData<SingleEvent<String>>
        get() = _toastEvent

    private val favoritesObserver = Observer<List<MusicModel>> { musics ->
        updateFavoritesUiState(musics)
    }

    init {
        favorites.observeForever(favoritesObserver)
    }

    private fun updateFavoritesUiState(musics: List<MusicModel>) {
        if (musics.isEmpty()) {
            _emptyStateMessage.value =
                getApplication<Application>().getString(R.string.no_music_found)
            _uiState.value = FavoritesUiState.Empty
        } else {
            _emptyStateMessage.value = ""
            _uiState.value = FavoritesUiState.Content(musics)
        }
    }

    fun removeMusicFromFavorites(musicModel: MusicModel) {
        viewModelScope.launch {
            musicRepository.removeMusicFromFavorites(musicModel)
        }
    }

    override fun onCleared() {
        favorites.removeObserver(favoritesObserver)
        super.onCleared()
    }
}
