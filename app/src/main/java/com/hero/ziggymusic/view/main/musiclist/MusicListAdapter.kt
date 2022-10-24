package com.hero.ziggymusic.view.main.musiclist

import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.ItemMusicListBinding
import com.hero.ziggymusic.listener.OnRecyclerItemClickListener
import com.hero.ziggymusic.view.main.BaseAdapter
import java.text.SimpleDateFormat

class MusicListAdapter(
) : BaseAdapter<MusicListAdapter.MusicListViewHolder, MusicModel>() {

    private val musicList = mutableListOf<MusicModel>()
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        val diffUtil = object : DiffUtil.ItemCallback<MusicModel>(){
            override fun areItemsTheSame(oldItem: MusicModel, newItem: MusicModel): Boolean { // id 값만 비교
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: MusicModel, newItem: MusicModel): Boolean { // 내부 내용 비교
                return oldItem == newItem
            }
        }
    }

    interface OnPopupClickListener {
        fun popupOnClick(musicModel: MusicModel)
    }

    private var onPopupClickListener: OnPopupClickListener? = null

    fun teamCallBack(onPopupClickListener: OnPopupClickListener?) {
        this.onPopupClickListener = onPopupClickListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicListViewHolder {
        val binding =
            ItemMusicListBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return MusicListViewHolder(binding, getOnRecyclerItemClickListener()!!)
    }

    override fun onBindViewHolder(holder: MusicListViewHolder, position: Int) {
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

    fun setMusicListOnTab(musicData: List<MusicModel>) {
        musicList.clear()
        musicList.addAll(musicData)
        notifyDataSetChanged()
    }


    inner class MusicListViewHolder(
        binding: ItemMusicListBinding,
        private val itemClickListener: OnRecyclerItemClickListener<MusicModel>,
    ) :
        RecyclerView.ViewHolder(binding.root), View.OnClickListener {
        private var musicUri: Uri? = null // 현재 음원의 Uri
        private var ivAlbum: ImageView = binding.ivAlbum
        private var tvSongArtist: TextView = binding.tvSongArtist
        private var tvSongTitle: TextView = binding.tvSongTitle
        private var tvDuration: TextView = binding.tvDuration


        fun setMusic(musicItem: MusicModel) {
            musicUri = getMusicFileUri(musicItem)
            ivAlbum.setImageURI(getAlbumUri(musicItem))
            tvSongArtist.text = musicItem.musicArtist
            tvSongTitle.text = musicItem.musicTitle

            val simpleDateFormat = SimpleDateFormat("mm:ss")
            tvDuration.text = simpleDateFormat.format(musicItem.duration)

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