package com.hero.ziggymusic.view.main.myplaylist

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.ItemMyPlaylistBinding

class MyPlaylistAdapter(
    private val onItemClick: (MusicModel) -> Unit,
    private val onOptionClick: (MusicModel, View) -> Unit,
) : ListAdapter<MusicModel, MyPlaylistAdapter.MyPlaylistViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyPlaylistViewHolder {
        val binding = ItemMyPlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MyPlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyPlaylistViewHolder, position: Int) {
        holder.bind(
            getItem(position),
            onItemClick,
            onOptionClick
        )
    }

    class MyPlaylistViewHolder(
        private val binding: ItemMyPlaylistBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            musicItem: MusicModel,
            onItemClick: (MusicModel) -> Unit,
            onOptionClick: (MusicModel, View) -> Unit,
        ) {
            binding.music = musicItem
            binding.executePendingBindings()

            binding.root.setOnClickListener {
                onItemClick(musicItem)
                Log.d("onItemClick", "MusicModel: $musicItem, ${musicItem.id}")
            }

            binding.ivMusicOptionMenu.setOnClickListener { view ->
                onOptionClick(musicItem, view)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MusicModel>() {
            override fun areItemsTheSame(oldItem: MusicModel, newItem: MusicModel): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: MusicModel, newItem: MusicModel): Boolean {
                return oldItem == newItem
            }
        }
    }
}
