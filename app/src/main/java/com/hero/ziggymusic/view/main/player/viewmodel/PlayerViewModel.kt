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

    // StateFlow 는 기본적으로 read-only 이기 때문에 값을 수정하기 위해서는 MutableStateFlow 로 선언하여 사용하면 됨
    // LiveData와 달리 StateFlow는 초기값이 필요하다.
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