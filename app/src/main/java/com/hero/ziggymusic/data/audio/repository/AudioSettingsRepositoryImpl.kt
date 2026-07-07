package com.hero.ziggymusic.data.audio.repository

import android.content.Context
import androidx.core.content.edit
import com.hero.ziggymusic.data.local.preferences.AudioSettingKeys
import com.hero.ziggymusic.domain.audio.repository.AudioSettingsRepository
import com.hero.ziggymusic.playback.manager.AudioEffectManager
import com.hero.ziggymusic.presentation.main.setting.model.AudioSettingsUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : AudioSettingsRepository {
    // EQ, BassBoost, Virtualizer, Loudness 설정을 Repository에서 공통 관리한다.
    private val prefs = context.getSharedPreferences(
        AudioSettingKeys.PREF_AUDIO_SETTINGS,
        Context.MODE_PRIVATE,
    )

    private val _settingsState = MutableStateFlow(loadAudioSettingsState())
    override val settingsState: StateFlow<AudioSettingsUiState> = _settingsState.asStateFlow()

    override fun refreshSettings() {
        updateStateFromStorage()
    }

    // 저장된 설정을 실제 Android AudioEffect와 DSP 체인에 다시 적용한다.
    override fun applySavedSettings() {
        val savedAudioSettings = loadAudioSettingsState()

        // 저장된 프리셋이 Custom이 아니면 Android Equalizer 프리셋을 먼저 적용한다.
        if (savedAudioSettings.currentPresetPosition > AudioSettingsUiState.CUSTOM_PRESET_POSITION) {
            AudioEffectManager.useEqualizerPreset(
                savedAudioSettings.currentPresetPosition - SETTINGS_PRESET_OFFSET
            )
        } else {
            applySavedEqualizerBands()
        }

        AudioEffectManager.applyBassStrength(savedAudioSettings.bassStrength)
        AudioEffectManager.applyVirtualizerStrength(savedAudioSettings.virtualizerStrength)
        AudioEffectManager.setEnabledFromPrefs(prefs)

        updateStateFromStorage()
    }

    override fun setEqualizerEnabled(enabled: Boolean) {
        if (loadAudioSettingsState().isEqualizerEnabled != enabled) {
            prefs.edit {
                putBoolean(AudioSettingKeys.KEY_EQUALIZER_ENABLED, enabled)
            }
        }

        AudioEffectManager.setEnabledFromPrefs(prefs)
        updateStateFromStorage()
    }

    // 프리셋 선택은 EQ 사용 의도가 명확하므로 EQ를 자동으로 켠다.
    override fun setEqualizerPreset(presetPosition: Int) {
        val normalizedPresetPosition = presetPosition.coerceAtLeast(
            AudioSettingsUiState.CUSTOM_PRESET_POSITION,
        )

        prefs.edit {
            putBoolean(AudioSettingKeys.KEY_EQUALIZER_ENABLED, true)
            putInt(AudioSettingKeys.KEY_PRESET, normalizedPresetPosition)
        }

        if (normalizedPresetPosition > AudioSettingsUiState.CUSTOM_PRESET_POSITION) {
            AudioEffectManager.useEqualizerPreset(normalizedPresetPosition - SETTINGS_PRESET_OFFSET)
        }

        AudioEffectManager.setEnabledFromPrefs(prefs)
        updateStateFromStorage()
    }

    // Custom은 저장된 EQ 밴드 값을 다시 적용하는 프리셋으로 취급한다.
    override fun setCustomEqualizerPreset() {
        prefs.edit {
            putBoolean(AudioSettingKeys.KEY_EQUALIZER_ENABLED, true)
            putInt(AudioSettingKeys.KEY_PRESET, AudioSettingsUiState.CUSTOM_PRESET_POSITION)
        }

        applySavedEqualizerBands()
        AudioEffectManager.setEnabledFromPrefs(prefs)

        updateStateFromStorage()
    }

    // 저장된 EQ 밴드 progress를 실제 Equalizer와 DSP EQ 값으로 복원한다.
    private fun applySavedEqualizerBands() {
        val bandLevelRange = AudioEffectManager.getBandLevelRange() ?: return
        val minBandLevel = bandLevelRange[0].toInt() // 실제 Equalizer가 받을 수 있는 최소 밴드 레벨
        val maxBandLevel = bandLevelRange[1].toInt() // 실제 Equalizer가 받을 수 있는 최대 밴드 레벨
        val maxBandProgress = maxBandLevel - minBandLevel // SeekBar가 가질 수 있는 최대 progress
        val numberOfBands = AudioEffectManager.getNumberOfBands()

        for (bandIndex in 0 until numberOfBands) {
            val savedProgress = prefs.getInt(bandIndex.toString(), 0)
            val clampedProgress = savedProgress.coerceIn(0, maxBandProgress)
            val nativeLevel = (clampedProgress + minBandLevel).toShort()
            val gainDb = mapEqProgressToDb(clampedProgress, maxBandProgress)

            AudioEffectManager.applyEqualizerBandLevel(
                bandIndex = bandIndex,
                level = nativeLevel,
            )
            AudioEffectManager.setBandGain(bandIndex, gainDb)
        }
    }

    private fun mapEqProgressToDb(progress: Int, max: Int): Float {
        if (max <= 0) return 0.0f
        val normalized = (progress.toFloat() / max.toFloat()).coerceIn(0.0f, 1.0f)

        return (normalized * 24.0f) - 12.0f
    }

    // EQ 밴드 직접 조작만 Custom 전환 조건으로 사용한다.
    override fun updateEqualizerBandFromUser(
        bandIndex: Int,
        progress: Int,
    ) {
        val bandLevelRange = AudioEffectManager.getBandLevelRange() ?: return
        val minBandLevel = bandLevelRange[0].toInt()
        val maxBandLevel = bandLevelRange[1].toInt()
        val maxBandProgress = maxBandLevel - minBandLevel

        val clampedProgress = progress.coerceIn(0, maxBandProgress)

        // SeekBar progress를 Android Equalizer가 요구하는 band level 값으로 변환한다.
        val nativeBandLevel = (clampedProgress + minBandLevel).toShort()
        val gainDb = mapEqProgressToDb(
            progress = clampedProgress,
            max = maxBandProgress,
        )

        prefs.edit {
            putBoolean(AudioSettingKeys.KEY_EQUALIZER_ENABLED, true)
            putInt(AudioSettingKeys.KEY_PRESET, AudioSettingsUiState.CUSTOM_PRESET_POSITION)
            putInt(bandIndex.toString(), clampedProgress)
        }

        AudioEffectManager.applyEqualizerBandLevel(
            bandIndex = bandIndex,
            level = nativeBandLevel,
        )
        AudioEffectManager.setBandGain(bandIndex, gainDb)
        AudioEffectManager.setEnabledFromPrefs(prefs)

        updateStateFromStorage()
    }

    // BassBoost 값은 EQ 프리셋 선택 상태를 유지한 채 별도로 저장한다.
    override fun updateBassStrength(progress: Int) {
        val clampedProgress = progress.coerceIn(MIN_EFFECT_VALUE, MAX_EFFECT_VALUE)

        prefs.edit {
            putInt(AudioSettingKeys.KEY_BASS, clampedProgress)
        }

        AudioEffectManager.applyBassStrength(clampedProgress)
        AudioEffectManager.setEnabledFromPrefs(prefs)

        updateStateFromStorage()
    }

    // Virtualizer 값은 EQ 프리셋 선택 상태를 유지한 채 별도로 저장한다.
    override fun updateVirtualizerStrength(progress: Int) {
        val clampedProgress = progress.coerceIn(MIN_EFFECT_VALUE, MAX_EFFECT_VALUE)

        prefs.edit {
            putInt(AudioSettingKeys.KEY_VIRTUALIZER, clampedProgress)
        }

        AudioEffectManager.applyVirtualizerStrength(clampedProgress)
        AudioEffectManager.setEnabledFromPrefs(prefs)

        updateStateFromStorage()
    }

    override fun setLoudnessNormalizerEnabled(enabled: Boolean) {
        prefs.edit {
            putBoolean(AudioSettingKeys.KEY_LOUDNESS_NORMALIZER_ENABLED, enabled)
        }

        AudioEffectManager.setEnabledFromPrefs(prefs)
        updateStateFromStorage()
    }

    private fun updateStateFromStorage() {
        _settingsState.value = loadAudioSettingsState()
    }

    // SharedPreferences에 저장된 값을 UI 상태 모델로 변환
    private fun loadAudioSettingsState(): AudioSettingsUiState {
        return AudioSettingsUiState(
            isEqualizerEnabled = prefs.getBoolean(AudioSettingKeys.KEY_EQUALIZER_ENABLED, false),
            currentPresetPosition = prefs.getInt(
                AudioSettingKeys.KEY_PRESET,
                AudioSettingsUiState.DEFAULT_PRESET_POSITION,
            ),
            bassStrength = prefs.getInt(
                AudioSettingKeys.KEY_BASS,
                AudioSettingsUiState.DEFAULT_EFFECT_VALUE,
            ),
            virtualizerStrength = prefs.getInt(
                AudioSettingKeys.KEY_VIRTUALIZER,
                AudioSettingsUiState.DEFAULT_EFFECT_VALUE,
            ),
            isLoudnessNormalizerEnabled = prefs.getBoolean(
                AudioSettingKeys.KEY_LOUDNESS_NORMALIZER_ENABLED,
                false,
            ),
        )
    }

    private companion object {
        const val SETTINGS_PRESET_OFFSET = 1
        const val MIN_EFFECT_VALUE = 0
        const val MAX_EFFECT_VALUE = 100
    }
}
