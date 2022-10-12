package com.hero.ziggymusic.view.main.myplaylist

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.database.music.entity.MusicFileData
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.ItemMyPlayListBinding
import com.hero.ziggymusic.view.main.BaseAdapter
import com.hero.ziggymusic.view.main.musiclist.MusicListAdapter
import java.text.SimpleDateFormat

class MyPlayListAdapter(private var context: Context,
                        private var data : List<MusicModel>,
                        private val onPopupClickListener: OnPopupClickListener)
    : BaseAdapter<MyPlayListAdapter.MyPlayListViewHolder, MusicModel>()  {

    val musicList = mutableListOf<MusicModel>()
    var mediaPlayer : MediaPlayer? = null

    interface OnPopupClickListener {
        fun popupOnClick(musicModel: MusicModel)
    }

//    private val onPopupClickListener: OnPopupClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyPlayListViewHolder {
        val binding = ItemMyPlayListBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return MyPlayListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyPlayListViewHolder, position: Int) {
        val musicItem = musicList[position]
        holder.setMusic(musicItem)
    }

    override fun getItemCount(): Int {
        return musicList.size
    }

    fun setMusicList(musicData: List<MusicModel>) {
        data = musicData
        notifyDataSetChanged()
    }


    inner class MyPlayListViewHolder(binding: ItemMyPlayListBinding) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {
        private var musicUri : Uri? = null // 현재 음원의 Uri
        private var ivAlbum : ImageView = binding.ivAlbum
        private var tvSongArtist : TextView = binding.tvSongArtist
        private var tvSongTitle : TextView = binding.tvSongTitle
        private var tvDuration : TextView = binding.tvDuration

        init {
            itemView.setOnClickListener {
                if(mediaPlayer != null) {
                    mediaPlayer?.release()
                    mediaPlayer = null
                }

                mediaPlayer = MediaPlayer.create(itemView.context, musicUri)
                mediaPlayer?.start()
            }
        }

        fun setMusic(musicItem: MusicModel) {
            musicUri = musicItem.getMusicFileUri()
            ivAlbum.setImageURI(musicItem.getAlbumUri())
            tvSongArtist.text = musicItem.musicArtist
            tvSongTitle.text = musicItem.musicTitle

            val simpleDateFormat = SimpleDateFormat("mm:ss")
            tvDuration.text = simpleDateFormat.format(musicItem.duration)
        }

        override fun onClick(p0: View) {
            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                getOnRecyclerItemClickListener()?.onItemClick(bindingAdapterPosition, p0, musicList[bindingAdapterPosition])
            }
        }
    }
}

