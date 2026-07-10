package com.hero.ziggymusic.presentation.main.setting

import com.hero.ziggymusic.presentation.main.setting.model.LicenseNotice
import org.json.JSONObject

/**
 * AboutLibraries가 생성한 JSON을 앱에서 표시할 고지 모델로 변환한다.
 */
object LicenseNoticeParser {
    fun parse(json: String): List<LicenseNotice> {
        val root = JSONObject(json)
        val libraries = root.optJSONArray("libraries") ?: return emptyList()

        return buildList {
            for (index in 0 until libraries.length()) {
                val libraryJson = libraries.optJSONObject(index) ?: continue

                val id = libraryJson.optString("uniqueId")
                    .ifBlank { libraryJson.optString("artifactId") }
                    .ifBlank { libraryJson.optString("name") }

                val name = libraryJson.optString("name")
                    .ifBlank { libraryJson.optString("artifactId") }
                    .ifBlank { id }

                val version = libraryJson.optString("artifactVersion")
                    .ifBlank { libraryJson.optString("version") }

                val website = libraryJson.optString("website")
                    .ifBlank { libraryJson.optString("scm") }

                val licenses = libraryJson.optJSONArray("licenses")
                val licenseLabels = mutableListOf<String>()
                val licenseDetails = mutableListOf<String>()

                if (licenses != null) {
                    for (licenseIndex in 0 until licenses.length()) {
                        val licenseJson = licenses.optJSONObject(licenseIndex) ?: continue

                        val licenseName = licenseJson.optString("name")
                            .ifBlank { licenseJson.optString("spdxId") }
                            .ifBlank { licenseJson.optString("hash") }

                        val licenseUrl = licenseJson.optString("url")
                        val licenseContent = licenseJson.optString("content")

                        if (licenseName.isNotBlank()) {
                            licenseLabels += licenseName
                        }

                        licenseDetails += buildString {
                            if (licenseName.isNotBlank()) {
                                appendLine(licenseName)
                            }
                            if (licenseUrl.isNotBlank()) {
                                appendLine(licenseUrl)
                            }
                            if (licenseContent.isNotBlank()) {
                                appendLine()
                                appendLine(licenseContent)
                            }
                        }.trim()
                    }
                }

                add(
                    LicenseNotice(
                        id = id,
                        name = name,
                        version = version,
                        licenseSummary = licenseLabels.distinct().joinToString(", "),
                        detail = buildString {
                            if (version.isNotBlank()) {
                                appendLine("Version: $version")
                            }
                            if (website.isNotBlank()) {
                                appendLine("Website: $website")
                            }
                            if (licenseDetails.any { it.isNotBlank() }) {
                                appendLine()
                                append(licenseDetails.filter { it.isNotBlank() }
                                    .joinToString("\n\n"))
                            }
                        }.trim()
                    )
                )
            }
        }
    }
}
