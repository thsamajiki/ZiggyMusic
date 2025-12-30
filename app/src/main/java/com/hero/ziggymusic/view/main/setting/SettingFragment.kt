package com.hero.ziggymusic.view.main.setting

import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.os.Build
import android.os.Bundle
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
import com.hero.ziggymusic.audio.AudioProcessorChainController

class SettingFragment : Fragment() {
    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!

    var seekbarIds: ArrayList<Int> = ArrayList()

    private lateinit var settings: SharedPreferences

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
        initBassSeekBar(settings)
        initReverb()
        initVirtualizerSeekbar()

        // 네이티브 체인 초기화: 시스템 샘플레이트를 사용 (없으면 안전한 기본값으로 폴백)
        val audioManager = requireContext().getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val sampleRate = audioManager.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()
            ?: 48000

        AudioProcessorChainController.createChain(sampleRate)
        // 필요하면 Audio IO도 시작
        AudioProcessorChainController.nativeStartAudioIO(sampleRate, 256)
    }

    private fun initSetting() {
        settings = requireContext().getSharedPreferences("SettingFragment", 0)

        if (equalizer != null && bassBoost!= null && virtualizer != null && reverb != null) {
            equalizer!!.enabled = true
            bassBoost!!.enabled = true
            virtualizer!!.enabled = true
            reverb!!.enabled = true
            settings.edit { putBoolean("ENABLED", true) }
        }
    }

    private fun initPresets(min: Int) {
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

        binding.spinnerPreset.setSelection(settings.getInt("PRESET", 1))

        binding.spinnerPreset.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long,
                ) {
                    if (equalizer != null) {
                        settings.edit { putInt("PRESET", position) }

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

            for (index in 0 until equalizer!!.numberOfBands) {
                val verticalSeekbar =
                    SoundEQVerticalSeekbar(requireContext())
                seekbarIds.add(index, View.generateViewId())
                verticalSeekbar.max = max - min
                verticalSeekbar.tag = index

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    verticalSeekbar.maxHeight = 1
                }
                verticalSeekbar.id = seekbarIds[index]
                verticalSeekbar.splitTrack = true

                verticalSeekbar.progressDrawable.setTint(mainColor)
                verticalSeekbar.thumb.setTint(mainColor)

                verticalSeekbar.progress = settings.getInt(
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
                                settings.edit { putInt(seekBar.tag.toString(), progress) }

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
        binding.spinnerReverb.setSelection(settings.getInt("REVERB", 0))

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
                        settings.edit { putInt("REVERB", position) }
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
        val virtualizerProgress = settings.getInt("VIRTUALIZER", 0)

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

        // 체인의 생명주기는 Fragment가 아니라 Application/Service 레벨에서 관리해야 함.
        // 여기서는 UI가 포그라운드로 돌아왔음을 네이티브에 알리는 정도만 수행.
        AudioProcessorChainController.nativeAudioIOOnForeground()
    }

    override fun onPause() {
        super.onPause()

        requireContext().getSharedPreferences("SettingFragment", 0).edit {
            putInt("BASS", binding.sbBass.progress)
            putInt("VIRTUALIZER", binding.sbVirtualizer.progress)
        }

        // 백그라운드로 내려갔음을 알리되, 체인 destroy/stop은 여기서 하지 않음.
        // (서비스에서 백그라운드 재생 중일 수 있음)
        AudioProcessorChainController.nativeAudioIOOnBackground()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): SettingFragment = SettingFragment()

        var equalizer: Equalizer? = null
        var reverb: PresetReverb? = null
        var bassBoost: BassBoost? = null
        var virtualizer: Virtualizer? = null
        var mainColor = "#00ffee".toColorInt()
    }
}
