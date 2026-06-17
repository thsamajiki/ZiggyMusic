package com.hero.ziggymusic.view.main.musiclist

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.FragmentMusicListBinding
import com.hero.ziggymusic.event.EventBus
import com.hero.ziggymusic.ext.playMusic
import com.hero.ziggymusic.view.main.popup.MusicOptionMenuPopup
import com.hero.ziggymusic.view.main.musiclist.viewmodel.MusicSearchResult
import com.hero.ziggymusic.view.main.musiclist.viewmodel.MusicListUiState
import com.hero.ziggymusic.view.main.musiclist.viewmodel.MusicListViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class MusicListFragment : Fragment() {
    private var _binding: FragmentMusicListBinding? = null
    private val binding get() = _binding!!

    private val vm by viewModels<MusicListViewModel>()

    private lateinit var musicListAdapter: MusicListAdapter
    private var mediaStoreObserver: ContentObserver? = null
    private var hasRefreshedAfterPermission = false
    private var searchAnimator: ValueAnimator? = null
    private var isSearchVisible = false
    private var searchProgress = 0f
    private var topOverscrollDistance = 0f
    private var shouldCollapseSearchOnIdle = false
    private var isSearchCollapseAnimating = false
    private var lastRecyclerTouchY = 0f
    private var lastSearchContainerTouchY = 0f
    private var searchRequestId = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicListBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EventBus.getInstance().register(this)
        initRecyclerView(binding.rvMusicList)
        initSearchUi()
        setSearchProgress(0f)
        collectUiState()
    }

    override fun onStart() {
        super.onStart()
        registerMediaStoreObserverIfNeeded()
    }

    override fun onResume() {
        super.onResume()

        if (hasAudioPermission()) {
            registerMediaStoreObserverIfNeeded()

            if (!hasRefreshedAfterPermission) {
                hasRefreshedAfterPermission = true
                vm.refreshMusicList()
            }
        } else {
            hasRefreshedAfterPermission = false
        }

        vm.startObservingMediaStoreChanges()
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        musicListAdapter = MusicListAdapter(
            onItemClick = { music ->
                playMusic(music.id)
            },
            onOptionClick = { music, view ->
                openMusicOptionMenuPopup(music, view)
            }
        )

        recyclerView.run {
            layoutManager = LinearLayoutManager(context)
            adapter = musicListAdapter
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

    private fun showSearchResult(searchResult: MusicSearchResult) {
        // 늦게 도착한 이전 검색 callback이 최신 결과 UI를 덮어쓰지 않도록 요청 id를 캡처한다.
        val currentRequestId = ++searchRequestId
        val hadVisibleItems = musicListAdapter.currentList.isNotEmpty()

        binding.tvNothingFound.text = searchResult.emptyMessage
        binding.tvNothingFound.isVisible = false

        musicListAdapter.submitList(searchResult.items) {
            if (currentRequestId != searchRequestId) return@submitList

            binding.rvMusicList.isVisible = searchResult.hasOriginalItems || searchResult.items.isNotEmpty()

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

        binding.rvMusicList.doOnNextLayout {
            showEmptySearchMessageWhenItemAnimationsFinish(requestId)
        }
    }

    private fun showEmptySearchMessageWhenItemAnimationsFinish(requestId: Long) {
        val itemAnimator = binding.rvMusicList.itemAnimator
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
        binding.rvMusicList.setPadding(
            binding.rvMusicList.paddingLeft,
            (expandedInset * searchProgress).roundToInt(),
            binding.rvMusicList.paddingRight,
            binding.rvMusicList.paddingBottom
        )

        val currentTopInset = (expandedInset * searchProgress).roundToInt()
        updateMusicListViewportClip(currentTopInset)
        keepFirstItemBelowSearchInset(currentTopInset)

        when (searchProgress) {
            0f -> {
                isSearchCollapseAnimating = false
                topOverscrollDistance = 0f
                searchContainer.alpha = 0f
                searchContainer.translationY = 0f
                binding.layoutSearchField.translationY =
                    -resources.displayMetrics.density * SEARCH_TRANSLATION_DP
                binding.rvMusicList.setPadding(
                    binding.rvMusicList.paddingLeft,
                    0,
                    binding.rvMusicList.paddingRight,
                    binding.rvMusicList.paddingBottom
                )
                binding.rvMusicList.translationY = 0f
                updateMusicListViewportClip(0)
            }

            1f -> {
                isSearchCollapseAnimating = false
                topOverscrollDistance = searchExpandedHeight().toFloat()
            }
        }
    }

    private fun updateMusicListViewportClip(topInset: Int) {
        val viewport = binding.layoutMusicListViewport
        viewport.clipBounds = if (topInset <= 0) {
            null
        } else {
            Rect(0, topInset, viewport.width, viewport.height)
        }
    }

    private fun keepFirstItemBelowSearchInset(topInset: Int) {
        if (topInset <= 0) return

        val layoutManager = binding.rvMusicList.layoutManager as? LinearLayoutManager ?: return
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
        vm.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MusicListUiState.Idle -> {
                    binding.rvMusicList.isVisible = false
                    binding.tvNothingFound.isVisible = false
                }

                is MusicListUiState.Content -> {
                    vm.setSearchMusicItems(state.data)
                }

                is MusicListUiState.Empty -> {
                    vm.setSearchMusicItems(emptyList())
                }

                is MusicListUiState.Error -> {
                    Log.e("MusicListFragment", getString(R.string.music_list_load_failed))
                    vm.clearSearchResult()
                    musicListAdapter.submitList(emptyList())
                    binding.rvMusicList.isVisible = false
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

        vm.toastEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun registerMediaStoreObserverIfNeeded() {
        if (!hasAudioPermission()) return
        if (mediaStoreObserver != null) return

        mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                vm.refreshMusicList()
            }
        }

        requireContext().contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaStoreObserver!!
        )
    }

    private fun unregisterMediaStoreObserver() {
        mediaStoreObserver?.let { observer ->
            requireContext().contentResolver.unregisterContentObserver(observer)
        }
        mediaStoreObserver = null
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

    private fun playMusic(musicKey: String) {
        requireContext().playMusic(musicKey)
    }

    private fun openMusicOptionMenuPopup(data: MusicModel, anchorView: View) {
        val isFavorite = vm.isContainedInFavorites(data.id)

        MusicOptionMenuPopup(
            anchorView = anchorView,
            showAddToFavorites = !isFavorite,
            showRemoveFromFavorites = isFavorite,
            onAddToFavorites = { addMusicToFavorites(data) },
            onRemoveFromFavorites = { removeMusicFromFavorites(data) }
        ).show()
    }

    private fun addMusicToFavorites(musicModel: MusicModel) {
        // Local DB에 저장한다.
        vm.addMusicToFavorites(musicModel)
    }

    private fun removeMusicFromFavorites(musicModel: MusicModel) {
        // Local DB에서 삭제한다.
        vm.removeMusicFromMyFavorites(musicModel)
    }

    override fun onStop() {
        unregisterMediaStoreObserver()
        super.onStop()
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

    companion object {
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

        fun newInstance() = MusicListFragment()
    }
}
