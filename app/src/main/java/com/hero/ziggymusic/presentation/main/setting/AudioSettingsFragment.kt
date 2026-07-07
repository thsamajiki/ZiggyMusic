package com.hero.ziggymusic.presentation.main.setting

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.hero.ziggymusic.playback.audio.HeadTracker
import com.hero.ziggymusic.playback.audio.PlayerAudioGraph
import com.hero.ziggymusic.playback.audio.SpatializerSupport
import com.hero.ziggymusic.playback.manager.AudioEffectManager
import com.hero.ziggymusic.playback.manager.AudioEffectManager.mainColor
import com.hero.ziggymusic.presentation.main.setting.model.AudioSettingsUiState
import com.hero.ziggymusic.presentation.main.setting.viewmodel.AudioSettingsViewModel
import com.hero.ziggymusic.presentation.main.setting.widget.SoundEQVerticalSeekbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class AudioSettingsFragment : Fragment() {
    private var _binding: FragmentAudioSettingsBinding? = null
    private val binding get() = _binding!!

    private val vm by activityViewModels<AudioSettingsViewModel>()
    private var isApplyingAudioSettingsState = false

    // Spinner adapter 연결 직후 발생하는 초기 선택 콜백을 사용자 선택과 구분하기 위한 플래그.
    private var isPresetSpinnerReady = false
    private var isReverbSpinnerReady = false

    var seekbarIds: ArrayList<Int> = ArrayList()

    private lateinit var prefs: SharedPreferences

    // XR / Spatial Audio Components
    private lateinit var headTracker: HeadTracker
    private lateinit var spatializerSupport: SpatializerSupport

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 초기화 시점이 빠를수록 좋음
        headTracker = HeadTracker(context)
        spatializerSupport = SpatializerSupport(context)
    }

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
        initBassSeekBar(prefs)
        initReverb()
        initLoudnessNormalizer()
        initVirtualizerSeekbar()
        initSpatialAudioUi() // XR 기능 UI 설정
        observeAudioSettingsState()
        vm.refreshSettings()
    }

    // Spatial Audio & Head Tracking UI 설정
    private fun initSpatialAudioUi() {
        Log.d(TAG, "Spatializer status: ${spatializerSupport.describeStatus()}")

        binding.swSpatialAudio.setOnCheckedChangeListener(null)
        binding.swHeadTracking.setOnCheckedChangeListener(null)

        // 1. Spatial Audio & Head Tracking Enable Switch
        val spatialEnabled = prefs.getBoolean(AudioSettingKeys.KEY_SPATIAL_ENABLED, false)
        val headEnabled = prefs.getBoolean(AudioSettingKeys.KEY_HEAD_TRACKING_ENABLED, false)

        binding.swSpatialAudio.isChecked = spatialEnabled
        binding.swHeadTracking.isChecked = spatialEnabled && headEnabled

        updateHeadTrackingUiState(spatialEnabled)

        // 2. 초기 적용: 네이티브에 현재 prefs 반영
        runCatching {
            AudioEffectManager.setSpatialEnabled(spatialEnabled)
            AudioEffectManager.setHeadTrackingEnabled(spatialEnabled && headEnabled)

            if (spatialEnabled && headEnabled) {
                PlayerAudioGraph.setHeadTrackingActive(true, true)
                headTracker.start()
            } else {
                PlayerAudioGraph.setHeadTrackingActive(false, false)
                headTracker.stop()
            }
        }

        headTracker.setOnHeadPoseListener { yawDeg ->
            val isSpatialOn = binding.swSpatialAudio.isChecked
            val isHeadTrackingOn = binding.swHeadTracking.isChecked

            if (isSpatialOn && isHeadTrackingOn) {
                AudioEffectManager.setHeadTrackingYaw(yawDeg)
            }
        }

        // 3. head switch는 한 번만 설정
        binding.swHeadTracking.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(AudioSettingKeys.KEY_HEAD_TRACKING_ENABLED, isChecked) }

            val isSpatialOn = binding.swSpatialAudio.isChecked
            val shouldTrack = isSpatialOn && isChecked

            AudioEffectManager.setHeadTrackingEnabled(shouldTrack)

            // JNI Controller로 센서값 주입
            PlayerAudioGraph.setHeadTrackingActive(shouldTrack, shouldTrack)

            if (shouldTrack) {
                headTracker.start()
            } else {
                headTracker.stop()
            }
        }

        // 4. spatial switch 단일 리스너
        binding.swSpatialAudio.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean(AudioSettingKeys.KEY_SPATIAL_ENABLED, isChecked)
                if (!isChecked) putBoolean(AudioSettingKeys.KEY_HEAD_TRACKING_ENABLED, false)
            }

            AudioEffectManager.setSpatialEnabled(isChecked)

            if (!isChecked) {
                // spatial off -> 강제 head off, 리스너는 유지(한 번만 설정)
                binding.swHeadTracking.isChecked = false
                AudioEffectManager.setHeadTrackingEnabled(false)
                PlayerAudioGraph.setHeadTrackingActive(
                    spatialEnabled = false,
                    headTrackingEnabled = false
                )
                headTracker.stop()
            } else {
                // spatial on: 기존 head 상태가 on이면 시작, head 스위치는 활성화
                val shouldTrack = binding.swHeadTracking.isChecked
                AudioEffectManager.setHeadTrackingEnabled(shouldTrack)
                PlayerAudioGraph.setHeadTrackingActive(shouldTrack, shouldTrack)

                if (shouldTrack) {
                    headTracker.start()
                }
            }

            updateHeadTrackingUiState(isChecked)
        }
    }

    private fun updateHeadTrackingUiState(isSpatialEnabled: Boolean) {
        binding.swHeadTracking.isEnabled = isSpatialEnabled
        binding.tvHeadTracking.alpha = if (isSpatialEnabled) 1.0f else 0.5f

        // Spatial 활성 여부에 따라 보이기/숨기기 처리
        if (isSpatialEnabled) {
            binding.tvHeadTracking.visibility = View.VISIBLE
            binding.swHeadTracking.visibility = View.VISIBLE
        } else {
            binding.tvHeadTracking.visibility = View.GONE
            binding.swHeadTracking.visibility = View.GONE
        }
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
        val max = bandLevelRange[1].toInt()
        val min = bandLevelRange[0].toInt()
        var uiMaxForNative = 0

        seekbarIds.clear()
        binding.tvSeekbar.removeAllViews()
        binding.seekbarContainer.removeAllViews()

        val numberOfBands = AudioEffectManager.getNumberOfBands()

        for (index in 0 until numberOfBands) {
            val verticalSeekbar = SoundEQVerticalSeekbar(requireContext())
            seekbarIds.add(index, View.generateViewId())
            verticalSeekbar.max = max - min
            verticalSeekbar.tag = index
            uiMaxForNative = verticalSeekbar.max

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                verticalSeekbar.maxHeight = 1
            }

            verticalSeekbar.id = seekbarIds[index]
            verticalSeekbar.splitTrack = true

            verticalSeekbar.progressDrawable.setTint(mainColor)
            verticalSeekbar.thumb.setTint(mainColor)

            verticalSeekbar.progress = prefs.getInt(
                index.toString(),
                (AudioEffectManager.getBandLevel(index)?.toInt() ?: 0) - min
            )

            verticalSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    if (!fromUser || isApplyingAudioSettingsState) return

                    // EQ 밴드 직접 조작은 Custom 프리셋 전환 조건이다.
                    vm.updateEqualizerBandFromUser(
                        bandIndex = seekBar.tag as Int,
                        progress = progress
                    )
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })

            val seekbarLayoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
            verticalSeekbar.layoutParams = seekbarLayoutParams

            val centerFreq = AudioEffectManager.getCenterFreq(index) ?: 0
            val title = formatCenterFreq(centerFreq)

            val textView = TextView(requireContext())
            textView.text = title
            textView.includeFontPadding = false
            textView.maxLines = 1
            textView.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.setting_eq_label_text_size)
            )
            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))

            val params =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            textView.gravity = Gravity.CENTER
            textView.layoutParams = params

            binding.tvSeekbar.addView(textView)
            binding.seekbarContainer.addView(verticalSeekbar)
            verticalSeekbar.post { verticalSeekbar.refreshThumbState() }

            val initialGainDb = mapEqProgressToDb(
                progress = verticalSeekbar.progress,
                max = verticalSeekbar.max
            )
            AudioEffectManager.setBandGain(index, initialGainDb)
        }

        runCatching {
            AudioEffectManager.applySettingsFromPrefs(prefs, eqMaxFromUi = uiMaxForNative)
        }

        initPresets()
        updateEqualizerUiState(binding.swEqualizer.isChecked)
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
            val seekbar = binding.seekbarContainer.getChildAt(index) as? SoundEQVerticalSeekbar
                ?: continue

            seekbar.isEnabled = isEnabled
            seekbar.progressDrawable.mutate().setTint(seekbarTint)
            seekbar.thumb.mutate().setTint(seekbarTint)
            seekbar.setTickAlpha(tickAlpha)
            seekbar.refreshThumbState()
        }

        for (index in 0 until binding.tvSeekbar.childCount) {
            binding.tvSeekbar.getChildAt(index).isEnabled = isEnabled
        }
    }

    // SettingsFragment의 progress 범위를 기준으로 dB로 환산
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

    private fun initBassSeekBar(settings: SharedPreferences) {
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

    override fun onResume() {
        super.onResume()

        // 화면에 돌아왔을 때 Head Tracking이 켜져 있으면 센서 추적을 재개한다.
        if (binding.swSpatialAudio.isChecked && binding.swHeadTracking.isChecked) {
            headTracker.start()
        }
    }

    override fun onDestroyView() {
        headTracker.stop()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): AudioSettingsFragment = AudioSettingsFragment()

        const val TAG = "AudioSettingsFragment"

        private const val EQ_ENABLED_ALPHA = 1.0f
        private const val EQ_DISABLED_CONTENT_ALPHA = 0.55f
        private const val EQ_DISABLED_SCALE_ALPHA = 0.5f
        private const val EQ_DISABLED_TICK_ALPHA = 0.45f
    }
}
