#include "AudioDspChain.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <cstdlib>

using namespace Superpowered;

static inline float dbToLinear(float db) {
    return powf(10.0f, db * 0.05f);
}

static inline float clampf(float v, float lo, float hi) {
    return std::max(lo, std::min(v, hi));
}

AudioDspChain::AudioDspChain(unsigned int sampleRate)
        : currentSampleRate(sampleRate),
          numBands(5),
          reverb(nullptr),
          compThreshold(dbToLinear(-24.0f)),
          compRatio(1.0f),
          compAttackCoeff(0.01f),
          compReleaseCoeff(0.1f),
          compGainMakeup(dbToLinear(0.0f)),
          compEnv(0.0f),
          reverbEnabled(false),
          reverbWet(0.25f) {

    // 1. EQ 초기화
    const float centersHz[] = {60.0f, 250.0f, 1000.0f, 4000.0f, 10000.0f};
    for (unsigned int i = 0; i < numBands; ++i) {
        auto f = std::make_unique<Superpowered::Filter>(Superpowered::Filter::Parametric, sampleRate);
        f->type = Superpowered::Filter::Parametric;
        f->frequency = centersHz[i];
        f->octave = 1.0f;
        f->decibel = 0.0f;
        eqBands.push_back(std::move(f));
    }

    // 2. Reverb 초기화
    reverb = std::make_unique<Superpowered::Reverb>(sampleRate);
    reverb->enabled = false;

    // 3. Spatializer 초기화 (HRTF based)
    // 왼쪽/오른쪽 채널을 독립적인 음원(Sound Source)으로 취급
    spatializerL = std::make_unique<Superpowered::Spatializer>(sampleRate);
    spatializerR = std::make_unique<Superpowered::Spatializer>(sampleRate);

    // 초기 설정: 1미터 앞, 적절한 잔향
    spatializerL->inputVolume = 0.5f; // Summing시 클리핑 방지
    spatializerR->inputVolume = 0.5f;
    // SuperpoweredSpatializer는 내부적으로 HRTF와 초기 반사음을 처리함

    // ITD(Interaural Time Difference) 근사 지연 버퍼 설정
    itdMaxDelaySamples = std::max<uint32_t>(1, (uint32_t)std::ceil(sampleRate * 0.0006f));
    itdDelayL.assign(itdMaxDelaySamples + 1, 0.0f);
    itdDelayR.assign(itdMaxDelaySamples + 1, 0.0f);
    itdWriteIndex = 0;
}

AudioDspChain::~AudioDspChain() = default;

