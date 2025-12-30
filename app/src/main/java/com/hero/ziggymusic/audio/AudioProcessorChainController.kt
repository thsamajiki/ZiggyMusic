package com.hero.ziggymusic.audio

import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioProcessorChainController {
    private const val TAG = "AudioProcessorChainController"

    init { System.loadLibrary("ziggymusic_audio_dsp") } // CMake에서 만든 라이브러리 이름과 일치시킬 것

    external fun createChain(sampleRate: Int)
    external fun destroyChain()
    external fun setEQBand(bandIndex: Int, gainDb: Float)
    external fun setCompressor(thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float, makeupDb: Float)
    external fun setReverb(enabled: Boolean, wet: Float)
    external fun processBuffer(bufferPtr: Long, frames: Int, sampleRate: Int)

    // Helper: try a list of candidate method names (no args)
    private fun tryCallNoArgs(vararg candidates: String): Boolean {
        for (name in candidates) {
            try {
                val method = this::class.java.getMethod(name)
                method.invoke(this)
                Log.d(TAG, "Called method: $name")
                return true
            } catch (ignored: NoSuchMethodException) { /* try next */ }
            catch (e: IllegalAccessException) { Log.w(TAG, "access denied for $name", e) ; return true }
            catch (e: InvocationTargetException) { Log.w(TAG, "invocation target for $name", e) ; return true }
        }
        return false
    }

    // Helper: try a list of candidate method names with (Int, Int) args
    private fun tryCallIntInt(vararg candidates: String, a: Int, b: Int): Boolean {
        for (name in candidates) {
            try {
                val method = this::class.java.getMethod(name, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                method.invoke(this, a, b)
                Log.d(TAG, "Called method: $name")
                return true
            } catch (ignored: NoSuchMethodException) { /* try next */ }
            catch (e: IllegalAccessException) { Log.w(TAG, "access denied for $name", e) ; return true }
            catch (e: InvocationTargetException) { Log.w(TAG, "invocation target for $name", e) ; return true }
        }
        return false
    }

    // Public API requested by SettingFragment — direct JNI calls only.
// Keep the API contract explicit and fail fast if the native symbol is missing.
    fun nativeStartAudioIO(sampleRate: Int, bufferSize: Int) {
        try {
            createChain(sampleRate)
            Log.d(TAG, "createChain(sampleRate=$sampleRate)")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native symbol missing: createChain()", e)
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start audio IO (= createChain)", t)
            throw t
        }
    }

    fun nativeAudioIOOnForeground() {
        // No native entrypoint defined for foreground transition.
        Log.d(TAG, "nativeAudioIOOnForeground: no-op")
    }

    fun nativeAudioIOOnBackground() {
        // No native entrypoint defined for background transition.
        Log.d(TAG, "nativeAudioIOOnBackground: no-op")
    }

    fun nativeStopAudioIO() {
        try {
            destroyChain()
            Log.d(TAG, "destroyChain()")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native symbol missing: destroyChain()", e)
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to stop audio IO (= destroyChain)", t)
            throw t
        }
    }

    // Helper: accept a FloatArray (interleaved stereo) and call native via a direct ByteBuffer.
    // Minimal, safe, and efficient: allocateDirect once per call and fill then call JNI.
    fun processFloatArrayInterleaved(array: FloatArray, frames: Int, sampleRate: Int) {
        val expectedFloats = frames * 2
        if (array.size < expectedFloats) {
            Log.w(TAG, "processFloatArrayInterleaved: array too small (need $expectedFloats floats)")
            return
        }

        val byteBuffer = ByteBuffer
            .allocateDirect(expectedFloats * 4)
            .order(ByteOrder.nativeOrder())

        byteBuffer.asFloatBuffer().put(array, 0, expectedFloats)

        // ByteBuffer를 그대로 넘기지 말고, 주소(Long)로 변환해서 넘김
        val bufferPtr = toDirectNativePtr(byteBuffer)
        processBuffer(bufferPtr, frames, sampleRate)
    }

    // Helper: accept any ByteBuffer; if it's direct + native order and large enough, call native directly,
    // otherwise create a direct buffer and copy contents (minimal fallback).
    fun processByteBufferInterleaved(buffer: ByteBuffer, frames: Int, sampleRate: Int) {
        val expectedBytes = frames * 2 * 4
        val src = buffer.duplicate()
        src.rewind()

        val direct: ByteBuffer =
            if (src.isDirect && src.order() == ByteOrder.nativeOrder() && src.capacity() >= expectedBytes) {
                src
            } else {
                val d = ByteBuffer.allocateDirect(expectedBytes).order(ByteOrder.nativeOrder())
                // copy up to expectedBytes (if source smaller, fill zeros implicitly)
                val toCopy = minOf(src.remaining(), expectedBytes)
                if (toCopy > 0) {
                    val tmp = ByteArray(toCopy)
                    src.get(tmp)
                    d.put(tmp)
                }
                d.rewind()
                d
            }

        // ByteBuffer -> Long(pointer)
        val bufferPtr = toDirectNativePtr(direct)
        processBuffer(bufferPtr, frames, sampleRate)
    }

    // Helper: ByteBuffer -> Long(bufferPtr) 변환
    private fun toDirectNativePtr(directBuffer: ByteBuffer): Long {
        // BufferAddressHelper는 이미 audio-engine을 load 하도록 되어 있으므로 그대로 사용
        val ptr = BufferAddressHelper.getDirectBufferAddress(directBuffer)
        if (ptr == 0L) {
            throw IllegalStateException("Direct buffer address is 0. Buffer must be a DIRECT ByteBuffer.")
        }
        return ptr
    }
}
