package com.hero.ziggymusic.view.main.model

import androidx.annotation.StringRes
import com.hero.ziggymusic.R

sealed class MainTitle(
    @get:StringRes val resId: Int,
    val showBackButton: Boolean = false,
    val showSettingButton: Boolean = true,
) {
    object MusicList : MainTitle(R.string.title_music_list)
    object MyPlaylist : MainTitle(R.string.title_my_playlist)
    object Setting : MainTitle(
        resId = R.string.title_setting,
        showBackButton = true,
        showSettingButton = false
    )
}
