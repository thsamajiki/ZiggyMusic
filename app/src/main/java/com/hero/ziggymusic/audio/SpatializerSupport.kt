package com.hero.ziggymusic.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

/**
 * System Spatializer(플랫폼 Spatial Audio) 상태/가능 여부 조회용.
 *
 * 중요:
 * - Spatializer는 일반 앱이 enable/disable 할 수 있는 public setEnabled()가 없습니다.
 * - 따라서 앱은 상태 조회 + 설정 화면 유도 UX가 현업에서 일반적입니다.
 */
class SpatializerSupport(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun isSupportedByApi(): Boolean = Build.VERSION.SDK_INT >= 32

    fun describeStatus(): String {
        if (Build.VERSION.SDK_INT < 32) return "System Spatializer: API<32"

        val base = runCatching { api32DescribeBaseStatus() }
            .getOrElse { "System Spatializer: error (${it.javaClass.simpleName})" }

        // head tracker availability는 API 33+에 추가됨
        if (Build.VERSION.SDK_INT < 33) return base

        val head = runCatching { api33IsHeadTrackerAvailable() }.getOrNull()
        return if (head != null) "$base, headTrackerAvailable=$head" else base
    }

    fun isAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < 32) return false
        return runCatching { api32Spatializer().isAvailable }.getOrDefault(false)
    }

    fun isEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < 32) return false
        return runCatching { api32Spatializer().isEnabled }.getOrDefault(false)
    }

    /**
     * 현재 라우팅/디바이스에서 "이 컨텐츠(속성/포맷)가 시스템에 의해 spatialize 될 수 있는지" 판단.
     * (기기/OEM/헤드폰 상태에 크게 좌우)
     */
    fun canBeSpatialized(attributes: AudioAttributes, format: AudioFormat): Boolean {
        if (Build.VERSION.SDK_INT < 32) return false
        return runCatching { api32Spatializer().canBeSpatialized(attributes, format) }.getOrDefault(false)
    }

    /**
     * 사용자에게 "설정에서 Spatial Audio를 켜세요"로 유도하는 안전한 방법.
     * - 공식적으로 Spatial Audio 전용 액션 intent가 보장되지 않는 기기들이 많아,
     *   현업에서는 Sound Settings / Bluetooth Settings로 유도하는 식으로 처리하는 경우가 많습니다.
     */
    fun buildOpenSpatialAudioSettingsIntent(isBluetoothHeadsetLikely: Boolean): Intent {
        return if (isBluetoothHeadsetLikely) {
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        } else {
            Intent(Settings.ACTION_SOUND_SETTINGS)
        }
    }

    // -----------------------------
    // API 32+
    // -----------------------------
    @RequiresApi(32)
    private fun api32Spatializer() = audioManager.spatializer

    @RequiresApi(32)
    private fun api32DescribeBaseStatus(): String {
        val sp = api32Spatializer()
        // isEnabled는 환경에 따라 제공되지 않거나 신뢰하기 어려워 사용하지 않음
        return "System Spatializer: " + (if (sp.isAvailable) "Available" else "Not available")
    }

    // -----------------------------
    // API 33+
    // -----------------------------
    @RequiresApi(33)
    private fun api33IsHeadTrackerAvailable(): Boolean {
        return api32Spatializer().isHeadTrackerAvailable
    }
}
