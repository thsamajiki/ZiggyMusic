package com.hero.ziggymusic.view.main.player.model

import com.hero.ziggymusic.view.main.player.PlaybackContentType

/**
 * 앱 재실행 시 "직전에 들었던 항목"을 복원하기 위한 최소 상태 모델.
 *
 * - type + id: 어떤 종류의 무엇을 들었는지
 * - positionMs: 팟캐스트/긴 트랙 재개에 필수 (라디오는 보통 0)
 * - playWhenReady: 재실행 시 자동 재생 여부를 복원할지(정책에 따라 사용)
 * - updatedAtMs: 가장 마지막으로 저장된 시각 (디버깅/정합성에 유용)
 */
data class LastPlayedMedia(
    val type: PlaybackContentType,
    val id: String,
    val positionMs: Long = 0L,
    val playWhenReady: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "LastPlayedMedia.id must not be blank." }
        require(positionMs >= 0L) { "LastPlayedMedia.positionMs must be >= 0." }
        require(updatedAtMs > 0L) { "LastPlayedMedia.updatedAtMs must be > 0." }
    }
}
