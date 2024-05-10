package com.hero.ziggymusic.view.main.setting

import android.graphics.Color
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer

object SoundEQSettings {
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

    /**
     * Sets the main color for SoundEQ UI.
     *
     * @param color A color string in the format "#RRGGBB" or "#AARRGGBB".
     */
    fun setColor(color: String) {
        try {
            SettingFragment.mainColor = Color.parseColor(color)
        } catch (_: Exception) {
            // Handle parsing exceptions
        }
    }
}
