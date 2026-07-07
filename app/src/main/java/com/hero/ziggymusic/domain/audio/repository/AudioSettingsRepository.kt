package com.hero.ziggymusic.domain.audio.repository

import com.hero.ziggymusic.presentation.main.setting.model.AudioSettingsUiState
import kotlinx.coroutines.flow.StateFlow

/*
 * 음향 설정 저장소와 실제 오디오 효과 적용을 한 곳에서 관리한다.
 */
interface AudioSettingsRepository {
    // AudioSettingsFragment와 AudioEffectBottomSheet가 같은 설정 상태를 관찰하기 위한 단일 상태 스트림
    val settingsState: StateFlow<AudioSettingsUiState>

    fun refreshSettings() // 저장된 값을 다시 읽어 현재 UI 상태로 반영한다.
    fun applySavedSettings() // 앱 시작 또는 오디오 세션 생성 후 저장된 음향 설정을 실제 효과에 적용한다.

    fun setEqualizerEnabled(enabled: Boolean)
    fun setEqualizerPreset(presetPosition: Int) // 프리셋 선택 시 EQ를 켜고 현재 프리셋을 갱신한다.
    fun setCustomEqualizerPreset() // 저장된 EQ 밴드 값을 사용하는 Custom 프리셋으로 전환한다.

    // 사용자가 EQ 밴드를 직접 조정하면 Custom 프리셋으로 전환하고 값을 적용한다.
    fun updateEqualizerBandFromUser(
        bandIndex: Int,
        progress: Int,
    )

    // BassBoost는 EQ 프리셋과 별도 효과이므로 currentPreset은 변경하지 않는다.
    fun updateBassStrength(progress: Int)
    // Virtualizer는 EQ 프리셋과 별도 효과이므로 currentPreset은 변경하지 않는다.
    fun updateVirtualizerStrength(progress: Int)

    fun setLoudnessNormalizerEnabled(enabled: Boolean)
}
