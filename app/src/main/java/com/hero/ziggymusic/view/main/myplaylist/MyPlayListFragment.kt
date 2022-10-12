package com.hero.ziggymusic.view.main.myplaylist

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicFileData
import com.hero.ziggymusic.databinding.FragmentMyPlayListBinding
import com.hero.ziggymusic.listener.OnRecyclerItemClickListener
import com.hero.ziggymusic.view.main.nowplaying.NowPlayingActivity
import com.hero.ziggymusic.view.main.myplaylist.viewmodel.MyPlayListViewModel

class MyPlayListFragment : Fragment(), View.OnClickListener {

    private var data = arrayListOf<MusicFileData>()
    private lateinit var binding: FragmentMyPlayListBinding
    private val myPlayListViewModel : MyPlayListViewModel by viewModels<MyPlayListViewModel>()

    companion object {
        fun newInstance() = MyPlayListFragment()
    }

    private lateinit var viewModel: MyPlayListViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view : View = inflater.inflate(R.layout.fragment_my_play_list, container, false)

        binding = DataBindingUtil.setContentView(requireActivity(), R.layout.fragment_my_play_list)


        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[MyPlayListViewModel::class.java]

        val myPlayListAdapter = MyPlayListAdapter(data)
        binding.rvMyPlayList.layoutManager = LinearLayoutManager(requireActivity())

        val intent = Intent(this.context, NowPlayingActivity::class.java)
        myPlayListAdapter.setOnRecyclerItemClickListener(object :
            OnRecyclerItemClickListener<MusicFileData> {
            override fun onItemClick(position: Int, view: View, data: MusicFileData) {
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
        val adapter = MyPlayListAdapter(data)
        adapter.musicList.addAll(getMyPlayList())
    }

    private fun getMyPlayList() : List<MusicFileData> {
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
}