package com.hero.ziggymusic.presentation.main.setting.viewmodel

import androidx.lifecycle.ViewModel
import com.hero.ziggymusic.domain.audio.repository.AudioSettingsRepository
import com.hero.ziggymusic.presentation.main.player.audioeffect.AudioEffectPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AudioSettingsViewModel @Inject constructor(
    private val audioSettingsRepository: AudioSettingsRepository,
) : ViewModel() {
    val uiState = audioSettingsRepository.settingsState

    fun refreshSettings() {
        audioSettingsRepository.refreshSettings()
    }

    fun applySavedSettings() {
        audioSettingsRepository.applySavedSettings()
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        audioSettingsRepository.setEqualizerEnabled(enabled)
    }

    // 설정 화면 spinner에서 선택한 프리셋 position을 그대로 Repository에 전달한다.
    fun setEqualizerPreset(presetPosition: Int) {
        audioSettingsRepository.setEqualizerPreset(presetPosition)
    }

    // 바텀시트 프리셋 모델을 설정 화면 spinner position으로 변환해 적용한다.
    fun setEqualizerPreset(preset: AudioEffectPreset) {
        audioSettingsRepository.setEqualizerPreset(preset.config.settingsPresetPosition)
    }

    fun setCustomEqualizerPreset() {
        audioSettingsRepository.setCustomEqualizerPreset()
    }

    fun updateEqualizerBandFromUser(
        bandIndex: Int,
        progress: Int,
    ) {
        audioSettingsRepository.updateEqualizerBandFromUser(
            bandIndex = bandIndex,
            progress = progress,
        )
    }

    fun updateBassStrength(progress: Int) {
        audioSettingsRepository.updateBassStrength(progress)
    }

    fun updateVirtualizerStrength(progress: Int) {
        audioSettingsRepository.updateVirtualizerStrength(progress)
    }

    fun setLoudnessNormalizerEnabled(enabled: Boolean) {
        audioSettingsRepository.setLoudnessNormalizerEnabled(enabled)
    }
}
