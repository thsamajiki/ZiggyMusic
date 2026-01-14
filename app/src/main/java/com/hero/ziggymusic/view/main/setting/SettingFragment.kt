package com.hero.ziggymusic.view.main.setting

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
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
import androidx.core.graphics.toColorInt
import com.hero.ziggymusic.audio.HeadTracker
import com.hero.ziggymusic.audio.PlayerAudioGraph
import com.hero.ziggymusic.audio.SpatializerSupport

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
        initializeAudioEngine()

        initEqualizer()
        initBassSeekBar(prefs)
        initReverb()
        initVirtualizerSeekbar()
        initSpatialAudioUi() // XR 기능 UI 설정
    }

    private fun initializeAudioEngine() {
        runCatching {
            val spatialEnabled = prefs.getBoolean(KEY_SPATIAL_ENABLED, false)
            val headEnabled = prefs.getBoolean(KEY_HEAD_TRACKING_ENABLED, false)
            SoundEQSettings.setSpatialEnabled(spatialEnabled)
            SoundEQSettings.setHeadTrackingEnabled(spatialEnabled && headEnabled)
        }
    }

    // Spatial Audio & Head Tracking UI 설정
    private fun initSpatialAudioUi() {
        Log.d("SettingFragment", "Spatializer status: ${spatializerSupport.describeStatus()}")
        // 1. Spatial Audio & Head Tracking Enable Switch
        val spatialEnabled = prefs.getBoolean(KEY_SPATIAL_ENABLED, false)
        val headEnabled = prefs.getBoolean(KEY_HEAD_TRACKING_ENABLED, false)

        binding.swSpatialAudio.setOnCheckedChangeListener(null)
        binding.swHeadTracking.setOnCheckedChangeListener(null)
        binding.swSpatialAudio.isChecked = spatialEnabled
        binding.swHeadTracking.isChecked = headEnabled && spatialEnabled

        headTracker.setOnHeadPoseListener { yawDeg ->
            // JNI Controller로 센서값 주입
            PlayerAudioGraph.setHeadTrackingActive(spatialEnabled, headEnabled)
        }

        updateHeadTrackingUiState(spatialEnabled)

        // 2. 초기 적용: 네이티브에 현재 prefs 반영
        runCatching {
            SoundEQSettings.setSpatialEnabled(spatialEnabled)
            SoundEQSettings.setHeadTrackingEnabled(spatialEnabled && headEnabled)
        }

        // 3. head switch는 한 번만 설정
        binding.swHeadTracking.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(KEY_HEAD_TRACKING_ENABLED, isChecked) }

            if (isChecked && binding.swSpatialAudio.isChecked) {
                headTracker.start()
                SoundEQSettings.setHeadTrackingEnabled(true)
            } else {
                headTracker.stop()
                SoundEQSettings.setHeadTrackingEnabled(false)
            }
        }

        // 4. spatial switch 단일 리스너
        binding.swSpatialAudio.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean(KEY_SPATIAL_ENABLED, isChecked)
                if (!isChecked) putBoolean(KEY_HEAD_TRACKING_ENABLED, false)
            }

            SoundEQSettings.setSpatialEnabled(isChecked)

            if (!isChecked) {
                // spatial off -> 강제 head off, 리스너는 유지(한 번만 설정)
                binding.swHeadTracking.isChecked = false
                binding.swHeadTracking.isEnabled = false
                headTracker.stop()
                SoundEQSettings.setHeadTrackingEnabled(false)
            } else {
                // spatial on: 기존 head 상태가 on이면 시작, head 스위치는 활성화
                binding.swHeadTracking.isEnabled = true
                if (binding.swHeadTracking.isChecked) {
                    headTracker.start()
                    SoundEQSettings.setHeadTrackingEnabled(true)
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

        // SettingFragment 진입 시점에 재생 중인 오디오 세션을 건드리지 않도록 "자동 enabled" 금지
        // (필요하면 사용자가 스위치/슬라이더 조작할 때만 enabled 변경)
        val enabled = prefs.getBoolean("ENABLED", false)

        equalizer?.enabled = enabled
        reverb?.enabled = enabled && (prefs.getInt("REVERB", 0) != 0)

        // Bass/Virtualizer는 progress==0이면 반드시 OFF (기기별 잡음/파이프라인 변경 방지)
        val bassProgress = prefs.getInt("BASS", 0)
        bassBoost?.enabled = enabled && bassProgress > 0

        val virtualizerProgress = prefs.getInt("VIRTUALIZER", 0)
        virtualizer?.enabled = enabled && virtualizerProgress > 0

    }

    private fun initPresets(min: Int) {
        if (equalizer == null) {
            // 실제 오디오 출력 또는 프리뷰 세션 ID가 있으면 전달.
            // 기본은 0(전역)으로 유지하되, 외부에서 세션이 확보되면 attachAudioSession을 호출해 재초기화 가능.
            val sessionId = 0
            equalizer = Equalizer(0, sessionId)
        }

        val noOfPresets = equalizer!!.numberOfPresets

        val presets = arrayOfNulls<String>(noOfPresets + 1)
        presets[0] = "Custom"

        for (i in 0 until noOfPresets) {
            presets[i + 1] = equalizer!!.getPresetName(i.toShort())
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

                        Runnable {
                            if (position != 0) {
                                equalizer!!.usePreset((position - 1).toShort())
                                for (i in seekbarIds.indices) {
                                    Runnable {
                                        val seekbar =
                                            requireActivity().findViewById<SoundEQVerticalSeekbar>(seekbarIds[i])
                                        seekbar.shouldChange = false
                                        seekbar.progress = 1
                                        seekbar.progress =
                                            equalizer!!.getBandLevel(i.toShort()) - min
                                        seekbar.updateThumb()
                                    }.run()
                                }
                            }
                        }.run()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun initEqualizer() {
        if (equalizer != null) {
            val max = equalizer!!.bandLevelRange[1].toInt()
            val min = equalizer!!.bandLevelRange[0].toInt()
            var uiMaxForNative = 0

            for (index in 0 until equalizer!!.numberOfBands) {
                val verticalSeekbar =
                    SoundEQVerticalSeekbar(requireContext())
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
                    equalizer!!.getBandLevel(index.toShort()).toInt() - min
                )

                verticalSeekbar.setOnSeekBarChangeListener(object :
                    SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromUser: Boolean,
                    ) {
                        if (equalizer != null) {
                            if (verticalSeekbar.shouldChange) {
                                binding.spinnerPreset.setSelection(0)
                                prefs.edit { putInt(seekBar.tag.toString(), progress) }

                                if (equalizer!!.enabled) {
                                    equalizer!!.setBandLevel(
                                        (seekBar.tag as Int).toShort(),
                                        (progress + min).toShort()
                                    )
                                }

                                // 네이티브 EQ에도 동일 변경 반영
                                val bandIndex = (seekBar.tag as Int)
                                val gainDb = mapEqProgressToDb(
                                    progress = progress,
                                    max = seekBar.max
                                )
                                SoundEQSettings.setBandGain(bandIndex, gainDb)
                            }
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {}
                })

                val layoutParams: TableRow.LayoutParams =
                    TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
                verticalSeekbar.layoutParams = layoutParams

                var title: String
                if (equalizer!!.getCenterFreq(index.toShort()) > 1000000) {
                    title = "${equalizer!!.getCenterFreq(index.toShort()) / 1000000} kHz"
                } else {
                    title = "${equalizer!!.getCenterFreq(index.toShort()) / 1000} "
                    title = title.substring(0, title.length - 2) + " Hz"
                }

                val textView = TextView(requireContext())
                textView.text = title
                textView.maxLines = 1
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.small_text_size))
                textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))

                val params: TableRow.LayoutParams =
                    TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                textView.gravity = Gravity.CENTER
                textView.layoutParams = params
                binding.tvSeekbar.addView(textView)
                binding.seekbarContainer.addView(verticalSeekbar)

                // 초기 로딩 시 네이티브 밴드도 저장값으로 동기화
                val initialGainDb = mapEqProgressToDb(
                    progress = verticalSeekbar.progress,
                    max = verticalSeekbar.max
                )
                SoundEQSettings.setBandGain(index, initialGainDb)
            }

            runCatching { SoundEQSettings.applySettingsFromPrefs(prefs, eqMaxFromUi = uiMaxForNative) }
            initPresets(min)
        }
    }

    // SettingsFragment의 progress 범위를 기준으로 dB로 환산
    private fun mapEqProgressToDb(progress: Int, max: Int): Float {
        if (max <= 0) return 0.0f
        val normalized = (progress.toFloat() / max.toFloat()).coerceIn(0.0f, 1.0f) // 0..1
        return (normalized * 24.0f) - 12.0f // -12..+12
    }

    private fun initReverb() {
        val reverbAdapter = ArrayAdapter(requireContext(),
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
                if (reverb != null) {
                    Runnable {
                        reverb!!.preset = position.toShort()
                        reverb!!.enabled = true
                        prefs.edit { putInt("REVERB", position) }
                    }.run()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun initBassSeekBar(settings: SharedPreferences) {
        val bassProgress = settings.getInt("BASS", 0)

        if (bassBoost != null) {
            if (bassBoost!!.strengthSupported) {
                bassBoost!!.setStrength((bassProgress * 15).toShort())
                bassBoost!!.enabled = true
            }
        }

        binding.sbBass.progressDrawable.setTint(mainColor)
        binding.sbBass.thumb.setTint(mainColor)
        binding.sbBass.progress = bassProgress

        binding.sbBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (bassBoost != null) {
                    if (bassBoost!!.strengthSupported) {
                        bassBoost!!.setStrength(((progress * 15).toShort()))
                        bassBoost!!.enabled = true
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun initVirtualizerSeekbar() {
        val virtualizerProgress = prefs.getInt("VIRTUALIZER", 0)

        if (virtualizer != null) {
            if (virtualizer!!.strengthSupported) {
                virtualizer!!.setStrength((virtualizerProgress * 15).toShort())
                virtualizer!!.enabled = true
            }
        }

        binding.sbVirtualizer.progressDrawable.setTint(mainColor)
        binding.sbVirtualizer.thumb.setTint(mainColor)
        binding.sbVirtualizer.progress = virtualizerProgress

        binding.sbVirtualizer.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (virtualizer != null) {
                    if (virtualizer!!.strengthSupported) {
                        virtualizer!!.setStrength((progress * 15).toShort())
                        virtualizer!!.enabled = true
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    override fun onResume() {
        super.onResume()

        val settings = requireContext().getSharedPreferences("SettingFragment", 0)

        val virtualizerProgress = settings.getInt("VIRTUALIZER", 0)
        binding.sbVirtualizer.progress = virtualizerProgress

        val bassProgress = settings!!.getInt("BASS", 0)
        binding.sbBass.progress = bassProgress

        // 화면에 돌아왔을 때 설정에 따라 트래킹 재개
        if (binding.swSpatialAudio.isChecked && binding.swHeadTracking.isChecked) {
            headTracker.start()
        }
    }

    override fun onPause() {
        super.onPause()

        requireContext().getSharedPreferences("SettingFragment", 0).edit {
            putInt("BASS", binding.sbBass.progress)
            putInt("VIRTUALIZER", binding.sbVirtualizer.progress)
        }

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

        var equalizer: Equalizer? = null
        var reverb: PresetReverb? = null
        var bassBoost: BassBoost? = null
        var virtualizer: Virtualizer? = null
        var mainColor = "#00ffee".toColorInt()
    }
}
