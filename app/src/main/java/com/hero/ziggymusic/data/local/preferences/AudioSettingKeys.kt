package com.hero.ziggymusic.data.local.preferences

object AudioSettingKeys {
    const val KEY_EQUALIZER_ENABLED = "EQUALIZER_ENABLED"
    const val KEY_PRESET = "PRESET"
    const val KEY_RECENT_PRESET_POSITIONS = "RECENT_PRESET_POSITIONS" // 바텀시트 최근 프리셋 버튼에 노출할 일반 EQ 프리셋 position 목록

    // Custom EQ 저장값과 현재 기기의 밴드 구성이 호환되는지 확인하기 위한 정보
    const val KEY_CUSTOM_EQ_BAND_COUNT = "CUSTOM_EQ_BAND_COUNT"
    const val KEY_CUSTOM_EQ_MIN_LEVEL = "CUSTOM_EQ_MIN_LEVEL"
    const val KEY_CUSTOM_EQ_MAX_LEVEL = "CUSTOM_EQ_MAX_LEVEL"

    const val KEY_BASS = "BASS"
    const val KEY_VIRTUALIZER = "VIRTUALIZER"
    const val KEY_REVERB = "REVERB"
    const val KEY_LOUDNESS_NORMALIZER_ENABLED = "LOUDNESS_NORMALIZER_ENABLED"
    const val KEY_SPATIAL_ENABLED = "SPATIAL_ENABLED"
    const val KEY_HEAD_TRACKING_ENABLED = "HEAD_TRACKING_ENABLED"

    const val PREF_AUDIO_SETTINGS = "audio_settings"
}
