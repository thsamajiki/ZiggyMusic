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
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.hero.ziggymusic.R
import com.hero.ziggymusic.data.local.preferences.AudioSettingKeys
import com.hero.ziggymusic.databinding.FragmentAudioEffectBottomSheetBinding
import com.hero.ziggymusic.playback.manager.AudioEffectManager
import com.hero.ziggymusic.presentation.main.setting.model.AudioSettingsUiState
import com.hero.ziggymusic.presentation.main.setting.viewmodel.AudioSettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AudioEffectBottomSheetDialogFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentAudioEffectBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val vm by activityViewModels<AudioSettingsViewModel>()
    private var isApplyingAudioSettingsState = false // StateFlow 값을 UI에 반영하는 동안 리스너가 사용자 입력으로 오인하지 않도록 막는다.

    private lateinit var prefs: SharedPreferences

    private val recentPresetButtonIds = listOf(
        R.id.btnRecentPresetFirst,
        R.id.btnRecentPresetSecond,
        R.id.btnRecentPresetThird,
        R.id.btnRecentPresetFourth,
    )

    private val defaultRecentPresets = listOf(
        AudioEffectPreset.NORMAL,
        AudioEffectPreset.POP,
        AudioEffectPreset.ROCK,
        AudioEffectPreset.CUSTOM,
    )

    private var recentPresets = defaultRecentPresets

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
            AudioSettingKeys.PREF_AUDIO_SETTINGS,
            Context.MODE_PRIVATE,
        )

        setupPresetControls()
        setupLoudnessNormalizer()
        setupAudioEffectSeekBars()
        setupSettingsNavigation()
        setupAudioSettingsState()
    }

    private fun setupBottomSheetBehavior(dialog: BottomSheetDialog) {
        val bottomSheet = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet,
        ) ?: return

        bottomSheet.background = Color.TRANSPARENT.toDrawable()

        BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    // 바텀시트에서 프리셋을 누르면 EQ를 자동으로 켜고 프리셋을 적용한다.
    private fun setupPresetControls() {
        binding.togglePresetGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isApplyingAudioSettingsState) return@addOnButtonCheckedListener

            val presetIndex = recentPresetButtonIds.indexOf(checkedId)
            val preset = recentPresets.getOrNull(presetIndex)
                ?: return@addOnButtonCheckedListener

            if (preset == AudioEffectPreset.CUSTOM) {
                vm.setCustomEqualizerPreset()
            } else {
                vm.setEqualizerPreset(preset)
            }
        }
    }

    private fun setupLoudnessNormalizer() {
        binding.swLoudnessNormalizer.setOnCheckedChangeListener { _, isChecked ->
            if (isApplyingAudioSettingsState) return@setOnCheckedChangeListener
            vm.setLoudnessNormalizerEnabled(isChecked)
        }
    }

    // Bass/Virtualizer 조작은 프리셋 전환 없이 효과 값만 동기화한다.
    private fun setupAudioEffectSeekBars() {
        binding.seekBarBass.setOnSeekBarChangeListener(
            createSeekBarChangeListener { progress ->
                vm.updateBassStrength(progress)
            },
        )

        binding.seekBarVirtualizer.setOnSeekBarChangeListener(
            createSeekBarChangeListener { progress ->
                vm.updateVirtualizerStrength(progress)
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

    private fun setupAudioSettingsState() {
        observeAudioSettingsState()
        vm.refreshSettings()
    }

    // 바텀시트가 보이는 동안 ViewModel의 음향 설정 상태를 관찰한다.
    private fun observeAudioSettingsState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { state ->
                    renderAudioSettingsState(state)
                }
            }
        }
    }

    // 공유 상태를 바텀시트의 스위치, 프리셋, 슬라이더에 반영한다.
    private fun renderAudioSettingsState(state: AudioSettingsUiState) {
        isApplyingAudioSettingsState = true

        if (binding.swLoudnessNormalizer.isChecked != state.isLoudnessNormalizerEnabled) {
            binding.swLoudnessNormalizer.isChecked = state.isLoudnessNormalizerEnabled
        }

        if (binding.seekBarBass.progress != state.bassStrength) {
            binding.seekBarBass.progress = state.bassStrength
        }

        if (binding.seekBarVirtualizer.progress != state.virtualizerStrength) {
            binding.seekBarVirtualizer.progress = state.virtualizerStrength
        }

        // 저장소에 저장된 최근 프리셋 순서를 버튼 라벨에 반영한다.
        updateRecentPresetButtons(state.recentPresetPositions)

        val checkedButtonId = findPresetButtonId(state.currentPresetPosition)
        if (checkedButtonId != null && binding.togglePresetGroup.checkedButtonId != checkedButtonId) {
            binding.togglePresetGroup.check(checkedButtonId)
        }

        isApplyingAudioSettingsState = false
    }

    // 현재 기기가 지원하는 프리셋만 버튼에 표시한다.
    private fun updateRecentPresetButtons(recentPresetPositions: List<Int>) {
        recentPresets =
            buildRecentPresetButtonModels(recentPresetPositions)

        recentPresetButtonIds.forEachIndexed { index, buttonId ->
            val button =
                binding.togglePresetGroup.findViewById<MaterialButton>(
                    buttonId,
                )

            val preset = recentPresets.getOrNull(index)

            button.isVisible = preset != null
            button.isEnabled = preset != null

            if (preset != null) {
                button.text = getString(preset.labelResId)
            }
        }
    }

    // 지원되는 최근 프리셋을 우선 배치하고 부족한 항목은 지원 프리셋으로 채운다.
    private fun buildRecentPresetButtonModels(
        recentPresetPositions: List<Int>,
    ): List<AudioEffectPreset> {
        val maxSupportedPresetPosition =
            AudioEffectManager.getNumberOfPresets()
                .coerceAtLeast(0)

        val supportedPresets = AudioEffectPreset.entries
            .filter { preset ->
                preset == AudioEffectPreset.CUSTOM ||
                        preset.config.settingsPresetPosition in
                        1..maxSupportedPresetPosition
            }

        val recentNonCustomPresets = recentPresetPositions
            .mapNotNull(::findPresetBySettingsPosition)
            .filter { preset ->
                preset != AudioEffectPreset.CUSTOM &&
                        preset in supportedPresets
            }
            .distinct()

        val fallbackPresets = supportedPresets
            .filterNot { preset ->
                preset == AudioEffectPreset.CUSTOM ||
                        preset in recentNonCustomPresets
            }

        return (
                recentNonCustomPresets + fallbackPresets
                )
            .take(RECENT_NON_CUSTOM_PRESET_COUNT) +
                AudioEffectPreset.CUSTOM
    }

    // 설정 화면의 spinner position 값에 대응하는 바텀시트 프리셋 모델을 찾는다.
    private fun findPresetBySettingsPosition(presetPosition: Int): AudioEffectPreset? {
        return AudioEffectPreset.entries.firstOrNull {
            it.config.settingsPresetPosition == presetPosition
        }
    }

    // 현재 프리셋 position에 대응하는 바텀시트 버튼 id를 찾는다.
    private fun findPresetButtonId(presetPosition: Int): Int? {
        val presetIndex = recentPresets.indexOfFirst {
            it.config.settingsPresetPosition == presetPosition
        }

        return recentPresetButtonIds.getOrNull(presetIndex)
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
                if (fromUser && !isApplyingAudioSettingsState) {
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

        // Fragment Result로 PlayerFragment에 전달할 바텀시트 액션 정보
        const val REQUEST_KEY = "audio_effect_bottom_sheet_request"
        const val RESULT_ACTION_KEY = "audio_effect_bottom_sheet_action"
        const val ACTION_OPEN_AUDIO_SETTINGS = "open_audio_settings"

        // 바텀시트 최근 프리셋 버튼 중 Custom을 제외한 일반 EQ 프리셋 슬롯 개수
        private const val RECENT_NON_CUSTOM_PRESET_COUNT = 3

        fun newInstance(): AudioEffectBottomSheetDialogFragment =
            AudioEffectBottomSheetDialogFragment()

    }
}
