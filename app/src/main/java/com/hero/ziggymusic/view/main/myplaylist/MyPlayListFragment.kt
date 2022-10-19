package com.hero.ziggymusic.view.main.myplaylist

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
import com.hero.ziggymusic.Injector
import com.hero.ziggymusic.R
import com.hero.ziggymusic.ZiggyMusicApp
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.FragmentMyPlayListBinding
import com.hero.ziggymusic.listener.OnRecyclerItemClickListener
import com.hero.ziggymusic.view.main.myplaylist.viewmodel.MyPlayListViewModel
import com.hero.ziggymusic.view.main.myplaylist.viewmodel.MyPlayListViewModelFactory
import com.hero.ziggymusic.view.main.nowplaying.NowPlayingActivity

class MyPlayListFragment : Fragment(), View.OnClickListener,
    OnRecyclerItemClickListener<MusicModel> {

    private var data = listOf<MusicModel>()
    private var _binding: FragmentMyPlayListBinding? = null
    private val binding get() = _binding!!

    private val myPlayListViewModel by viewModels<MyPlayListViewModel> {
        MyPlayListViewModelFactory(
            ZiggyMusicApp.getInstance(),
            Injector.provideMusicRepository()
        )
    }

    private lateinit var myPlayListAdapter: MyPlayListAdapter

    companion object {
        fun newInstance() = MyPlayListFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_my_play_list, container, false)

        _binding = DataBindingUtil.setContentView(requireActivity(), R.layout.fragment_my_play_list)
        binding.lifecycleOwner = this

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initRecyclerView(binding.rvMyPlayList)

        setupListeners()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        myPlayListAdapter = MyPlayListAdapter(
            object : MyPlayListAdapter.OnPopupClickListener {
                override fun popupOnClick(musicModel: MusicModel) {
                }
            })
        recyclerView.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = myPlayListAdapter
        }
    }

    private fun setupListeners() {
        myPlayListAdapter.setOnRecyclerItemClickListener(object :
            OnRecyclerItemClickListener<MusicModel> {
            override fun onItemClick(position: Int, view: View, data: MusicModel) {
                intentNowPlaying(data.id)
            }
        })

        myPlayListAdapter.notifyDataSetChanged()
    }

    override fun onClick(view: View?) {
        when (requireView().id) {

        }
    }

    override fun onItemClick(position: Int, view: View, data: MusicModel) {
        when (view.id) {
            R.id.iv_music_option_menu -> openDeleteFromMyPlayListOptionMenu(data)

            else -> intentNowPlaying(data.id)
        }
    }

    private fun intentNowPlaying(musicKey: String) {
        val intent = NowPlayingActivity.getIntent(requireActivity(), musicKey)
        startActivity(intent)
    }

    private fun openDeleteFromMyPlayListOptionMenu(data: MusicModel) {
        val popupMenu = PopupMenu(requireActivity(), requireView())
        popupMenu.menuInflater.inflate(
            R.menu.menu_delete_music_from_myplaylist_option,
            popupMenu.menu
        )
        popupMenu.setOnMenuItemClickListener { item ->
            when (item?.itemId) {
                R.id.delete_music_from_myplaylist -> deleteMusicFromMyPlayList(data)
            }

            true
        }

        popupMenu.show()
    }

    private fun deleteMusicFromMyPlayList(musicModel: MusicModel) {

    }
}