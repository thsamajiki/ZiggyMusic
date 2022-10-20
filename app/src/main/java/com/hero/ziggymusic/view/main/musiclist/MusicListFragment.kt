package com.hero.ziggymusic.view.main.musiclist

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
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
import com.hero.ziggymusic.view.main.nowplaying.NowPlayingActivity
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
    private val permission = Manifest.permission.READ_EXTERNAL_STORAGE

    companion object {
        fun newInstance() = MusicListFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_music_list, container, false)

        _binding = DataBindingUtil.setContentView(requireActivity(), R.layout.fragment_music_list)
        binding.lifecycleOwner = this

        return view
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
        musicListAdapter = MusicListAdapter(
            object : MusicListAdapter.OnPopupClickListener {
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

    private fun isPermitted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onClick(view: View?) {
        when (requireView().id) {

        }
    }

    override fun onItemClick(position: Int, view: View, data: MusicModel) {
        when (view.id) {
            R.id.iv_music_option_menu -> openAddToMyPlayListOptionMenu(data)

            else -> intentNowPlaying(data.id)
        }
    }

    private fun intentNowPlaying(musicKey: String) {
        val intent = NowPlayingActivity.getIntent(requireActivity(), musicKey)
        startActivity(intent)
    }

    private fun openAddToMyPlayListOptionMenu(data: MusicModel) {
        val popupMenu = PopupMenu(requireActivity(), requireView())
        popupMenu.menuInflater.inflate(R.menu.menu_add_music_to_myplaylist_option, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item?.itemId) {
                R.id.menu_add_music_to_myplaylist -> addMusicToMyPlayList(data)
            }

            true
        }

        popupMenu.show()
    }

    private fun addMusicToMyPlayList(musicModel: MusicModel) {

    }
}