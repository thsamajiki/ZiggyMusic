#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

class AudioDspChain;

// Oboe 기반 저지연 오디오 I/O 엔진.
// - 목적: (1) 설정 화면에서 "실시간 미리듣기(프리뷰)" 제공
//        (2) 디바이스/버전별 저지연 경로(AAudio/OpenSL ES) 대응 포트폴리오 어필
// - 실제 음악 재생(Media3 등)은 기존 파이프라인을 유지해도 되고,
//   필요 시 ringbuffer로 이 엔진에 PCM을 공급하도록 확장할 수 있음.
class AudioIOEngine : public oboe::AudioStreamCallback {
public:
    AudioIOEngine();
    ~AudioIOEngine();

    // 스트림 시작: sampleRate, 콜백 프레임 크기, 그리고 DSP 체인(shared_ptr)을 전달.
    // 반환: 스트림 시작 성공 시 true.
    bool start(int32_t sampleRate, int32_t framesPerCallback, std::shared_ptr<AudioDspChain> chain);
    bool isRunning() const { return running.load(std::memory_order_acquire); }

    // 스트림 중지 및 리소스 해제.
    void stop();

    // 앱 포그라운드/백그라운드 전환 처리용 hook.
    // 필요 시 성능 모드나 버퍼를 조정하거나 스트림을 재생성할 수 있음.
    void onForeground();
    void onBackground();

    // 테스트 톤 관련 설정
    // enabled: 테스트 톤 출력 사용 여부
    // hz: 주파수 (20..20000)
    // linear0to1: 출력 레벨 (선형 0..1)
    void setTestToneEnabled(bool enabled);
    void setTestToneFrequency(float hz);
    void setTestToneLevel(float linear0to1);

    // Media3(또는 다른 디코더)에서 생성된 "실제 음원 PCM(float stereo interleaved)"을
    // 프리뷰(Oboe) 출력으로 흘려보내기 위한 입력 API.
    // - bufferPtr: float interleaved stereo(LRLR...)를 가리키는 포인터
    // - frames: 프레임 수 (stereo이므로 샘플 수 = frames * 2)
    // - sampleRate: 입력 PCM의 sampleRate. (프리뷰 스트림과 다르면 로그만 남기고 그대로 재생)
    void enqueuePreviewPcm(const float* bufferPtr, int32_t frames, int32_t sampleRate);

// 프리뷰 입력 버퍼를 비움 (preview 시작/정지 시 사용).
    void clearPreviewBuffer();

    // 오디오 콜백: Oboe가 프레임을 요청할 때 호출됨.
    // 여기에 테스트 톤을 렌더링하고, DSP 체인이 있으면 체인을 통해 처리함.
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream,
                                          void *audioData,
                                          int32_t numFrames) override;

    // 스트림 종료 후 에러 핸들러: 스트림이 닫힌 뒤 발생한 에러를 로깅하고 상태 갱신.
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    std::mutex streamMutex; // 스트림 접근 동기화용 뮤텍스
    std::shared_ptr<oboe::AudioStream> stream; // 현재 사용 중인 Oboe 스트림

    // 오디오 처리를 담당하는 DSP 체인 (shared_ptr로 안전하게 교체 가능)
    std::shared_ptr<AudioDspChain> dspChain;

    // 상태 플래그
    std::atomic<bool> running{false};          // 스트림 실행 상태
    std::atomic<bool> testToneEnabled{false};  // 테스트 톤 사용 여부
    std::atomic<float> testToneHz{220.0f};     // 테스트 톤 주파수(Hz)
    std::atomic<float> testToneLevel{0.12f};   // 테스트 톤 레벨(선형 0..1)

    // ---------------------------
    // Preview PCM Ring Buffer (SPSC, lock-free)
    // - Producer: Media3 AudioProcessorAdapter thread
    // - Consumer: Oboe real-time callback thread
    // - Float interleaved stereo(LRLR...) only
    // ---------------------------
    std::vector<float> previewRing;                 // size = previewCapacityFrames * 2
    uint32_t previewCapacityFrames{0};              // power-of-two
    std::atomic<uint64_t> previewWriteIndex{0};     // in frames
    std::atomic<uint64_t> previewReadIndex{0};      // in frames
    std::atomic<int32_t> previewProducerSampleRate{0}; // last producer sampleRate (diagnostic)
    std::atomic<bool> previewProducerRateWarned{false};

    // 사인파 생성용 위상 변수 (콜백에서 지속 유지)
    double phase{0.0};

    // 시작 시 설정된 sampleRate (스트림의 실제 sampleRate와 동기화)
    int32_t currentSampleRate{48000};
};