void AudioDspChain::prepare(int sampleRate) {
    const unsigned int sr = (unsigned int)sampleRate;
    if (sr == 0 || sr == currentSampleRate) return;

    currentSampleRate = sr;

    // ----- Reverb 재생성/갱신 -----
    const bool reverbOn = reverbEnabled.load(std::memory_order_relaxed);
    if (!reverb) {
        reverb = std::make_unique<Superpowered::Reverb>(sr);
    } else {
        // Superpowered Reverb는 sampleRate를 public으로 가지고 있으므로 갱신
        reverb->sampleRate = sr;
    }
    reverb->enabled = reverbOn;

    // ----- EQ 재생성 (Filter는 생성 시 sampleRate가 들어가므로 안전하게 재생성) -----
    const float centersHz[] = {60.0f, 250.0f, 1000.0f, 4000.0f, 10000.0f};

    eqBands.clear();
    eqBands.reserve(numBands);

    for (unsigned int i = 0; i < numBands; ++i) {
        auto f = std::make_unique<Superpowered::Filter>(Superpowered::Filter::Parametric, sr);
        f->type = Superpowered::Filter::Parametric;
        f->frequency = centersHz[i];
        f->octave = 1.0f;

        const float g = eqGainsDb[i].load(std::memory_order_relaxed);
        f->decibel = g;

        eqBands.push_back(std::move(f));
    }

    // ----- Spatializer 재생성 (생성 시 sampleRate 필요) -----
    spatializerL = std::make_unique<Superpowered::Spatializer>(sr);
    spatializerR = std::make_unique<Superpowered::Spatializer>(sr);
    spatializerL->inputVolume = 0.5f;
    spatializerR->inputVolume = 0.5f;

    // ----- ITD 버퍼 재설정 (sampleRate에 의존) -----
    itdMaxDelaySamples = std::max<uint32_t>(1, (uint32_t)std::ceil(sr * 0.0006f));
    itdDelayL.assign(itdMaxDelaySamples + 1, 0.0f);
    itdDelayR.assign(itdMaxDelaySamples + 1, 0.0f);
    itdWriteIndex = 0;

    // ----- Compressor 계수 재계산 (sampleRate 의존) -----
    // 기존 threshold/ratio/makeup은 유지되고, attack/release 계수만 재계산
    const float minMs = 0.1f;
    const float atkMs = (compAttackMs <= minMs) ? minMs : compAttackMs;
    const float relMs = (compReleaseMs <= minMs) ? minMs : compReleaseMs;

    const float attackSeconds  = 0.001f * atkMs;
    const float releaseSeconds = 0.001f * relMs;

    compAttackCoeff  = expf(-1.0f / (attackSeconds * (float)currentSampleRate));
    compReleaseCoeff = expf(-1.0f / (releaseSeconds * (float)currentSampleRate));
}

// 내부 버퍼 용량 보장: 필요한 프레임 수에 맞게 리사이즈
void AudioDspChain::ensureCapacity(uint32_t frames) {
    if (frames <= maxFramesAllocated) return;
    maxFramesAllocated = frames;
    leftBuffer.resize(maxFramesAllocated);
    rightBuffer.resize(maxFramesAllocated);
    interleavedTemp.resize(maxFramesAllocated * 2);
    spatialOutputTemp.resize(maxFramesAllocated * 2);
}

// EQ 적용 (In-place): interleave된 스테레오 버퍼를 분리 후 각각 필터 처리
void AudioDspChain::applyEQ(float *buffer, unsigned int frames) {
    if (frames > maxFramesAllocated) {
        maxFramesAllocated = frames;
        leftBuffer.resize(maxFramesAllocated);
        rightBuffer.resize(maxFramesAllocated);
        interleavedTemp.resize(maxFramesAllocated * 2);
    }

    ensureCapacity(frames); // ensureCapacity 호출 보장, 안전하게 버퍼 확보

    // interleave 해체(de-interleave) : left/right 분리
    for (unsigned int i = 0; i < frames; ++i) {
        leftBuffer[i] = buffer[i * 2];
        rightBuffer[i] = buffer[i * 2 + 1];
    }

    // UI thread에서 설정한 gain(dB)을 atomic으로 읽어 filter 파라미터에 반영
    for (unsigned int band = 0; band < numBands; ++band) {
        const float gainDb = eqGainsDb[band].load(std::memory_order_relaxed);
        auto &f = eqBands[band];
        f->decibel = gainDb;
        f->process(leftBuffer.data(), leftBuffer.data(), frames);
        f->process(rightBuffer.data(), rightBuffer.data(), frames);
    }

    // 다시 interleave하여 원본 버퍼에 저장
    for (unsigned int i = 0; i < frames; ++i) {
        buffer[i * 2] = leftBuffer[i];
        buffer[i * 2 + 1] = rightBuffer[i];
    }
}

