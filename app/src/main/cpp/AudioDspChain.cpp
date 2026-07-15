#include "AudioDspChain.h"
#include <cmath>

static inline float dbToLinear(float db) {
    return powf(10.0f, db * 0.05f);
}

AudioDspChain::AudioDspChain(unsigned int sampleRate)
        : currentSampleRate(sampleRate),
          compThreshold(dbToLinear(-24.0f)),
          compRatio(1.0f),
          compAttackCoeff(0.01f),
          compReleaseCoeff(0.1f),
          compGainMakeup(dbToLinear(0.0f)),
          compEnv(0.0f) {
}

AudioDspChain::~AudioDspChain() = default;

void AudioDspChain::prepare(int sampleRate) {
    const unsigned int sr = static_cast<unsigned int>(sampleRate);
    if (sr == 0 || sr == currentSampleRate) return;

    currentSampleRate = sr;

    // Compressor 계수 재계산 (sampleRate 의존)
    // 기존 threshold/ratio/makeup은 유지되고, attack/release 계수만 재계산
    const float minMs = 0.1f;
    const float atkMs = (compAttackMs <= minMs) ? minMs : compAttackMs;
    const float relMs = (compReleaseMs <= minMs) ? minMs : compReleaseMs;

    const float attackSeconds = 0.001f * atkMs;
    const float releaseSeconds = 0.001f * relMs;

    compAttackCoeff = expf(-1.0f / (attackSeconds * static_cast<float>(currentSampleRate)));
    compReleaseCoeff = expf(-1.0f / (releaseSeconds * static_cast<float>(currentSampleRate)));
}

// 간단한 스테레오 Compressor 적용
void AudioDspChain::applyCompressor(float *buffer, unsigned int frames) {
    if (!buffer || frames == 0) return;

    // ratio<=1.0이면 Compressor bypass (makeup이 1이면 완전 패스스루)
    if (compRatio <= 1.0f) {
        if (fabsf(compGainMakeup - 1.0f) < 1e-6f) return;
        for (unsigned int i = 0; i < frames; ++i) {
            buffer[i * 2] *= compGainMakeup;
            buffer[i * 2 + 1] *= compGainMakeup;
        }
        return;
    }

    // 크고 작은 신호에 대해 Attack/Release로 Envelope를 업데이트하고 게인 계산
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

    // 두번째 패스에서 안정화된 Envelope를 이용해 보정 (안정성 향상)
    for (unsigned int i = 0; i < frames; ++i) {
        float l = fabsf(buffer[i * 2]);
        float r = fabsf(buffer[i * 2 + 1]);
        float in = 0.5f * (l + r);
        if (in > compEnv) compEnv = compAttackCoeff * (compEnv - in) + in;
        else compEnv = compReleaseCoeff * (compEnv - in) + in;

        float finalGain = compGainMakeup;
        if (compEnv > compThreshold) {
            float over = compEnv / compThreshold;
            finalGain *= powf(over, -(compRatio - 1.0f));
        }
        buffer[i * 2] *= finalGain;
        buffer[i * 2 + 1] *= finalGain;
    }
}

// 전체 처리: Compressor만 적용하며, 비활성 상태에서는 입력 PCM을 그대로 통과시킨다.
void AudioDspChain::process(float *bufferInterleaved, unsigned int numberOfFrames, unsigned int sampleRate) {
    if (!bufferInterleaved || numberOfFrames == 0) return;

    if (sampleRate != 0 && sampleRate != currentSampleRate) {
        prepare(static_cast<int>(sampleRate));
    }

    applyCompressor(bufferInterleaved, numberOfFrames);
}

// Compressor 파라미터 설정: threshold(dB), ratio, attack/release(ms), makeup(dB)
void AudioDspChain::setCompressor(float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupDb) {
    compThreshold = dbToLinear(thresholdDb);
    compRatio = ratio <= 1.0f ? 1.0f : ratio;

    // division by zero 또는 denormals 방지를 위한 최소 시간 설정
    const float minMs = 0.1f;
    if (attackMs <= minMs) attackMs = minMs;
    if (releaseMs <= minMs) releaseMs = minMs;

    compAttackMs = attackMs;
    compReleaseMs = releaseMs;

    const float attackSeconds = 0.001f * attackMs;
    const float releaseSeconds = 0.001f * releaseMs;

    compAttackCoeff = expf(-1.0f / (attackSeconds * static_cast<float>(currentSampleRate)));
    compReleaseCoeff = expf(-1.0f / (releaseSeconds * static_cast<float>(currentSampleRate)));

    compGainMakeup = dbToLinear(makeupDb);
}
