package com.hero.ziggymusic.presentation.main.model

import androidx.annotation.StringRes
import com.hero.ziggymusic.R

sealed class MainTitle(
    @get:StringRes val resId: Int,
    val showBackButton: Boolean = false,
    val showSettingButton: Boolean = true,
) {
    object MusicTracks : MainTitle(R.string.title_music_tracks)
    object FavoriteTracks : MainTitle(R.string.title_favorite_tracks)
    object Settings : MainTitle(
        resId = R.string.title_settings,
        showBackButton = true,
        showSettingButton = false
    )
}
