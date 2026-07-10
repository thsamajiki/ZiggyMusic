package com.hero.ziggymusic.presentation.main.setting.model

/**
 * 앱의 라이선스 및 서드파티 고지 화면에 표시할 단일 항목
 */
data class LicenseNotice(
    val id: String,
    val name: String,
    val version: String,
    val licenseSummary: String,
    val detail: String,
)
