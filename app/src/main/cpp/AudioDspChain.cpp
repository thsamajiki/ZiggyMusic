#include "AudioDspChain.h"
#include <cmath>
#include <cstring>
#include <cstdlib>

using namespace Superpowered;

static float dbToLinear(float db) {
    return powf(10.0f, db * 0.05f);
}

AudioDspChain::AudioDspChain(unsigned int sampleRate)
        : currentSampleRate(sampleRate),
          numBands(5),
          reverb(nullptr),
          compThreshold(dbToLinear(-24.0f)),
          compRatio(2.0f),
          compAttackCoeff(0.01f),
          compReleaseCoeff(0.1f),
          compGainMakeup(dbToLinear(0.0f)),
          compEnv(0.0f),
          reverbEnabled(false),
          reverbWet(0.25f) {
    const float centersHz[] = {60.0f, 250.0f, 1000.0f, 4000.0f, 10000.0f};
    for (unsigned int i = 0; i < numBands; ++i) {
        auto f = std::make_unique<Superpowered::Filter>(Superpowered::Filter::Parametric, sampleRate);
        f->type = Superpowered::Filter::Parametric;
        f->frequency = centersHz[i];
        f->octave = 1.0f;
        f->decibel = 0.0f;
        eqBands.push_back(std::move(f));
    }

    reverb = std::make_unique<Superpowered::Reverb>(sampleRate);
    reverb->enabled = false;
}

AudioDspChain::~AudioDspChain() = default;

void AudioDspChain::applyEQ(float *buffer, unsigned int frames) {
    if (frames > maxFramesAllocated) {
        maxFramesAllocated = frames;
        leftBuffer.resize(maxFramesAllocated);
        rightBuffer.resize(maxFramesAllocated);
        interleavedTemp.resize(maxFramesAllocated * 2);
    }

    // de-interleave into left/right
    for (unsigned int i = 0; i < frames; ++i) {
        leftBuffer[i] = buffer[i * 2];
        rightBuffer[i] = buffer[i * 2 + 1];
    }

    // process each band — keep per-channel processing (no per-frame alloc)
    for (auto &fptr : eqBands) {
        // If Superpowered filter supports mono processing, use that; keep current API used in code
        fptr->process(leftBuffer.data(), leftBuffer.data(), frames);
        fptr->process(rightBuffer.data(), rightBuffer.data(), frames);
    }

    // re-interleave
    for (unsigned int i = 0; i < frames; ++i) {
        buffer[i * 2] = leftBuffer[i];
        buffer[i * 2 + 1] = rightBuffer[i];
    }
}

void AudioDspChain::applyCompressor(float *buffer, unsigned int frames) {
    for (unsigned int i = 0; i < frames; ++i) {
        float l = fabsf(buffer[i * 2]);
        float r = fabsf(buffer[i * 2 + 1]);
        float in = 0.5f * (l + r);

        if (in > compEnv) compEnv = compAttackCoeff * (compEnv - in) + in;
        else compEnv = compReleaseCoeff * (compEnv - in) + in;

        if (compEnv > compThreshold) {
            float over = compEnv / compThreshold;
            float gain = powf(over, -(compRatio - 1.0f));
            float finalGain = gain * compGainMakeup;
            buffer[i * 2] *= finalGain;
            buffer[i * 2 + 1] *= finalGain;
        } else {
            buffer[i * 2] *= compGainMakeup;
            buffer[i * 2 + 1] *= compGainMakeup;
        }
    }
}

void AudioDspChain::process(float *bufferInterleaved, unsigned int numberOfFrames, unsigned int sampleRate) {
    if (sampleRate != currentSampleRate) {
        currentSampleRate = sampleRate;
        if (reverb) reverb->sampleRate = sampleRate;
    }

    // EQ & compressor operate in-place and use preallocated buffers
    applyEQ(bufferInterleaved, numberOfFrames);
    applyCompressor(bufferInterleaved, numberOfFrames);

    if (reverbEnabled && reverb) {
        if (numberOfFrames > maxFramesAllocated) {
            maxFramesAllocated = numberOfFrames;
            leftBuffer.resize(maxFramesAllocated);
            rightBuffer.resize(maxFramesAllocated);
            interleavedTemp.resize(maxFramesAllocated * 2);
        }
        // copy into interleavedTemp, process, mix
        std::memcpy(interleavedTemp.data(), bufferInterleaved, sizeof(float) * numberOfFrames * 2);
        reverb->process(interleavedTemp.data(), interleavedTemp.data(), numberOfFrames);
        float wet = reverbWet;
        float dry = 1.0f - wet;
        for (unsigned int i = 0; i < numberOfFrames * 2; ++i) {
            bufferInterleaved[i] = bufferInterleaved[i] * dry + interleavedTemp[i] * wet;
        }
    }
}

void AudioDspChain::setEQBand(int bandIndex, float gainDb) {
    if (bandIndex < 0 || (unsigned)bandIndex >= numBands) return;
    eqBands[bandIndex]->decibel = gainDb;
}

void AudioDspChain::setCompressor(float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupDb) {
    compThreshold = dbToLinear(thresholdDb);
    compRatio = ratio <= 1.0f ? 1.0f : ratio;

    // Prevent division by zero / denormals: enforce a small minimum time.
    const float minMs = 0.1f;
    if (attackMs <= minMs) attackMs = minMs;
    if (releaseMs <= minMs) releaseMs = minMs;

    const float attackSeconds = 0.001f * attackMs;
    const float releaseSeconds = 0.001f * releaseMs;

    compAttackCoeff = expf(-1.0f / (attackSeconds * (float)currentSampleRate));
    compReleaseCoeff = expf(-1.0f / (releaseSeconds * (float)currentSampleRate));

    compGainMakeup = dbToLinear(makeupDb);
}

void AudioDspChain::setReverb(bool enabled, float wet) {
    reverbEnabled = enabled;
    if (reverb) reverb->enabled = enabled;
    reverbWet = wet;
}
