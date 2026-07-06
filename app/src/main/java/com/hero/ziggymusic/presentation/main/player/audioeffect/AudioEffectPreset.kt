package com.hero.ziggymusic.presentation.main.player.audioeffect

import androidx.annotation.StringRes
import com.hero.ziggymusic.R

/**
 * 프리셋 선택 시 적용할 음향 효과 값과 네이티브 EQ preset 후보명.
 *
 * 기기마다 Equalizer preset 이름이 다를 수 있어 후보명을 함께 관리한다.
 */
data class AudioEffectPresetConfig(
    val settingsPresetPosition: Int,
    val equalizerPresetNameCandidates: List<String> = emptyList(),
)

/**
 * 플레이어 바텀시트에서 빠르게 적용하는 음향 효과 프리셋.
 *
 * CUSTOM은 사용자가 직접 조절한 상태를 표시하기 위한 항목이며,
 * 선택 시 기존 효과 값을 덮어쓰지 않는다.
 */
enum class AudioEffectPreset(
    @param:StringRes val labelResId: Int,
    val config: AudioEffectPresetConfig,
) {
    CUSTOM(
        labelResId = R.string.preset_custom,
        config = AudioEffectPresetConfig(
            settingsPresetPosition = 0,
        )
    ),

    NORMAL(
        labelResId = R.string.preset_normal,
        config = AudioEffectPresetConfig(
            settingsPresetPosition = 1,
        )
    ),

    CLASSICAL(
        labelResId = R.string.preset_classical,
        config = AudioEffectPresetConfig(
            settingsPresetPosition = 2,
        )
    ),

    DANCE(
        labelResId = R.string.preset_dance,
        config = AudioEffectPresetConfig(
            settingsPresetPosition = 3,
        )
    ),

    FLAT(
        labelResId = R.string.preset_flat,
        config = AudioEffectPresetConfig(
            settingsPresetPosition = 4,
            equalizerPresetNameCandidates = listOf("Flat", "Normal"),
        )
    ),

    FOLK(
        labelResId = R.string.preset_folk,
        config = AudioEffectPresetConfig(
            settingsPresetPosition = 5,
            equalizerPresetNameCandidates = listOf("Folk"),
        )
    ),

    HEAVY_METAL(
        labelResId = R.string.preset_heavy_metal,
        config = AudioEffectPresetConfig(
            settingsPresetPosition = 6,
            equalizerPresetNameCandidates = listOf("Heavy Metal"),
        )
    ),

    HIP_HOP(
        labelResId = R.string.preset_hip_hop,
        config = AudioEffectPresetConfig(
            settingsPresetPosition = 7,
            equalizerPresetNameCandidates = listOf("Hip Hop"),
        )
    ),

    JAZZ(
        labelResId = R.string.preset_jazz,
        config = AudioEffectPresetConfig(
            settingsPresetPosition = 8,
            equalizerPresetNameCandidates = listOf("Jazz"),
        )
    ),

    POP(
        labelResId = R.string.preset_pop,
        config = AudioEffectPresetConfig(
            settingsPresetPosition = 9,
            equalizerPresetNameCandidates = listOf("Pop"),
        )
    ),

    ROCK(
        labelResId = R.string.preset_rock,
        config = AudioEffectPresetConfig(
            settingsPresetPosition = 10,
            equalizerPresetNameCandidates = listOf("Rock"),
        )
    );
}
