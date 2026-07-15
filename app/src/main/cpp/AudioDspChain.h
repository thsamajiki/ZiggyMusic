#pragma once

// 오디오 DSP 체인: 앱 자체 Compressor를 담당
class AudioDspChain {
public:
    // 생성자: sampleRate를 받아 Compressor 상태를 초기화
    explicit AudioDspChain(unsigned int sampleRate);
    ~AudioDspChain();

    // sampleRate 변경 시 Compressor 시간 계수를 갱신
    void prepare(int sampleRate);

    // Interleave된 스테레오 버퍼를 처리
    // bufferInterleaved: L,R,L,R,... 포맷의 float 버퍼
    // numberOfFrames: 스테레오 프레임 수
    // sampleRate: 현재 처리할 샘플레이트
    void process(float *bufferInterleaved, unsigned int numberOfFrames, unsigned int sampleRate);

    // Compressor 파라미터 설정
    // thresholdDb: 입력 임계 데시벨, ratio: 압축 비율, attackMs/releaseMs: 시간(ms), makeupDb: 메이크업 게인
    void setCompressor(float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupDb);

private:
    unsigned int currentSampleRate; // 현재 sampleRate

    // Compressor 상태 변수들
    float compThreshold;            // 선형 임계값
    float compRatio;                // 압축 비율
    float compAttackCoeff;          // Attack 계수 (지수 필터)
    float compReleaseCoeff;         // Release 계수
    float compGainMakeup;           // Makeup Gain (선형)
    float compEnv;                  // 현재 Envelope 값
    float compAttackMs = 10.0f;     // 마지막으로 설정된 attack(ms) 저장
    float compReleaseMs = 100.0f;   // 마지막으로 설정된 release(ms) 저장

    void applyCompressor(float *buffer, unsigned int frames); // Compressor 적용
};
