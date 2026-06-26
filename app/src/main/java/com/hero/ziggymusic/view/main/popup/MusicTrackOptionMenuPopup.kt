package com.hero.ziggymusic.view.main.popup

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.hero.ziggymusic.R

class MusicTrackOptionMenuPopup(
    private val anchorView: View,
    private val showAddToFavorites: Boolean,
    private val showRemoveFromFavorites: Boolean,
    private val onAddToFavorites: () -> Unit,
    private val onRemoveFromFavorites: () -> Unit,
) {
    fun show() {
        val popupView = LayoutInflater.from(anchorView.context)
            .inflate(R.layout.popup_music_option_menu, null, false)
        val addLayout = popupView.findViewById<View>(R.id.layoutAddToFavorites)
        val removeLayout = popupView.findViewById<View>(R.id.layoutRemoveFromFavorites)
        val divider = popupView.findViewById<View>(R.id.dividerFavoriteOptions)

        addLayout.visibility = if (showAddToFavorites) View.VISIBLE else View.GONE
        removeLayout.visibility = if (showRemoveFromFavorites) View.VISIBLE else View.GONE
        divider.visibility = if (showAddToFavorites && showRemoveFromFavorites) View.VISIBLE else View.GONE

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = anchorView.resources.getDimension(R.dimen.music_option_menu_popup_elevation)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        addLayout.setOnClickListener {
            onAddToFavorites()
            popupWindow.dismiss()
        }
        removeLayout.setOnClickListener {
            onRemoveFromFavorites()
            popupWindow.dismiss()
        }

        popupView.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        )
        val horizontalOffset = anchorView.width - popupView.measuredWidth
        popupWindow.showAsDropDown(anchorView, horizontalOffset, 0)
    }
}
