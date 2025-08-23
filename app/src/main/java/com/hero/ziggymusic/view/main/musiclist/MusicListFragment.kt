package com.hero.ziggymusic.view.main.musiclist

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.entity.PlayerModel
import com.hero.ziggymusic.databinding.FragmentMusicListBinding
import com.hero.ziggymusic.event.EventBus
import com.hero.ziggymusic.ext.playMusic
import com.hero.ziggymusic.service.MusicService
import com.hero.ziggymusic.view.listener.OnRecyclerItemClickListener
import com.hero.ziggymusic.view.main.musiclist.viewmodel.MusicListViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MusicListFragment : Fragment(),
    OnRecyclerItemClickListener<MusicModel> {

    private var _binding: FragmentMusicListBinding? = null
    private val binding get() = _binding!!

    private val musicListViewModel by viewModels<MusicListViewModel>()
    private var playerModel: PlayerModel = PlayerModel()

    private lateinit var musicListAdapter: MusicListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(inflater, R.layout.fragment_music_list, container, false)
        binding.lifecycleOwner = this
        binding.viewModel = musicListViewModel

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EventBus.getInstance().register(this)
        initRecyclerView(binding.rvMusicList)
        setupListeners()
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        musicListAdapter = MusicListAdapter()

        recyclerView.run {
            layoutManager = LinearLayoutManager(context)
            adapter = musicListAdapter
        }
    }

    private fun setupListeners() {
        musicListAdapter.setOnRecyclerItemClickListener(this)
    }

    override fun onItemClick(position: Int, view: View, data: MusicModel) {
        when (view.id) {
            R.id.iv_music_option_menu -> openAddOrDeleteToFromMyPlaylistOptionMenu(data, view)

            else -> {
                playMusic(data.id)
                Log.d("onItemClick", "MusicModel: $data, ${data.id}")
            }
        }
    }

    private fun playMusic(musicKey: String) {
        val intent = Intent(requireActivity(), MusicService::class.java)
        intent.putExtra("id", musicKey)
        requireActivity().startService(intent)
        requireContext().playMusic(musicKey)
    }

    private fun openAddOrDeleteToFromMyPlaylistOptionMenu(data: MusicModel, anchorView: View) {
        val popupMenu = PopupMenu(requireActivity(), anchorView)
        val menuId: Int = if (musicListViewModel.isContainedInMyPlayList(data.id)) {
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
        musicListViewModel.addMusicToMyPlaylist(musicModel)
    }

    private fun deleteMusicFromMyPlayList(musicModel: MusicModel) {
        // Local DB에 저장한다.
        musicListViewModel.deleteMusicFromMyPlaylist(musicModel)
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