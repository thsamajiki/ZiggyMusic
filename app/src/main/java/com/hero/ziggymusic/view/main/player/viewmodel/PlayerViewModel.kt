package com.hero.ziggymusic.view.main.player.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.hero.ziggymusic.database.music.entity.MusicModel
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

    val musicList: LiveData<List<MusicModel>> = musicRepository.getAllMusic()

    fun changeState(toggleState: PlayerMotionManager.State) {
        _motionState.value = toggleState
    }
}
