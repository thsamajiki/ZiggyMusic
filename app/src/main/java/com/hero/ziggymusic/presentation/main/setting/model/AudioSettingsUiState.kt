package com.hero.ziggymusic.presentation.main.setting.model

/**
 * 설정 화면과 바텀시트가 함께 관찰하는 음향 설정 UI 상태.
 *
 * Custom 프리셋은 EQ 밴드 직접 조작 상태를 의미하며,
 * BassBoost/Virtualizer 값은 EQ 프리셋과 독립적으로 관리한다.
 */
data class AudioSettingsUiState(
    val isEqualizerEnabled: Boolean = false,
    val currentPresetPosition: Int = DEFAULT_PRESET_POSITION,
    val bassStrength: Int = DEFAULT_EFFECT_VALUE,
    val virtualizerStrength: Int = DEFAULT_EFFECT_VALUE,
    val isLoudnessNormalizerEnabled: Boolean = false,
) {
    companion object {
        const val CUSTOM_PRESET_POSITION = 0 // 0번은 사용자가 EQ 밴드를 직접 조정한 Custom 프리셋 위치를 의미한다.
        const val DEFAULT_PRESET_POSITION = 1 // 기본 프리셋은 설정 화면의 spinner 기준 1번 항목으로 시작한다.
        const val DEFAULT_EFFECT_VALUE = 0
    }
}
