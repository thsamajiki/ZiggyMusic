package com.hero.ziggymusic.view.main.myplaylist

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicFileData
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.FragmentMyPlayListBinding
import com.hero.ziggymusic.listener.OnRecyclerItemClickListener
import com.hero.ziggymusic.view.main.musiclist.MusicListAdapter
import com.hero.ziggymusic.view.main.nowplaying.NowPlayingActivity
import com.hero.ziggymusic.view.main.myplaylist.viewmodel.MyPlayListViewModel

class MyPlayListFragment : Fragment(), View.OnClickListener, OnRecyclerItemClickListener<MusicModel> {

    private var data = listOf<MusicModel>()
    private lateinit var binding: FragmentMyPlayListBinding
    private lateinit var myPlayListAdapter: MyPlayListAdapter
//    private val myPlayListViewModel : MyPlayListViewModel by viewModels<MyPlayListViewModel>()
    private lateinit var myPlayListViewModel : MyPlayListViewModel
    private val EXTRA_MUSIC_DATA : String = "musicData"

    companion object {
        fun newInstance() = MyPlayListFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view : View = inflater.inflate(R.layout.fragment_my_play_list, container, false)

        binding = DataBindingUtil.setContentView(requireActivity(), R.layout.fragment_my_play_list)
        binding.lifecycleOwner = this

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViewModel()

        initRecyclerView(binding.rvMyPlayList)

        startProcess()

        binding.rvMyPlayList.layoutManager = LinearLayoutManager(requireActivity())

        setupListeners()
    }

    private fun initViewModel() {
        myPlayListViewModel = ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
            .create(MyPlayListViewModel::class.java)

        myPlayListViewModel.myPlayListLiveData.observe(viewLifecycleOwner, Observer {
            (binding.rvMyPlayList.adapter as MusicListAdapter).setMusicList(it)
        })
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        myPlayListAdapter = MyPlayListAdapter(requireActivity(), data, object : MyPlayListAdapter.OnPopupClickListener {
            override fun popupOnClick(musicModel: MusicModel) {
                TODO("Not yet implemented")
            }
        })

        recyclerView.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = myPlayListAdapter
        }
    }

    private fun setupListeners() {
        val intent = Intent(this.context, NowPlayingActivity::class.java)
        myPlayListAdapter.setOnRecyclerItemClickListener(object : OnRecyclerItemClickListener<MusicModel> {
            override fun onItemClick(position: Int, view: View, data: MusicModel) {
                intent.putExtra("id", data.id)
                intent.putExtra("musicTitle", data.musicTitle)
                intent.putExtra("musicArtist", data.musicArtist)
                intent.putExtra("albumId", data.albumId)
                intent.putExtra("duration", data.duration)

                startActivity(intent)
            }
        })

        myPlayListAdapter.notifyDataSetChanged()
    }

    private fun startProcess() {
        val adapter = MyPlayListAdapter(requireActivity(), data, object : MyPlayListAdapter.OnPopupClickListener {
            override fun popupOnClick(musicModel: MusicModel) {
                TODO("Not yet implemented")
            }
        })

        adapter.musicList.addAll(getMyPlayList())
    }

    private fun getMyPlayList() : List<MusicModel> {
        // 콘텐츠 리졸버로 음원 목록 가져오기
        // 1. 데이터 테이블 주소
        val musicListUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // 2. 가져올 데이터 컬럼을 정의
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )

        // 3. 콘텐츠 리졸버에 해당 데이터 요청 (음원 목록에 있는 0번째 줄을 가리킴)
        val cursor = requireContext().contentResolver.query(musicListUri, projection, null, null, null)

        // 4. 커서로 전달된 데이터를 꺼내서 저장
        val musicList = mutableListOf<MusicFileData>()

        while (cursor?.moveToNext() == true) {
            val id = cursor.getString(0)
            val title = cursor.getString(1)
            val artist = cursor.getString(2)
            val albumId = cursor.getString(3)
            val duration = cursor.getLong(4)

            val music = MusicFileData(id, title, artist, albumId, duration)
            musicList.add(music)
        }

        return musicList
    }

    override fun onClick(view: View?) {
        when(requireView().id) {

        }
    }

    private fun intentNowPlaying(data: MusicModel) {
        val intent = Intent(requireActivity(), NowPlayingActivity::class.java)

        intent.putExtra(EXTRA_MUSIC_DATA, data)
        startActivity(intent)
    }

    override fun onItemClick(position: Int, view: View, data: MusicModel) {
        when(view.id) {
            R.id.iv_music_option_menu -> openDeleteFromMyPlayListOptionMenu(data)
        }

        intentNowPlaying(data)
    }

    private fun openDeleteFromMyPlayListOptionMenu(data: MusicModel) {
        val popupMenu = PopupMenu(requireActivity(), requireView())
        popupMenu.menuInflater.inflate(R.menu.menu_delete_music_from_myplaylist_option, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener(object : PopupMenu.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem?): Boolean {
                when(item?.itemId) {
                    R.id.delete_music_from_myplaylist -> deleteMusicFromMyPlayList(data)
                }

                return true
            }
        })

        popupMenu.show()
    }

    private fun deleteMusicFromMyPlayList(musicModel: MusicModel) {
        TODO("Not yet implemented")
    }
}