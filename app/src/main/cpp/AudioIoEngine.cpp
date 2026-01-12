#include "AudioIoEngine.h"
#include "AudioDspChain.h"
#include <atomic>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <vector>
#include <android/log.h>

#define LOG_TAG "AudioIOEngine"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioIOEngine::AudioIOEngine() = default;

AudioIOEngine::~AudioIOEngine() {
    stop();
}

static uint32_t nextPow2(uint32_t v) {
    if (v == 0) return 1;
    v--;
    v |= v >> 1;
    v |= v >> 2;
    v |= v >> 4;
    v |= v >> 8;
    v |= v >> 16;
    return v + 1;
}

// 스트림 시작: sampleRate, 콜백 프레임 크기, 그리고 DSP 체인을 전달하여 Oboe 스트림을 생성/시작
bool AudioIOEngine::start(int32_t sampleRate, int32_t framesPerCallback, std::shared_ptr<AudioDspChain> chain) {
    std::lock_guard<std::mutex> lock(streamMutex); // stream과 관련된 상태 동기화

    if (running.load()) return true; // 이미 실행 중이면 성공 반환

    // dspChain을 atomic으로 교체하여 콜백 스레드와 안전하게 공유
    std::atomic_store(&dspChain, std::move(chain));
    currentSampleRate = (sampleRate > 0) ? sampleRate : 48000;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Shared);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setChannelCount(oboe::ChannelCount::Stereo);
    builder.setSampleRate(currentSampleRate);
    builder.setCallback(this);

    if (framesPerCallback > 0) {
        builder.setFramesPerDataCallback(framesPerCallback);
    }

    // 스트림 열기
    oboe::Result result = builder.openStream(stream);
    if (result != oboe::Result::OK || !stream) {
        ALOGE("openStream failed: %s", oboe::convertToText(result));
        stream.reset();
        return false;
    }

    // Burst 기반 버퍼 크기 조정(프레임당 버퍼)
    int32_t burst = stream->getFramesPerBurst();
    if (framesPerCallback <= 0 && burst > 0) {
        stream->setBufferSizeInFrames(burst * 2);
    }

    // 스트림 시작 요청
    result = stream->requestStart();
    if (result != oboe::Result::OK) {
        ALOGE("requestStart failed: %s", oboe::convertToText(result));
        stream->close();
        stream.reset();
        return false;
    }

    // requestStart 성공 이후(=running=true 세팅 전후) 링버퍼 초기화/클리어
    {
        // 콜백 크기 기반으로 충분한 버퍼 확보 (드랍 정책으로 XR/저지연 유지)
        const int32_t callbackFrames =
                (framesPerCallback > 0) ? framesPerCallback : stream->getFramesPerBurst();

        // 대략 callbackFrames * 64 프레임 (최소 2048, 최대 32768) 정도로 설정
        uint32_t target = (uint32_t)std::max(2048, std::min(32768, callbackFrames * 64));
        previewCapacityFrames = nextPow2(target);

        previewRing.assign(previewCapacityFrames * 2, 0.0f);
        clearPreviewBuffer();
    }

    running.store(true);
    ALOGI("AudioIO started. SR=%d, burst=%d, buffer=%d",
          stream->getSampleRate(), stream->getFramesPerBurst(), stream->getBufferSizeInFrames());
    return true;
}

// 스트림 중지 및 리소스 정리
void AudioIOEngine::stop() {
    std::lock_guard<std::mutex> lock(streamMutex);

    running.store(false);

    if (stream) {
        // 안전하게 정지 요청 후 닫기
        stream->requestStop();
        stream->close();
        stream.reset();
    }
    // DSP 체인 포인터 해제
    std::atomic_store(&dspChain, std::shared_ptr<AudioDspChain>());
}

// 포그라운드 전환 hook: 필요 시 스트림 재구성/성능 변경 가능
void AudioIOEngine::onForeground() {
    std::lock_guard<std::mutex> lock(streamMutex);
    // 필요 시: 성능모드/버퍼 조정 (일부는 스트림 재생성 필요)
}

// 백그라운드 전환 hook: 여기선 재생 유지할 수 있으므로 stop하지 않음
void AudioIOEngine::onBackground() {
    std::lock_guard<std::mutex> lock(streamMutex);
    // 백그라운드에서도 계속 재생해야 할 수 있어 stop하지 않음.
}

// 테스트 톤 관련 설정 (원자적으로 저장)
void AudioIOEngine::setTestToneEnabled(bool enabled) {
    testToneEnabled.store(enabled);
}

void AudioIOEngine::setTestToneFrequency(float hz) {
    if (hz < 20.0f) hz = 20.0f;
    if (hz > 20000.0f) hz = 20000.0f;
    testToneHz.store(hz);
}

void AudioIOEngine::setTestToneLevel(float linear0to1) {
    if (linear0to1 < 0.0f) linear0to1 = 0.0f;
    if (linear0to1 > 1.0f) linear0to1 = 1.0f;
    testToneLevel.store(linear0to1);
}

