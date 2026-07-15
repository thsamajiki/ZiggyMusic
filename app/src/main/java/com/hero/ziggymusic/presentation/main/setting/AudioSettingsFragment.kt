package com.hero.ziggymusic.presentation.main.setting

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.hero.ziggymusic.R
import com.hero.ziggymusic.databinding.FragmentAudioSettingsBinding
import androidx.core.content.edit
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hero.ziggymusic.data.local.preferences.AudioSettingKeys
import com.hero.ziggymusic.playback.manager.AudioEffectManager
import com.hero.ziggymusic.playback.manager.AudioEffectManager.mainColor
import com.hero.ziggymusic.presentation.main.setting.model.AudioSettingsUiState
import com.hero.ziggymusic.presentation.main.setting.viewmodel.AudioSettingsViewModel
import com.hero.ziggymusic.presentation.main.setting.widget.EqualizerBandSeekBar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class AudioSettingsFragment : Fragment() {
    private var _binding: FragmentAudioSettingsBinding? = null
    private val binding get() = _binding!!

    private val vm by activityViewModels<AudioSettingsViewModel>()
    private var isApplyingAudioSettingsState = false

    // Spinner adapter 연결 직후 발생하는 초기 선택 콜백을 사용자 선택과 구분하기 위한 플래그
    private var isPresetSpinnerReady = false
    private var isReverbSpinnerReady = false

    private val equalizerBandSeekBarIds = mutableListOf<Int>()

    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAudioSettingsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initSetting()

        initEqualizer()
        initReverb()
        initLoudnessNormalizer()
        initBassSeekBar()
        initVirtualizerSeekbar()
        setupAudioSettingsState()
    }

    private fun setupAudioSettingsState() {
        observeAudioSettingsState()
        vm.refreshSettings()
    }

    private fun initSetting() {
        prefs = requireContext().getSharedPreferences(AudioSettingKeys.PREF_AUDIO_SETTINGS, 0)
        AudioEffectManager.setEnabledFromPrefs(prefs)

        // 사용자가 직접 EQ 스위치를 바꾼 경우에만 상태를 갱신한다.
        binding.swEqualizer.setOnCheckedChangeListener { _, isChecked ->
            if (isApplyingAudioSettingsState) return@setOnCheckedChangeListener
            vm.setEqualizerEnabled(isChecked)
        }
    }

    private fun observeAudioSettingsState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { state ->
                    renderAudioSettingsState(state)
                }
            }
        }
    }

    private fun renderAudioSettingsState(state: AudioSettingsUiState) {
        isApplyingAudioSettingsState = true

        if (binding.swEqualizer.isChecked != state.isEqualizerEnabled) {
            binding.swEqualizer.isChecked = state.isEqualizerEnabled
        }

        updateEqualizerUiState(state.isEqualizerEnabled)

        if (binding.spinnerPreset.adapter != null &&
            binding.spinnerPreset.selectedItemPosition != state.currentPresetPosition
        ) {
            binding.spinnerPreset.setSelection(state.currentPresetPosition)
        }

        updateEqualizerBandSeekBars(state.equalizerBandProgresses)

        // Reverb 선택 위치도 공유 상태를 기준으로 맞춘다.
        if (binding.spinnerReverb.adapter != null &&
            binding.spinnerReverb.selectedItemPosition != state.reverbPresetPosition
        ) {
            binding.spinnerReverb.setSelection(state.reverbPresetPosition)
        }

        if (binding.swLoudnessNormalizer.isChecked != state.isLoudnessNormalizerEnabled) {
            binding.swLoudnessNormalizer.isChecked = state.isLoudnessNormalizerEnabled
        }

        if (binding.sbBass.progress != state.bassStrength) {
            binding.sbBass.progress = state.bassStrength
        }

        if (binding.sbVirtualizer.progress != state.virtualizerStrength) {
            binding.sbVirtualizer.progress = state.virtualizerStrength
        }

        isApplyingAudioSettingsState = false
    }

    private fun initPresets() {
        val noOfPresets = AudioEffectManager.getNumberOfPresets()
        if (noOfPresets <= 0) return

        isPresetSpinnerReady = false

        val presets = arrayOfNulls<String>(noOfPresets + 1)
        presets[0] = "Custom"

        for (i in 0 until noOfPresets) {
            presets[i + 1] = AudioEffectManager.getPresetName(i)
        }

        val spinnerAdapter: ArrayAdapter<String?> = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner,
            presets
        )

        binding.spinnerPreset.adapter = spinnerAdapter

        // Adapter 연결 직후 발생할 수 있는 초기 onItemSelected 콜백은 무시한다.
        binding.spinnerPreset.post {
            isPresetSpinnerReady = true
        }

        // 프리셋 선택은 EQ 프리셋 상태만 바꾸고 Bass/Virtualizer 값은 유지한다.
        binding.spinnerPreset.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long,
                ) {
                    // 초기화 중 발생한 선택 콜백은 사용자 입력이 아니므로 처리하지 않는다.
                    if (!isPresetSpinnerReady || isApplyingAudioSettingsState) return

                    if (position == AudioSettingsUiState.CUSTOM_PRESET_POSITION) {
                        vm.setCustomEqualizerPreset()
                    } else {
                        vm.setEqualizerPreset(position)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    private fun initEqualizer() {
        val bandLevelRange = AudioEffectManager.getBandLevelRange() ?: return
        val minBandLevel = bandLevelRange[0].toInt()
        val maxBandLevel = bandLevelRange[1].toInt()
        var uiMaxForNative = 0

        equalizerBandSeekBarIds.clear()
        binding.tvSeekbar.removeAllViews()
        binding.seekbarContainer.removeAllViews()

        val numberOfBands = AudioEffectManager.getNumberOfBands()

        for (index in 0 until numberOfBands) {
            val equalizerBandSeekBar = EqualizerBandSeekBar(requireContext())

            equalizerBandSeekBarIds.add(index, View.generateViewId())

            equalizerBandSeekBar.max = maxBandLevel - minBandLevel
            equalizerBandSeekBar.tag = index
            uiMaxForNative = equalizerBandSeekBar.max

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                equalizerBandSeekBar.maxHeight = 1
            }

            equalizerBandSeekBar.id = equalizerBandSeekBarIds[index]
            equalizerBandSeekBar.splitTrack = true

            equalizerBandSeekBar.progressDrawable.setTint(mainColor)
            equalizerBandSeekBar.thumb.setTint(mainColor)

            equalizerBandSeekBar.progress = prefs.getInt(
                index.toString(),
                (AudioEffectManager.getBandLevel(index)?.toInt() ?: 0) - minBandLevel
            )

            equalizerBandSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    if (!isEqualizerBandChangedByUser(seekBar, fromUser) || isApplyingAudioSettingsState) return

                    // EQ 밴드 직접 조작은 Custom 프리셋 전환 조건이다.
                    vm.updateEqualizerBandFromUser(
                        bandIndex = seekBar.tag as Int,
                        progress = progress
                    )
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })

            val equalizerBandSeekBarLayoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
            equalizerBandSeekBar.layoutParams = equalizerBandSeekBarLayoutParams

            val centerFreq = AudioEffectManager.getCenterFreq(index) ?: 0
            val frequencyLabel = formatCenterFreq(centerFreq)

            val frequencyLabelView = TextView(requireContext())
            frequencyLabelView.text = frequencyLabel
            frequencyLabelView.includeFontPadding = false
            frequencyLabelView.maxLines = 1
            frequencyLabelView.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.setting_eq_label_text_size)
            )
            frequencyLabelView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))

            val frequencyLabelLayoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            frequencyLabelView.gravity = Gravity.CENTER
            frequencyLabelView.layoutParams = frequencyLabelLayoutParams

            binding.tvSeekbar.addView(frequencyLabelView)
            binding.seekbarContainer.addView(equalizerBandSeekBar)
            equalizerBandSeekBar.post { equalizerBandSeekBar.refreshThumbState() }

            val initialGainDb = mapEqProgressToDb(
                progress = equalizerBandSeekBar.progress,
                max = equalizerBandSeekBar.max
            )

            AudioEffectManager.setBandGain(index, initialGainDb)
        }

        runCatching {
            AudioEffectManager.applySettingsFromPrefs(prefs, eqMaxFromUi = uiMaxForNative)
        }

        initPresets()
        updateEqualizerUiState(binding.swEqualizer.isChecked)
    }

    private fun updateEqualizerBandSeekBars(equalizerBandProgresses: List<Int>) {
        if (equalizerBandProgresses.isEmpty()) return

        for (index in 0 until binding.seekbarContainer.childCount) {
            val equalizerBandSeekBar = binding.seekbarContainer.getChildAt(index) as? EqualizerBandSeekBar
                ?: continue

            val progress = equalizerBandProgresses.getOrNull(index)
                ?.coerceIn(0, equalizerBandSeekBar.max)
                ?: continue

            if (equalizerBandSeekBar.progress != progress) {
                equalizerBandSeekBar.progress = progress
                equalizerBandSeekBar.refreshThumbState()
            }
        }
    }

    // SoundEQVerticalSeekbar는 터치를 직접 처리하므로 fromUser 대신 tracking 상태도 함께 확인한다.
    private fun isEqualizerBandChangedByUser(
        seekBar: SeekBar,
        fromUser: Boolean,
    ): Boolean {
        val isVerticalSeekBarTracking =
            (seekBar as? EqualizerBandSeekBar)?.isTrackingTouch == true

        return fromUser || isVerticalSeekBarTracking
    }

    private fun updateEqualizerUiState(isEnabled: Boolean) {
        val contentAlpha = if (isEnabled) EQ_ENABLED_ALPHA else EQ_DISABLED_CONTENT_ALPHA
        val scaleAlpha = if (isEnabled) EQ_ENABLED_ALPHA else EQ_DISABLED_SCALE_ALPHA
        val tickAlpha = if (isEnabled) EQ_ENABLED_ALPHA else EQ_DISABLED_TICK_ALPHA
        val seekbarTint = if (isEnabled) {
            mainColor
        } else {
            ContextCompat.getColor(requireContext(), R.color.dark_gray)
        }

        binding.tvPreset.isEnabled = isEnabled
        binding.tvPreset.alpha = contentAlpha
        binding.spinnerPreset.isEnabled = isEnabled
        binding.spinnerPreset.alpha = contentAlpha
        binding.layoutEqScale.alpha = scaleAlpha
        binding.tvSeekbar.alpha = contentAlpha

        for (index in 0 until binding.seekbarContainer.childCount) {
            val equalizerBandSeekBar = binding.seekbarContainer.getChildAt(index) as? EqualizerBandSeekBar
                ?: continue

            equalizerBandSeekBar.isEnabled = isEnabled
            equalizerBandSeekBar.progressDrawable.mutate().setTint(seekbarTint)
            equalizerBandSeekBar.thumb.mutate().setTint(seekbarTint)
            equalizerBandSeekBar.setTickAlpha(tickAlpha)
            equalizerBandSeekBar.refreshThumbState()
        }

        for (index in 0 until binding.tvSeekbar.childCount) {
            binding.tvSeekbar.getChildAt(index).isEnabled = isEnabled
        }
    }

    // EQ SeekBar progress(0..max)를 DSP EQ gain 범위(-12dB..+12dB)에 맞춰 변환한다.
    private fun mapEqProgressToDb(progress: Int, max: Int): Float {
        if (max <= 0) return 0.0f
        val normalized = (progress.toFloat() / max.toFloat()).coerceIn(0.0f, 1.0f) // 0..1
        return (normalized * 24.0f) - 12.0f // -12..+12
    }

    private fun formatCenterFreq(centerFreqMilliHz: Int): String {
        val hz = centerFreqMilliHz / 1000
        if (hz < 1000) return "$hz Hz"

        val khz = hz / 1000f
        if (khz in 3.5f..4.4f) return "4 kHz"

        return if (khz % 1f == 0f) {
            "${khz.toInt()} kHz"
        } else {
            String.format(Locale.US, "%.1f kHz", khz)
        }
    }

    private fun initReverb() {
        isReverbSpinnerReady = false

        val reverbAdapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner,
            arrayOf(
                "None",
                "Small Room",
                "Medium Room",
                "Large Room",
                "Medium Hall",
                "Large Hall",
                "Plate"
            )
        )

        binding.spinnerReverb.adapter = reverbAdapter

        // Adapter 연결 직후 발생할 수 있는 초기 onItemSelected 콜백은 무시한다.
        binding.spinnerReverb.post {
            isReverbSpinnerReady = true
        }

        binding.spinnerReverb.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long,
            ) {
                // 초기화 중 발생한 선택 콜백은 사용자 입력이 아니므로 처리하지 않는다.
                if (!isReverbSpinnerReady || isApplyingAudioSettingsState) return

                vm.setReverbPreset(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun initLoudnessNormalizer() {
        binding.swLoudnessNormalizer.setOnCheckedChangeListener { _, isChecked ->
            if (isApplyingAudioSettingsState) return@setOnCheckedChangeListener
            vm.setLoudnessNormalizerEnabled(isChecked)
        }
    }

    private fun initBassSeekBar() {
        binding.sbBass.progressDrawable.setTint(mainColor)
        binding.sbBass.thumb.setTint(mainColor)

        // BassBoost는 EQ 프리셋과 독립된 효과이므로 프리셋 상태를 바꾸지 않는다.
        binding.sbBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser || isApplyingAudioSettingsState) return
                vm.updateBassStrength(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    private fun initVirtualizerSeekbar() {
        binding.sbVirtualizer.progressDrawable.setTint(mainColor)
        binding.sbVirtualizer.thumb.setTint(mainColor)

        // 3D Virtualizer는 EQ 프리셋과 독립된 효과이므로 프리셋 상태를 바꾸지 않는다.
        binding.sbVirtualizer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser || isApplyingAudioSettingsState) return
                vm.updateVirtualizerStrength(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AudioSettingsFragment"

        private const val EQ_ENABLED_ALPHA = 1.0f
        private const val EQ_DISABLED_CONTENT_ALPHA = 0.55f
        private const val EQ_DISABLED_SCALE_ALPHA = 0.5f
        private const val EQ_DISABLED_TICK_ALPHA = 0.45f

        fun newInstance(): AudioSettingsFragment = AudioSettingsFragment()
    }
}
