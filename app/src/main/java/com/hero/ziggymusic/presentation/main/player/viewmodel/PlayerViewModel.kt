package com.hero.ziggymusic.presentation.main.player.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity
import com.hero.ziggymusic.data.local.preferences.FavoriteMusicTracksSortStore
import com.hero.ziggymusic.domain.music.model.FavoriteMusicTrack
import com.hero.ziggymusic.domain.music.model.MusicTracksSortOrder
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import com.hero.ziggymusic.presentation.common.sort.MusicTrackSorter
import com.hero.ziggymusic.presentation.main.player.manager.PlayerMotionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    musicRepository: MusicRepository,
    private val favoriteSortStore: FavoriteMusicTracksSortStore,
    private val musicTrackSorter: MusicTrackSorter,
) : ViewModel() {
    private val _motionState: MutableStateFlow<PlayerMotionManager.State> =
        MutableStateFlow(PlayerMotionManager.State.COLLAPSED)
    val motionState: StateFlow<PlayerMotionManager.State> = _motionState.asStateFlow()

    val musicTrackList: LiveData<List<MusicTrackEntity>> = musicRepository.observeMusicTracks()

    private val favoriteMusicTracks: LiveData<List<FavoriteMusicTrack>> =
        musicRepository.observeFavoriteMusicTracks()

    private val _availableFavoriteTracks = MediatorLiveData<List<MusicTrackEntity>>()

    val availableFavoriteTracks: LiveData<List<MusicTrackEntity>>
        get() = _availableFavoriteTracks

    private var lastFavoriteSortLocaleTag: String? = null

    init {
        _availableFavoriteTracks.addSource(favoriteMusicTracks) {
            updateFavoriteTrackOrder()
        }

        /*
         * 즐겨찾기 화면에서 정렬을 변경하면 현재 즐겨찾기 출처의 재생 큐도
         * 같은 순서로 즉시 다시 동기화되도록 공용 정렬 상태를 관찰한다.
         */
        _availableFavoriteTracks.addSource(favoriteSortStore.sortOrder) {
            updateFavoriteTrackOrder()
        }
    }

    private fun updateFavoriteTrackOrder() {
        val favorites = favoriteMusicTracks.value ?: return
        val sortOrder = favoriteSortStore.sortOrder.value
            ?: MusicTracksSortOrder.DATE_ADDED_DESCENDING

        lastFavoriteSortLocaleTag =
            musicTrackSorter.currentLocaleTag()

        _availableFavoriteTracks.value = musicTrackSorter
            .sortFavoriteMusicTracks(
                items = favorites,
                sortOrder = sortOrder,
            )
            .map { favorite -> favorite.track }
    }

    fun refreshFavoriteTrackOrderForCurrentLanguage() {
        if (lastFavoriteSortLocaleTag == musicTrackSorter.currentLocaleTag()) {
            return
        }

        updateFavoriteTrackOrder()
    }

    fun changeState(toggleState: PlayerMotionManager.State) {
        _motionState.value = toggleState
    }
}