// Oboe 콜백: 오디오 프레임 요청 시 호출됨 (실시간 스레드)
// - 테스트 톤을 렌더링하거나 0으로 채우고 DSP 체인을 적용
oboe::DataCallbackResult AudioIOEngine::onAudioReady(oboe::AudioStream *audioStream,
                                                     void *audioData,
                                                     int32_t numFrames) {
    auto *out = static_cast<float *>(audioData);
    const int32_t ch = audioStream->getChannelCount();

    // 이 엔진은 float stereo만 지원
    if (ch != 2) {
        std::memset(out, 0, sizeof(float) * numFrames * ch);
        return oboe::DataCallbackResult::Continue;
    }

    // 1) 테스트 톤 우선
    if (testToneEnabled.load(std::memory_order_relaxed)) {
        const float hz = testToneHz.load(std::memory_order_relaxed);
        const float level = testToneLevel.load(std::memory_order_relaxed);
        const auto sr = (double)audioStream->getSampleRate();
        const double phaseInc = (2.0 * M_PI * (double)hz) / sr;

        for (int32_t i = 0; i < numFrames; ++i) {
            auto s = (float)(std::sin(phase) * (double)level);
            phase += phaseInc;
            if (phase > 2.0 * M_PI) phase -= 2.0 * M_PI;

            out[i * 2 + 0] = s;
            out[i * 2 + 1] = s;
        }
    } else {
        // 2) 실제 음원: 링버퍼에서 dequeue
        uint32_t cap = previewCapacityFrames;
        if (cap == 0 || previewRing.empty()) {
            std::memset(out, 0, sizeof(float) * numFrames * 2);
        } else {
            uint64_t w = previewWriteIndex.load(std::memory_order_acquire);
            uint64_t r = previewReadIndex.load(std::memory_order_relaxed);
            uint64_t avail = w - r;

            auto want = (uint32_t)numFrames;
            uint32_t take = (uint32_t)std::min<uint64_t>(avail, want);

            // take 만큼 복사 (wrap 고려)
            auto pos = (uint32_t)(r & (cap - 1));
            uint32_t first = std::min(take, cap - pos);
            uint32_t second = take - first;

            // first chunk
            std::memcpy(out, &previewRing[pos * 2], sizeof(float) * first * 2);
            // wrap chunk
            if (second > 0) {
                std::memcpy(out + (first * 2), &previewRing[0], sizeof(float) * second * 2);
            }

            // 부족분은 무음(언더플로우)
            if (take < want) {
                std::memset(out + (take * 2), 0, sizeof(float) * (want - take) * 2);
            }

            previewReadIndex.store(r + take, std::memory_order_release);
        }
    }

    // 3) DSP 적용 (preview에도 동일하게 적용)
    auto localChain = std::atomic_load(&dspChain);
    if (localChain) {
        localChain->process(out,
                            (uint32_t)numFrames,
                            (uint32_t)audioStream->getSampleRate());
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioIOEngine::clearPreviewBuffer() {
    previewReadIndex.store(0, std::memory_order_release);
    previewWriteIndex.store(0, std::memory_order_release);
    previewProducerSampleRate.store(0, std::memory_order_release);
    previewProducerRateWarned.store(false, std::memory_order_release);
}

void AudioIOEngine::enqueuePreviewPcm(const float* bufferPtr, int32_t frames, int32_t sampleRate) {
    if (!bufferPtr || frames <= 0) return;
    if (!running.load(std::memory_order_acquire)) return;
    if (previewCapacityFrames == 0 || previewRing.empty()) return;

    // sampleRate 진단 (리샘플링은 하지 않음: mismatch면 pitch/tempo 변형 가능)
    const int32_t prev = previewProducerSampleRate.load(std::memory_order_relaxed);
    if (prev == 0) {
        previewProducerSampleRate.store(sampleRate, std::memory_order_relaxed);
    } else if (prev != sampleRate) {
        const bool warned = previewProducerRateWarned.exchange(true, std::memory_order_relaxed);
        if (!warned) {
            ALOGE("Preview PCM sampleRate mismatch: producer=%d, stream=%d (no resample)",
                  sampleRate, currentSampleRate);
        }
    }

    // 너무 큰 입력은 최신 구간만 유지
    uint32_t cap = previewCapacityFrames;
    uint32_t inFrames = (uint32_t)frames;
    const float* src = bufferPtr;

    if (inFrames > cap) {
        uint32_t drop = inFrames - cap;
        src += (drop * 2);      // stereo interleaved
        inFrames = cap;
    }

    // SPSC ring: free 공간 부족 시 "오래된 데이터"를 버리고 최신 데이터를 수용 (드랍 정책)
    uint64_t w = previewWriteIndex.load(std::memory_order_relaxed);
    uint64_t r = previewReadIndex.load(std::memory_order_acquire);
    uint64_t used = w - r;
    uint64_t free = (used >= cap) ? 0 : (cap - used);

    if (inFrames > free) {
        uint64_t need = (uint64_t)inFrames - free;
        previewReadIndex.store(r + need, std::memory_order_release); // 오래된 프레임 버림
        r += need;
    }

    // 실제 write (wrap 고려)
    uint32_t pos = (uint32_t)(w & (cap - 1));
    uint32_t first = std::min(inFrames, cap - pos);
    uint32_t second = inFrames - first;

    // first chunk
    std::memcpy(&previewRing[pos * 2], src, sizeof(float) * first * 2);
    // wrap chunk
    if (second > 0) {
        std::memcpy(&previewRing[0], src + (first * 2), sizeof(float) * second * 2);
    }

    previewWriteIndex.store(w + inFrames, std::memory_order_release);
}

// 스트림 종료 후 에러 콜백: 로그와 상태 플래그 갱신
void AudioIOEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    ALOGE("Audio stream error after close: %s", oboe::convertToText(error));
    running.store(false);
}
