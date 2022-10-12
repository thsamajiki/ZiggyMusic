package com.hero.ziggymusic.view.main.musiclist

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
import com.hero.ziggymusic.databinding.ItemMusicListBinding
import com.hero.ziggymusic.view.main.BaseAdapter
import java.text.SimpleDateFormat

class MusicListAdapter(private var context: Context,
                       private var data : List<MusicModel>,
                       private val onPopupClickListener: OnPopupClickListener)
    : BaseAdapter<MusicListAdapter.MusicListViewHolder, MusicModel>() {

    val musicList = mutableListOf<MusicModel>()
    var mediaPlayer : MediaPlayer? = null

    interface OnPopupClickListener {
        fun popupOnClick(musicModel: MusicModel)
    }

//    private val onPopupClickListener: OnPopupClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicListViewHolder {
        val binding = ItemMusicListBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return MusicListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicListViewHolder, position: Int) {
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


    inner class MusicListViewHolder(binding: ItemMusicListBinding) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {
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