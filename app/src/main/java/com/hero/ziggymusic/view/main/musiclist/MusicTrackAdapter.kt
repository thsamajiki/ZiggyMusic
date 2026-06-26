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
import com.hero.ziggymusic.database.music.entity.MusicTrackEntity
import com.hero.ziggymusic.databinding.ItemMusicTrackBinding
import com.hero.ziggymusic.ext.expandTouchArea
import com.hero.ziggymusic.ext.toDurationText

class MusicTrackAdapter(
    private val onItemClick: (MusicTrackEntity) -> Unit,
    private val onOptionClick: (MusicTrackEntity, View) -> Unit,
) : ListAdapter<MusicTrackEntity, MusicTrackAdapter.MusicTrackViewHolder>(DIFF_CALLBACK) {
    private var favoriteTrackIds: Set<String> = emptySet() // 중복 ID를 허용하지 않아서 Set을 사용

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): MusicTrackViewHolder {
        val binding = ItemMusicTrackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return MusicTrackViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: MusicTrackViewHolder,
        position: Int,
    ) {
        val item = getItem(position)

        holder.bind(
            musicItem = item,
            isFavorite = item.id in favoriteTrackIds,
            onItemClick = onItemClick,
            onOptionClick = onOptionClick,
        )
    }

    /**
     * payload가 즐겨찾기 변경을 나타내면 별 아이콘만 부분 갱신한다.
     * payload가 없거나 처리할 수 없는 변경이면 아이템 전체를 다시 바인딩한다.
     */
    override fun onBindViewHolder(
        holder: MusicTrackViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (PAYLOAD_FAVORITE in payloads) {
            val item = getItem(position)
            holder.bindFavoriteTrack(item.id in favoriteTrackIds)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    /**
     * 최신 즐겨찾기 음악 ID를 반영하고, 즐겨찾기 상태가 변경된 아이템만
     * PAYLOAD_FAVORITE를 전달하여 부분 갱신한다.
     */
    fun updateFavoriteMusicTrackIds(newFavoriteTrackIds: Set<String>) {
        if (favoriteTrackIds == newFavoriteTrackIds) return

        val oldFavoriteTrackIds = favoriteTrackIds
        favoriteTrackIds = newFavoriteTrackIds

        currentList.forEachIndexed { index, music ->
            val wasFavorite = music.id in oldFavoriteTrackIds
            val isFavorite = music.id in newFavoriteTrackIds

            if (wasFavorite != isFavorite) {
                notifyItemChanged(index, PAYLOAD_FAVORITE)
            }
        }
    }

    class MusicTrackViewHolder(
        private val binding: ItemMusicTrackBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            musicItem: MusicTrackEntity,
            isFavorite: Boolean,
            onItemClick: (MusicTrackEntity) -> Unit,
            onOptionClick: (MusicTrackEntity, View) -> Unit,
        ) {
            bindFavoriteTrack(isFavorite)

            binding.root.setCardBackgroundColor(
                if (musicItem.isPlaying) Color.GRAY else Color.TRANSPARENT
            )

            Glide.with(binding.ivAlbumArt)
                .load(musicItem.getAlbumArtUri())
                .error(R.drawable.placeholder_album_art)
                .fallback(R.drawable.placeholder_album_art)
                .into(binding.ivAlbumArt)

            binding.tvTitle.text = musicItem.title.orEmpty()
            binding.tvArtist.text = musicItem.artist.orEmpty()
            binding.tvDuration.text = musicItem.duration.toDurationText()

            binding.root.setOnClickListener {
                onItemClick(musicItem)
            }

            binding.ivMusicTrackOptionMenu.expandTouchArea()
            binding.ivMusicTrackOptionMenu.setOnClickListener { view ->
                onOptionClick(musicItem, view)
            }
        }

        fun bindFavoriteTrack(isFavorite: Boolean) {
            binding.ivFavoriteStar.visibility =
                if (isFavorite) View.VISIBLE else View.INVISIBLE
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

        private const val PAYLOAD_FAVORITE = "payload_favorite" // 즐겨찾기 표시만 갱신
    }
}
