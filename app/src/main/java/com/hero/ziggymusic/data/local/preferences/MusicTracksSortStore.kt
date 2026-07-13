package com.hero.ziggymusic.data.local.preferences

import android.content.Context
import androidx.core.content.edit
import com.hero.ziggymusic.domain.music.model.MusicTracksSortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MusicTracksSortStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun loadMusicTracksSortOrder(): MusicTracksSortOrder {
        val savedSortOrderName = prefs.getString(
            KEY_MUSIC_TRACKS_SORT_ORDER,
            null
        )

        // 저장값이 없거나 유효하지 않으면 제목 오름차순을 사용한다.
        return savedSortOrderName
            ?.let { sortOrderName ->
                runCatching {
                    MusicTracksSortOrder.valueOf(sortOrderName)
                }.getOrNull()
            }
            ?: MusicTracksSortOrder.TITLE_ASCENDING
    }

    fun saveMusicTracksSortOrder(
        sortOrder: MusicTracksSortOrder,
    ) {
        prefs.edit {
            putString(
                KEY_MUSIC_TRACKS_SORT_ORDER,
                sortOrder.name
            )
        }
    }

    private companion object {
        const val PREFERENCES_NAME =
            "ziggymusic_music_tracks_sort_preferences"

        const val KEY_MUSIC_TRACKS_SORT_ORDER =
            "music_tracks_sort_order"
    }
}
