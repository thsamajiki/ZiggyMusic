package com.hero.ziggymusic.presentation.main.model

import androidx.annotation.StringRes
import com.hero.ziggymusic.R

sealed class MainTitle(
    @get:StringRes val resId: Int,
    val showBackButton: Boolean = false,
    val showAppSettingsButton: Boolean = true,
    val showMusicTrackSortButton: Boolean = false,
) {
    object MusicTracks : MainTitle(
        resId = R.string.title_music_tracks,
        showMusicTrackSortButton = true
    )

    object FavoriteTracks : MainTitle(
        resId = R.string.title_favorite_tracks,
        showMusicTrackSortButton = true,
    )

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

    object LicenseNotices : MainTitle(
        resId = R.string.settings_license_notices,
        showBackButton = true,
        showAppSettingsButton = false
    )
}
