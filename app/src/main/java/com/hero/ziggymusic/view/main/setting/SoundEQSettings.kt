package com.hero.ziggymusic.view.main.setting

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import androidx.core.graphics.toColorInt
import com.hero.ziggymusic.audio.AudioProcessorChainController

object SoundEQSettings {
    private val bandGainsDb = FloatArray(5)

    /**
     * Initializes the Audio Effects settings.
     *
     * @param mediaSession An integer representing the media session.
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
     * Sets the main color for SoundEQ UI.
     *
     * @param color A color string in the format "#RRGGBB" or "#AARRGGBB".
     */
    fun setColor(color: String) {
        try {
            SettingFragment.mainColor = color.toColorInt()
        } catch (_: Exception) {
            // Handle parsing exceptions
        }
    }
}
