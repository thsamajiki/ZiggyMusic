package com.hero.ziggymusic.view.main.player.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import com.hero.ziggymusic.database.music.entity.MusicTrackEntity
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import com.hero.ziggymusic.view.main.player.PlayerMotionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    musicRepository: MusicRepository
) : ViewModel() {
    private val _motionState: MutableStateFlow<PlayerMotionManager.State> =
        MutableStateFlow(PlayerMotionManager.State.COLLAPSED)
    val motionState: StateFlow<PlayerMotionManager.State> = _motionState.asStateFlow()

    val musicList: LiveData<List<MusicTrackEntity>> = musicRepository.getAllMusic()

    private val favoriteMusicList: LiveData<List<MusicTrackEntity>> =
        musicRepository.getFavorites()

    // 실제 파일이 남아 있는 즐겨찾기만 재생 큐에 사용한다.
    val availableFavoriteMusicList: LiveData<List<MusicTrackEntity>> =
        MediatorLiveData<List<MusicTrackEntity>>().apply {
            fun updateAvailableFavorites() {
                val favorites = favoriteMusicList.value ?: return
                val allMusic = musicList.value ?: return
                val availableIds = allMusic.mapTo(mutableSetOf()) { it.id }

                value = favorites.filter { it.id in availableIds }
            }

            addSource(favoriteMusicList) {
                updateAvailableFavorites()
            }

            addSource(musicList) {
                updateAvailableFavorites()
            }
        }

    fun changeState(toggleState: PlayerMotionManager.State) {
        _motionState.value = toggleState
    }
}
