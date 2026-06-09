package com.hero.ziggymusic.view.main.musiclist

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.ItemMusicListBinding
import com.hero.ziggymusic.ext.toDurationText

class MusicListAdapter(
    private val onItemClick: (MusicModel) -> Unit,
    private val onOptionClick: (MusicModel, View) -> Unit,
) : ListAdapter<MusicModel, MusicListAdapter.MusicListViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicListViewHolder {
        val binding = ItemMusicListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return MusicListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicListViewHolder, position: Int) {
        holder.bind(
            getItem(position),
            onItemClick,
            onOptionClick
        )
    }

    class MusicListViewHolder(
        private val binding: ItemMusicListBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            musicItem: MusicModel,
            onItemClick: (MusicModel) -> Unit,
            onOptionClick: (MusicModel, View) -> Unit,
        ) {
            binding.root.setCardBackgroundColor(
                if (musicItem.isPlaying) Color.GRAY else Color.TRANSPARENT
            )
            Glide.with(binding.ivAlbum)
                .load(musicItem.getAlbumUri())
                .error(R.drawable.placeholder_album_art)
                .fallback(R.drawable.placeholder_album_art)
                .into(binding.ivAlbum)
            binding.tvSongTitle.text = musicItem.title.orEmpty()
            binding.tvSongArtist.text = musicItem.artist.orEmpty()
            binding.tvDuration.text = musicItem.duration.toDurationText()

            binding.root.setOnClickListener {
                onItemClick(musicItem)
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