// 간단한 스테레오 Compressor 적용
void AudioDspChain::applyCompressor(float *buffer, unsigned int frames) {
    if (!buffer || frames == 0) return;

    // ratio<=1.0이면 Compressor bypass (makeup이 1이면 완전 패스스루)
    if (compRatio <= 1.0f) {
        if (fabsf(compGainMakeup - 1.0f) < 1e-6f) return;
        for (unsigned int i = 0; i < frames; ++i) {
            buffer[i * 2]     *= compGainMakeup;
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

// Reverb 적용 (포스트-프로세스, wet/dry 믹스)
void AudioDspChain::applyReverb(float *buffer, uint32_t frames) {
    if (!reverb) return;

    const bool reverbOn = reverbEnabled.load(std::memory_order_relaxed);
    if (!reverbOn) return;

    ensureCapacity(frames);

    // wet buffer를 임시 버퍼(interleavedTemp)에 만들고, dry/wet mix.
    std::memcpy(interleavedTemp.data(), buffer, sizeof(float) * frames * 2);

    // 입력: buffer, 출력: interleavedTemp (wet)
    reverb->process(buffer, interleavedTemp.data(), frames);

    const float wet = clampf(reverbWet.load(std::memory_order_relaxed), 0.0f, 1.0f);
    const float dry = 1.0f - wet;

    for (uint32_t i = 0; i < frames * 2; ++i) {
        buffer[i] = buffer[i] * dry + interleavedTemp[i] * wet;
    }
}

// HRTF 기반 공간 음향 처리
void AudioDspChain::applySpatial(float *inputBuffer, float *outputBuffer, unsigned int frames) {
    const bool spatialOn = spatialEnabled.load(std::memory_order_relaxed);
    if (!spatialOn) {
        // Spatializer가 꺼져 있으면 그대로 복사
        memcpy(outputBuffer, inputBuffer, frames * 2 * sizeof(float));
        return;
    }

    // Head Tracking이 켜져있다면 yaw를 반영
    const bool htOn = headTrackingEnabled.load(std::memory_order_relaxed);
    const float currentYaw = htOn ? headYawDeg.load(std::memory_order_relaxed) : 0.0f;

    // UI에서 설정한 공간 좌표(기본 방향/고도/거리)를 atomic하게 읽음
    const float globalAzimuthDeg   = spkAzimuth.load(std::memory_order_relaxed);
    const float globalElevationDeg = spkElevation.load(std::memory_order_relaxed);
    const float distanceMeters     = spkDistance.load(std::memory_order_relaxed);

    // 좌/우 소스 기본 각도
    float baseAzimuthL = -30.0f + globalAzimuthDeg;
    float baseAzimuthR = +30.0f + globalAzimuthDeg;

    // Head 회전에 따른 상대 각도 계산
    float relAzimuthL = baseAzimuthL - currentYaw;
    float relAzimuthR = baseAzimuthR - currentYaw;

    // 각도 정규화 함수 (-180..180)
    auto normalizeAngle = [](float ang) {
        while (ang > 180.0f) ang -= 360.0f;
        while (ang < -180.0f) ang += 360.0f;
        return ang;
    };

    // Spatializer 파라미터 설정
    spatializerL->azimuth = normalizeAngle(relAzimuthL);
    spatializerL->elevation = globalElevationDeg;
    spatializerL->reverbmix = 0.1f;

    spatializerR->azimuth = normalizeAngle(relAzimuthR);
    spatializerR->elevation = globalElevationDeg;
    spatializerR->reverbmix = 0.1f;

    // 거리 기반 간단 감쇠 적용 (0.2m 이하 보정, d^2 기준)
    const float d = std::max(0.2f, distanceMeters);
    const float attenuation = std::min(1.0f, 1.0f / (d * d));
    const float baseInputVol = 0.5f;
    const float inputVol = baseInputVol * attenuation;

    spatializerL->inputVolume = inputVol;
    spatializerR->inputVolume = inputVol;

    // 입력을 분리
    for (unsigned int i = 0; i < frames; ++i) {
        leftBuffer[i] = inputBuffer[i * 2];
        rightBuffer[i] = inputBuffer[i * 2 + 1];
    }

    // 출력 초기화 후 좌/우 Spatializer 처리하여 합산
    memset(outputBuffer, 0, frames * 2 * sizeof(float));
    spatializerL->process(leftBuffer.data(), nullptr, outputBuffer, outputBuffer, frames, true);
    spatializerR->process(rightBuffer.data(), nullptr, outputBuffer, outputBuffer, frames, true);
}

// 전체 처리: EQ -> Compressor (In-place) -> Spatializer (Out-of-place) -> Reverb
void AudioDspChain::process(float *bufferInterleaved, unsigned int numberOfFrames, unsigned int sampleRate) {
    if (!bufferInterleaved || numberOfFrames == 0) return;

    ensureCapacity(numberOfFrames);

    // sampleRate 변경이 있으면 관련 모듈에 반영
    if (sampleRate != currentSampleRate) {
        currentSampleRate = sampleRate;
        if (reverb) reverb->sampleRate = sampleRate;
    }

    // 1. EQ 및 Compressor (톤/다이내믹)
    applyEQ(bufferInterleaved, numberOfFrames);
    applyCompressor(bufferInterleaved, numberOfFrames);

    // 2. Spatial Audio (HRTF) : Out-of-place (temp buffer -> output)
    // EQ/Comp 처리된 bufferInterleaved를 입력으로 사용하여 spatialOutputTemp에 출력
    // 만약 Spatial이 꺼져있으면 spatialOutputTemp에 그대로 복사됨
    applySpatial(bufferInterleaved, spatialOutputTemp.data(), numberOfFrames);

    // 결과물을 원본 버퍼에 복사 (interleaved)
    memcpy(bufferInterleaved, spatialOutputTemp.data(), numberOfFrames * 2 * sizeof(float));

    // 3. 전역 Reverb (Optional Post-FX)
    applyReverb(bufferInterleaved, numberOfFrames);
}

// UI에서 EQ 밴드 값을 설정 (원자적 저장)
void AudioDspChain::setEQBand(int bandIndex, float gainDb) {
    if (bandIndex < 0 || (unsigned)bandIndex >= numBands) return;
    eqGainsDb[bandIndex].store(gainDb, std::memory_order_relaxed);
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

    compAttackCoeff = expf(-1.0f / (attackSeconds * (float)currentSampleRate));
    compReleaseCoeff = expf(-1.0f / (releaseSeconds * (float)currentSampleRate));

    compGainMakeup = dbToLinear(makeupDb);
}

// Reverb 활성화/비활성화 및 wet 값 설정
void AudioDspChain::setReverb(bool enabled, float wet) {
    reverbEnabled.store(enabled, std::memory_order_relaxed);
    if (reverb) reverb->enabled = enabled;
    reverbWet.store(clampf(wet, 0.0f, 1.0f), std::memory_order_relaxed);
}

// Spatial 오디오 전체 토글
void AudioDspChain::setSpatialEnabled(bool enabled) {
    spatialEnabled.store(enabled, std::memory_order_relaxed);
}

// 소스의 전역 위치 설정: 방위, 고도, 거리 (원자적 저장)
void AudioDspChain::setSpatialPosition(float azimuthDeg, float elevationDeg, float distanceMeters) {
    spkAzimuth.store(azimuthDeg, std::memory_order_relaxed);
    spkElevation.store(elevationDeg, std::memory_order_relaxed);
    spkDistance.store(distanceMeters, std::memory_order_relaxed);
}

// Head Tracking yaw 설정 (입력 범위를 -180..180으로 클램프)
void AudioDspChain::setHeadTrackingYaw(float yawDeg) {
    // 센서 입력: -180 ~ 180
    const float clamped = clampf(yawDeg, -180.0f, 180.0f);
    headYawDeg.store(clamped, std::memory_order_relaxed);
}

// Head Tracking 사용 여부 설정
void AudioDspChain::setHeadTrackingEnabled(bool enabled) {
    headTrackingEnabled.store(enabled, std::memory_order_relaxed);
}
