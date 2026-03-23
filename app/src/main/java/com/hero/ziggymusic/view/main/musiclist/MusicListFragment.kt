package com.hero.ziggymusic.view.main.musiclist

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.FragmentMusicListBinding
import com.hero.ziggymusic.event.EventBus
import com.hero.ziggymusic.ext.playMusic
import com.hero.ziggymusic.service.MusicServiceLauncher
import com.hero.ziggymusic.view.main.musiclist.viewmodel.MusicListUiState
import com.hero.ziggymusic.view.main.musiclist.viewmodel.MusicListViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MusicListFragment : Fragment() {
    private var _binding: FragmentMusicListBinding? = null
    private val binding get() = _binding!!

    private val vm by viewModels<MusicListViewModel>()

    private lateinit var musicListAdapter: MusicListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(inflater, R.layout.fragment_music_list, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = vm

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EventBus.getInstance().register(this)
        initRecyclerView(binding.rvMusicList)
        collectUiState()
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        musicListAdapter = MusicListAdapter(
            onItemClick = { music ->
                playMusic(music.id)
            },
            onOptionClick = { music, view ->
                openAddOrDeleteToFromMyPlaylistOptionMenu(music, view)
            }
        )

        recyclerView.run {
            layoutManager = LinearLayoutManager(context)
            adapter = musicListAdapter
        }
    }

    private fun collectUiState() {
        vm.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MusicListUiState.Idle -> {
                    binding.rvMusicList.isVisible = false
                    binding.tvNothingFound.isVisible = false
                }

                is MusicListUiState.Content -> {
                    musicListAdapter.submitList(state.data)
                    binding.rvMusicList.isVisible = true
                    binding.tvNothingFound.isVisible = false
                }

                is MusicListUiState.Empty -> {
                    musicListAdapter.submitList(emptyList())
                    binding.rvMusicList.isVisible = false
                    binding.tvNothingFound.isVisible = true
                }

                is MusicListUiState.Error -> {
                    Log.e("MusicListFragment", "음원 목록 불러오기 실패")
                    musicListAdapter.submitList(emptyList())
                    binding.rvMusicList.isVisible = false
                }
            }
        }

        vm.toastEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playMusic(musicKey: String) {
        requireContext().playMusic(musicKey)
        MusicServiceLauncher.startOrRefresh(requireContext())
    }

    private fun openAddOrDeleteToFromMyPlaylistOptionMenu(data: MusicModel, anchorView: View) {
        val popupMenu = PopupMenu(requireActivity(), anchorView)
        val menuId: Int = if (vm.isContainedInMyPlayList(data.id)) {
            R.menu.menu_delete_music_from_myplaylist_option
        } else {
            R.menu.menu_add_music_to_my_playlist_option
        }

        popupMenu.menuInflater.inflate(menuId, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item?.itemId) {
                R.id.menu_add_music_to_my_playlist -> addMusicToMyPlaylist(data)
                R.id.delete_music_from_my_playlist -> deleteMusicFromMyPlayList(data)
            }

            true
        }

        popupMenu.show()
    }

    private fun addMusicToMyPlaylist(musicModel: MusicModel) {
        // Local DB에 저장한다.
        vm.addMusicToMyPlaylist(musicModel)
    }

    private fun deleteMusicFromMyPlayList(musicModel: MusicModel) {
        // Local DB에 저장한다.
        vm.deleteMusicFromMyPlaylist(musicModel)
    }

    override fun onDestroyView() {
        _binding = null
        EventBus.getInstance().unregister(this)
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = MusicListFragment()
    }
}
