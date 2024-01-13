package com.hero.ziggymusic.view.main.myplaylist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.ItemMyPlaylistBinding
import com.hero.ziggymusic.view.listener.OnRecyclerItemClickListener
import com.hero.ziggymusic.view.main.BaseAdapter

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