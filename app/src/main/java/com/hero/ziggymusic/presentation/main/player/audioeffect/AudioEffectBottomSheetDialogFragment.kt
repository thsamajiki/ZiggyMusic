package com.hero.ziggymusic.presentation.main.player.audioeffect

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hero.ziggymusic.R
import com.hero.ziggymusic.data.local.preferences.AudioSettingKeys
import com.hero.ziggymusic.databinding.FragmentAudioEffectBottomSheetBinding
import com.hero.ziggymusic.playback.manager.AudioEffectManager
import com.hero.ziggymusic.presentation.main.setting.AudioSettingsFragment

class AudioEffectBottomSheetDialogFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentAudioEffectBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: SharedPreferences

    private val recentPresetButtonIds = listOf(
        R.id.btnRecentPresetFirst,
        R.id.btnRecentPresetSecond,
        R.id.btnRecentPresetThird,
        R.id.btnRecentPresetFourth,
    )

    private val recentPresets = listOf(
        AudioEffectPreset.NORMAL,
        AudioEffectPreset.POP,
        AudioEffectPreset.ROCK,
        AudioEffectPreset.CUSTOM,
    )

    override fun getTheme(): Int = R.style.PlayerBottomSheet

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme).apply {
            setOnShowListener {
                setupBottomSheetBehavior(this)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAudioEffectBottomSheetBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = requireContext().getSharedPreferences(
            AudioSettingsFragment.TAG,
            Context.MODE_PRIVATE,
        )

        setupAudioEffectInitialState()
        setupPresetControls()
        setupLoudnessNormalizer()
        setupAudioEffectSeekBars()
        setupSettingsNavigation()
    }

    private fun setupBottomSheetBehavior(dialog: BottomSheetDialog) {
        val bottomSheet = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet,
        ) ?: return

        val maxWidth = resources.getDimensionPixelSize(R.dimen.audio_effect_bottom_sheet_max_width)

        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            width = if (maxWidth > 0) {
                minOf(resources.displayMetrics.widthPixels, maxWidth)
            } else {
                ViewGroup.LayoutParams.MATCH_PARENT
            }
        }

        bottomSheet.background = Color.TRANSPARENT.toDrawable()

        BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun setupAudioEffectInitialState() {
        binding.swLoudnessNormalizer.isChecked = prefs.getBoolean(
            AudioSettingKeys.KEY_LOUDNESS_NORMALIZER_ENABLED,
            false,
        )

        binding.seekBarBass.progress = prefs.getInt(
            AudioSettingKeys.KEY_BASS,
            DEFAULT_EFFECT_VALUE
        )

        binding.seekBarVirtualizer.progress = prefs.getInt(
            AudioSettingKeys.KEY_VIRTUALIZER,
            DEFAULT_EFFECT_VALUE,
        )
    }

    private fun setupPresetControls() {
        binding.togglePresetGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val presetIndex = recentPresetButtonIds.indexOf(checkedId)
            val preset = recentPresets.getOrNull(presetIndex)
                ?: return@addOnButtonCheckedListener

            applyAudioEffectPreset(preset)
        }
    }

    private fun setupLoudnessNormalizer() {
        binding.swLoudnessNormalizer.setOnCheckedChangeListener { _, isChecked ->
            AudioEffectManager.applyLoudnessNormalizer(isChecked, prefs)
        }
    }

    private fun setupAudioEffectSeekBars() {
        binding.seekBarBass.setOnSeekBarChangeListener(
            createSeekBarChangeListener { progress ->
                applyBass(progress)
                saveCustomPresetSelection()
            },
        )

        binding.seekBarVirtualizer.setOnSeekBarChangeListener(
            createSeekBarChangeListener { progress ->
                applyVirtualizer(progress)
                saveCustomPresetSelection()
            },
        )
    }

    private fun setupSettingsNavigation() {
        binding.tvOpenAudioEffectSettings.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply {
                    putString(RESULT_ACTION_KEY, ACTION_OPEN_AUDIO_SETTINGS)
                }
            )
            dismiss()
        }
    }

    private fun applyAudioEffectPreset(preset: AudioEffectPreset) {
        if (preset == AudioEffectPreset.CUSTOM) {
            saveCustomPresetSelection()
            return
        }

        applyNativeEqualizerPresetIfAvailable(preset)
        AudioEffectManager.setEnabledFromPrefs(prefs)
    }

    private fun applyNativeEqualizerPresetIfAvailable(preset: AudioEffectPreset) {
        val nativePresetIndex = findNativeEqualizerPresetIndex(preset)

        if (nativePresetIndex == null) {
            saveCustomPresetSelection()
            return
        }

        AudioEffectManager.useEqualizerPreset(nativePresetIndex)
        prefs.edit {
            putInt(AudioSettingKeys.KEY_PRESET, nativePresetIndex + SETTINGS_PRESET_OFFSET)
        }
    }

    private fun findNativeEqualizerPresetIndex(preset: AudioEffectPreset): Int? {
        val numberOfPresets = AudioEffectManager.getNumberOfPresets()
        if (numberOfPresets <= 0) return null

        val candidates = preset.config.equalizerPresetNameCandidates

        return (0 until numberOfPresets).firstOrNull { index ->
            val presetName = AudioEffectManager.getPresetName(index).orEmpty()
            candidates.any { candidate ->
                presetName.equals(candidate, ignoreCase = true)
            }
        }
    }

    private fun applyBass(progress: Int) {
        val clampedProgress = progress.coerceIn(MIN_EFFECT_VALUE, MAX_EFFECT_VALUE) // 베이스 보정값

        if (binding.seekBarBass.progress != clampedProgress) {
            binding.seekBarBass.progress = clampedProgress
        }

        AudioEffectManager.applyBassStrength(clampedProgress, prefs)
    }

    private fun applyVirtualizer(progress: Int) {
        val clampedProgress = progress.coerceIn(MIN_EFFECT_VALUE, MAX_EFFECT_VALUE) // 3D 버추얼라이저 보정값

        if (binding.seekBarVirtualizer.progress != clampedProgress) {
            binding.seekBarVirtualizer.progress = clampedProgress
        }

        AudioEffectManager.applyVirtualizerStrength(clampedProgress, prefs)
    }

    private fun saveCustomPresetSelection() {
        prefs.edit {
            putInt(AudioSettingKeys.KEY_PRESET, CUSTOM_PRESET_POSITION)
        }
    }

    private fun createSeekBarChangeListener(
        onProgressChangedByUser: (progress: Int) -> Unit,
    ): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean,
            ) {
                if (fromUser) {
                    onProgressChangedByUser(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "AudioEffectBottomSheetDialogFragment"

        // Fragment Result로 PlayerFragment에 전달할 바텀시트 액션 정보.
        const val REQUEST_KEY = "audio_effect_bottom_sheet_request"
        const val RESULT_ACTION_KEY = "audio_effect_bottom_sheet_action"
        const val ACTION_OPEN_AUDIO_SETTINGS = "open_audio_settings"

        // SeekBar 기반 음향 효과 값은 0~100 범위로 통일한다.
        private const val MIN_EFFECT_VALUE = 0
        private const val MAX_EFFECT_VALUE = 100
        private const val DEFAULT_EFFECT_VALUE = 0

        // AudioSettingsFragment의 프리셋 목록은 0번을 Custom으로 사용한다.
        private const val CUSTOM_PRESET_POSITION = 0
        private const val SETTINGS_PRESET_OFFSET = 1

        fun newInstance(): AudioEffectBottomSheetDialogFragment =
            AudioEffectBottomSheetDialogFragment()

    }
}
