package com.hero.ziggymusic.presentation.main.musictracks.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.hero.ziggymusic.R
import com.hero.ziggymusic.presentation.common.SingleEvent
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity
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
import com.hero.ziggymusic.domain.music.model.MusicTracksSortOrder
import com.hero.ziggymusic.data.local.preferences.MusicTracksSortStore
import java.text.Collator
import java.util.Locale
import androidx.core.os.ConfigurationCompat
import java.text.Normalizer

sealed class MusicTrackListUiState {
    object Idle : MusicTrackListUiState()
    data class Content(val data: List<MusicTrackEntity>) : MusicTrackListUiState()
    data class Empty(val message: String) : MusicTrackListUiState()
    object Error : MusicTrackListUiState()
}

data class MusicTrackSearchResult(
    val items: List<MusicTrackEntity>,
    val emptyMessage: String,
    val hasOriginalItems: Boolean,
)

@HiltViewModel
class MusicTracksViewModel @Inject constructor(
    application: Application,
    private val musicRepository: MusicRepository,
    private val musicTracksSortStore: MusicTracksSortStore,
) : AndroidViewModel(application) {
    private var musicLibrarySyncJob: Job? = null // 음악 목록 동기화 중복 실행을 방지한다.
    private var mediaStoreChangesJob: Job? = null // MediaStore 변경 감지 작업의 중복 실행을 방지한다.

    val favoriteMusicTrackIdList: LiveData<Set<String>> =
        musicRepository.observeFavoriteTrackIdList().map { trackIdList ->
            trackIdList.toSet()
        } // 전체 음악 목록에서 각 음악의 즐겨찾기 여부를 확인하기 위해 즐겨찾기 음악 목록을 중복 없는 ID 집합으로 변환

    val musicTrackList = musicRepository.observeMusicTracks()

    // 정렬 기준이나 앱 언어가 바뀔 때 다시 정렬할 최신 전체 목록을 보관한다.
    private var currentMusicTracks: List<MusicTrackEntity> = emptyList()

    private val _musicTracksSortOrder = MutableLiveData(
        musicTracksSortStore.loadMusicTracksSortOrder()
    )

    val musicTracksSortOrder: LiveData<MusicTracksSortOrder>
        get() = _musicTracksSortOrder

    private enum class MusicTrackTextGroup {
        HANGUL, // 한글
        LATIN, // 영문 알파벳
        OTHER, // 기타 (숫자, 일본어, 중국어 등)
        EMPTY // 제목 또는 아티스트가 비어 있음
    }

    // 앱 언어가 바뀐 경우에만 다시 정렬하도록 마지막 언어를 기억한다.
    private var lastMusicTrackSortLocaleTag: String? = null

    private val _uiState = MutableLiveData<MusicTrackListUiState>(MusicTrackListUiState.Idle)
    val uiState: LiveData<MusicTrackListUiState>
        get() = _uiState

    private var currentStateMessage: String = "" // 현재 상태를 나타내는 메시지

    private val _toastEvent = MutableLiveData<SingleEvent<String>>()
    val toastEvent: LiveData<SingleEvent<String>>
        get() = _toastEvent

    private val _searchQuery = MutableStateFlow("")
    private val _searchMusicItems = MutableStateFlow<List<MusicTrackEntity>>(emptyList())
    private val _searchResult = MutableStateFlow<MusicTrackSearchResult?>(null)
    val searchResult: StateFlow<MusicTrackSearchResult?>
        get() = _searchResult

    private var hasSearchMusicItems = false

    private val allMusicTracksObserver = Observer<List<MusicTrackEntity>> { musicTracks ->
        if (musicTracks.isNotEmpty()) {
            updateMusicTrackListUiState(musicTracks)
        }
    }

    init {
        // Observer 는 항상 활성 상태로 간주되므로 항상 수정 관련 알림을 받는다.
        musicTrackList.observeForever(allMusicTracksObserver)
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
                musicRepository.getMusicTracksFromMediaStore()
            }.onSuccess { trackList ->
                updateMusicTrackListUiState(trackList)

                // 사용자에게 먼저 목록을 보여준 뒤 캐시를 저장한다.
                musicRepository.replaceCachedMusicTracks(trackList)
            }.onFailure {
                _toastEvent.value = SingleEvent(
                    getApplication<Application>()
                        .getString(R.string.load_music_tracks_failed)
                )
                _uiState.value = MusicTrackListUiState.Error
            }
        }
    }

    private fun updateMusicTrackListUiState(
        trackList: List<MusicTrackEntity>,
    ) {
        currentMusicTracks = trackList

        if (trackList.isEmpty()) {
            val message = getApplication<Application>()
                .getString(R.string.no_music_track_found)

            _uiState.value = MusicTrackListUiState.Empty(message)
            return
        }

        val selectedSortOrder =
            musicTracksSortOrder.value ?: MusicTracksSortOrder.TITLE_ASCENDING

        _uiState.value = MusicTrackListUiState.Content(
            sortMusicTracks(
                trackList = trackList,
                sortOrder = selectedSortOrder
            )
        )
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
            musicRepository.observeMediaStoreMusicChanges()
                .debounce(MEDIA_STORE_REFRESH_DELAY_MS.milliseconds)
                .collect {
                    syncMusicLibrary()
                }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchMusicItems(
        musicItems: List<MusicTrackEntity>,
        emptyStateMessage: String = "",
    ) {
        hasSearchMusicItems = true
        currentStateMessage = emptyStateMessage

        _searchMusicItems.value = musicItems
        // 목록이 갱신되면 현재 검색어를 즉시 다시 적용해 화면 상태를 맞춘다.
        _searchResult.value = searchMusicItems(_searchQuery.value, musicItems)
    }

    fun clearSearchResult() {
        hasSearchMusicItems = false
        currentStateMessage = ""
        _searchMusicItems.value = emptyList()
        _searchResult.value = null
    }

    fun searchMusicItems(
        query: String,
        musicTracks: List<MusicTrackEntity>,
    ): MusicTrackSearchResult {
        val keyword = query.trim()
        val filteredItems = if (keyword.isBlank()) {
            musicTracks
        } else {
            // 검색 대상은 사용자에게 보이는 곡 제목과 아티스트명으로 제한한다.
            musicTracks.filter { music ->
                music.title.orEmpty().contains(keyword, ignoreCase = true) ||
                        music.artist.orEmpty().contains(keyword, ignoreCase = true)
            }
        }

        val emptyMessage = if (keyword.isBlank()) {
            currentStateMessage
        } else {
            getApplication<Application>().getString(R.string.music_track_search_no_result)
        }

        return MusicTrackSearchResult(
            items = filteredItems,
            emptyMessage = emptyMessage,
            hasOriginalItems = musicTracks.isNotEmpty()
        )
    }

    fun setMusicTrackSortOrder(
        sortOrder: MusicTracksSortOrder,
    ) {
        if (_musicTracksSortOrder.value == sortOrder) return

        musicTracksSortStore.saveMusicTracksSortOrder(sortOrder)

        _musicTracksSortOrder.value = sortOrder

        if (currentMusicTracks.isNotEmpty()) {
            updateMusicTrackListUiState(
                currentMusicTracks
            )
        }
    }

    private fun sortMusicTracks(
        trackList: List<MusicTrackEntity>,
        sortOrder: MusicTracksSortOrder,
    ): List<MusicTrackEntity> {
        val appLocale = getCurrentAppLocale()
        val collator = createMusicTrackCollator(appLocale)

        lastMusicTrackSortLocaleTag = appLocale.toLanguageTag()

        if (sortOrder.isDateAddedOrder) {
            return sortMusicTracksByDateAdded(
                trackList = trackList,
                sortOrder = sortOrder,
                appLocale = appLocale,
                collator = collator
            )
        }

        return trackList.sortedWith { firstTrack, secondTrack ->
            val primaryResult = if (sortOrder.isTitleOrder) {
                compareMusicTrackText(
                    first = firstTrack.title,
                    second = secondTrack.title,
                    descending = sortOrder.isDescending,
                    appLocale = appLocale,
                    collator = collator
                )
            } else {
                compareMusicTrackText(
                    first = firstTrack.artist,
                    second = secondTrack.artist,
                    descending = sortOrder.isDescending,
                    appLocale = appLocale,
                    collator = collator
                )
            }

            if (primaryResult != 0) {
                primaryResult
            } else {
                // 1차 비교값이 같으면 다른 항목과 id로 순서를 일정하게 유지한다.
                val secondaryResult = if (sortOrder.isTitleOrder) {
                    compareMusicTrackText(
                        first = firstTrack.artist,
                        second = secondTrack.artist,
                        descending = sortOrder.isDescending,
                        appLocale = appLocale,
                        collator = collator
                    )
                } else {
                    compareMusicTrackText(
                        first = firstTrack.title,
                        second = secondTrack.title,
                        descending = sortOrder.isDescending,
                        appLocale = appLocale,
                        collator = collator
                    )
                }

                if (secondaryResult != 0) {
                    secondaryResult
                } else {
                    firstTrack.id.compareTo(secondTrack.id)
                }
            }
        }
    }

    private fun sortMusicTracksByDateAdded(
        trackList: List<MusicTrackEntity>,
        sortOrder: MusicTracksSortOrder,
        appLocale: Locale,
        collator: Collator,
    ): List<MusicTrackEntity> {
        return trackList.sortedWith { firstTrack, secondTrack ->
            val dateResult =
                compareMusicTrackDateAdded(
                    first = firstTrack.dateAdded,
                    second = secondTrack.dateAdded,
                    descending = sortOrder.isDescending
                )

            if (dateResult != 0) {
                dateResult
            } else {
                // 같은 날짜는 제목, 아티스트, id 순으로 비교해 순서를 일정하게 유지한다.
                val titleResult =
                    compareMusicTrackText(
                        first = firstTrack.title,
                        second = secondTrack.title,
                        descending = false,
                        appLocale = appLocale,
                        collator = collator
                    )

                if (titleResult != 0) {
                    titleResult
                } else {
                    val artistResult =
                        compareMusicTrackText(
                            first = firstTrack.artist,
                            second = secondTrack.artist,
                            descending = false,
                            appLocale = appLocale,
                            collator = collator
                        )

                    if (artistResult != 0) {
                        artistResult
                    } else {
                        firstTrack.id.compareTo(secondTrack.id)
                    }
                }
            }
        }
    }

    private fun compareMusicTrackDateAdded(
        first: Long,
        second: Long,
        descending: Boolean,
    ): Int {
        // 날짜를 알 수 없는 항목은 정렬 방향과 관계없이 마지막에 둔다.
        val firstHasDateAdded = first > 0L
        val secondHasDateAdded = second > 0L

        if (firstHasDateAdded != secondHasDateAdded) {
            return if (firstHasDateAdded) {
                -1
            } else {
                1
            }
        }

        return if (descending) {
            second.compareTo(first)
        } else {
            first.compareTo(second)
        }
    }

    private fun compareMusicTrackText(
        first: String?,
        second: String?,
        descending: Boolean,
        appLocale: Locale,
        collator: Collator,
    ): Int {
        val firstText = normalizeMusicTrackText(first)
        val secondText = normalizeMusicTrackText(second)

        val firstGroup = getMusicTrackTextGroup(firstText)
        val secondGroup = getMusicTrackTextGroup(secondText)

        val firstGroupRank = getMusicTrackTextGroupRank(
            group = firstGroup,
            appLocale = appLocale,
            descending = descending
        )

        val secondGroupRank = getMusicTrackTextGroupRank(
            group = secondGroup,
            appLocale = appLocale,
            descending = descending
        )

        val groupResult = firstGroupRank.compareTo(secondGroupRank)

        if (groupResult != 0) {
            return groupResult
        }

        val compareResult =
            collator.compare(firstText, secondText)

        return if (descending) {
            -compareResult
        } else {
            compareResult
        }
    }

    fun updateMusicTrackSortForCurrentLanguage() {
        if (currentMusicTracks.isEmpty()) return

        val currentLocaleTag = getCurrentAppLocale().toLanguageTag()

        if (lastMusicTrackSortLocaleTag == currentLocaleTag) {
            return
        }

        updateMusicTrackListUiState(currentMusicTracks)
    }

    private fun createMusicTrackCollator(
        locale: Locale,
    ): Collator {
        return Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
            decomposition = Collator.CANONICAL_DECOMPOSITION
        }
    }

    private fun getCurrentAppLocale(): Locale {
        val localeList = ConfigurationCompat.getLocales(
            getApplication<Application>().resources.configuration
        )

        return if (localeList.isEmpty) {
            Locale.getDefault()
        } else {
            localeList[0] ?: Locale.getDefault()
        }
    }

    private fun normalizeMusicTrackText(
        text: String?,
    ): String {
        return Normalizer.normalize(
            text?.trim().orEmpty(),
            Normalizer.Form.NFC
        )
    }

    private fun getMusicTrackTextGroup(
        text: String,
    ): MusicTrackTextGroup {
        if (text.isBlank()) {
            return MusicTrackTextGroup.EMPTY
        }

        // 앞쪽 기호를 건너뛰고 첫 문자나 숫자를 기준으로 그룹을 판별한다.
        val firstMeaningfulCharacter =
            text.firstOrNull { character ->
                Character.isLetterOrDigit(character)
            } ?: return MusicTrackTextGroup.OTHER

        return when (
            Character.UnicodeScript.of(
                firstMeaningfulCharacter.code
            )
        ) {
            Character.UnicodeScript.HANGUL ->
                MusicTrackTextGroup.HANGUL

            Character.UnicodeScript.LATIN ->
                MusicTrackTextGroup.LATIN

            else ->
                MusicTrackTextGroup.OTHER
        }
    }

    private fun getMusicTrackTextGroupRank(
        group: MusicTrackTextGroup,
        appLocale: Locale,
        descending: Boolean,
    ): Int {
        return when (group) {
            MusicTrackTextGroup.EMPTY -> 3
            MusicTrackTextGroup.OTHER -> 2

            MusicTrackTextGroup.HANGUL,
            MusicTrackTextGroup.LATIN,
                -> {
                getHangulAndLatinGroupRank(
                    group = group,
                    appLocale = appLocale,
                    descending = descending
                )
            }
        }
    }

    private fun getHangulAndLatinGroupRank(
        group: MusicTrackTextGroup,
        appLocale: Locale,
        descending: Boolean,
    ): Int {
        // 한국어·영어에서는 앱 언어의 문자 그룹을 우선하고 내림차순에서 순서를 뒤집는다.
        val hangulFirst = when (appLocale.language) {
            Locale.KOREAN.language -> {
                /*
                 * 한국어:
                 * 오름차순 = 한글 → 영문
                 * 내림차순 = 영문 → 한글
                 */
                !descending
            }

            Locale.ENGLISH.language -> {
                /*
                 * 영어:
                 * 오름차순 = 영문 → 한글
                 * 내림차순 = 한글 → 영문
                 */
                descending
            }

            else -> {
                /*
                 * 아직 명시적인 정책이 없는 언어에서는
                 * 한글과 영문에 동일한 그룹 순위를 주고
                 * Collator가 순서를 결정하게 한다.
                 */
                return 0
            }
        }

        return when (group) {
            MusicTrackTextGroup.HANGUL ->
                if (hangulFirst) 0 else 1

            MusicTrackTextGroup.LATIN ->
                if (hangulFirst) 1 else 0

            else -> 2
        }
    }

    fun addMusicTrackToFavorites(id: String) {
        viewModelScope.launch {
            musicRepository.addMusicTrackToFavorites(id)
        }
    }

    fun isContainedInFavorites(id: String): Boolean {
        return id in favoriteMusicTrackIdList.value.orEmpty()
    }

    fun removeMusicTrackFromMyFavorites(id: String) {
        viewModelScope.launch {
            musicRepository.removeMusicTrackFromFavorites(id)
        }
    }

    override fun onCleared() {
        musicTrackList.removeObserver(allMusicTracksObserver)
        super.onCleared()
    }

    companion object {
        private const val MEDIA_STORE_REFRESH_DELAY_MS = 500L
        private const val SEARCH_DEBOUNCE_MS = 200L
    }
}
