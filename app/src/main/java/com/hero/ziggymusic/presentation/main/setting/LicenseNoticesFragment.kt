package com.hero.ziggymusic.presentation.main.setting

import android.app.AlertDialog
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.hero.ziggymusic.R
import com.hero.ziggymusic.databinding.FragmentLicenseNoticesBinding
import com.hero.ziggymusic.presentation.main.setting.model.LicenseNotice

// AboutLibraries가 생성한 고지와 자동 수집이 어려운 수동 고지를 함께 표시한다.
class LicenseNoticesFragment : Fragment() {
    private var _binding: FragmentLicenseNoticesBinding? = null
    private val binding get() = _binding!!

    private val licenseNoticeAdapter = LicenseNoticeAdapter(::showLicenseNoticeDialog)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLicenseNoticesBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initView()
    }

    private fun initView() {
        binding.rvLicenseNotices.adapter = licenseNoticeAdapter
        binding.rvLicenseNotices.layoutManager = LinearLayoutManager(requireContext())
        licenseNoticeAdapter.submitList(loadLicenseNotices())
    }

    private fun loadLicenseNotices(): List<LicenseNotice> {
        // AboutLibraries가 생성한 고지와 자동 수집이 어려운 수동 고지를 함께 표시한다.
        return runCatching {
            val json = resources
                .openRawResource(R.raw.aboutlibraries)
                .bufferedReader()
                .use { it.readText() }

            LicenseNoticeParser.parse(json)
                .plus(ManualLicenseNotices.notices)
                .filterNot { it.name.isBlank() }
                .distinctBy { it.id }
                .sortedBy { it.name.lowercase() }
        }.getOrElse {
            // 생성 리소스를 읽지 못해도 수동 고지는 표시한다.
            ManualLicenseNotices.notices
        }
    }

    private fun showLicenseNoticeDialog(notice: LicenseNotice) {
        val messageView = TextView(requireContext()).apply {
            setPadding(48, 24, 48, 0)
            movementMethod = ScrollingMovementMethod() // 긴 라이선스 전문을 다이얼로그 안에서 스크롤한다.
            text = notice.detail.ifBlank { notice.licenseSummary }
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(notice.name)
            .setView(messageView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        binding.rvLicenseNotices.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(): LicenseNoticesFragment = LicenseNoticesFragment()
    }
}
