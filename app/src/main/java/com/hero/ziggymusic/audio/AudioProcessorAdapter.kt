package com.hero.ziggymusic.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@UnstableApi
class AudioProcessorAdapter(
    private val sampleRateProvider: () -> Int,
) : AudioProcessor {
    private var pendingInputFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var inputEnded: Boolean = false

    private var inputEncoding: Int = C.ENCODING_INVALID
    private var channelCount: Int = 0

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER

    // 내부 float 처리 버퍼 (DIRECT)
    private var floatBuffer: ByteBuffer = EMPTY_BUFFER

    // 최종 출력 버퍼(입력 16-bit일 때는 PCM16) (DIRECT)
    private var outBuffer: ByteBuffer = EMPTY_BUFFER

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        pendingInputFormat = inputAudioFormat
        inputEncoding = inputAudioFormat.encoding
        channelCount = inputAudioFormat.channelCount

        // stereo 강제
        if (channelCount != 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        // float/16bit만 허용
        if (inputEncoding != C.ENCODING_PCM_FLOAT && inputEncoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        // 출력 포맷은 "입력과 동일" (중요: 16bit 입력이면 16bit로 반환해야 함)
        return inputAudioFormat
    }

    override fun isActive(): Boolean = pendingInputFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val inRemaining = inputBuffer.remaining()

        val frames = when (inputEncoding) {
            C.ENCODING_PCM_FLOAT -> inRemaining / (channelCount * 4)
            C.ENCODING_PCM_16BIT -> inRemaining / (channelCount * 2)
            else -> 0
        }
        if (frames <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val formatRate = pendingInputFormat.sampleRate
        val providedRate = sampleRateProvider()
        val sampleRate = if (providedRate > 0) providedRate else formatRate

        var processedFrames = frames

        // 1) 입력을 floatBuffer(DIRECT)에 채움 (float interleaved)
        val maxSamples = frames * channelCount
        val maxFloatBytes = maxSamples * 4
        ensureCapacityFloat(maxFloatBytes)
        floatBuffer.clear()

        if (inputEncoding == C.ENCODING_PCM_FLOAT) {
            // float 입력: 실제로 복사 가능한 바이트에 맞춰 processedFrames를 재계산
            val bytesPerFrame = channelCount * 4
            val bytesToCopyRaw = min(inputBuffer.remaining(), maxFloatBytes)
            processedFrames = bytesToCopyRaw / bytesPerFrame
            if (processedFrames <= 0) return

            val bytesToCopy = processedFrames * bytesPerFrame
            val src = inputBuffer.slice()
            src.limit(bytesToCopy)
            floatBuffer.put(src)

            floatBuffer.position(0)
            floatBuffer.limit(bytesToCopy)

            // 소비도 실제 복사한 만큼만
            inputBuffer.position(inputBuffer.position() + bytesToCopy)
        } else {
            // PCM16 -> float 변환
            val bytesPerFrame = channelCount * 2
            val availableBytes = inputBuffer.remaining()
            val safeFrames = min(frames, availableBytes / bytesPerFrame)
            if (safeFrames <= 0) return

            processedFrames = safeFrames
            val safeSamples = processedFrames * channelCount

            val inShorts: ShortBuffer = inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val dstFloats: FloatBuffer = floatBuffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()

            for (i in 0 until safeSamples) {
                val s = inShorts.get(i).toInt()
                dstFloats.put(i, s / 32768.0f)
            }

            val safeFloatBytes = safeSamples * 4
            floatBuffer.position(0)
            floatBuffer.limit(safeFloatBytes)

            // 소비도 safeFrames만큼만
            inputBuffer.position(inputBuffer.position() + processedFrames * bytesPerFrame)
        }

        // 2) 네이티브 DSP 적용 (반드시 processedFrames로 호출)
        val ptr = BufferAddressHelper.getDirectBufferAddress(floatBuffer)
        if (ptr == 0L) {
            outputBuffer = floatBuffer
            outputBuffer.rewind()
            return
        }

        // Gate: Preview(Oboe) 실행 중이면 Media3 경로에서 네이티브 처리를 수행하지 않음
        // (Preview 우선 정책은 PlayerAudioGraph에서 관리)
        // 2) 네이티브 DSP/라우팅 게이트
        val shouldProcess = PlayerAudioGraph.shouldProcessFromMedia3()
        val previewRunning = PlayerAudioGraph.isPreviewRunning()

        when {
            // (A) 정상 Media3 재생 + DSP 적용
            shouldProcess -> {
                AudioProcessorChainController.processBuffer(ptr, processedFrames, sampleRate)
            }

            // (B) 프리뷰(Oboe)가 "실제로 실행 중"일 때만:
            //     - 실제 PCM을 네이티브 프리뷰 링버퍼로 enqueue
            //     - Media3 출력은 무음으로 만들어 이중출력 방지
            previewRunning -> {
                // 필수 안전 가드: 프리뷰 엔진은 stereo float(LRLR)만 지원
                if (channelCount != 2 || inputEncoding != C.ENCODING_PCM_FLOAT) {
                    // 프리뷰 라우팅을 하지 말고 원본 재생(패스스루)
                    // (이 상태에서 Media3를 무음 처리하면 다시 무음이 될 수 있음)
                    // => 아무 처리 없이 아래 else로 흐르게 하려면 return하지 말고 when 분기 자체를 타지 않게 해야 함
                    // 가장 안전: 여기서 그냥 "원본 그대로" 반환
                    outputBuffer = floatBuffer
                    outputBuffer.rewind()
                    return
                }

                AudioProcessorChainController.nativeEnqueuePreviewPcm(ptr, processedFrames, sampleRate)

                // Media3 출력 무음 처리(이중 출력 방지)
                val fb = floatBuffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                val samples = processedFrames * channelCount
                for (i in 0 until samples) fb.put(i, 0f)

                outBuffer = floatBuffer
                outBuffer.rewind()
                outputBuffer = outBuffer
                return
            }

            // (C) DSP 체인이 없거나, 프리뷰도 안 켜진 상태면:
            //     - 절대 무음 처리하지 말고 "원본 그대로" 재생되게 둔다 (pass-through)
            else -> {
                // 아무 것도 하지 않음 (floatBuffer 그대로 사용)
            }
        }


        // 3) 출력 포맷에 맞게 outBuffer 구성
        if (inputEncoding == C.ENCODING_PCM_FLOAT) {
            // float 입력이면 float 출력
            outBuffer = floatBuffer
            outBuffer.rewind()
            outputBuffer = outBuffer
            return
        }

        // 16bit 입력이면 16bit 출력으로 변환해서 내보냄
        val processedSamples = processedFrames * channelCount
        val outBytes = processedSamples * 2
        ensureCapacityOut(outBytes)
        outBuffer.clear()

        if (processedSamples > 0) {
            // floatBuffer의 데이터는 interleaved float(LRLR...)
            val fb: FloatBuffer = floatBuffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            // 안전하게 범위만 읽기
            val limit = processedSamples.coerceAtMost(fb.limit())
            var sumSq = 0.0
            for (i in 0 until limit) {
                val v = fb.get(i).toDouble()
                sumSq += v * v
            }
            val rms = if (limit > 0) sqrt(sumSq / limit) else 0.0
        }

        val processedFloats: FloatBuffer = floatBuffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val outShorts: ShortBuffer = outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (i in 0 until processedSamples) {
            val f = processedFloats.get(i)
            val clamped = min(0.9999695f, max(-1.0f, f))
            val pcm = (clamped * 32767.0f).toInt()
            outShorts.put(i, pcm.toShort())
        }

        outBuffer.position(0)
        outBuffer.limit(outBytes)

        outputBuffer = outBuffer
        outputBuffer.rewind()
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        pendingInputFormat = AudioProcessor.AudioFormat.NOT_SET
        inputEncoding = C.ENCODING_INVALID
        channelCount = 0
        floatBuffer = EMPTY_BUFFER
        outBuffer = EMPTY_BUFFER
    }

    private fun ensureCapacityFloat(requiredBytes: Int) {
        if (floatBuffer.isDirect && floatBuffer.capacity() >= requiredBytes) return
        floatBuffer = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun ensureCapacityOut(requiredBytes: Int) {
        if (outBuffer.isDirect && outBuffer.capacity() >= requiredBytes) return
        outBuffer = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.LITTLE_ENDIAN)
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer =
            ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN)
    }
}
