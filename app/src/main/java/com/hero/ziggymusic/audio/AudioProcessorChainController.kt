package com.hero.ziggymusic.audio

/**
 * JNI 브릿지 전용 컨트롤러.
 *
 * - 여기에는 "선언(external)"만 둠.
 * - 구현은 app/src/main/cpp/AudioDspChainJni.cpp 에서 JNIEXPORT로 제공.
 *
 * 주의:
 * - 함수명/시그니처가 JNI와 1:1로 매칭되어야 함.
 * - 라이브러리 이름("ziggymusic_audio_dsp")은 CMake의 add_library 이름과 일치해야 함.
 */
object AudioProcessorChainController {

    init {
        System.loadLibrary("ziggymusic_audio_dsp")
    }

    external fun createChain(sampleRate: Int)
    external fun destroyChain()

    external fun setEQBand(bandIndex: Int, gainDb: Float)
    external fun setCompressor(
        thresholdDb: Float,
        ratio: Float,
        attackMs: Float,
        releaseMs: Float,
        makeupDb: Float
    )

    external fun setReverb(enabled: Boolean, wet: Float)

    // In-app spatial (Superpowered DSP 내부 처리)
    external fun setSpatialEnabled(enabled: Boolean)
    external fun setSpatialPosition(azimuthDeg: Float, elevationDeg: Float, distanceMeters: Float)

    // Head tracking (Yaw in degrees)
    external fun setHeadTrackingEnabled(enabled: Boolean)
    external fun setHeadTrackingYaw(yawDeg: Float)

    /**
     * Media3 AudioProcessorAdapter 에서 DIRECT ByteBuffer의 주소(ptr)를 얻어 전달합니다.
     * bufferPtr은 float interleaved stereo(LRLR...)를 가리키는 포인터여야 합니다.
     */
    external fun processBuffer(bufferPtr: Long, frames: Int, sampleRate: Int)

    // Oboe Preview I/O (Settings에서 테스트 톤 + DSP 체감용)
    external fun nativeStartAudioIO(sampleRate: Int, framesPerCallback: Int)
    external fun nativeStopAudioIO()
    external fun nativeAudioIOOnForeground()
    external fun nativeAudioIOOnBackground()

    external fun nativeSetTestToneEnabled(enabled: Boolean)
    external fun nativeSetTestToneFrequency(hz: Float)
    external fun nativeSetTestToneLevel(level0to1: Float)

    // Media3 → (float PCM) → Native preview ringbuffer enqueue
    external fun nativeEnqueuePreviewPcm(bufferPtr: Long, frames: Int, sampleRate: Int)
}
