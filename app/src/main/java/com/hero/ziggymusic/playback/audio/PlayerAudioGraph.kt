package com.hero.ziggymusic.playback.audio

import java.util.concurrent.atomic.AtomicBoolean

/**
 * “플레이어 오디오 DSP 체인 라이프사이클을 관리하는 단일 책임 객체”
 * Player 수명에 맞춰 네이티브 DSP 체인의 lifecycle만 관리하는 컴포넌트.
 * native chain lifecycle을 ‘Player lifecycle'에 붙이는 오케스트레이터로 두는 게 목적.
 * 그러면 PlayerModule은 여전히 ExoPlayer를 제공하고, PlayerAudioGraph는 체인만 관리함.
 * 스레드 안전성을 위해 double-checked synchronization 적용.
 *
 * 원칙:
 * - AudioProcessorChainController.createChain()/destroyChain()은 여기서만 호출
 * - Media3 재생 경로(AudioProcessorAdapter)와 Preview(Oboe)는 동시 실행 금지(기본 정책)
 */
object PlayerAudioGraph {
    private val chainCreated = AtomicBoolean(false)
    private val previewRunning = AtomicBoolean(false)

    // policy: Preview를 켜면 Media3 DSP 경로는 사용하지 않도록 강제(또는 Preview 시작을 거부)
    // 지금은 "Preview 우선" 정책: preview 시작 시 processBuffer를 무시하도록 플래그 제공
    private val allowMedia3Processing = AtomicBoolean(true)

    /**
     * 앱/플레이어 라이프사이클에 맞춰 "딱 한 번" 체인을 생성.
     */
    fun ensureChainCreated(sampleRate: Int) {
        if (chainCreated.get()) return
        synchronized(this) {
            if (chainCreated.get()) return
            AudioProcessorChainController.createChain(sampleRate)
            chainCreated.set(true)
        }
    }

    /**
     * 앱 종료/플레이어 종료 등에 맞춰 "딱 한 번" 체인을 파괴.
     */
    fun destroyChainIfNeeded() {
        if (!chainCreated.get()) return
        synchronized(this) {
            if (!chainCreated.get()) return

            // 안전하게 preview를 먼저 내림
            stopPreviewIfRunning()

            AudioProcessorChainController.destroyChain()
            chainCreated.set(false)
            allowMedia3Processing.set(true)
        }
    }

    /**
     * Media3 AudioProcessorAdapter 경로에서 호출하기 위한 게이트.
     * Preview가 켜진 상태에서는 false를 반환하여 DSP + output duplication을 방지.
     */
    fun shouldProcessFromMedia3(): Boolean = chainCreated.get() && allowMedia3Processing.get()

    fun isPreviewRunning(): Boolean = previewRunning.get()

    /**
     * Settings Preview(Oboe) 시작.
     * 기본 정책: Preview 켜는 순간 Media3 DSP 처리를 차단.
     */
    fun startPreview(sampleRate: Int, framesPerCallback: Int) {
        ensureChainCreated(sampleRate)

        if (previewRunning.get()) return
        synchronized(this) {
            if (previewRunning.get()) return

            // 동시 실행 정책 적용: Preview 중에는 Media3 DSP 처리 금지
            allowMedia3Processing.set(false)

            AudioProcessorChainController.nativeStartAudioIO(sampleRate, framesPerCallback)
            previewRunning.set(true)
        }
    }

    fun stopPreviewIfRunning() {
        if (!previewRunning.get()) return
        synchronized(this) {
            if (!previewRunning.get()) return

            AudioProcessorChainController.nativeStopAudioIO()
            previewRunning.set(false)

            // Preview 종료 시 Media3 DSP 처리 재허용
            allowMedia3Processing.set(true)
        }
    }

    /** App lifecycle hook: foreground 진입 시 AudioIO에 알림. */
    fun onAppForeground() {
        runCatching { AudioProcessorChainController.nativeAudioIOOnForeground() }
    }

    /** App lifecycle hook: background 진입 시 AudioIO에 알림. */
    fun onAppBackground() {
        runCatching { AudioProcessorChainController.nativeAudioIOOnBackground() }
    }

    // Preview Test Tone controls (Settings 화면에서 사용)
    fun setTestToneEnabled(enabled: Boolean) {
        if (!chainCreated.get()) return
        AudioProcessorChainController.nativeSetTestToneEnabled(enabled)
    }

    fun setTestToneFrequency(hz: Float) {
        if (!chainCreated.get()) return
        AudioProcessorChainController.nativeSetTestToneFrequency(hz)
    }

    fun setTestToneLevel(level0to1: Float) {
        if (!chainCreated.get()) return
        AudioProcessorChainController.nativeSetTestToneLevel(level0to1)
    }
}
