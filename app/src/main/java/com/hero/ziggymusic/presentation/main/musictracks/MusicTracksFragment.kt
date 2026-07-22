package com.hero.ziggymusic.presentation.main.musictracks

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EdgeEffect
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.R
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity
import com.hero.ziggymusic.databinding.FragmentMusicTracksBinding
import com.hero.ziggymusic.presentation.common.event.EventBus
import com.hero.ziggymusic.presentation.common.ext.playMusic
import com.hero.ziggymusic.playback.queue.PlaybackQueueSource
import com.hero.ziggymusic.domain.music.model.MusicTracksSortOrder
import com.hero.ziggymusic.presentation.main.popup.MusicTrackOptionMenuPopup
import com.hero.ziggymusic.presentation.main.musictracks.viewmodel.MusicTrackSearchResult
import com.hero.ziggymusic.presentation.main.musictracks.viewmodel.MusicTrackListUiState
import com.hero.ziggymusic.presentation.main.musictracks.viewmodel.MusicTracksViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class MusicTracksFragment : Fragment() {
    private var _binding: FragmentMusicTracksBinding? = null
    private val binding get() = _binding!!

    private val vm by activityViewModels<MusicTracksViewModel>()

    private lateinit var musicTrackAdapter: MusicTrackAdapter
    private var isRefreshedAfterPermission = false
    private var searchAnimator: ValueAnimator? = null
    private var isSearchVisible = false
    private var searchProgress = 0f
    private var topOverscrollDistance = 0f
    private var shouldCollapseSearchOnIdle = false
    private var isSearchCollapseAnimating = false
    private var lastRecyclerTouchY = 0f
    private var lastSearchContainerTouchY = 0f
    private var searchRequestId = 0L
    private var lastMusicTracksSortOrder: MusicTracksSortOrder? = null
    private var pendingMusicTrackScrollPosition: MusicTrackScrollPosition? = null
    private var scrollToTopAfterSortPending = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicTracksBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EventBus.getInstance().register(this)
        initRecyclerView(binding.rvMusicTracks)
        initSearchUi()
        setSearchProgress(0f)
        collectUiState()
    }

    override fun onResume() {
        super.onResume()

        vm.updateMusicTrackSortForCurrentLanguage()

        if (hasAudioPermission()) {
            if (!isRefreshedAfterPermission) {
                isRefreshedAfterPermission = true
                vm.syncMusicLibrary()
            }

            vm.startObservingMediaStoreChanges()
        } else {
            isRefreshedAfterPermission = false
        }
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        musicTrackAdapter = MusicTrackAdapter(
            onItemClick = { music ->
                playMusic(music.id)
            },
            onOptionClick = { music, view ->
                openMusicOptionMenuPopup(music, view)
            }
        )

        recyclerView.run {
            layoutManager = LinearLayoutManager(context)
            adapter = musicTrackAdapter
            edgeEffectFactory = SearchRevealEdgeEffectFactory()
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    return handleSearchCollapseTouch(e)
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                    handleSearchCollapseTouch(e)
                }
            })
            onFlingListener = object : RecyclerView.OnFlingListener() {
                override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                    if (velocityY > 0 && searchProgress > 0f) {
                        shouldCollapseSearchOnIdle = true
                        hideSearchBarIfAllowed()
                    }
                    return false
                }
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    if (dy > 0 && searchProgress > 0f) {
                        shouldCollapseSearchOnIdle = true
                        clearSearchFocusForScroll()
                        collapseSearchByScroll(dy.toFloat())
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        if (shouldCollapseSearchOnIdle) {
                            shouldCollapseSearchOnIdle = false
                            hideSearchBarIfAllowed()
                        } else {
                            settleSearchBar()
                        }
                    } else if (newState == RecyclerView.SCROLL_STATE_SETTLING &&
                        searchProgress > 0f &&
                        shouldCollapseSearchOnIdle
                    ) {
                        hideSearchBarIfAllowed()
                    }
                }
            })
        }
    }

    private fun handleSearchCollapseTouch(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastRecyclerTouchY = event.y
                false
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.y - lastRecyclerTouchY
                lastRecyclerTouchY = event.y
                val collapseThresholdPx = resources.displayMetrics.density * SEARCH_TOUCH_COLLAPSE_DY_DP

                if (deltaY < -collapseThresholdPx && searchProgress > 0f) {
                    shouldCollapseSearchOnIdle = true
                    clearSearchFocusForScroll()
                    collapseSearchByScroll(-deltaY)
                    true
                } else {
                    false
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                lastRecyclerTouchY = 0f
                false
            }

            else -> false
        }
    }

    private fun revealSearchFromTopPull(deltaDistance: Float, recyclerViewHeight: Int) {
        if (recyclerViewHeight <= 0) return

        shouldCollapseSearchOnIdle = false
        isSearchCollapseAnimating = false
        searchAnimator?.cancel()
        binding.layoutSearchContainer.animate().cancel()
        topOverscrollDistance += deltaDistance * recyclerViewHeight * TOP_PULL_RESISTANCE
        setSearchProgress(topOverscrollDistance / searchExpandedHeight())
    }

    private fun settleSearchBar() {
        if (searchProgress <= 0f || searchProgress >= 1f) return

        if (searchProgress >= SEARCH_SETTLE_EXPAND_PROGRESS) {
            showSearchBar()
        } else {
            hideSearchBarIfAllowed()
        }
    }

    private fun initSearchUi() {
        binding.layoutSearchContainer.onSearchTouchEvent = { event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastSearchContainerTouchY = event.y
                }

                MotionEvent.ACTION_MOVE -> {
                    handleSearchContainerDrag(event.y)
                }

                MotionEvent.ACTION_UP -> {
                    lastSearchContainerTouchY = 0f
                    settleSearchBar()
                }

                MotionEvent.ACTION_CANCEL -> {
                    lastSearchContainerTouchY = 0f
                    settleSearchBar()
                }
            }
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }

        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showSearchBar()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                binding.btnClearSearch.isVisible = query.isNotEmpty()
                // debounce와 실제 필터링은 ViewModel의 StateFlow에서 처리한다.
                vm.setSearchQuery(query)
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun handleSearchContainerDrag(currentY: Float) {
        val deltaY = currentY - lastSearchContainerTouchY
        lastSearchContainerTouchY = currentY

        if (deltaY < 0f && searchProgress > 0f) {
            shouldCollapseSearchOnIdle = true
            clearSearchFocusForScroll()
            collapseSearchByScroll(-deltaY)
        }
    }

    private fun showSearchResult(searchResult: MusicTrackSearchResult) {
        // 늦게 도착한 이전 검색 callback이 최신 결과 UI를 덮어쓰지 않도록 요청 id를 캡처한다.
        val currentRequestId = ++searchRequestId
        val hadVisibleItems = musicTrackAdapter.currentList.isNotEmpty()

        binding.tvNothingFound.text = searchResult.emptyMessage
        binding.tvNothingFound.isVisible = false

        musicTrackAdapter.submitList(searchResult.items) {
            if (currentRequestId != searchRequestId) return@submitList

            binding.rvMusicTracks.isVisible = searchResult.hasOriginalItems || searchResult.items.isNotEmpty()

            // 정렬된 목록이 반영된 뒤 예약된 스크롤을 적용한다.
            applyMusicTrackScrollAfterSort()

            if (searchResult.items.isEmpty()) {
                showEmptySearchMessageAfterListSettled(
                    requestId = currentRequestId,
                    waitForListRemoval = hadVisibleItems && searchResult.hasOriginalItems
                )
            }
        }
    }

    private fun showEmptySearchMessageAfterListSettled(
        requestId: Long,
        waitForListRemoval: Boolean
    ) {
        // 이전 결과 아이템이 사라지는 중이면, 제거 애니메이션 이후에 검색 결과가 없다는 문구를 보여준다.
        if (!waitForListRemoval) {
            binding.tvNothingFound.isVisible = requestId == searchRequestId
            return
        }

        binding.rvMusicTracks.doOnNextLayout {
            showEmptySearchMessageWhenItemAnimationsFinish(requestId)
        }
    }

    private fun showEmptySearchMessageWhenItemAnimationsFinish(requestId: Long) {
        val itemAnimator = binding.rvMusicTracks.itemAnimator
        if (itemAnimator == null || !itemAnimator.isRunning) {
            binding.tvNothingFound.isVisible = requestId == searchRequestId
            return
        }

        itemAnimator.isRunning {
            if (requestId == searchRequestId) {
                binding.tvNothingFound.isVisible = true
            }
        }
    }

    private fun showSearchBar() {
        if (searchProgress >= 1f) return

        isSearchVisible = true
        shouldCollapseSearchOnIdle = false
        isSearchCollapseAnimating = false
        topOverscrollDistance = searchExpandedHeight().toFloat()
        animateSearchBar(targetProgress = 1f)
    }

    private fun hideSearchBarIfAllowed() {
        if (searchProgress <= 0f) return
        if (isSearchCollapseAnimating) return

        clearSearchFocusForScroll()

        shouldCollapseSearchOnIdle = false
        isSearchCollapseAnimating = true
        isSearchVisible = false
        topOverscrollDistance = 0f
        animateSearchBar(targetProgress = 0f)
    }

    private fun collapseSearchByScroll(distancePx: Float) {
        val expandedHeight = searchExpandedHeight()
        if (expandedHeight <= 0 || distancePx <= 0f || searchProgress <= 0f) return

        searchAnimator?.cancel()
        binding.layoutSearchContainer.animate().cancel()
        isSearchCollapseAnimating = false

        val currentDistance = searchProgress * expandedHeight
        val nextDistance = (currentDistance - distancePx).coerceAtLeast(0f)
        topOverscrollDistance = nextDistance
        setSearchProgress(nextDistance / expandedHeight)
    }

    private fun animateSearchBar(targetProgress: Float) {
        searchAnimator?.cancel()
        binding.layoutSearchContainer.animate().cancel()

        val coercedTargetProgress = targetProgress.coerceIn(0f, 1f)

        searchAnimator = ValueAnimator.ofFloat(searchProgress, coercedTargetProgress).apply {
            duration = SEARCH_ANIMATION_DURATION_MS
            addUpdateListener { animator ->
                setSearchProgress(animator.animatedValue as Float)
            }
            start()
        }
    }

    private fun setSearchProgress(progress: Float) {
        searchProgress = progress.coerceIn(0f, 1f).let { coercedProgress ->
            when {
                coercedProgress < SEARCH_PROGRESS_EPSILON -> 0f
                coercedProgress > 1f - SEARCH_PROGRESS_EPSILON -> 1f
                else -> coercedProgress
            }
        }
        isSearchVisible = searchProgress > 0f

        val searchContainer = binding.layoutSearchContainer
        searchContainer.isVisible = searchProgress > 0f

        searchContainer.alpha = searchProgress
        searchContainer.translationY = 0f
        binding.layoutSearchField.translationY =
            -resources.displayMetrics.density * SEARCH_TRANSLATION_DP * (1f - searchProgress)

        val expandedInset = searchExpandedHeight() + searchListSafeGap()
        binding.rvMusicTracks.setPadding(
            binding.rvMusicTracks.paddingLeft,
            (expandedInset * searchProgress).roundToInt(),
            binding.rvMusicTracks.paddingRight,
            binding.rvMusicTracks.paddingBottom
        )

        val currentTopInset = (expandedInset * searchProgress).roundToInt()
        updateMusicTrackListViewportClip(currentTopInset)
        keepFirstItemBelowSearchInset(currentTopInset)

        when (searchProgress) {
            0f -> {
                isSearchCollapseAnimating = false
                topOverscrollDistance = 0f
                searchContainer.alpha = 0f
                searchContainer.translationY = 0f
                binding.layoutSearchField.translationY =
                    -resources.displayMetrics.density * SEARCH_TRANSLATION_DP
                binding.rvMusicTracks.setPadding(
                    binding.rvMusicTracks.paddingLeft,
                    0,
                    binding.rvMusicTracks.paddingRight,
                    binding.rvMusicTracks.paddingBottom
                )
                binding.rvMusicTracks.translationY = 0f
                updateMusicTrackListViewportClip(0)
            }

            1f -> {
                isSearchCollapseAnimating = false
                topOverscrollDistance = searchExpandedHeight().toFloat()
            }
        }
    }

    private fun updateMusicTrackListViewportClip(topInset: Int) {
        val viewport = binding.layoutMusicTracksViewport
        viewport.clipBounds = if (topInset <= 0) {
            null
        } else {
            Rect(0, topInset, viewport.width, viewport.height)
        }
    }

    private fun keepFirstItemBelowSearchInset(topInset: Int) {
        if (topInset <= 0) return

        val layoutManager = binding.rvMusicTracks.layoutManager as? LinearLayoutManager ?: return
        if (layoutManager.findFirstVisibleItemPosition() != 0) return

        val firstChild = layoutManager.findViewByPosition(0) ?: return
        if (firstChild.top >= topInset) return

        layoutManager.scrollToPositionWithOffset(0, topInset)
    }

    private fun clearSearchFocusForScroll() {
        if (!binding.etSearch.hasFocus()) return

        binding.etSearch.clearFocus()
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun searchExpandedHeight(): Int {
        val measuredHeight = binding.layoutSearchContainer.height
        if (measuredHeight > 0) return measuredHeight

        val layoutHeight = binding.layoutSearchContainer.layoutParams?.height ?: 0
        if (layoutHeight > 0) return layoutHeight

        return resources.getDimensionPixelSize(R.dimen.music_search_container_height)
    }

    private fun searchListSafeGap(): Int {
        return resources.getDimensionPixelSize(R.dimen.music_search_list_safe_gap)
    }

    private fun collectUiState() {
        vm.sortOrder.observe(viewLifecycleOwner) { sortOrder ->
            val previousSortOrder = lastMusicTracksSortOrder

            // 저장된 정렬 상태의 최초 전달에는 스크롤 정책을 적용하지 않는다.
            if (previousSortOrder != null && previousSortOrder != sortOrder) {
                if (sortOrder.isDateAddedOrder) {
                    scrollToTopAfterSortPending = true
                    pendingMusicTrackScrollPosition = null
                } else {
                    scrollToTopAfterSortPending = false
                    pendingMusicTrackScrollPosition = captureMusicTrackScrollPosition()
                }
            }

            lastMusicTracksSortOrder = sortOrder
        }

        vm.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MusicTrackListUiState.Idle -> {
                    binding.rvMusicTracks.isVisible = false
                    binding.tvNothingFound.isVisible = false
                }

                is MusicTrackListUiState.Content -> {
                    vm.setSearchMusicItems(state.data)
                }

                is MusicTrackListUiState.Empty -> {
                    vm.setSearchMusicItems(
                        musicItems = emptyList(),
                        emptyStateMessage = state.message
                    )
                }

                is MusicTrackListUiState.Error -> {
                    Log.e(TAG, getString(R.string.music_tracks_load_failed))
                    vm.clearSearchResult()
                    musicTrackAdapter.submitList(emptyList())
                    binding.rvMusicTracks.isVisible = false
                    binding.tvNothingFound.isVisible = false
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Fragment는 검색 결과를 화면에 반영하는 역할만 담당한다.
                vm.searchResult.collect { searchResult ->
                    searchResult?.let { showSearchResult(it) }
                }
            }
        }

        vm.favoriteMusicTrackIdList.observe(viewLifecycleOwner) { trackIds ->
            musicTrackAdapter.updateFavoriteMusicTrackIds(trackIds)
        }

        vm.toastEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyMusicTrackScrollAfterSort() {
        if (scrollToTopAfterSortPending) {
            scrollToTopAfterSortPending = false
            pendingMusicTrackScrollPosition = null

            scrollMusicTracksToTop()
            return
        }

        val scrollPosition = pendingMusicTrackScrollPosition ?: return

        pendingMusicTrackScrollPosition = null

        restoreMusicTrackScrollPosition(scrollPosition)
    }

    private fun captureMusicTrackScrollPosition(): MusicTrackScrollPosition? {
        val layoutManager =
            binding.rvMusicTracks.layoutManager
                    as? LinearLayoutManager
                ?: return null

        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()

        if (firstVisiblePosition == RecyclerView.NO_POSITION) return null

        val firstVisibleView =
            layoutManager.findViewByPosition(
                firstVisiblePosition
            ) ?: return null

        return MusicTrackScrollPosition(
            adapterPosition = firstVisiblePosition,

            // 검색창으로 paddingTop이 바뀌어도 같은 화면 위치를 복원하도록 상대 offset을 저장한다.
            topOffset = firstVisibleView.top - binding.rvMusicTracks.paddingTop
        )
    }

    private fun restoreMusicTrackScrollPosition(
        scrollPosition: MusicTrackScrollPosition,
    ) {
        val layoutManager =
            binding.rvMusicTracks.layoutManager
                    as? LinearLayoutManager
                ?: return

        val lastAdapterPosition = musicTrackAdapter.itemCount - 1

        if (lastAdapterPosition < 0) return

        // 목록 갱신 중 항목 수가 바뀌어도 유효한 위치로 보정한다.
        val availablePosition =
            scrollPosition.adapterPosition.coerceIn(
                minimumValue = 0,
                maximumValue = lastAdapterPosition
            )

        layoutManager.scrollToPositionWithOffset(
            availablePosition,
            scrollPosition.topOffset
        )
    }

    private fun scrollMusicTracksToTop() {
        val layoutManager =
            binding.rvMusicTracks.layoutManager
                    as? LinearLayoutManager
                ?: return

        if (musicTrackAdapter.itemCount == 0) return

        layoutManager.scrollToPositionWithOffset(
            0,
            0
        )
    }

    private fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun playMusic(id: String) {
        requireContext().playMusic(
            id = id,
            queueSource = PlaybackQueueSource.MUSIC_TRACKS
        )
    }

    private fun openMusicOptionMenuPopup(music: MusicTrackEntity, anchorView: View) {
        val isFavorite = vm.isContainedInFavorites(music.id)

        MusicTrackOptionMenuPopup(
            anchorView = anchorView,
            showAddToFavorites = !isFavorite,
            showRemoveFromFavorites = isFavorite,
            onAddToFavorites = { addMusicToFavorites(music.id) },
            onRemoveFromFavorites = { removeMusicFromFavorites(music.id) }
        ).show()
    }

    private fun addMusicToFavorites(id: String) {
        // Local DB에 저장한다.
        vm.addMusicTrackToFavorites(id)
    }

    private fun removeMusicFromFavorites(id: String) {
        // Local DB에서 삭제한다.
        vm.removeMusicTrackFromMyFavorites(id)
    }

    override fun onDestroyView() {
        searchAnimator?.cancel()
        searchAnimator = null
        _binding = null
        EventBus.getInstance().unregister(this)
        super.onDestroyView()
    }

    private inner class SearchRevealEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {
        override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {
            if (direction != DIRECTION_TOP) {
                return super.createEdgeEffect(recyclerView, direction)
            }

            return object : EdgeEffect(recyclerView.context) {
                override fun onPull(deltaDistance: Float) {
                    revealSearchFromTopPull(deltaDistance, recyclerView.height)
                }

                override fun onPull(deltaDistance: Float, displacement: Float) {
                    revealSearchFromTopPull(deltaDistance, recyclerView.height)
                }

                override fun onRelease() {
                    settleSearchBar()
                }

                override fun onAbsorb(velocity: Int) = Unit

                override fun draw(canvas: Canvas): Boolean = false

                override fun isFinished(): Boolean = true
            }
        }
    }

    private data class MusicTrackScrollPosition(
        val adapterPosition: Int,
        val topOffset: Int,
    )

    companion object {
        private const val TAG = "MusicTracksFragment"

        private const val SEARCH_ANIMATION_DURATION_MS = 180L
        private const val SEARCH_TRANSLATION_DP = 12f
        private const val SEARCH_SETTLE_EXPAND_PROGRESS = 0.5f
        private const val SEARCH_PROGRESS_EPSILON = 0.001f
        /*
        * 검색창을 완전히 연 상태에서 RecyclerView를 아주 살짝 터치하거나 미세하게 움직여 본다.
        * 검색창이 너무 쉽게 닫히면 값이 아직 작다. 이 경우 4dp 또는 5dp로 올리는 게 좋다.
        * 검색창을 완전히 연 상태에서 RecyclerView를 천천히 위로 스크롤했는데 검색창이 잘 안 닫히면 값이 너무 크다. 이 경우 2dp 또는 3dp가 맞다.
        */
        private const val SEARCH_TOUCH_COLLAPSE_DY_DP = 4f
        private const val TOP_PULL_RESISTANCE = 0.7f

        fun newInstance() = MusicTracksFragment()
    }
}
