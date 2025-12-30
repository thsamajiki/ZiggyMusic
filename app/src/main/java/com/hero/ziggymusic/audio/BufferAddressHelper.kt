// Kotlin
package com.hero.ziggymusic.audio

import java.nio.ByteBuffer

object BufferAddressHelper {
    init {
        try {
            System.loadLibrary("ziggymusic_audio_dsp")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e(
                "BufferAddressHelper",
                "Failed to load native library 'ziggymusic_audio_dsp'. " +
                        "The application cannot use native audio DSP features.",
                e
            )
            throw IllegalStateException(
                "Native library 'ziggymusic_audio_dsp' could not be loaded",
                e
            )
        }
    }

    /**
     * 전달된 ByteBuffer가 direct이면 네이티브 포인터를 jlong으로 반환.
     * direct가 아니거나 null이면 0 반환.
     */
    @JvmStatic
    external fun getDirectBufferAddress(buffer: ByteBuffer?): Long
}
