package com.hero.ziggymusic.view.main.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.database.music.entity.MusicTrackEntity
import com.hero.ziggymusic.databinding.FragmentFavoriteMusicTracksBinding
import com.hero.ziggymusic.event.EventBus
import com.hero.ziggymusic.ext.playMusic
import com.hero.ziggymusic.playback.PlaybackQueueSource
import com.hero.ziggymusic.view.main.popup.MusicTrackOptionMenuPopup
import com.hero.ziggymusic.view.main.favorites.viewmodel.FavoriteMusicTrackListUiState
import com.hero.ziggymusic.view.main.favorites.viewmodel.FavoriteMusicTracksViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FavoriteMusicTracksFragment : Fragment() {
    private var _binding: FragmentFavoriteMusicTracksBinding? = null
    private val binding get() = _binding!!

    private val vm by viewModels<FavoriteMusicTracksViewModel>()

    private lateinit var favoriteMusicTrackAdapter: FavoriteMusicTrackAdapter

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
        vm.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is FavoriteMusicTrackListUiState.Idle -> {
                    binding.rvFavoriteMusicTracks.isVisible = false
                    binding.tvNothingFound.isVisible = false
                }

                is FavoriteMusicTrackListUiState.Content -> {
                    favoriteMusicTrackAdapter.submitList(state.data)
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
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
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

    companion object {
        fun newInstance() = FavoriteMusicTracksFragment()
    }
}
