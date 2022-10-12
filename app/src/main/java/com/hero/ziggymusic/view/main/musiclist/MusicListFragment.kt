package com.hero.ziggymusic.view.main.musiclist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicFileData
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.FragmentMusicListBinding
import com.hero.ziggymusic.listener.OnRecyclerItemClickListener
import com.hero.ziggymusic.view.main.musiclist.viewmodel.MusicListViewModel
import com.hero.ziggymusic.view.main.nowplaying.NowPlayingActivity

class MusicListFragment : Fragment(), View.OnClickListener, OnRecyclerItemClickListener<MusicModel> {

    private var data = listOf<MusicModel>()
    private lateinit var binding : FragmentMusicListBinding
    private lateinit var musicListViewModel: MusicListViewModel
    private lateinit var musicListAdapter: MusicListAdapter
    private val permission = Manifest.permission.READ_EXTERNAL_STORAGE

    private val EXTRA_MUSIC_DATA : String = "musicData"


    companion object {
        fun newInstance() = MusicListFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view : View = inflater.inflate(R.layout.fragment_music_list, container, false)
//        binding = FragmentMusicListBinding.inflate(layoutInflater, container, false)
        binding = DataBindingUtil.setContentView(requireActivity(), R.layout.fragment_music_list)
        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViewModel()

        setupListeners()
    }

    private fun initViewModel() {
        musicListViewModel = ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
            .create(MusicListViewModel::class.java)
        musicListViewModel.musicListLiveData.observe(viewLifecycleOwner, Observer {
            (binding.rvMusicList.adapter as MusicListAdapter).setMusicList(it)
        })
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        musicListAdapter = MusicListAdapter(requireActivity(), data, object : MusicListAdapter.OnPopupClickListener {
            override fun popupOnClick(musicModel: MusicModel) {
                TODO("Not yet implemented")
            }

        })
        recyclerView.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = musicListAdapter
        }
    }

    private fun initTeamListAdapter() {
        teamListAdapter =
            TeamListAdapter(requireActivity(), teamDataList, object : OnPopupClickListener() {
                fun popupOnClick(data: TeamEntity?) {
                    val intent = Intent(requireActivity(), TeamDetailActivity::class.java)
                    intent.putExtra(
                        com.hero.seoultechteams.view.main.team.TeamListFragment.EXTRA_TEAM_DATA,
                        data
                    )
                    //                startActivityForResult(intent, UPDATE_TEAM_DETAIL_REQ_CODE);
                }
            })
        teamListAdapter.setOnRecyclerItemClickListener(this)
        teamListAdapter.notifyDataSetChanged()
        rvTeamList.setAdapter(teamListAdapter)
    }

    private fun setupListeners() {
        val intent = Intent(this.context, NowPlayingActivity::class.java)
        musicListAdapter.setOnRecyclerItemClickListener(object : OnRecyclerItemClickListener<MusicModel> {
            override fun onItemClick(position: Int, view: View, data: MusicModel) {
                intent.putExtra("id", data.id)
                intent.putExtra("musicTitle", data.musicTitle)
                intent.putExtra("musicArtist", data.musicArtist)
                intent.putExtra("albumId", data.albumId)
                intent.putExtra("duration", data.duration)

                startActivity(intent)
            }
        })
    }

    private fun startProcess() {
        val adapter = MusicListAdapter(requireActivity(), data)
        adapter.musicList.addAll(getMusicList())
    }

    private fun isPermitted() : Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
    }


    private fun getMusicList() : List<MusicModel> {
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
        val musicList = mutableListOf<MusicModel>()

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

    override fun onClick(p0: View?) {
        TODO("Not yet implemented")
    }

    override fun onItemClick(position: Int, view: View, data: MusicModel) {
        when(view.id) {

        }
        binding.
        intentNowPlaying(data)
    }

    private fun intentNowPlaying(data: MusicModel) {
        val intent = Intent(requireActivity(), NowPlayingActivity::class.java)
        intent.putExtra(EXTRA_MUSIC_DATA, data)
        startActivity(intent)
    }
}