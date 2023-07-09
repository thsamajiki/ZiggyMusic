package com.hero.ziggymusic.view.main.myplaylist

import android.content.Intent
import android.os.Bundle
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
import com.hero.ziggymusic.databinding.FragmentMyPlaylistBinding
import com.hero.ziggymusic.event.Event
import com.hero.ziggymusic.event.EventBus
import com.hero.ziggymusic.ext.playMusic
import com.hero.ziggymusic.service.MusicService
import com.hero.ziggymusic.view.listener.OnRecyclerItemClickListener
import com.hero.ziggymusic.view.main.myplaylist.viewmodel.MyPlaylistViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MyPlaylistFragment : Fragment(), View.OnClickListener,
    OnRecyclerItemClickListener<MusicModel> {

    private var _binding: FragmentMyPlaylistBinding? = null
    private val binding get() = _binding!!

    private val myPlayListViewModel by viewModels<MyPlaylistViewModel>()
    private val playerModel: PlayerModel = PlayerModel.getInstance()

    private lateinit var myPlayListAdapter: MyPlaylistAdapter

    companion object {
        fun newInstance() = MyPlaylistFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(inflater, R.layout.fragment_my_playlist, container, false)
        binding.lifecycleOwner = this
        binding.viewModel = myPlayListViewModel

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EventBus.getInstance().register(this)
        initRecyclerView(binding.rvMyPlayList)
        setupListeners()
    }

    override fun onDestroyView() {
        _binding = null
        EventBus.getInstance().unregister(this)
        super.onDestroyView()
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        myPlayListAdapter = MyPlaylistAdapter()

        recyclerView.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = myPlayListAdapter
        }
    }

    private fun setupListeners() {
        myPlayListAdapter.setOnRecyclerItemClickListener(this)
    }

    override fun onItemClick(position: Int, view: View, data: MusicModel) {
        when (view.id) {
            R.id.iv_music_option_menu -> openDeleteFromMyPlayListOptionMenu(data, view)

            else -> playMusic(data.id)
        }
    }

    private fun playMusic(musicKey: String) {
        val intent = Intent(requireActivity(), MusicService::class.java)
        requireActivity().startService(intent)
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
        myPlayListViewModel.deleteMusicFromMyPlaylist(musicModel)
    }

    override fun onClick(view: View?) {
    }
}