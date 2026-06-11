package com.hero.ziggymusic.view.main.favorites

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.ItemFavoritesBinding
import com.hero.ziggymusic.ext.expandTouchArea
import com.hero.ziggymusic.ext.toDurationText

class FavoritesAdapter(
    private val onItemClick: (MusicModel) -> Unit,
    private val onOptionClick: (MusicModel, View) -> Unit,
) : ListAdapter<MusicModel, FavoritesAdapter.FavoritesViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoritesViewHolder {
        val binding = ItemFavoritesBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FavoritesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoritesViewHolder, position: Int) {
        holder.bind(
            getItem(position),
            onItemClick,
            onOptionClick
        )
    }

    class FavoritesViewHolder(
        private val binding: ItemFavoritesBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            favoriteItem: MusicModel,
            onItemClick: (MusicModel) -> Unit,
            onOptionClick: (MusicModel, View) -> Unit,
        ) {
            binding.root.setCardBackgroundColor(
                if (favoriteItem.isPlaying) Color.GRAY else Color.TRANSPARENT
            )
            Glide.with(binding.ivAlbum)
                .load(favoriteItem.getAlbumUri())
                .error(R.drawable.placeholder_album_art)
                .fallback(R.drawable.placeholder_album_art)
                .into(binding.ivAlbum)
            binding.tvSongTitle.text = favoriteItem.title.orEmpty()
            binding.tvSongArtist.text = favoriteItem.artist.orEmpty()
            binding.tvDuration.text = favoriteItem.duration.toDurationText()

            binding.root.setOnClickListener {
                onItemClick(favoriteItem)
                Log.d("onItemClick", "MusicModel: $favoriteItem, ${favoriteItem.id}")
            }

            binding.ivMusicOptionMenu.expandTouchArea()
            binding.ivMusicOptionMenu.setOnClickListener { view ->
                onOptionClick(favoriteItem, view)
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
