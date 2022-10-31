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
class MusicListFragment : Fragment(), View.OnClickListener, OnRecyclerItemClickListener<MusicModel> {

    // Fragment View의 생명주기는 onCreateView ~ onDestroyView
    // Fragment에서 View Binding을 사용할 경우 Fragment는 View보다 오래 지속되어,
    // Fragment의 Lifecycle로 인해 메모리 누수가 발생할 수 있기 때문이다.
    // onDestroyView() 이후에 Fragment view는 종료되지만, Fragment는 여전히 살아 있다.
    // 즉 메모리 누수가 발생하게 된다.
    private var _binding: FragmentMusicListBinding? = null
    private val binding get() = _binding!! // 해당 속성을 참조할 때 실제 게터가 자동으로 호출된다

    private val musicListViewModel by viewModels<MusicListViewModel>()

    private lateinit var musicListAdapter: MusicListAdapter

    companion object {
        fun newInstance() = MusicListFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(inflater, R.layout.fragment_music_list, container, false)
        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initRecyclerView(binding.rvMusicList)
        setupViewModel()
        setupListeners()
    }

    // 특정 뷰들에 대해 작업의 범위를 지정
    // observe() 메소드를 사용하여 Observer를 LiveData에 연결한다.
    // observe() 메소드는 LifecycleOwner를 가져온다.
    // 일반적으로 Activity나 Fragment와 같은 UI 컨트롤러에서 Observer를 연결한다.
    private fun setupViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            musicListViewModel.getAllMusics().observe(viewLifecycleOwner) {
                musicListAdapter.setMusicList(it)
            }
        }
    }

    private fun setupListeners() {
        musicListAdapter.setOnRecyclerItemClickListener(this)
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        musicListAdapter = MusicListAdapter()

        recyclerView.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = musicListAdapter
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
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

    override fun onClick(view: View?) {
    }
}