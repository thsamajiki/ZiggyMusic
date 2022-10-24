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
import com.hero.ziggymusic.listener.OnRecyclerItemClickListener
import com.hero.ziggymusic.view.main.musiclist.viewmodel.MusicListViewModel
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
        setupView()
        setupViewModel()
    }

    private fun setupView() {

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
        musicListAdapter.teamCallBack(object : MusicListAdapter.OnPopupClickListener {
            override fun popupOnClick(musicModel: MusicModel) {
            }

        })
        recyclerView.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = musicListAdapter
        }
    }

    private fun setupListeners() {
        musicListAdapter.setOnRecyclerItemClickListener(object :
            OnRecyclerItemClickListener<MusicModel> {
            override fun onItemClick(position: Int, view: View, data: MusicModel) {
                intentNowPlaying(data.id)
            }
        })

        musicListAdapter.notifyDataSetChanged()
    }

    override fun onClick(view: View?) {
        when (view?.id) {

        }
    }

    override fun onItemClick(position: Int, view: View, data: MusicModel) {
        when (view.id) {
            R.id.iv_music_option_menu -> openAddToMyPlayListOptionMenu(data, view)

            else -> intentNowPlaying(data.id)
        }
    }

    private fun intentNowPlaying(musicKey: String) {
//        val intent = NowPlayingFragment.newInstance(requireActivity(), musicKey)
//        startActivity(intent)
    }

    private fun openAddToMyPlayListOptionMenu(data: MusicModel, anchorView: View) {
        val popupMenu = PopupMenu(requireActivity(), anchorView)
        popupMenu.menuInflater.inflate(R.menu.menu_add_music_to_myplaylist_option, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item?.itemId) {
                R.id.menu_add_music_to_my_playlist -> addMusicToMyPlayList(data)
            }

            true
        }

        popupMenu.show()
    }

    private fun addMusicToMyPlayList(musicModel: MusicModel) {

    }
}