package com.hero.ziggymusic.presentation.main.setting

import com.hero.ziggymusic.presentation.main.setting.model.LicenseNotice

/**
 * Gradle 의존성으로 자동 수집되지 않는 서드파티 SDK 고지 목록
 */
object ManualLicenseNotices {
    val notices: List<LicenseNotice> = listOf(
        LicenseNotice(
            id = "superpowered-audio-sdk",
            name = "Superpowered Audio SDK",
            version = "",
            licenseSummary = "Proprietary third-party SDK",
            detail = """
                Superpowered Audio SDK is used under a separate commercial third-party license.

                Superpowered Audio SDK is proprietary software and is not an open-source library.

                Website: https://superpowered.com
                License: https://superpowered.com/licensing
            """.trimIndent()
        )
    )
}
