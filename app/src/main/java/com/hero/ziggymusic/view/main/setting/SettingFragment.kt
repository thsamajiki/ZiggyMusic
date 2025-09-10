package com.hero.ziggymusic.view.main.setting

import android.content.SharedPreferences
import android.graphics.Color
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
    }

    private fun initSetting() {
        settings = requireContext().getSharedPreferences("SettingFragment", 0)

        if (equalizer != null && bassBoost!= null && virtualizer != null && reverb != null) {
            equalizer!!.enabled = true
            bassBoost!!.enabled = true
            virtualizer!!.enabled = true
            reverb!!.enabled = true
            settings.edit().putBoolean("ENABLED", true).apply()
        }
    }

    private fun initPresets(min: Int) {
        val noOfPresets = equalizer!!.numberOfPresets

        val presets = arrayOfNulls<String>(noOfPresets + 1)
        presets[0] = "Custom"

        for (i in 0 until noOfPresets) {
            presets[i + 1] = equalizer!!.getPresetName(i.toShort())
        }

        val spinnerAdapter: ArrayAdapter<String?> =
//            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, presets)
            ArrayAdapter(requireContext(), R.layout.spinner_item, presets)
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
                        settings.edit().putInt("PRESET", position).apply()
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
                //verticalSeekbar.progressDrawable = ContextCompat.getDrawable(this, R.drawable.seekbar_style_equalizer)
                //verticalSeekbar.thumb = ContextCompat.getDrawable(this, R.drawable.thumb_equalizer)
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
                                settings.edit().putInt(seekBar.tag.toString(), progress).apply()
                                if (equalizer!!.enabled) {

                                    equalizer!!.setBandLevel(
                                        (seekBar.tag as Int).toShort(),
                                        (progress + min).toShort()
                                    )
                                }
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
            }

            initPresets(min)
        }
    }

    private fun initReverb() {
        val reverbAdapter =
//            ArrayAdapter(
//            requireContext(),
//            android.R.layout.simple_spinner_dropdown_item,
//            arrayOf(
//                "None",
//                "Small Room",
//                "Medium Room",
//                "Large Room",
//                "Medium Hall",
//                "Large Hall",
//                "Plate"
//            )
//        )
        ArrayAdapter(requireContext(), R.layout.spinner_item, arrayOf(
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
                        settings.edit().putInt("REVERB", position).apply()
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
        //overridePendingTransition(R.anim.zoom_enter, R.anim.none)

        val settings = requireActivity().getSharedPreferences("SettingFragment", 0)

        val virtualizerProgress = settings.getInt("VIRTUALIZER", 0)
        binding.sbVirtualizer.progress = virtualizerProgress

        val bassProgress = settings!!.getInt("BASS", 0)
        binding.sbBass.progress = bassProgress
    }

    override fun onPause() {
        super.onPause()

        //overridePendingTransition(R.anim.none, R.anim.zoom_exit)

        val settings = requireActivity().getSharedPreferences("SettingFragment", 0).edit()

        settings.putInt("BASS", binding.sbBass.progress)
        settings.putInt("VIRTUALIZER", binding.sbVirtualizer.progress)

        settings.apply()
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
        var mainColor = Color.parseColor("#00ffee")
    }
}
