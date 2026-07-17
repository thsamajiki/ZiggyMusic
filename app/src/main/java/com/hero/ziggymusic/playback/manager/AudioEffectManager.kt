package com.hero.ziggymusic.playback.manager

import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.hero.ziggymusic.data.local.preferences.AudioSettingKeys

object AudioEffectManager {
    var equalizer: Equalizer? = null
        private set
    var bassBoost: BassBoost? = null
        private set
    var virtualizer: Virtualizer? = null
        private set
    var reverb: PresetReverb? = null
        private set

    var mainColor: Int = "#00ffee".toColorInt()
        private set

    /**
     * 음향 효과 설정을 초기화함.
     *
     * @param mediaSession media session을 나타내는 정수값.
     */
    fun init(mediaSession: Int) {
        release()

        runCatching { Equalizer(1, mediaSession) }.onSuccess { equalizer = it }
        runCatching { BassBoost(1, mediaSession) }.onSuccess { bassBoost = it }
        runCatching { Virtualizer(1, mediaSession) }.onSuccess { virtualizer = it }
        runCatching { PresetReverb(1, mediaSession) }.onSuccess { reverb = it }
    }

    fun setEnabledFromPrefs(prefs: SharedPreferences) {
        val enabled = prefs.getBoolean(AudioSettingKeys.KEY_EQUALIZER_ENABLED, false)
        val reverbPreset = prefs.getInt(AudioSettingKeys.KEY_REVERB, 0)
        val bassProgress = prefs.getInt(AudioSettingKeys.KEY_BASS, 0)
        val virtualizerProgress = prefs.getInt(AudioSettingKeys.KEY_VIRTUALIZER, 0)
        val loudnessNormalizerEnabled = prefs.getBoolean(AudioSettingKeys.KEY_LOUDNESS_NORMALIZER_ENABLED, false)

        equalizer?.enabled = enabled
        reverb?.enabled = enabled && reverbPreset != 0
        bassBoost?.enabled = enabled && bassProgress > 0
        virtualizer?.enabled = enabled && virtualizerProgress > 0
    }

    fun applyBassStrength(progress: Int, prefs: SharedPreferences? = null) {
        prefs?.edit { putInt(AudioSettingKeys.KEY_BASS, progress) }

        bassBoost?.let { effect ->
            if (effect.strengthSupported) {
                effect.setStrength((progress * 15).toShort())
            }
        }

        prefs?.let { setEnabledFromPrefs(it) }
    }

    fun applyVirtualizerStrength(progress: Int, prefs: SharedPreferences? = null) {
        prefs?.edit { putInt(AudioSettingKeys.KEY_VIRTUALIZER, progress) }

        virtualizer?.let { effect ->
            if (effect.strengthSupported) {
                effect.setStrength((progress * 15).toShort())
            }
        }

        prefs?.let { setEnabledFromPrefs(it) }
    }

    fun applyReverbPreset(position: Int, prefs: SharedPreferences? = null) {
        prefs?.edit { putInt(AudioSettingKeys.KEY_REVERB, position) }
        reverb?.preset = position.toShort()
        prefs?.let { setEnabledFromPrefs(it) }
    }

    fun applyEqualizerBandLevel(
        bandIndex: Int,
        level: Short,
    ) {
        equalizer?.setBandLevel(bandIndex.toShort(), level)
    }

    // 프리셋 인덱스를 검증한 뒤 적용하고 성공 여부를 반환한다.
    fun useEqualizerPreset(presetIndex: Int): Boolean {
        val effect = equalizer ?: return false

        val numberOfPresets = runCatching {
            effect.numberOfPresets.toInt()
        }.getOrDefault(0)

        if (presetIndex !in 0 until numberOfPresets) {
            return false
        }

        return runCatching {
            effect.usePreset(presetIndex.toShort())
        }.isSuccess
    }

    fun getBandLevel(bandIndex: Int): Short? {
        return equalizer?.getBandLevel(bandIndex.toShort())
    }

    fun getCenterFreq(bandIndex: Int): Int? {
        return equalizer?.getCenterFreq(bandIndex.toShort())
    }

    fun getBandLevelRange(): ShortArray? {
        return equalizer?.bandLevelRange
    }

    fun getNumberOfBands(): Int {
        return equalizer?.numberOfBands?.toInt() ?: 0
    }

    fun getNumberOfPresets(): Int {
        return equalizer?.numberOfPresets?.toInt() ?: 0
    }

    fun getPresetName(index: Int): String? {
        return equalizer?.getPresetName(index.toShort())
    }

    // 저장된 음향 효과 값을 현재 AudioEffect 상태에 다시 반영한다.
    fun applySettingsFromPrefs(prefs: SharedPreferences) {
        applyBassStrength(prefs.getInt(AudioSettingKeys.KEY_BASS, 0), null)
        applyVirtualizerStrength(prefs.getInt(AudioSettingKeys.KEY_VIRTUALIZER, 0), null)
        applyReverbPreset(prefs.getInt("REVERB", 0), null)
        setEnabledFromPrefs(prefs)
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { reverb?.release() }

        equalizer = null
        bassBoost = null
        virtualizer = null
        reverb = null
    }
}
