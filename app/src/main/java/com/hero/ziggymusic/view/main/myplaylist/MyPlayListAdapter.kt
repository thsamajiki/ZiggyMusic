package com.hero.ziggymusic.view.main.myplaylist

import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.ItemMyPlayListBinding
import com.hero.ziggymusic.view.main.BaseAdapter
import java.text.SimpleDateFormat

class MyPlayListAdapter(
    private val onPopupClickListener: OnPopupClickListener
) : BaseAdapter<MyPlayListAdapter.MyPlayListViewHolder, MusicModel>() {

    private val musicList = mutableListOf<MusicModel>()
    private var mediaPlayer: MediaPlayer? = null

    interface OnPopupClickListener {
        fun popupOnClick(musicModel: MusicModel)
    }

//    private val onPopupClickListener: OnPopupClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyPlayListViewHolder {
        val binding =
            ItemMyPlayListBinding.inflate(LayoutInflater.from(parent.context), parent, false)

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
        musicList.clear()
        musicList.addAll(musicData)
        notifyDataSetChanged()
    }


    inner class MyPlayListViewHolder(binding: ItemMyPlayListBinding) :
        RecyclerView.ViewHolder(binding.root), View.OnClickListener {
        private var musicUri: Uri? = null // 현재 음원의 Uri
        private var ivAlbum: ImageView = binding.ivAlbum
        private var tvSongArtist: TextView = binding.tvSongArtist
        private var tvSongTitle: TextView = binding.tvSongTitle
        private var tvDuration: TextView = binding.tvDuration

        init {
            itemView.setOnClickListener {
                if (mediaPlayer != null) {
                    mediaPlayer?.release()
                    mediaPlayer = null
                }

                mediaPlayer = MediaPlayer.create(itemView.context, musicUri)
                mediaPlayer?.start()
            }
        }

        fun setMusic(musicItem: MusicModel) {
            musicUri = getMusicFileUri(musicItem)
            ivAlbum.setImageURI(getAlbumUri(musicItem))
            tvSongArtist.text = musicItem.musicArtist
            tvSongTitle.text = musicItem.musicTitle

            val simpleDateFormat = SimpleDateFormat("mm:ss")
            tvDuration.text = simpleDateFormat.format(musicItem.duration)
        }

        private fun getMusicFileUri(musicItem: MusicModel): Uri {
            return Uri.withAppendedPath(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, musicItem.id)
        }

        private fun getAlbumUri(musicItem: MusicModel): Uri {
            return Uri.parse("content://media/external/audio/albumart/${musicItem.albumId}")
        }

        override fun onClick(view: View) {
            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                getOnRecyclerItemClickListener()?.onItemClick(
                    bindingAdapterPosition,
                    view,
                    musicList[bindingAdapterPosition]
                )
            }
        }
    }
}