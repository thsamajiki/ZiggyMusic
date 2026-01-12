#pragma once

#include "Superpowered.h"
#include "SuperpoweredFilter.h"
#include "SuperpoweredReverb.h"
#include "SuperpoweredSpatializer.h"
#include <vector>
#include <memory>
#include <mutex>
#include <atomic>
#include <cstdint>

// 오디오 DSP 체인: EQ, Compressor, Reverb, Spatializer 등을 담당
class AudioDspChain {
public:
    // 생성자: sampleRate를 받아 내부 모듈 초기화
    explicit AudioDspChain(unsigned int sampleRate);
    ~AudioDspChain();

    // sampleRate 변경 등 준비 작업 (선언만, 필요시 구현)
    void prepare(int sampleRate);

    // Interleave된 스테레오 버퍼를 처리
    // bufferInterleaved: L,R,L,R,... 포맷의 float 버퍼
    // numberOfFrames: 스테레오 프레임 수
    // sampleRate: 현재 처리할 샘플레이트
    void process(float *bufferInterleaved, unsigned int numberOfFrames, unsigned int sampleRate);

    // EQ 밴드 설정 (bandIndex: 0..numBands-1, gainDb: 데시벨)
    void setEQBand(int bandIndex, float gainDb); // bandIndex: 0..(numBands-1)

    // Compressor 파라미터 설정
    // thresholdDb: 입력 임계 데시벨, ratio: 압축 비율, attackMs/releaseMs: 시간(ms), makeupDb: 메이크업 게인
    void setCompressor(float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupDb);

    // Reverb 설정: 활성화 및 wet 비율(0..1)
    void setReverb(bool enabled, float wet);

    // Spatial (공간 오디오) 제어
    void setSpatialEnabled(bool enabled);
    void setSpatialPosition(float azimuthDeg, float elevationDeg, float distanceMeters);
    void setHeadTrackingYaw(float headYawDeg);
    void setHeadTrackingEnabled(bool enabled);

private:
    unsigned int currentSampleRate; // 현재 sampleRate
    unsigned int numBands; // EQ 밴드 수

    // 파라메트릭 EQ 밴드들 (각 밴드는 Superpowered::Filter)
    std::vector<std::unique_ptr<Superpowered::Filter>> eqBands;
    // 전역 Reverb (포스트 프로세싱용)
    std::unique_ptr<Superpowered::Reverb> reverb;
    // Spatializer: 왼쪽/오른쪽 별도 처리하여 HRTF 기반 출력 생성
    std::unique_ptr<Superpowered::Spatializer> spatializerL;
    std::unique_ptr<Superpowered::Spatializer> spatializerR;

    // 콜백마다 재할당을 피하기 위한 미리 할당된 버퍼들
    unsigned int maxFramesAllocated{};
    std::vector<float> leftBuffer;              // 좌 채널 임시
    std::vector<float> rightBuffer;             // 우 채널 임시
    std::vector<float> spatialOutputTemp;       // Spatializer 처리 결과 누적용 버퍼
    std::vector<float> interleavedTemp;         // Reverb 및 믹싱용 임시 Interleave 버퍼

    // Compressor 상태 변수들
    float compThreshold;            // 선형 임계값
    float compRatio;                // 압축 비율
    float compAttackCoeff;          // Attack 계수 (지수 필터)
    float compReleaseCoeff;         // Release 계수
    float compGainMakeup;           // Makeup GainM (선형)
    float compEnv;                  // 현재 Envelope 값
    float compAttackMs = 10.0f;     // 마지막으로 설정된 attack(ms) 저장
    float compReleaseMs = 100.0f;   // 마지막으로 설정된 release(ms) 저장

    std::atomic<bool> reverbEnabled{false}; // Reverb 활성화 플래그
    std::atomic<float> reverbWet{0.0f};     // Reverb wet 비율

    // Spatial 상태
    std::atomic<bool> spatialEnabled{false};      // Spatializer 활성화
    std::atomic<bool> headTrackingEnabled{false};// Head Tracking 활성화

    // 공간 좌표 (원자적으로 접근, UI/스레드 안전)
    std::atomic<float> spkAzimuth{0.0f};    // 소스 방위(도)
    std::atomic<float> spkElevation{0.0f};  // 소스 고도(도)
    std::atomic<float> spkDistance{1.0f};   // 소스 거리(미터), 기본 1m
    std::atomic<float> headYawDeg{0.0f};    // Head 회전 yaw (도)

    // EQ 게인은 각 밴드별로 atomic 저장 (데시벨)
    std::atomic<float> eqGainsDb[5] = { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f };

    // 간단한 ITD(두 귀 시간차) 지연 버퍼 (반대 귀 지연 근사)
    uint32_t itdMaxDelaySamples = 0; // 최대 지연 샘플 수
    uint32_t itdWriteIndex = 0;      // 쓰기 인덱스
    std::vector<float> itdDelayL;    // 왼쪽 지연 버퍼
    std::vector<float> itdDelayR;    // 오른쪽 지연 버퍼

private:
    // 내부 유틸리티
    void ensureCapacity(uint32_t frames); // 내부 버퍼 용량 보장
    void applyEQ(float *buffer, unsigned int frames); // EQ 적용 (인플레이스)
    void applyCompressor(float *buffer, unsigned int frames); // Compressor 적용
    void applyReverb(float *buffer, uint32_t frames); // Reverb 적용 (post)
    void applySpatial(float *inputBuffer, float *outputBuffer, unsigned int frames); // Spatializer 처리
};
