package com.hero.ziggymusic.presentation.main.favorites.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hero.ziggymusic.R
import com.hero.ziggymusic.presentation.common.SingleEvent
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FavoriteMusicTrackListUiState {
    object Idle : FavoriteMusicTrackListUiState()
    data class Content(val data: List<MusicTrackEntity>) : FavoriteMusicTrackListUiState()
    object Empty : FavoriteMusicTrackListUiState()
    object Error : FavoriteMusicTrackListUiState()
}

@HiltViewModel
class FavoriteMusicTracksViewModel @Inject constructor(
    application: Application,
    private val musicRepository: MusicRepository
) : AndroidViewModel(application) {
    private val favoriteMusicTracks : LiveData<List<MusicTrackEntity>> = musicRepository.observeFavoriteMusicTracks()
    private val musicTrackList : LiveData<List<MusicTrackEntity>> = musicRepository.observeMusicTracks()

    private val _uiState = MediatorLiveData<FavoriteMusicTrackListUiState>(FavoriteMusicTrackListUiState.Idle)
    val uiState: LiveData<FavoriteMusicTrackListUiState>
        get() = _uiState

    private val _emptyStateMessage = MutableLiveData("")
    val emptyStateMessage: LiveData<String>
        get() = _emptyStateMessage

    private val _toastEvent = MutableLiveData<SingleEvent<String>>()
    val toastEvent: LiveData<SingleEvent<String>>
        get() = _toastEvent

    init {
        _uiState.addSource(favoriteMusicTracks) {
            updateFavoriteMusicTrackListUiState()
        }

        _uiState.addSource(musicTrackList) {
            updateFavoriteMusicTrackListUiState()
        }
    }

    private fun updateFavoriteMusicTrackListUiState() {
        val favoriteItems = favoriteMusicTracks.value ?: return
        val availableItems = musicTrackList.value ?: return
        val availableIds = availableItems.mapTo(mutableSetOf()) { it.id }

        // 기기에 남아 있는 즐겨찾기만 표시한다.
        val newFavoriteMusicTracks = favoriteItems.filter { it.id in availableIds }

        if (newFavoriteMusicTracks.isEmpty()) {
            _emptyStateMessage.value =
                getApplication<Application>().getString(R.string.no_music_track_found)
            _uiState.value = FavoriteMusicTrackListUiState.Empty
        } else {
            _emptyStateMessage.value = ""
            _uiState.value = FavoriteMusicTrackListUiState.Content(newFavoriteMusicTracks)
        }
    }

    fun removeMusicTrackFromFavorites(id: String) {
        viewModelScope.launch {
            musicRepository.removeMusicTrackFromFavorites(id)
        }
    }
}
