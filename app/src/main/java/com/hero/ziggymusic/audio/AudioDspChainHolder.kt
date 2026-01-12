package com.hero.ziggymusic.audio

import android.content.Context
import android.media.AudioManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 네이티브 DSP 체인(JNI) 생명주기 단일화 Holder.
 * - UI(Fragment)에서 직접 create/destroy 하지 않고, 플레이어/앱 스코프에서 관리한다.
 */
object AudioDspChainHolder {

    private val isDspChainInitialized = AtomicBoolean(false)
    private val initializedDspSampleRateHz = AtomicInteger(0)

    /**
     * 디바이스 출력 sampleRate를 기반으로 네이티브 체인을 준비한다.
     * 여러 번 호출돼도 안전(idempotent)하게 동작한다.
     */
    fun ensureNativeDspChainInitialized(appContext: Context): Int {
        val sampleRate = getOutputSampleRateHz(appContext)

        // 이미 초기화됐고 SR도 같으면 그대로 사용
        if (isDspChainInitialized.get() && initializedDspSampleRateHz.get() == sampleRate) {
            return sampleRate
        }

        // 이미 초기화됐는데 SR이 바뀌면: 체인 재생성
        if (isDspChainInitialized.get()) {
            runCatching { AudioProcessorChainController.destroyChain() }
            isDspChainInitialized.set(false)
            initializedDspSampleRateHz.set(0)
        }

        runCatching { AudioProcessorChainController.createChain(sampleRate) }
            .onSuccess {
                initializedDspSampleRateHz.set(sampleRate)
                isDspChainInitialized.set(true)
            }
            .getOrThrow()

        return sampleRate
    }

    /**
     * 앱 종료/명시적 정리에 사용.
     */
    fun releaseNativeChain() {
        if (!isDspChainInitialized.get()) return
        runCatching { AudioProcessorChainController.destroyChain() }
        isDspChainInitialized.set(false)
        initializedDspSampleRateHz.set(0)
    }

    fun isInitialized(): Boolean = isDspChainInitialized.get()

    fun currentSampleRateHz(): Int = initializedDspSampleRateHz.get()

    private fun getOutputSampleRateHz(appContext: Context): Int {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: 48000
    }
}
