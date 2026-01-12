package com.hero.ziggymusic.view.main.setting

import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import androidx.core.graphics.toColorInt
import com.hero.ziggymusic.audio.AudioProcessorChainController
import com.hero.ziggymusic.view.main.setting.SettingFragment.Companion.KEY_HEAD_TRACKING_ENABLED
import com.hero.ziggymusic.view.main.setting.SettingFragment.Companion.KEY_SPATIAL_ENABLED

object SoundEQSettings {
    private val bandGainsDb = FloatArray(5)

    /**
     * 음향 효과 설정을 초기화함.
     *
     * @param mediaSession media session을 나타내는 정수값.
     */
    fun init(mediaSession: Int) {
        SettingFragment.equalizer = Equalizer(1, mediaSession)
        SettingFragment.bassBoost = BassBoost(1, mediaSession)
        SettingFragment.virtualizer = Virtualizer(1, mediaSession)
        SettingFragment.reverb = PresetReverb(1, mediaSession)
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex in bandGainsDb.indices) {
            bandGainsDb[bandIndex] = gainDb
            AudioProcessorChainController.setEQBand(bandIndex, gainDb)
        }
    }

    fun setCompressor(thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float, makeupDb: Float) {
        AudioProcessorChainController.setCompressor(thresholdDb, ratio, attackMs, releaseMs, makeupDb)
    }

    fun setReverb(enabled: Boolean, wet: Float) {
        AudioProcessorChainController.setReverb(enabled, wet)
    }

    /**
     * [XR Feature] 공간 음향 활성화
     */
    fun setSpatialEnabled(enabled: Boolean) {
        AudioProcessorChainController.setSpatialEnabled(enabled)
    }

    /**
     * [XR Feature] 헤드 트래킹 데이터 주입 활성화
     */
    fun setHeadTrackingEnabled(enabled: Boolean) {
        AudioProcessorChainController.setHeadTrackingEnabled(enabled)
    }

    /**
     * [XR Feature] Head tracking yaw(도 단위) 값을 네이티브 DSP 체인으로 주입
     */
    fun setHeadTrackingYaw(yawDeg: Float) {
        AudioProcessorChainController.setHeadTrackingYaw(yawDeg)
    }

    /**
     * [XR Feature] 가상 스피커 위치 등을 수동으로 설정할 때 사용
     */
    fun setSpatialPosition(azimuth: Float, elevation: Float, distance: Float) {
        AudioProcessorChainController.setSpatialPosition(azimuth, elevation, distance)
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
        val spatialEnabled = prefs.getBoolean(KEY_SPATIAL_ENABLED, false)
        val headTrackingEnabled = prefs.getBoolean(KEY_HEAD_TRACKING_ENABLED, false)

        setSpatialEnabled(spatialEnabled)
        setHeadTrackingEnabled(spatialEnabled && headTrackingEnabled)

        for (bandIndex in 0 until bandCount) {
            val progress = prefs.getInt(bandIndex.toString(), 0)
            val gainDb = mapEqProgressToDb(progress, eqMaxFromUi)
            setBandGain(bandIndex, gainDb)
        }
    }

    private fun mapEqProgressToDb(progress: Int, max: Int): Float {
        if (max <= 0) return 0.0f
        val normalized = (progress.toFloat() / max.toFloat()).coerceIn(0.0f, 1.0f)
        return (normalized * 24.0f) - 12.0f
    }

    /**
     * SoundEQ UI의 main color를 설정.
     *
     * @param color "#RRGGBB" or "#AARRGGBB" 형식의 color string.
     */
    fun setColor(color: String) {
        try {
            SettingFragment.mainColor = color.toColorInt()
        } catch (_: Exception) {
            // Handle parsing exceptions
        }
    }
}
