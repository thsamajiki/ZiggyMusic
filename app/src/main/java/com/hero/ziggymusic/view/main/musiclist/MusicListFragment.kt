package com.hero.ziggymusic.view.main.musiclist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.FragmentMusicListBinding
import com.hero.ziggymusic.ext.playMusic
import com.hero.ziggymusic.listener.OnRecyclerItemClickListener
import com.hero.ziggymusic.view.main.musiclist.viewmodel.MusicListViewModel
import com.hero.ziggymusic.view.main.myplaylist.MyPlaylistAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicListFragment : Fragment(), View.OnClickListener,
    OnRecyclerItemClickListener<MusicModel> {

    private var data = listOf<MusicModel>()
    private var _binding: FragmentMusicListBinding? = null
    private val binding get() = _binding!!

    private val musicListViewModel by viewModels<MusicListViewModel>()

    private lateinit var musicListAdapter: MusicListAdapter
    private lateinit var myPlayListAdapter: MyPlaylistAdapter

    companion object {
        fun newInstance() = MusicListFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DataBindingUtil.inflate(inflater, R.layout.fragment_music_list, container, false)
        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initRecyclerView(binding.rvMusicList)
        setupListeners()
        setupViewModel()
    }

    private fun setupViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            musicListViewModel.getAllMusics().observe(viewLifecycleOwner) {
                musicListAdapter.setMusicList(it)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        musicListAdapter = MusicListAdapter()

        recyclerView.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = musicListAdapter
        }
    }

    private fun setupListeners() {
        musicListAdapter.setOnRecyclerItemClickListener(this)
    }

    override fun onClick(view: View?) {
        when (view?.id) {

        }
    }

    override fun onItemClick(position: Int, view: View, data: MusicModel) {
        when (view.id) {
            R.id.iv_music_option_menu -> openAddOrDeleteToFromMyPlaylistOptionMenu(data, view)

            else -> playMusic(data.id)
        }
    }

    private fun playMusic(musicKey: String) {
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
}