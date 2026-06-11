package com.hero.ziggymusic.view.main.musiclist.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.hero.ziggymusic.R
import com.hero.ziggymusic.common.SingleEvent
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MusicListUiState {
    object Idle : MusicListUiState()
    data class Content(val data: List<MusicModel>) : MusicListUiState()
    object Empty : MusicListUiState()
    object Error : MusicListUiState()
}

data class MusicSearchResult(
    val items: List<MusicModel>,
    val emptyMessage: String
)

@HiltViewModel
class MusicListViewModel @Inject constructor(
    application: Application,
    private val musicRepository : MusicRepository
) : AndroidViewModel(application) {
    private val favorites = musicRepository.getFavorites()
    private val favoritesObserver: (List<MusicModel>) -> Unit = {
    }

    val allMusics = musicRepository.getAllMusic()

    private val _uiState = MutableLiveData<MusicListUiState>(MusicListUiState.Idle)
    val uiState: LiveData<MusicListUiState>
        get() = _uiState

    private val _emptyStateMessage = MutableLiveData("")
    val emptyStateMessage: LiveData<String>
        get() = _emptyStateMessage

    private val _toastEvent = MutableLiveData<SingleEvent<String>>()
    val toastEvent: LiveData<SingleEvent<String>>
        get() = _toastEvent

    private var isInitialized = false

    private val allMusicsObserver = Observer<List<MusicModel>> { musics ->
        if (!isInitialized) return@Observer

        _uiState.value = if (musics.isEmpty()) {
            MusicListUiState.Empty
        } else {
            MusicListUiState.Content(musics)
        }
    }

    init {
        // Observer 는 항상 활성 상태로 간주되므로 항상 수정 관련 알림을 받는다.
        allMusics.observeForever(allMusicsObserver)
        favorites.observeForever(favoritesObserver)

        viewModelScope.launch {
            runCatching {
                if (musicRepository.getMusicCount() == 0) {
                    musicRepository.loadMusics()
                }
            }.onSuccess {
                isInitialized = true

                val musics = allMusics.value.orEmpty()
                if (musics.isEmpty()) {
                    _emptyStateMessage.value =
                        getApplication<Application>().getString(R.string.no_music_found)
                    _uiState.value = MusicListUiState.Empty
                } else {
                    _emptyStateMessage.value = ""
                    _uiState.value = MusicListUiState.Content(musics)
                }
            }.onFailure {
                isInitialized = true
                _toastEvent.value = SingleEvent(getApplication<Application>().getString(R.string.load_music_failed))
                _uiState.value = MusicListUiState.Error
            }
        }
    }

    fun refreshMusicList() {
        viewModelScope.launch {
            runCatching {
                musicRepository.loadMusics()
            }.onFailure {
                _toastEvent.value = SingleEvent(
                    getApplication<Application>().getString(R.string.load_music_failed)
                )
                _uiState.value = MusicListUiState.Error
            }
        }
    }

    fun searchMusicItems(query: String, musicList: List<MusicModel>): MusicSearchResult {
        val keyword = query.trim()
        val filteredItems = if (keyword.isBlank()) {
            musicList
        } else {
            musicList.filter { music ->
                music.title.orEmpty().contains(keyword, ignoreCase = true) ||
                        music.artist.orEmpty().contains(keyword, ignoreCase = true)
            }
        }

        val emptyMessage = if (keyword.isBlank()) {
            _emptyStateMessage.value.orEmpty()
        } else {
            getApplication<Application>().getString(R.string.music_search_no_result)
        }

        return MusicSearchResult(
            items = filteredItems,
            emptyMessage = emptyMessage
        )
    }

    fun addMusicToFavorites(musicModel: MusicModel) {
        viewModelScope.launch {
            musicRepository.addMusicToFavorites(musicModel)
        }
    }

    fun isContainedInFavorites(musicId: String) : Boolean {
        return favorites.value.orEmpty().any { it.id == musicId }
    }

    fun removeMusicFromMyFavorites(musicModel: MusicModel) {
        viewModelScope.launch {
            musicRepository.removeMusicFromFavorites(musicModel)
        }
    }

    override fun onCleared() {
        allMusics.removeObserver(allMusicsObserver)
        favorites.removeObserver(favoritesObserver)
        super.onCleared()
    }
}
