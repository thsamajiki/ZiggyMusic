package com.hero.ziggymusic.view.main.setting

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
import android.widget.SeekBar
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.hero.ziggymusic.R
import com.hero.ziggymusic.databinding.FragmentSettingBinding
import androidx.core.content.edit
import com.hero.ziggymusic.audio.HeadTracker
import com.hero.ziggymusic.audio.PlayerAudioGraph
import com.hero.ziggymusic.audio.SpatializerSupport
import com.hero.ziggymusic.view.main.setting.AudioEffectManager.equalizer
import com.hero.ziggymusic.view.main.setting.AudioEffectManager.mainColor

class SettingFragment : Fragment() {
    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!

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
        _binding = FragmentSettingBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initSetting()

        initEqualizer()
        initBassSeekBar(prefs)
        initReverb()
        initVirtualizerSeekbar()
        initSpatialAudioUi() // XR 기능 UI 설정
    }

    // Spatial Audio & Head Tracking UI 설정
    private fun initSpatialAudioUi() {
        Log.d("SettingFragment", "Spatializer status: ${spatializerSupport.describeStatus()}")

        binding.swSpatialAudio.setOnCheckedChangeListener(null)
        binding.swHeadTracking.setOnCheckedChangeListener(null)

        // 1. Spatial Audio & Head Tracking Enable Switch
        val spatialEnabled = prefs.getBoolean(KEY_SPATIAL_ENABLED, false)
        val headEnabled = prefs.getBoolean(KEY_HEAD_TRACKING_ENABLED, false)

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
            prefs.edit { putBoolean(KEY_HEAD_TRACKING_ENABLED, isChecked) }

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
                putBoolean(KEY_SPATIAL_ENABLED, isChecked)
                if (!isChecked) putBoolean(KEY_HEAD_TRACKING_ENABLED, false)
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
        prefs = requireContext().getSharedPreferences("SettingFragment", 0)
        AudioEffectManager.setEnabledFromPrefs(prefs)
    }

    private fun initPresets(min: Int) {
        val noOfPresets = AudioEffectManager.getNumberOfPresets()
        if (noOfPresets <= 0) return

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
        binding.spinnerPreset.setSelection(prefs.getInt("PRESET", 1))

        binding.spinnerPreset.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long,
                ) {
                    if (equalizer != null) {
                        prefs.edit { putInt("PRESET", position) }

                        if (position != 0) {
                            AudioEffectManager.useEqualizerPreset(position - 1)

                            for (i in seekbarIds.indices) {
                                val seekbar =
                                    requireActivity().findViewById<SoundEQVerticalSeekbar>(seekbarIds[i])
                                seekbar.progress = 1
                                seekbar.progress =
                                    (AudioEffectManager.getBandLevel(i)?.toInt() ?: 0) - min
                                seekbar.refreshThumbState()
                            }
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    private fun initEqualizer() {
        val equalizer = AudioEffectManager.equalizer ?: return
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

            verticalSeekbar.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(
                seekBar: SeekBar,
                progress: Int,
                fromUser: Boolean,
                ) {
                    if (!verticalSeekbar.isTrackingTouch) return

                    binding.spinnerPreset.setSelection(0)
                    prefs.edit { putInt(seekBar.tag.toString(), progress) }

                    if (AudioEffectManager.equalizer?.enabled == true) {
                        AudioEffectManager.applyEqualizerBandLevel(
                            bandIndex = seekBar.tag as Int,
                            level = (progress + min).toShort()
                        )
                    }

                    val bandIndex = seekBar.tag as Int
                    val gainDb = mapEqProgressToDb(
                        progress = progress,
                        max = seekBar.max
                    )
                    AudioEffectManager.setBandGain(bandIndex, gainDb)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })

            val layoutParams: TableRow.LayoutParams =
                TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
            verticalSeekbar.layoutParams = layoutParams

            val centerFreq = AudioEffectManager.getCenterFreq(index) ?: 0
            val title = if (centerFreq > 1000000) {
                "${centerFreq / 1000000} kHz"
            } else {
                "${centerFreq / 1000} ".let { it.substring(0, it.length - 2) + " Hz" }
            }

            val textView = TextView(requireContext())
            textView.text = title
            textView.maxLines = 1
            textView.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.small_text_size)
            )
            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))

            val params =
                TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
            textView.gravity = Gravity.CENTER
            textView.layoutParams = params

            binding.tvSeekbar.addView(textView)
            binding.seekbarContainer.addView(verticalSeekbar)

            val initialGainDb = mapEqProgressToDb(
                progress = verticalSeekbar.progress,
                max = verticalSeekbar.max
            )
            AudioEffectManager.setBandGain(index, initialGainDb)
        }

        runCatching {
            AudioEffectManager.applySettingsFromPrefs(prefs, eqMaxFromUi = uiMaxForNative)
        }

        initPresets(min)
    }

    // SettingsFragment의 progress 범위를 기준으로 dB로 환산
    private fun mapEqProgressToDb(progress: Int, max: Int): Float {
        if (max <= 0) return 0.0f
        val normalized = (progress.toFloat() / max.toFloat()).coerceIn(0.0f, 1.0f) // 0..1
        return (normalized * 24.0f) - 12.0f // -12..+12
    }

    private fun initReverb() {
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
        ))

        binding.spinnerReverb.adapter = reverbAdapter
        binding.spinnerReverb.setSelection(prefs.getInt("REVERB", 0))

        binding.spinnerReverb.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long,
            ) {
                if (AudioEffectManager.reverb != null) {
                    AudioEffectManager.applyReverbPreset(position, prefs)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun initBassSeekBar(settings: SharedPreferences) {
        val bassProgress = settings.getInt("BASS", 0)

        AudioEffectManager.applyBassStrength(bassProgress)

        binding.sbBass.progressDrawable.setTint(AudioEffectManager.mainColor)
        binding.sbBass.thumb.setTint(AudioEffectManager.mainColor)
        binding.sbBass.progress = bassProgress

        binding.sbBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                AudioEffectManager.applyBassStrength(progress, prefs)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    private fun initVirtualizerSeekbar() {
        val virtualizerProgress = prefs.getInt("VIRTUALIZER", 0)

        AudioEffectManager.applyVirtualizerStrength(virtualizerProgress)

        binding.sbVirtualizer.progressDrawable.setTint(AudioEffectManager.mainColor)
        binding.sbVirtualizer.thumb.setTint(AudioEffectManager.mainColor)
        binding.sbVirtualizer.progress = virtualizerProgress

        binding.sbVirtualizer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                AudioEffectManager.applyVirtualizerStrength(progress, prefs)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    override fun onResume() {
        super.onResume()

        binding.sbVirtualizer.progress = prefs.getInt(KEY_VIRTUALIZER, 0)
        binding.sbBass.progress = prefs.getInt(KEY_BASS, 0)

        // 화면에 돌아왔을 때 설정에 따라 트래킹 재개
        if (binding.swSpatialAudio.isChecked && binding.swHeadTracking.isChecked) {
            headTracker.start()
        }
    }

    override fun onPause() {
        super.onPause()

        // Preference 저장 로직
        prefs.edit {
            putInt(KEY_BASS, binding.sbBass.progress)
            putInt(KEY_VIRTUALIZER, binding.sbVirtualizer.progress)
        }
    }

    override fun onDestroyView() {
        headTracker.stop()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): SettingFragment = SettingFragment()

        const val KEY_SPATIAL_ENABLED = "SPATIAL_ENABLED"
        const val KEY_HEAD_TRACKING_ENABLED = "HEAD_TRACKING_ENABLED"
        const val KEY_BASS = "BASS"
        const val KEY_VIRTUALIZER = "VIRTUALIZER"
    }
}
