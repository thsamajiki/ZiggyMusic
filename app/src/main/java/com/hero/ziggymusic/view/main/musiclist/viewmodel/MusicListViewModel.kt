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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

sealed class MusicListUiState {
    object Idle : MusicListUiState()
    data class Content(val data: List<MusicModel>) : MusicListUiState()
    object Empty : MusicListUiState()
    object Error : MusicListUiState()
}

data class MusicSearchResult(
    val items: List<MusicModel>,
    val emptyMessage: String,
    val hasOriginalItems: Boolean,
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

    private val _searchQuery = MutableStateFlow("")
    private val _searchMusicItems = MutableStateFlow<List<MusicModel>>(emptyList())
    private val _searchResult = MutableStateFlow<MusicSearchResult?>(null)
    val searchResult: StateFlow<MusicSearchResult?>
        get() = _searchResult

    private var isInitialized = false
    private var isObservingMediaStore = false
    private var hasSearchMusicItems = false

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
        observeSearchQuery()

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

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        viewModelScope.launch {
            _searchQuery
                // 입력이 멈춘 뒤에만 검색하여 빠른 타이핑 중 불필요한 필터링을 줄인다.
                .debounce(SEARCH_DEBOUNCE_MS.milliseconds)
                .map { query ->
                    if (hasSearchMusicItems) {
                        searchMusicItems(query, _searchMusicItems.value)
                    } else {
                        null
                    }
                }
                .collect { searchResult ->
                    searchResult?.let { _searchResult.value = it }
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

    @OptIn(FlowPreview::class)
    private fun observeMediaStoreChanges() {
        viewModelScope.launch {
            musicRepository.observeMusicChanges()
                .debounce(MEDIA_STORE_REFRESH_DELAY_MS.milliseconds)
                .collect {
                    refreshMusicList()
                }
        }
    }

    fun startObservingMediaStoreChanges() {
        if (isObservingMediaStore) return
        isObservingMediaStore = true
        observeMediaStoreChanges()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchMusicItems(musicItems: List<MusicModel>) {
        hasSearchMusicItems = true
        _searchMusicItems.value = musicItems
        // 목록이 갱신되면 현재 검색어를 즉시 다시 적용해 화면 상태를 맞춘다.
        _searchResult.value = searchMusicItems(_searchQuery.value, musicItems)
    }

    fun clearSearchResult() {
        hasSearchMusicItems = false
        _searchMusicItems.value = emptyList()
        _searchResult.value = null
    }

    fun searchMusicItems(query: String, musicList: List<MusicModel>): MusicSearchResult {
        val keyword = query.trim()
        val filteredItems = if (keyword.isBlank()) {
            musicList
        } else {
            // 검색 대상은 사용자에게 보이는 곡 제목과 아티스트명으로 제한한다.
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
            emptyMessage = emptyMessage,
            hasOriginalItems = musicList.isNotEmpty()
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

    companion object {
        private const val MEDIA_STORE_REFRESH_DELAY_MS = 500L
        private const val SEARCH_DEBOUNCE_MS = 200L
    }
}
