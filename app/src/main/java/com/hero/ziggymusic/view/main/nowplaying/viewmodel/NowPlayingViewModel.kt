package com.hero.ziggymusic.view.main.nowplaying.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import kotlinx.coroutines.launch

// ViewModel은 DB에 직접 접근하지 않아야함. Repository 에서 데이터 통신.
class NowPlayingViewModel(
    application: Application,
    private val musicRepository: MusicRepository
) : AndroidViewModel(application) {

    private val _nowPlayingMusic = MutableLiveData<MusicModel>()
    val nowPlayingMusic : LiveData<MusicModel>
        get() = _nowPlayingMusic

    fun requestMusic(musicKey: String) {
        viewModelScope.launch {
            val music = musicRepository.getMusic(musicKey)
            if (music != null) {
                _nowPlayingMusic.value = music
            } else {
                Log.e("NowPlayingViewModel", "music is null")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}


class NowPlayingViewModelFactory(
    private val application: Application,
    private val musicRepository : MusicRepository
): AbstractSavedStateViewModelFactory() {

    override fun <T : ViewModel?> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        return NowPlayingViewModel(application, musicRepository) as T
    }
}