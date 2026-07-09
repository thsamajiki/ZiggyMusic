package com.hero.ziggymusic.presentation.main.setting

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebViewClient
import com.hero.ziggymusic.databinding.FragmentWebPageBinding

/**
 * 이용약관, 개인정보처리방침, 오픈소스 라이선스와 같은
 * 정적 웹 문서를 앱 내부 WebView로 표시하는 Fragment.
 *
 * 표시할 URL과 제목은 [newInstance]를 통해 전달받으며,
 * 보안상 JavaScript와 파일 접근 등 불필요한 WebView 기능은 비활성화한다.
 */
class WebPageFragment : Fragment() {
    private var _binding: FragmentWebPageBinding? = null
    private val binding get() = _binding!!

    val titleResId: Int
        get() = requireArguments().getInt(ARG_WEB_PAGE_TITLE_RES_ID)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWebPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.webView.apply {
            webViewClient = WebViewClient()

            settings.apply {
                javaScriptEnabled = false
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
            }

            loadUrl(requireArguments().getString(ARG_WEB_PAGE_URL).orEmpty())
        }
    }

    override fun onDestroyView() {
        binding.webView.destroy()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_WEB_PAGE_URL = "web_page_url"
        private const val ARG_WEB_PAGE_TITLE_RES_ID = "web_page_title_res_id"

        /**
         * 지정한 URL과 제목으로 [WebPageFragment] 인스턴스를 생성한다.
         */
        fun newInstance(
            url: String,
            titleResId: Int,
        ): WebPageFragment {
            return WebPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WEB_PAGE_URL, url)
                    putInt(ARG_WEB_PAGE_TITLE_RES_ID, titleResId)
                }
            }
        }
    }
}
