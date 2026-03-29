package com.hero.ziggymusic.view.main.myplaylist

import android.os.Bundle
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
import com.hero.ziggymusic.databinding.FragmentMyPlaylistBinding
import com.hero.ziggymusic.event.EventBus
import com.hero.ziggymusic.ext.playMusic
import com.hero.ziggymusic.service.MusicServiceLauncher
import com.hero.ziggymusic.view.main.myplaylist.viewmodel.MyPlaylistUiState
import com.hero.ziggymusic.view.main.myplaylist.viewmodel.MyPlaylistViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MyPlaylistFragment : Fragment() {
    private var _binding: FragmentMyPlaylistBinding? = null
    private val binding get() = _binding!!

    private val vm by viewModels<MyPlaylistViewModel>()

    private lateinit var myPlayListAdapter: MyPlaylistAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(inflater, R.layout.fragment_my_playlist, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = vm

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EventBus.getInstance().register(this)
        initRecyclerView(binding.rvMyPlayList)
        collectUiState()
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        myPlayListAdapter = MyPlaylistAdapter(
            onItemClick = { music ->
                playMusic(music.id)
            },
            onOptionClick = { music, view ->
                openDeleteFromMyPlayListOptionMenu(music, view)
            }
        )

        recyclerView.run {
            layoutManager = LinearLayoutManager(context)
            adapter = myPlayListAdapter
        }
    }

    private fun collectUiState() {
        vm.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MyPlaylistUiState.Idle -> {
                    binding.rvMyPlayList.isVisible = false
                    binding.tvNothingFound.isVisible = false
                }

                is MyPlaylistUiState.Content -> {
                    myPlayListAdapter.submitList(state.data)
                    binding.rvMyPlayList.isVisible = true
                    binding.tvNothingFound.isVisible = false
                }

                is MyPlaylistUiState.Empty -> {
                    myPlayListAdapter.submitList(emptyList())
                    binding.rvMyPlayList.isVisible = false
                    binding.tvNothingFound.isVisible = true
                }

                is MyPlaylistUiState.Error -> {
                    myPlayListAdapter.submitList(emptyList())
                    binding.rvMyPlayList.isVisible = false
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
    }

    private fun openDeleteFromMyPlayListOptionMenu(data: MusicModel, anchorView: View) {
        val popupMenu = PopupMenu(requireActivity(), anchorView)
        popupMenu.menuInflater.inflate(
            R.menu.menu_delete_music_from_myplaylist_option,
            popupMenu.menu
        )
        popupMenu.setOnMenuItemClickListener { item ->
            when (item?.itemId) {
                R.id.delete_music_from_my_playlist -> deleteMusicFromMyPlayList(data)
            }

            true
        }

        popupMenu.show()
    }

    private fun deleteMusicFromMyPlayList(musicModel: MusicModel) {
        vm.deleteMusicFromMyPlaylist(musicModel)
    }

    override fun onDestroyView() {
        _binding = null
        EventBus.getInstance().unregister(this)
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = MyPlaylistFragment()
    }
}
