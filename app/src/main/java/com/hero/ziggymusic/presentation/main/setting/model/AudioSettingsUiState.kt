package com.hero.ziggymusic.presentation.main.setting.model

/**
 * 설정 화면과 바텀시트가 함께 관찰하는 음향 설정 UI 상태.
 *
 * Custom 프리셋은 EQ 밴드 직접 조작 상태를 의미하며,
 * Bass/Virtualizer/Reverb 값은 EQ 프리셋과 독립적으로 관리한다.
 */
data class AudioSettingsUiState(
    val isEqualizerEnabled: Boolean = false,
    val currentPresetPosition: Int = DEFAULT_PRESET_POSITION,
    val equalizerBandProgresses: List<Int> = emptyList(),
    val recentPresetPositions: List<Int> = DEFAULT_RECENT_PRESET_POSITIONS,
    val bassStrength: Int = DEFAULT_EFFECT_VALUE,
    val virtualizerStrength: Int = DEFAULT_EFFECT_VALUE,
    val reverbPresetPosition: Int = DEFAULT_REVERB_PRESET_POSITION,
    val isLoudnessNormalizerEnabled: Boolean = false,
) {
    companion object {
        // 사용자가 EQ 밴드를 직접 조정한 Custom 프리셋 위치
        const val CUSTOM_PRESET_POSITION = 0

        // 설정 화면 spinner 기준 기본 EQ 프리셋 위치
        const val DEFAULT_PRESET_POSITION = 1

        // 설정 화면 spinner 기준 리버브 프리셋 위치
        const val DEFAULT_REVERB_PRESET_POSITION = 0

        const val DEFAULT_EFFECT_VALUE = 0

        private const val POP_PRESET_POSITION = 9
        private const val ROCK_PRESET_POSITION = 10

        // 저장된 최근 프리셋이 없을 때 사용하는 기본 일반 EQ 프리셋 목록
        val DEFAULT_RECENT_PRESET_POSITIONS = listOf(
            DEFAULT_PRESET_POSITION,
            POP_PRESET_POSITION,
            ROCK_PRESET_POSITION,
        )
    }
}
