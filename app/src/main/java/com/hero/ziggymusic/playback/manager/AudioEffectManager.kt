package com.hero.ziggymusic.playback.manager

import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.hero.ziggymusic.playback.audio.AudioProcessorChainController
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

    private val bandGainsDb = FloatArray(5)

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
        setLoudnessNormalizerEnabled(enabled && loudnessNormalizerEnabled)
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

    fun applyLoudnessNormalizer(enabled: Boolean, prefs: SharedPreferences) {
        prefs.edit { putBoolean(AudioSettingKeys.KEY_LOUDNESS_NORMALIZER_ENABLED, enabled) }
        setEnabledFromPrefs(prefs)
    }

    fun applyEqualizerBandLevel(
        bandIndex: Int,
        level: Short,
    ) {
        equalizer?.setBandLevel(bandIndex.toShort(), level)
    }

    fun useEqualizerPreset(presetIndex: Int) {
        equalizer?.usePreset(presetIndex.toShort())
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

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex in bandGainsDb.indices) {
            bandGainsDb[bandIndex] = gainDb
        }
    }

    fun setCompressor(thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float, makeupDb: Float) {
        AudioProcessorChainController.setCompressor(thresholdDb, ratio, attackMs, releaseMs, makeupDb)
    }

    private fun setLoudnessNormalizerEnabled(enabled: Boolean) {
        if (enabled) {
            setCompressor(
                thresholdDb = LOUDNESS_NORMALIZER_THRESHOLD_DB,
                ratio = LOUDNESS_NORMALIZER_RATIO,
                attackMs = LOUDNESS_NORMALIZER_ATTACK_MS,
                releaseMs = LOUDNESS_NORMALIZER_RELEASE_MS,
                makeupDb = LOUDNESS_NORMALIZER_MAKEUP_DB
            )
        } else {
            setCompressor(
                thresholdDb = COMPRESSOR_BYPASS_THRESHOLD_DB,
                ratio = COMPRESSOR_BYPASS_RATIO,
                attackMs = COMPRESSOR_BYPASS_ATTACK_MS,
                releaseMs = COMPRESSOR_BYPASS_RELEASE_MS,
                makeupDb = COMPRESSOR_BYPASS_MAKEUP_DB
            )
        }
    }

    /**
     * - Fragment는 체인 생성/파괴를 하지 않고, 현재 저장된 설정값을 네이티브에 반영.
     * - EQ band 값은 prefs에 저장된 progress를 dB로 변환해서 반영.
     */
    fun applySettingsFromPrefs(
        prefs: SharedPreferences,
        bandCount: Int = bandGainsDb.size,
        eqMaxFromUi: Int,
    ) {
        for (bandIndex in 0 until bandCount) {
            val progress = prefs.getInt(bandIndex.toString(), 0)
            val gainDb = mapEqProgressToDb(progress, eqMaxFromUi)
            setBandGain(bandIndex, gainDb)
        }

        applyBassStrength(prefs.getInt(AudioSettingKeys.KEY_BASS, 0), null)
        applyVirtualizerStrength(prefs.getInt(AudioSettingKeys.KEY_VIRTUALIZER, 0), null)
        applyReverbPreset(prefs.getInt("REVERB", 0), null)
        setEnabledFromPrefs(prefs)
    }

    private fun mapEqProgressToDb(progress: Int, max: Int): Float {
        if (max <= 0) return 0.0f
        val normalized = (progress.toFloat() / max.toFloat()).coerceIn(0.0f, 1.0f)
        return (normalized * 24.0f) - 12.0f
    }

    private const val LOUDNESS_NORMALIZER_THRESHOLD_DB = -18.0f
    private const val LOUDNESS_NORMALIZER_RATIO = 3.0f
    private const val LOUDNESS_NORMALIZER_ATTACK_MS = 8.0f
    private const val LOUDNESS_NORMALIZER_RELEASE_MS = 180.0f
    private const val LOUDNESS_NORMALIZER_MAKEUP_DB = 4.0f

    private const val COMPRESSOR_BYPASS_THRESHOLD_DB = 0.0f
    private const val COMPRESSOR_BYPASS_RATIO = 1.0f
    private const val COMPRESSOR_BYPASS_ATTACK_MS = 10.0f
    private const val COMPRESSOR_BYPASS_RELEASE_MS = 100.0f
    private const val COMPRESSOR_BYPASS_MAKEUP_DB = 0.0f

    fun release() {
        runCatching { setLoudnessNormalizerEnabled(false) }
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
