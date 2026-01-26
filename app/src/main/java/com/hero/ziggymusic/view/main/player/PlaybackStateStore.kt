package com.hero.ziggymusic.view.main.player

import android.content.Context
import androidx.core.content.edit
import com.hero.ziggymusic.view.main.player.model.LastPlayedMedia

/**
 * 마지막 재생 곡을 저장/복구하기 위한 간단한 영속 저장소
 * (앱 프로세스가 종료되어도 유지)
 */
class PlaybackStateStore(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 타입별 "마지막으로 재생한 ID"만 저장.
     * (최소 저장이 필요할 때 사용)
     */
    fun saveLastPlayedId(type: PlaybackContentType, id: String) {
        if (id.isBlank()) return
        val now = System.currentTimeMillis()

        prefs.edit {
            putString(type.idKey, id)
            putString(KEY_LAST_TYPE, type.name)
            putLong(KEY_LAST_UPDATED_AT, now)
        }
    }

    /**
     * 앱 전체 기준 "마지막으로 재생한 항목" 저장.
     * (type + id + position + playWhenReady)
     */
    fun saveLastPlayedMedia(media: LastPlayedMedia) {
        val now = media.updatedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()

        prefs.edit {
            putString(media.type.idKey, media.id)
            putLong(media.type.positionKey, media.positionMs)
            putBoolean(media.type.playWhenReadyKey, media.playWhenReady)

            putString(KEY_LAST_TYPE, media.type.name)
            putLong(KEY_LAST_UPDATED_AT, now)
        }
    }

    /**
     * 앱 전체 기준 "마지막으로 재생된 콘텐츠 1개"를 복원
     */
    fun loadLastPlayedMedia(): LastPlayedMedia? {
        val typeName = prefs.getString(KEY_LAST_TYPE, null) ?: return null
        val type = runCatching { PlaybackContentType.valueOf(typeName) }.getOrNull()
            ?: return null

        val id = prefs.getString(type.idKey, null) ?: return null
        val positionMs = prefs.getLong(type.positionKey, 0L)
        val playWhenReady = prefs.getBoolean(type.playWhenReadyKey, false)
        val updatedAtMs = prefs.getLong(KEY_LAST_UPDATED_AT, 0L).let { if (it > 0L) it else System.currentTimeMillis() }

        return LastPlayedMedia(
            type = type,
            id = id,
            positionMs = positionMs,
            playWhenReady = playWhenReady,
            updatedAtMs = updatedAtMs
        )
    }

    /**
     * 특정 타입(MUSIC/RADIO/PODCAST)에 대한 마지막 ID만 조회
     */
    fun loadLastPlayedId(type: PlaybackContentType): String? =
        prefs.getString(type.idKey, null)

    companion object {
        private const val PREF_NAME = "ziggymusic_playback_prefs"
        private const val KEY_LAST_TYPE = "last_playback_type"
        private const val KEY_LAST_UPDATED_AT = "last_updated_at_ms"
    }
}
