package com.hero.ziggymusic.view.main.musiclist.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import kotlinx.coroutines.launch

// ViewModel은 DB에 직접 접근하지 않아야함. Repository 에서 데이터 통신.
class MusicListViewModel(
    application: Application,
    private val musicRepository : MusicRepository
) : AndroidViewModel(application) {

    init {
        viewModelScope.launch {
            musicRepository.loadMusics()
        }
    }

    suspend fun getAllMusics(): LiveData<List<MusicModel>> {
        return musicRepository.getAllMusic()
    }
}

class MusicListViewModelFactory(
    private val application: Application,
    private val musicRepository : MusicRepository
): AbstractSavedStateViewModelFactory() {

    override fun <T : ViewModel?> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        return MusicListViewModel(application, musicRepository) as T
    }
}