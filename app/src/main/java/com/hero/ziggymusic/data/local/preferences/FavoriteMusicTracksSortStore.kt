package com.hero.ziggymusic.data.local.preferences

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.hero.ziggymusic.domain.music.model.MusicTracksSortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteMusicTracksSortStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private val _sortOrder = MutableLiveData(loadSavedSortOrder())
    val sortOrder: LiveData<MusicTracksSortOrder>
        get() = _sortOrder

    fun setSortOrder(
        sortOrder: MusicTracksSortOrder
    ) {
        if (_sortOrder.value == sortOrder) return

        prefs.edit {
            putString(KEY_FAVORITE_MUSIC_TRACKS_SORT_ORDER, sortOrder.name)
        }
        _sortOrder.value = sortOrder
    }

    private fun loadSavedSortOrder(): MusicTracksSortOrder {
        val savedName = prefs.getString(KEY_FAVORITE_MUSIC_TRACKS_SORT_ORDER, null)

        return savedName
            ?.let { name ->
                runCatching {
                    MusicTracksSortOrder.valueOf(name)
                }.getOrNull()
            }
            // 즐겨찾기에서는 최근에 추가한 곡을 바로 찾는 흐름을 기본으로 한다.
            ?: MusicTracksSortOrder.DATE_ADDED_DESCENDING
    }

    private companion object {
        const val PREFERENCES_NAME =
            "ziggymusic_favorite_music_tracks_sort_preferences"

        const val KEY_FAVORITE_MUSIC_TRACKS_SORT_ORDER =
            "favorite_music_tracks_sort_order"
    }
}
