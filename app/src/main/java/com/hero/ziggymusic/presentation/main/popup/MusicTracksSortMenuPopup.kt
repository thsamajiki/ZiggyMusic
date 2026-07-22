package com.hero.ziggymusic.presentation.main.popup

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.annotation.StringRes
import androidx.core.graphics.drawable.toDrawable
import com.hero.ziggymusic.R
import com.hero.ziggymusic.databinding.PopupMusicTracksSortMenuBinding
import com.hero.ziggymusic.domain.music.model.MusicTracksSortOrder

class MusicTracksSortMenuPopup(
    private val anchorView: View,
    private val selectedSortOrder: MusicTracksSortOrder,
    @get:StringRes
    private val dateAddedLabelResId: Int = R.string.sort_added_to_music_tracks_date,
    private val onSortOrderSelected: (MusicTracksSortOrder) -> Unit,
) {
    fun show() {
        val binding = PopupMusicTracksSortMenuBinding.inflate(
            LayoutInflater.from(anchorView.context)
        )

        val popupWidth =
            anchorView.resources.getDimensionPixelSize(
                R.dimen.music_tracks_sort_menu_popup_width
            )

        val popupWindow = PopupWindow(
            binding.root,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = anchorView.resources.getDimension(
                R.dimen.music_option_menu_popup_elevation
            )
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        updateSortItemState(
            layout = binding.layoutTitleSort,
            checkIcon = binding.ivCheckTitleSort,
            directionIcon = binding.ivDirectionTitleSort,
            isSelected = selectedSortOrder.isTitleOrder,
            labelResId = R.string.sort_title
        )

        updateSortItemState(
            layout = binding.layoutArtistSort,
            checkIcon = binding.ivCheckArtistSort,
            directionIcon = binding.ivDirectionArtistSort,
            isSelected = selectedSortOrder.isArtistOrder,
            labelResId = R.string.sort_artist
        )

        updateSortItemState(
            layout = binding.layoutAddedDateSort,
            checkIcon = binding.ivCheckAddedDateSort,
            directionIcon = binding.ivDirectionAddedDateSort,
            isSelected = selectedSortOrder.isDateAddedOrder,
            labelResId = dateAddedLabelResId
        )

        binding.layoutTitleSort.setOnClickListener {
            onSortOrderSelected(
                getNextTitleSortOrder()
            )
            popupWindow.dismiss()
        }

        binding.layoutArtistSort.setOnClickListener {
            onSortOrderSelected(
                getNextArtistSortOrder()
            )
            popupWindow.dismiss()
        }

        binding.layoutAddedDateSort.setOnClickListener {
            onSortOrderSelected(
                getNextDateAddedSortOrder()
            )
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(
            anchorView,
            0,
            0,
            Gravity.END
        )
    }

    private fun updateSortItemState(
        layout: View,
        checkIcon: ImageView,
        directionIcon: ImageView,
        isSelected: Boolean,
        @StringRes labelResId: Int,
    ) {
        val visibility =
            if (isSelected) View.VISIBLE else View.INVISIBLE

        checkIcon.visibility = visibility
        directionIcon.visibility = visibility
        layout.isSelected = isSelected

        if (!isSelected) {
            layout.contentDescription =
                anchorView.context.getString(labelResId)
            return
        }

        val directionIconResId =
            if (selectedSortOrder.isDescending) {
                R.drawable.ic_sort_descending
            } else {
                R.drawable.ic_sort_ascending
            }

        val directionTextResId =
            if (selectedSortOrder.isDescending) {
                R.string.sort_descending
            } else {
                R.string.sort_ascending
            }

        directionIcon.setImageResource(directionIconResId)

        layout.contentDescription =
            anchorView.context.getString(
                R.string.sort_item_selected_description,
                anchorView.context.getString(labelResId),
                anchorView.context.getString(directionTextResId)
            )
    }

    // 같은 기준을 다시 선택하면 방향을 전환하고, 새 기준은 기본 방향으로 시작한다.
    private fun getNextTitleSortOrder(): MusicTracksSortOrder {
        return when (selectedSortOrder) {
            MusicTracksSortOrder.TITLE_ASCENDING ->
                MusicTracksSortOrder.TITLE_DESCENDING

            MusicTracksSortOrder.TITLE_DESCENDING ->
                MusicTracksSortOrder.TITLE_ASCENDING

            MusicTracksSortOrder.ARTIST_ASCENDING,
            MusicTracksSortOrder.ARTIST_DESCENDING,
            MusicTracksSortOrder.DATE_ADDED_ASCENDING,
            MusicTracksSortOrder.DATE_ADDED_DESCENDING,
                ->
                MusicTracksSortOrder.TITLE_ASCENDING
        }
    }

    private fun getNextArtistSortOrder(): MusicTracksSortOrder {
        return when (selectedSortOrder) {
            MusicTracksSortOrder.ARTIST_ASCENDING ->
                MusicTracksSortOrder.ARTIST_DESCENDING

            MusicTracksSortOrder.ARTIST_DESCENDING ->
                MusicTracksSortOrder.ARTIST_ASCENDING

            MusicTracksSortOrder.TITLE_ASCENDING,
            MusicTracksSortOrder.TITLE_DESCENDING,
            MusicTracksSortOrder.DATE_ADDED_ASCENDING,
            MusicTracksSortOrder.DATE_ADDED_DESCENDING,
                ->
                MusicTracksSortOrder.ARTIST_ASCENDING
        }
    }

    private fun getNextDateAddedSortOrder(): MusicTracksSortOrder {
        return when (selectedSortOrder) {
            MusicTracksSortOrder.DATE_ADDED_DESCENDING ->
                MusicTracksSortOrder.DATE_ADDED_ASCENDING

            MusicTracksSortOrder.DATE_ADDED_ASCENDING ->
                MusicTracksSortOrder.DATE_ADDED_DESCENDING

            MusicTracksSortOrder.TITLE_ASCENDING,
            MusicTracksSortOrder.TITLE_DESCENDING,
            MusicTracksSortOrder.ARTIST_ASCENDING,
            MusicTracksSortOrder.ARTIST_DESCENDING,
                ->
                MusicTracksSortOrder.DATE_ADDED_DESCENDING
        }
    }
}
