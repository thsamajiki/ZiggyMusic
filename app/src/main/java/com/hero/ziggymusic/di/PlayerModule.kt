package com.hero.ziggymusic.di

import android.content.Context
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.hero.ziggymusic.audio.AudioProcessorAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        trackSelector: DefaultTrackSelector,
        audioAttributes: AudioAttributes,
        audioProcessorAdapter: AudioProcessorAdapter,
    ): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    // enableFloatOutput 파라미터를 존중하거나, DSP 특성상 float 강제하려면 true로 고정
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(audioProcessorAdapter))
                    .build()
            }
        }

        return ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    @Provides
    @Singleton
    fun provideSampleRateProvider(
        @ApplicationContext context: Context,
    ): () -> Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val defaultSampleRate = 48000

        return {
            val raw = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            val parsed = raw?.toIntOrNull()

            when {
                parsed != null && parsed > 0 -> parsed
                else -> defaultSampleRate
            }
        }
    }

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideNativeAudioProcessor(sampleRateProvider: () -> Int): AudioProcessorAdapter =
        AudioProcessorAdapter(sampleRateProvider)

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideTrackSelector(@ApplicationContext context: Context): DefaultTrackSelector =
        DefaultTrackSelector(context)

    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }
}
