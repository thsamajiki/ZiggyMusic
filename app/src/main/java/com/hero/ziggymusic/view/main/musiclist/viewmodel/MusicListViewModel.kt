package com.hero.ziggymusic.view.main.musiclist.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.hero.ziggymusic.R
import com.hero.ziggymusic.common.SingleEvent
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
    private var musicLibrarySyncJob: Job? = null // 음악 목록 동기화 중복 실행을 방지한다.
    private var mediaStoreChangesJob: Job? = null // MediaStore 변경 감지 작업의 중복 실행을 방지한다.

    val favoriteMusicIdList: LiveData<Set<String>> = musicRepository.getFavoriteMusicIdList().map { musicIdList ->
        musicIdList.toSet()
    } // 전체 음악 목록에서 각 음악의 즐겨찾기 여부를 확인하기 위해 즐겨찾기 음악 목록을 중복 없는 ID 집합으로 변환

    val allMusicList = musicRepository.getAllMusic()

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

    private var hasSearchMusicItems = false

    private val allMusicListObserver = Observer<List<MusicModel>> { musicList ->
        if (musicList.isNotEmpty()) {
            updateMusicListUiState(musicList)
        }
    }

    init {
        // Observer 는 항상 활성 상태로 간주되므로 항상 수정 관련 알림을 받는다.
        allMusicList.observeForever(allMusicListObserver)
        observeSearchQuery()
    }

    // MediaStore에서 최신 음악 목록을 조회하고, UI 갱신 후 Room 캐시를 갱신한다.
    fun syncMusicLibrary() {
        // 이미 동기화 중이면 새 요청은 무시한다.
        if (musicLibrarySyncJob?.isActive == true) {
            return
        }

        musicLibrarySyncJob = viewModelScope.launch {
            runCatching {
                musicRepository.getMusicList()
            }.onSuccess { musicList ->
                updateMusicListUiState(musicList)

                // 사용자에게 먼저 목록을 보여준 뒤 캐시를 저장한다.
                musicRepository.replaceCachedMusicList(musicList)
            }.onFailure {
                _toastEvent.value = SingleEvent(getApplication<Application>()
                        .getString(R.string.load_music_failed)
                )
                _uiState.value = MusicListUiState.Error
            }
        }
    }

    private fun updateMusicListUiState(
        musicList: List<MusicModel>
    ) {
        if (musicList.isEmpty()) {
            _emptyStateMessage.value =
                getApplication<Application>().getString(R.string.no_music_found)

            _uiState.value = MusicListUiState.Empty
        } else {
            _emptyStateMessage.value = ""
            _uiState.value = MusicListUiState.Content(musicList)
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

    // MediaStore 변경을 감지해 음악 목록을 최신 상태로 동기화한다.
    @OptIn(FlowPreview::class)
    fun startObservingMediaStoreChanges() {
        if (mediaStoreChangesJob?.isActive == true) {
            return
        }

        // 짧은 시간에 여러 변경 이벤트가 발생해도 한 번만 동기화한다.
        mediaStoreChangesJob = viewModelScope.launch {
            musicRepository.observeMusicChanges()
                .debounce(MEDIA_STORE_REFRESH_DELAY_MS.milliseconds)
                .collect {
                    syncMusicLibrary()
                }
        }
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

    fun addMusicToFavorites(id: String) {
        viewModelScope.launch {
            musicRepository.addMusicToFavorites(id)
        }
    }

    fun isContainedInFavorites(id: String) : Boolean {
        return id in favoriteMusicIdList.value.orEmpty()
    }

    fun removeMusicFromMyFavorites(id: String) {
        viewModelScope.launch {
            musicRepository.removeMusicFromFavorites(id)
        }
    }

    override fun onCleared() {
        allMusicList.removeObserver(allMusicListObserver)
        super.onCleared()
    }

    companion object {
        private const val MEDIA_STORE_REFRESH_DELAY_MS = 500L
        private const val SEARCH_DEBOUNCE_MS = 200L
    }
}
