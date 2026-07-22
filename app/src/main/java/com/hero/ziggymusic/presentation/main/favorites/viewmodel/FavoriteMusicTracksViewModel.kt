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
import com.hero.ziggymusic.data.local.preferences.FavoriteMusicTracksSortStore
import com.hero.ziggymusic.domain.music.model.FavoriteMusicTrack
import com.hero.ziggymusic.domain.music.model.MusicTracksSortOrder
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import com.hero.ziggymusic.presentation.common.sort.MusicTrackSorter
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
    private val musicRepository: MusicRepository,
    private val sortStore: FavoriteMusicTracksSortStore,
    private val musicTrackSorter: MusicTrackSorter,
) : AndroidViewModel(application) {
    private val favoriteMusicTracks : LiveData<List<FavoriteMusicTrack>> = musicRepository.observeFavoriteMusicTracks()

    private val _uiState = MediatorLiveData<FavoriteMusicTrackListUiState>(FavoriteMusicTrackListUiState.Idle)
    val uiState: LiveData<FavoriteMusicTrackListUiState>
        get() = _uiState

    private val _emptyStateMessage = MutableLiveData("")
    val emptyStateMessage: LiveData<String>
        get() = _emptyStateMessage

    val sortOrder: LiveData<MusicTracksSortOrder>
        get() = sortStore.sortOrder

    private var lastFavoriteSortLocaleTag: String? = null

    private val _toastEvent = MutableLiveData<SingleEvent<String>>()
    val toastEvent: LiveData<SingleEvent<String>>
        get() = _toastEvent

    init {
        _uiState.addSource(favoriteMusicTracks) {
            updateFavoriteMusicTrackListUiState()
        }

        // 사용자가 정렬 기준을 변경하면 목록을 즉시 다시 계산한다.
        _uiState.addSource(sortStore.sortOrder) {
            updateFavoriteMusicTrackListUiState()
        }
    }

    private fun updateFavoriteMusicTrackListUiState() {
        val favoriteItems = favoriteMusicTracks.value ?: return
        val selectedSortOrder = sortStore.sortOrder.value
            ?: MusicTracksSortOrder.DATE_ADDED_DESCENDING

        lastFavoriteSortLocaleTag = musicTrackSorter.currentLocaleTag()

        /*
         * DAO가 music_tracks와 favorite_music_tracks를 INNER JOIN하므로
         * 실제 음악 목록에 존재하지 않는 즐겨찾기를 여기서 다시 필터링할 필요가 없다.
         */
        val sortedMusicTracks = musicTrackSorter
            .sortFavoriteMusicTracks(
                items = favoriteItems,
                sortOrder = selectedSortOrder,
            )
            .map { favoriteMusicTrack ->
                favoriteMusicTrack.track
            }

        if (sortedMusicTracks.isEmpty()) {
            _emptyStateMessage.value =
                getApplication<Application>().getString(
                    R.string.no_music_track_found,
                )

            _uiState.value = FavoriteMusicTrackListUiState.Empty
        } else {
            _emptyStateMessage.value = ""
            _uiState.value = FavoriteMusicTrackListUiState.Content(sortedMusicTracks)
        }
    }

    fun setSortOrder(sortOrder: MusicTracksSortOrder) {
        sortStore.setSortOrder(sortOrder)
    }

    fun refreshSortForCurrentLanguage() {
        if (lastFavoriteSortLocaleTag == musicTrackSorter.currentLocaleTag()) return
        updateFavoriteMusicTrackListUiState()
    }

    fun removeMusicTrackFromFavorites(id: String) {
        viewModelScope.launch {
            musicRepository.removeMusicTrackFromFavorites(id)
        }
    }
}
