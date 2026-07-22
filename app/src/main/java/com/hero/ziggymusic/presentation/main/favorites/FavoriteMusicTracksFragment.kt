package com.hero.ziggymusic.presentation.main.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity
import com.hero.ziggymusic.databinding.FragmentFavoriteMusicTracksBinding
import com.hero.ziggymusic.domain.music.model.MusicTrackSortOrder
import com.hero.ziggymusic.presentation.common.event.EventBus
import com.hero.ziggymusic.presentation.common.ext.playMusic
import com.hero.ziggymusic.playback.queue.PlaybackQueueSource
import com.hero.ziggymusic.presentation.main.popup.MusicTrackOptionMenuPopup
import com.hero.ziggymusic.presentation.main.favorites.viewmodel.FavoriteMusicTrackListUiState
import com.hero.ziggymusic.presentation.main.favorites.viewmodel.FavoriteMusicTracksViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FavoriteMusicTracksFragment : Fragment() {
    private var _binding: FragmentFavoriteMusicTracksBinding? = null
    private val binding get() = _binding!!

    private val vm by activityViewModels<FavoriteMusicTracksViewModel>()

    private lateinit var favoriteMusicTrackAdapter: FavoriteMusicTrackAdapter

    private var lastFavoriteMusicTrackSortOrder: MusicTrackSortOrder? = null
    private var pendingFavoriteMusicTrackScrollPosition: FavoriteMusicTrackScrollPosition? = null
    private var scrollToTopAfterSortPending = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteMusicTracksBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EventBus.getInstance().register(this)
        initRecyclerView(binding.rvFavoriteMusicTracks)
        collectUiState()
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        favoriteMusicTrackAdapter = FavoriteMusicTrackAdapter(
            onItemClick = { music ->
                playMusic(music.id)
            },
            onOptionClick = { music, view ->
                openMusicOptionMenuPopup(music, view)
            }
        )

        recyclerView.run {
            layoutManager = LinearLayoutManager(context)
            adapter = favoriteMusicTrackAdapter
        }
    }

    private fun collectUiState() {
        /* 정렬 상태를 uiState보다 먼저 관찰하는 것이 중요하다.
         * 그래야 정렬된 목록이 전달되기 전에 기존 스크롤 위치를 캡처할 수 있다.
         */
        vm.sortOrder.observe(viewLifecycleOwner) { sortOrder ->
            val previousSortOrder = lastFavoriteMusicTrackSortOrder

            // 저장된 정렬값이 최초 전달될 때는 스크롤 정책을 적용하지 않는다.
            if (previousSortOrder != null && previousSortOrder != sortOrder) {
                if (sortOrder.isDateAddedOrder) {
                    scrollToTopAfterSortPending = true
                    pendingFavoriteMusicTrackScrollPosition = null
                } else {
                    scrollToTopAfterSortPending = false
                    pendingFavoriteMusicTrackScrollPosition = captureFavoriteMusicTrackScrollPosition()
                }
            }

            lastFavoriteMusicTrackSortOrder = sortOrder
        }

        vm.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is FavoriteMusicTrackListUiState.Idle -> {
                    binding.rvFavoriteMusicTracks.isVisible = false
                    binding.tvNothingFound.isVisible = false
                }

                is FavoriteMusicTrackListUiState.Content -> {
                    favoriteMusicTrackAdapter.submitList(state.data) {
                        // ListAdapter의 정렬 결과가 실제 목록에 반영된 후 스크롤을 적용한다.
                        applyFavoriteMusicTrackScrollAfterSort()
                    }

                    binding.rvFavoriteMusicTracks.isVisible = true
                    binding.tvNothingFound.isVisible = false
                }

                is FavoriteMusicTrackListUiState.Empty -> {
                    favoriteMusicTrackAdapter.submitList(emptyList())
                    binding.tvNothingFound.text = vm.emptyStateMessage.value.orEmpty()
                    binding.rvFavoriteMusicTracks.isVisible = false
                    binding.tvNothingFound.isVisible = true
                }

                is FavoriteMusicTrackListUiState.Error -> {
                    favoriteMusicTrackAdapter.submitList(emptyList())
                    binding.rvFavoriteMusicTracks.isVisible = false
                }
            }
        }

        vm.toastEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT,).show()
            }
        }
    }

    private fun applyFavoriteMusicTrackScrollAfterSort() {
        if (scrollToTopAfterSortPending) {
            scrollToTopAfterSortPending = false
            pendingFavoriteMusicTrackScrollPosition = null

            scrollFavoriteMusicTracksToTop()
            return
        }

        val scrollPosition = pendingFavoriteMusicTrackScrollPosition ?: return

        pendingFavoriteMusicTrackScrollPosition = null

        restoreFavoriteMusicTrackScrollPosition(scrollPosition)
    }

    private fun captureFavoriteMusicTrackScrollPosition(): FavoriteMusicTrackScrollPosition? {
        val layoutManager =
            binding.rvFavoriteMusicTracks.layoutManager
                    as? LinearLayoutManager
                ?: return null

        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()

        if (firstVisiblePosition == RecyclerView.NO_POSITION) return null

        val firstVisibleView =
            layoutManager.findViewByPosition(
                firstVisiblePosition
            ) ?: return null

        return FavoriteMusicTrackScrollPosition(
            adapterPosition = firstVisiblePosition,

            // RecyclerView의 padding을 제외한 화면상 위치를 복원한다.
            topOffset = firstVisibleView.top - binding.rvFavoriteMusicTracks.paddingTop
        )
    }

    private fun restoreFavoriteMusicTrackScrollPosition(
        scrollPosition: FavoriteMusicTrackScrollPosition,
    ) {
        val layoutManager =
            binding.rvFavoriteMusicTracks.layoutManager
                    as? LinearLayoutManager
                ?: return

        val lastAdapterPosition = favoriteMusicTrackAdapter.itemCount - 1

        if (lastAdapterPosition < 0) return

        // 목록 개수가 달라졌을 경우 유효한 adapter position으로 보정한다.
        val availablePosition =
            scrollPosition.adapterPosition.coerceIn(
                minimumValue = 0,
                maximumValue = lastAdapterPosition,
            )

        layoutManager.scrollToPositionWithOffset(
            availablePosition,
            scrollPosition.topOffset,
        )
    }

    private fun scrollFavoriteMusicTracksToTop() {
        val layoutManager =
            binding.rvFavoriteMusicTracks.layoutManager
                    as? LinearLayoutManager
                ?: return

        if (favoriteMusicTrackAdapter.itemCount == 0) return

        layoutManager.scrollToPositionWithOffset(
            0,
            0,
        )
    }

    override fun onResume() {
        super.onResume()
        vm.refreshSortForCurrentLanguage()
    }

    private fun playMusic(id: String) {
        requireContext().playMusic(
            id = id,
            queueSource = PlaybackQueueSource.FAVORITE_MUSIC_TRACKS
        )
    }

    private fun openMusicOptionMenuPopup(track: MusicTrackEntity, anchorView: View) {
        MusicTrackOptionMenuPopup(
            anchorView = anchorView,
            showAddToFavorites = false,
            showRemoveFromFavorites = true,
            onAddToFavorites = {},
            onRemoveFromFavorites = { removeMusicFromFavorites(track) }
        ).show()
    }

    private fun removeMusicFromFavorites(track: MusicTrackEntity) {
        vm.removeMusicTrackFromFavorites(track.id)
    }

    override fun onDestroyView() {
        _binding = null
        EventBus.getInstance().unregister(this)
        super.onDestroyView()
    }

    private data class FavoriteMusicTrackScrollPosition(
        val adapterPosition: Int,
        val topOffset: Int,
    )

    companion object {
        fun newInstance() = FavoriteMusicTracksFragment()
    }
}
