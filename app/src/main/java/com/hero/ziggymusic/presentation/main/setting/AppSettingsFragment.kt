package com.hero.ziggymusic.presentation.main.setting

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.hero.ziggymusic.databinding.FragmentAppSettingsBinding
import com.hero.ziggymusic.presentation.main.MainViewModel

class AppSettingsFragment : Fragment() {
    private var _binding: FragmentAppSettingsBinding? = null
    private val binding get() = _binding!!

    private val mainVm by activityViewModels<MainViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppSettingsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initView()
        initListeners()
    }

    private fun initView() {
        binding.tvAppVersionValue.text = getAppVersionName()
    }

    private fun initListeners() {
        binding.rowAudioSettings.setOnClickListener {
            mainVm.requestOpenAudioSettings()
        }

        binding.rowTermsOfService.setOnClickListener {
            mainVm.requestTermsOfService()
        }

        binding.rowPrivacyPolicy.setOnClickListener {
            mainVm.requestOpenPrivacyPolicy()
        }

        binding.rowOpenSourceLicenses.setOnClickListener {
        }
    }

    private fun getAppVersionName(): String {
        return runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().packageManager.getPackageInfo(
                    requireContext().packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            }

            packageInfo.versionName ?: DEFAULT_APP_VERSION
        }.getOrDefault(DEFAULT_APP_VERSION)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG_AUDIO_SETTINGS = "audio_settings"
        private const val DEFAULT_APP_VERSION = "-"

        fun newInstance(): AppSettingsFragment = AppSettingsFragment()
    }
}
