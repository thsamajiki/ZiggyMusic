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
import com.hero.ziggymusic.databinding.FragmentFavoritesBinding
import com.hero.ziggymusic.event.EventBus
import com.hero.ziggymusic.ext.playMusic
import com.hero.ziggymusic.playback.PlaybackQueueSource
import com.hero.ziggymusic.view.main.popup.MusicTrackOptionMenuPopup
import com.hero.ziggymusic.view.main.favorites.viewmodel.FavoritesUiState
import com.hero.ziggymusic.view.main.favorites.viewmodel.FavoritesViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FavoriteTracksFragment : Fragment() {
    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private val vm by viewModels<FavoritesViewModel>()

    private lateinit var favoriteTrackAdapter: FavoriteTrackAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EventBus.getInstance().register(this)
        initRecyclerView(binding.rvFavorites)
        collectUiState()
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        favoriteTrackAdapter = FavoriteTrackAdapter(
            onItemClick = { music ->
                playMusic(music.id)
            },
            onOptionClick = { music, view ->
                openMusicOptionMenuPopup(music, view)
            }
        )

        recyclerView.run {
            layoutManager = LinearLayoutManager(context)
            adapter = favoriteTrackAdapter
        }
    }

    private fun collectUiState() {
        vm.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is FavoritesUiState.Idle -> {
                    binding.rvFavorites.isVisible = false
                    binding.tvNothingFound.isVisible = false
                }

                is FavoritesUiState.Content -> {
                    favoriteTrackAdapter.submitList(state.data)
                    binding.rvFavorites.isVisible = true
                    binding.tvNothingFound.isVisible = false
                }

                is FavoritesUiState.Empty -> {
                    favoriteTrackAdapter.submitList(emptyList())
                    binding.tvNothingFound.text = vm.emptyStateMessage.value.orEmpty()
                    binding.rvFavorites.isVisible = false
                    binding.tvNothingFound.isVisible = true
                }

                is FavoritesUiState.Error -> {
                    favoriteTrackAdapter.submitList(emptyList())
                    binding.rvFavorites.isVisible = false
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
            queueSource = PlaybackQueueSource.FAVORITES
        )
    }

    private fun openMusicOptionMenuPopup(music: MusicTrackEntity, anchorView: View) {
        MusicTrackOptionMenuPopup(
            anchorView = anchorView,
            showAddToFavorites = false,
            showRemoveFromFavorites = true,
            onAddToFavorites = {},
            onRemoveFromFavorites = { removeMusicFromFavorites(music) }
        ).show()
    }

    private fun removeMusicFromFavorites(music: MusicTrackEntity) {
        vm.removeMusicFromFavorites(music.id)
    }

    override fun onDestroyView() {
        _binding = null
        EventBus.getInstance().unregister(this)
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = FavoriteTracksFragment()
    }
}
