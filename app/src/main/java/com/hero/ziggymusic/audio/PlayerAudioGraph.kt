package com.hero.ziggymusic.audio

import android.util.Log
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
    private const val TAG = "PlayerAudioGraph"
    private val chainCreated = AtomicBoolean(false)
    private val previewRunning = AtomicBoolean(false)

    // policy: Preview를 켜면 Media3 DSP 경로는 사용하지 않도록 강제(또는 Preview 시작을 거부)
    // 지금은 "Preview 우선" 정책: preview 시작 시 processBuffer를 무시하도록 플래그 제공
    private val allowMedia3Processing = AtomicBoolean(true)
    private val headTrackingRuntimeEnabled = AtomicBoolean(false)
    @Volatile private var headTracker: HeadTracker? = null

    // App state
    private val isInForeground = AtomicBoolean(true)

    // 제품 정책: 백그라운드에서도 헤드트래킹 유지 여부
    private val keepHeadTrackingInBackground = AtomicBoolean(false)

    // 실제로 “백그라운드에서도 센서가 의미가 있는 상태(재생 중 등)”인지
    private val isPlaybackActive = AtomicBoolean(false)

    // Feature toggles (Settings에서 변경될 수 있음)
    private val spatialEnabled = AtomicBoolean(false)
    private val headTrackingEnabled = AtomicBoolean(false)

    /**
     * Settings\->Graph로 HeadTracker 인스턴스를 주입.
     * Fragment가 recreate 되어도 최신 인스턴스로 덮어씀.
     */
    fun attachHeadTracker(tracker: HeadTracker) {
        headTracker = tracker
        // 현재 정책 상태에 맞춰 즉시 적용
        applyHeadTrackingSensorPolicy("attachHeadTracker")
    }

    fun detachHeadTracker(tracker: HeadTracker) {
        if (headTracker === tracker) {
            // 안전하게 중지 후 해제
            runCatching { tracker.stop() }
            headTracker = null
        }
    }

    /**
     * 설정값 변경/재생 시작/라우팅 변경 등에서 호출.
     * spatialEnabled=false면 head tracking도 강제 off 처리.
     */
    fun setHeadTrackingActive(spatialEnabled: Boolean, headTrackingEnabled: Boolean) {
        val shouldRun = spatialEnabled && headTrackingEnabled

        // 네이티브 토글도 함께 맞춘다 (DSP에서 yaw를 적용할지 여부)
        AudioProcessorChainController.setHeadTrackingEnabled(shouldRun)

        val tracker = headTracker ?: return
        if (shouldRun) {
            headTrackingRuntimeEnabled.set(true)
            tracker.start()
        } else {
            headTrackingRuntimeEnabled.set(false)
            tracker.stop()
        }
    }

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

            // 체인 종료 전 head tracking도 내림
            headTracker?.stop()
            headTrackingRuntimeEnabled.set(false)

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

    /**
     * App lifecycle hook: foreground 진입.
     * - AudioIO는 기존대로 알림
     * - Head tracking 센서는 정책에 따라 재시작
     */
    fun onAppForeground() {
        isInForeground.set(true)

        runCatching { AudioProcessorChainController.nativeAudioIOOnForeground() }

        // 전면 복귀 시 고주기 모드로 재전환
        applyHeadTrackingSensorPolicy("onAppForeground")
    }

    /**
     * App lifecycle hook: background 진입.
     * - AudioIO는 기존대로 알림
     * - Head tracking 센서는 즉시 stop (배터리/발열/정책 리스크 방지)
     *
     * 정책:
     * - 백그라운드에서도 "enabled"는 유지하지만, 센서 입력은 끊는다.
     * - 백그라운드에서 센서 유지가 제품 요건이면, 별도 ForegroundService 정책으로 분리해야 함.
     */
    fun onAppBackground() {
        isInForeground.set(false)

        runCatching { AudioProcessorChainController.nativeAudioIOOnBackground() }

        // 백그라운드 정책에 따라 stop 또는 저전력 모드로 전환
        applyHeadTrackingSensorPolicy("onAppBackground")
    }

    fun setKeepHeadTrackingInBackground(enabled: Boolean) {
        keepHeadTrackingInBackground.set(enabled)
        applyHeadTrackingSensorPolicy("setKeepHeadTrackingInBackground")
    }

    fun onPlaybackActiveChanged(active: Boolean) {
        isPlaybackActive.set(active)
        applyHeadTrackingSensorPolicy("onPlaybackActiveChanged")
    }

    private fun applyHeadTrackingSensorPolicy(reason: String) {
        val tracker = headTracker ?: return

        val spatialOn = spatialEnabled.get()
        val headOn = headTrackingEnabled.get()

        // 센서가 의미 있는 조건:
        // - XR(Spatial) + HeadTracking이 켜져 있고
        // - (전면) 이거나
        // - (백그라운드 정책 ON && 실제 재생 중) 일 때만
        val shouldRunInBackground = keepHeadTrackingInBackground.get() && isPlaybackActive.get()
        val shouldRunSensor = spatialOn && headOn && (isInForeground.get() || shouldRunInBackground)

        if (!shouldRunSensor) {
            runCatching { tracker.stop() }
                .onFailure { Log.w(TAG, "HeadTracker stop failed($reason): ${it.javaClass.simpleName}") }
            return
        }

        // 모드 결정: 전면=고주기 / 백그라운드=저전력+batching
        val mode = if (isInForeground.get()) HeadTracker.Mode.FOREGROUND else HeadTracker.Mode.BACKGROUND_LOW_POWER
        tracker.start(mode)

        runCatching { tracker.start(mode) }
            .onFailure { Log.w(TAG, "HeadTracker start failed($reason): ${it.javaClass.simpleName}") }
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
