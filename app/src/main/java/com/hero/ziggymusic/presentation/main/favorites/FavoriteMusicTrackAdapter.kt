package com.hero.ziggymusic.presentation.main.favorites

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.hero.ziggymusic.R
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity
import com.hero.ziggymusic.databinding.ItemFavoriteMusicTrackBinding
import com.hero.ziggymusic.presentation.common.ext.expandTouchArea
import com.hero.ziggymusic.core.ext.toDurationText

class FavoriteMusicTrackAdapter(
    private val onItemClick: (MusicTrackEntity) -> Unit,
    private val onOptionClick: (MusicTrackEntity, View) -> Unit,
) : ListAdapter<MusicTrackEntity, FavoriteMusicTrackAdapter.FavoriteMusicTrackViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): FavoriteMusicTrackViewHolder {
        val binding = ItemFavoriteMusicTrackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FavoriteMusicTrackViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: FavoriteMusicTrackViewHolder,
        position: Int
    ) {
        val item = getItem(position)

        holder.bind(
            favoriteItem = item,
            onItemClick = onItemClick,
            onOptionClick = onOptionClick,
        )
    }

    class FavoriteMusicTrackViewHolder(
        private val binding: ItemFavoriteMusicTrackBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            favoriteItem: MusicTrackEntity,
            onItemClick: (MusicTrackEntity) -> Unit,
            onOptionClick: (MusicTrackEntity, View) -> Unit,
        ) {
            binding.root.setCardBackgroundColor(
                if (favoriteItem.isPlaying) Color.GRAY else Color.TRANSPARENT
            )

            Glide.with(binding.ivAlbumArt)
                .load(favoriteItem.getAlbumArtUri())
                .error(R.drawable.placeholder_album_art)
                .fallback(R.drawable.placeholder_album_art)
                .into(binding.ivAlbumArt)

            binding.tvTitle.text = favoriteItem.title.orEmpty()
            binding.tvArtist.text = favoriteItem.artist.orEmpty()
            binding.tvDuration.text = favoriteItem.duration.toDurationText()

            binding.root.setOnClickListener {
                onItemClick(favoriteItem)
            }

            binding.ivMusicTrackOptionMenu.expandTouchArea()
            binding.ivMusicTrackOptionMenu.setOnClickListener { view ->
                onOptionClick(favoriteItem, view)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MusicTrackEntity>() {
            override fun areItemsTheSame(oldItem: MusicTrackEntity, newItem: MusicTrackEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: MusicTrackEntity, newItem: MusicTrackEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
