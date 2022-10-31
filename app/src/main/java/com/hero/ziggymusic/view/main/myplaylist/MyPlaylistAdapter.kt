package com.hero.ziggymusic.view.main.myplaylist

import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.ItemMyPlaylistBinding
import com.hero.ziggymusic.listener.OnRecyclerItemClickListener
import com.hero.ziggymusic.view.main.BaseAdapter
import java.text.SimpleDateFormat

class MyPlaylistAdapter(
) : BaseAdapter<MyPlaylistAdapter.MyPlayListViewHolder, MusicModel>() {

    private val myPlaylist = mutableListOf<MusicModel>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyPlayListViewHolder {
        val binding = ItemMyPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return MyPlayListViewHolder(binding, getOnRecyclerItemClickListener()!!)
    }

    override fun onBindViewHolder(holder: MyPlayListViewHolder, position: Int) {
        val musicItem = myPlaylist[position]
        holder.setMusic(musicItem)
    }

    override fun getItemCount(): Int {
        return myPlaylist.size
    }

    fun setMusicList(musicData: List<MusicModel>) {
        myPlaylist.clear()
        myPlaylist.addAll(musicData)
        notifyDataSetChanged()
    }

    class MyPlayListViewHolder(
        private val binding: ItemMyPlaylistBinding,
        private val itemClickListener: OnRecyclerItemClickListener<MusicModel>
    ) :
        RecyclerView.ViewHolder(binding.root) {
        private var musicUri: Uri? = null // 현재 음원의 Uri

        fun setMusic(musicItem: MusicModel) {
            musicUri = getMusicFileUri(musicItem)
            binding.ivAlbum.setImageURI(getAlbumUri(musicItem))
            binding.tvSongArtist.text = musicItem.musicArtist
            binding.tvSongTitle.text = musicItem.musicTitle

            binding.ivMusicOptionMenu.setOnClickListener {
                itemClickListener.onItemClick(absoluteAdapterPosition, it, musicItem)
            }

            val simpleDateFormat = SimpleDateFormat("mm:ss")
            binding.tvDuration.text = simpleDateFormat.format(musicItem.duration)

            // 재생 중에 따라
            if(musicItem.isPlaying){
                // itemView 를 사용했는데 이건 리사이클러 뷰에서 뷰홀더(아이템 하나) 현재 아이템에 해당
                itemView.setBackgroundColor(Color.GRAY) // 재생중이면 배경 색을 회색
            }else{
                itemView.setBackgroundColor(Color.TRANSPARENT)
            }

            itemView.setOnClickListener {
                itemClickListener.onItemClick(absoluteAdapterPosition, it, musicItem)
            }
        }

        private fun getMusicFileUri(musicItem: MusicModel): Uri {
            return Uri.withAppendedPath(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, musicItem.id)
        }

        private fun getAlbumUri(musicItem: MusicModel): Uri {
            return Uri.parse("content://media/external/audio/albumart/${musicItem.albumId}")
        }
    }
}