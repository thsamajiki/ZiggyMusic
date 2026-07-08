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

        if (savedAudioSettings.currentPresetPosition > AudioSettingsUiState.CUSTOM_PRESET_POSITION) {
            AudioEffectManager.useEqualizerPreset(
                savedAudioSettings.currentPresetPosition - SETTINGS_PRESET_OFFSET
            )
        } else {
            applySavedEqualizerBands()
        }

        // 저장된 별도 음향 효과 값도 프리셋과 함께 복원한다.
        AudioEffectManager.applyBassStrength(savedAudioSettings.bassStrength)
        AudioEffectManager.applyVirtualizerStrength(savedAudioSettings.virtualizerStrength)
        AudioEffectManager.applyReverbPreset(savedAudioSettings.reverbPresetPosition)
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

        // 어떤 화면에서 프리셋을 선택하든 최근 프리셋 목록을 같은 기준으로 갱신한다.
        val recentPresetPositions = buildRecentPresetPositions(normalizedPresetPosition)

        prefs.edit {
            putBoolean(AudioSettingKeys.KEY_EQUALIZER_ENABLED, true)
            putInt(AudioSettingKeys.KEY_PRESET, normalizedPresetPosition)

            // Custom은 고정 슬롯으로 처리하므로 일반 EQ 프리셋을 선택했을 때만 최근 목록을 저장한다.
            if (normalizedPresetPosition > AudioSettingsUiState.CUSTOM_PRESET_POSITION) {
                putString(
                    AudioSettingKeys.KEY_RECENT_PRESET_POSITIONS,
                    serializeRecentPresetPositions(recentPresetPositions),
                )
            }
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

    // Reverb 값은 EQ 프리셋 선택 상태를 유지한 채 별도로 저장한다.
    override fun setReverbPreset(position: Int) {
        val normalizedPosition = position.coerceIn(MIN_REVERB_PRESET_POSITION, MAX_REVERB_PRESET_POSITION)

        prefs.edit {
            putInt(AudioSettingKeys.KEY_REVERB, normalizedPosition)
        }

        AudioEffectManager.applyReverbPreset(normalizedPosition)
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
            recentPresetPositions = loadRecentPresetPositions(),
            bassStrength = prefs.getInt(
                AudioSettingKeys.KEY_BASS,
                AudioSettingsUiState.DEFAULT_EFFECT_VALUE,
            ),
            virtualizerStrength = prefs.getInt(
                AudioSettingKeys.KEY_VIRTUALIZER,
                AudioSettingsUiState.DEFAULT_EFFECT_VALUE,
            ),
            reverbPresetPosition = prefs.getInt(
                AudioSettingKeys.KEY_REVERB,
                AudioSettingsUiState.DEFAULT_REVERB_PRESET_POSITION,
            ),
            isLoudnessNormalizerEnabled = prefs.getBoolean(
                AudioSettingKeys.KEY_LOUDNESS_NORMALIZER_ENABLED,
                false,
            ),
        )
    }

    // 선택한 일반 EQ 프리셋을 맨 앞에 두고, 기존 최근 목록을 뒤로 밀어 최신순 목록을 만든다.
    private fun buildRecentPresetPositions(selectedPresetPosition: Int): List<Int> {
        if (selectedPresetPosition <= AudioSettingsUiState.CUSTOM_PRESET_POSITION) {
            return loadRecentPresetPositions()
        }

        val recentPresetPositions = mutableListOf(selectedPresetPosition)

        loadRecentPresetPositions()
            .filterNot { it == selectedPresetPosition }
            .forEach { presetPosition ->
                if (presetPosition !in recentPresetPositions) {
                    recentPresetPositions += presetPosition
                }
            }

        AudioSettingsUiState.DEFAULT_RECENT_PRESET_POSITIONS
            .filterNot { it == selectedPresetPosition }
            .forEach { presetPosition ->
                if (presetPosition !in recentPresetPositions) {
                    recentPresetPositions += presetPosition
                }
            }

        return recentPresetPositions.take(RECENT_NON_CUSTOM_PRESET_COUNT)
    }

    // 저장된 최근 프리셋 목록을 읽고, 값이 없거나 부족하면 기본 프리셋으로 보충한다.
    private fun loadRecentPresetPositions(): List<Int> {
        val savedPresetPositions = prefs.getString(
            AudioSettingKeys.KEY_RECENT_PRESET_POSITIONS,
            null,
        )
            ?.split(RECENT_PRESET_POSITION_SEPARATOR)
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()

        return (savedPresetPositions + AudioSettingsUiState.DEFAULT_RECENT_PRESET_POSITIONS)
            .filter { it > AudioSettingsUiState.CUSTOM_PRESET_POSITION }
            .distinct()
            .take(RECENT_NON_CUSTOM_PRESET_COUNT)
    }

    // SharedPreferences에 저장할 수 있도록 최근 프리셋 position 목록을 문자열로 변환한다.
    private fun serializeRecentPresetPositions(presetPositions: List<Int>): String {
        return presetPositions.joinToString(RECENT_PRESET_POSITION_SEPARATOR)
    }

    private companion object {
        const val SETTINGS_PRESET_OFFSET = 1

        // Custom을 제외하고 저장/표시할 일반 EQ 최근 프리셋 개수
        const val RECENT_NON_CUSTOM_PRESET_COUNT = 3

        // SharedPreferences에 최근 프리셋 position 목록을 저장할 때 사용하는 구분자
        const val RECENT_PRESET_POSITION_SEPARATOR = ","

        // SeekBar 기반 음향 효과 값은 0~100 범위로 통일한다.
        const val MIN_EFFECT_VALUE = 0
        const val MAX_EFFECT_VALUE = 100

        const val MIN_REVERB_PRESET_POSITION = 0
        const val MAX_REVERB_PRESET_POSITION = 6
    }
}
