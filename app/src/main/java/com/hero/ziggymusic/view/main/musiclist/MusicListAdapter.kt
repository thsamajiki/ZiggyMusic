package com.hero.ziggymusic.view.main.musiclist

import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.ItemMusicListBinding
import com.hero.ziggymusic.view.listener.OnRecyclerItemClickListener
import com.hero.ziggymusic.view.main.BaseAdapter

class MusicListAdapter(
) : BaseAdapter<MusicListAdapter.MusicListViewHolder, MusicModel>() {

    private val musicList = mutableListOf<MusicModel>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicListViewHolder {
        val binding = ItemMusicListBinding.inflate(LayoutInflater.from(parent.context), parent, false)

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

    class MusicListViewHolder(
        private val binding: ItemMusicListBinding,
        private val itemClickListener: OnRecyclerItemClickListener<MusicModel>
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun setMusic(musicItem: MusicModel) {
            binding.music = musicItem
            binding.executePendingBindings()

            binding.ivMusicOptionMenu.setOnClickListener {
                itemClickListener.onItemClick(absoluteAdapterPosition, it, musicItem)
            }

            itemView.setOnClickListener {
                itemClickListener.onItemClick(absoluteAdapterPosition, it, musicItem)
            }
        }
    }
}