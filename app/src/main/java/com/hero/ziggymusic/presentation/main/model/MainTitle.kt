package com.hero.ziggymusic.presentation.main.model

import androidx.annotation.StringRes
import com.hero.ziggymusic.R

sealed class MainTitle(
    @get:StringRes val resId: Int,
    val showBackButton: Boolean = false,
    val showAppSettingsButton: Boolean = true,
) {
    object MusicTracks : MainTitle(R.string.title_music_tracks)
    object FavoriteTracks : MainTitle(R.string.title_favorite_tracks)
    object AppSettings : MainTitle(
        resId = R.string.title_app_settings,
        showBackButton = true,
        showAppSettingsButton = false
    )

    object AudioSettings : MainTitle(
        resId = R.string.title_audio_settings,
        showBackButton = true,
        showAppSettingsButton = false
    )

    data class WebPage(
        @param:StringRes val titleResId: Int,
    ) : MainTitle(
        resId = titleResId,
        showBackButton = true,
        showAppSettingsButton = false
    )
}
