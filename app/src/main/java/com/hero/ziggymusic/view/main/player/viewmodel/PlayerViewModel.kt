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
import kotlin.text.Typography.dagger

// ViewModel은 DB에 직접 접근하지 않아야함. Repository 에서 데이터 통신.
@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    private val musicRepository: MusicRepository
) : AndroidViewModel(application) {

    private val _state: MutableStateFlow<PlayerMotionManager.State> =
        MutableStateFlow(PlayerMotionManager.State.COLLAPSED)
    val state: StateFlow<PlayerMotionManager.State> = _state.asStateFlow()

    val musicList: LiveData<List<MusicModel>> = musicRepository.getAllMusic()

//    fun getAllMusics(): LiveData<List<MusicModel>> {
//        return musicRepository.getAllMusic()
//    }

    override fun onCleared() {
        super.onCleared()
    }

    fun changeState(toggleState: PlayerMotionManager.State) {
        _state.value = toggleState
    }
}