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

        // ✅ 출력 포맷은 "입력과 동일" (중요: 16bit 입력이면 16bit로 반환해야 함)
        return inputAudioFormat
    }

    override fun isActive(): Boolean = pendingInputFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        // ✅ 이전 output이 아직 소비되지 않았다면 덮어쓰지 않기
        if (outputBuffer.hasRemaining()) return

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

        val samples = frames * channelCount

        // 1) 입력을 floatBuffer(DIRECT)에 채움 (float interleaved)
        val floatBytes = samples * 4
        ensureCapacityFloat(floatBytes)
        floatBuffer.clear()

        if (inputEncoding == C.ENCODING_PCM_FLOAT) {
            // float 입력이면 그대로 복사
            val src = inputBuffer.slice()
            src.limit(min(src.remaining(), floatBytes))
            floatBuffer.put(src)
            floatBuffer.flip()
            // 소비
            inputBuffer.position(inputBuffer.position() + frames * channelCount * 4)
        } else {
            // PCM16 -> float 변환
            val src = inputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN)
            val srcShorts: ShortBuffer = src.asShortBuffer()
            val dstFloats: FloatBuffer = floatBuffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()

            for (i in 0 until samples) {
                val s = srcShorts.get(i).toInt()
                dstFloats.put(i, s / 32768.0f)
            }

            floatBuffer.position(0)
            floatBuffer.limit(floatBytes)

            // 소비
            inputBuffer.position(inputBuffer.position() + frames * channelCount * 2)
        }

        // 2) (선택) 네이티브 DSP 적용 - 사용 중이면 호출, 아니면 주석 가능
        val ptr = BufferAddressHelper.getDirectBufferAddress(floatBuffer)
        if (ptr == 0L) {
            throw IllegalStateException("floatBuffer is not direct or address unavailable")
        }
        AudioProcessorChainController.processBuffer(ptr, frames, sampleRate)

        // 3) 출력 포맷에 맞게 outBuffer 구성
        if (inputEncoding == C.ENCODING_PCM_FLOAT) {
            // ✅ float 입력이면 float 출력
            outBuffer = floatBuffer
            outBuffer.rewind()
            outputBuffer = outBuffer
            return
        }

        // ✅ 16bit 입력이면 16bit 출력으로 변환해서 내보냄 (이게 지지직 해결 포인트)
        val outBytes = samples * 2
        ensureCapacityOut(outBytes)
        outBuffer.clear()

        val processedFloats: FloatBuffer = floatBuffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val outShorts: ShortBuffer = outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (i in 0 until samples) {
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
