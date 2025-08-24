package com.hero.ziggymusic.view.main.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
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
    application: Application,
    private val musicRepository: MusicRepository
) : AndroidViewModel(application) {

    private val _state: MutableStateFlow<PlayerMotionManager.State> =
        MutableStateFlow(PlayerMotionManager.State.COLLAPSED)
    val state: StateFlow<PlayerMotionManager.State> = _state.asStateFlow()

    val musicList: LiveData<List<MusicModel>> = musicRepository.getAllMusic()

    fun changeState(toggleState: PlayerMotionManager.State) {
        _state.value = toggleState
    }

    override fun onCleared() {
        super.onCleared()
    }
}