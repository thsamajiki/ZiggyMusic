#include <jni.h>
#include <memory>
#include <thread>
#include <mutex>
#include <atomic>
#include <android/log.h>
#include "Superpowered.h"
#include "AudioDspChain.h"
#include "AudioIoEngine.h"
#include "SuperpoweredConfig.h"

#define LOG_TAG "AudioDspChainJNI"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::atomic<bool> gShuttingDown{false};
static std::atomic<int>  gInFlight{0};
static std::shared_ptr<AudioDspChain> gChain;
static std::mutex gChainMutex;
static std::once_flag gSuperpoweredInitOnce;

static std::unique_ptr<AudioIOEngine> gAudioIO;
static std::mutex gAudioIOMutex;

static void ensureSuperpoweredInit() {
    std::call_once(gSuperpoweredInitOnce, []() {
        Superpowered::Initialize(SUPERPOWERED_LICENSE_KEY);
        ALOGI("Superpowered initialized.");
    });
}

static std::shared_ptr<AudioDspChain> getChainSnapshot() {
    std::lock_guard<std::mutex> lock(gChainMutex);
    return gChain;
}

static std::shared_ptr<AudioDspChain> getOrCreateChainLocked(int sampleRate) {
    // 호출자는 gChainMutex를 소유해야 함
    if (!gChain) {
        gChain = std::make_shared<AudioDspChain>((unsigned int)sampleRate);
    }
    gChain->prepare(sampleRate);

    return gChain;
}

// ------------------------------------------------------
// Chain 생명주기
// ------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_createChain(
        JNIEnv* /*env*/, jobject /*thiz*/, jint sampleRate) {
    ensureSuperpoweredInit();

    std::lock_guard<std::mutex> lock(gChainMutex);
    gChain = std::make_shared<AudioDspChain>((unsigned int)sampleRate);
    gChain->prepare((int)sampleRate);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_destroyChain(
        JNIEnv* env, jobject thiz
) {
    (void)env; (void)thiz;

    gShuttingDown.store(true, std::memory_order_release);

    // 진행 중인 처리 대기 (짧게 대기)
    for (int i = 0; i < 200; ++i) { // ~200ms (1ms sleep)
        if (gInFlight.load(std::memory_order_acquire) == 0) break;
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }

    std::lock_guard<std::mutex> lock(gChainMutex);
    gChain.reset();
    gShuttingDown.store(false, std::memory_order_release);
}

// ------------------------------------------------------
// DSP 제어
// ------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_setEQBand(
        JNIEnv* /*env*/, jobject /*thiz*/, jint bandIndex, jfloat gainDb) {
    auto chain = getChainSnapshot();
    if (!chain) return;
    chain->setEQBand((int)bandIndex, (float)gainDb);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_setCompressor(
        JNIEnv* /*env*/, jobject /*thiz*/,
        jfloat thresholdDb, jfloat ratio, jfloat attackMs, jfloat releaseMs, jfloat makeupDb) {
    auto chain = getChainSnapshot();
    if (!chain) return;
    chain->setCompressor((float)thresholdDb, (float)ratio, (float)attackMs, (float)releaseMs, (float)makeupDb);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_setReverb(
        JNIEnv* /*env*/, jobject /*thiz*/, jboolean enabled, jfloat wet) {
    auto chain = getChainSnapshot();
    if (!chain) return;
    chain->setReverb((bool)enabled, (float)wet);
}

// ------------------------------------------------------
// In-app Spatial
// ------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_setSpatialEnabled(
        JNIEnv* /*env*/, jobject /*thiz*/, jboolean enabled) {
    auto chain = getChainSnapshot();
    if (!chain) return;
    chain->setSpatialEnabled((bool)enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_setSpatialPosition(
        JNIEnv* /*env*/, jobject /*thiz*/,
        jfloat azimuthDeg, jfloat elevationDeg, jfloat distanceMeters) {
    auto chain = getChainSnapshot();
    if (!chain) return;
    chain->setSpatialPosition((float)azimuthDeg, (float)elevationDeg, (float)distanceMeters);
}

// ------------------------------------------------------
// Head tracking
// ------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_setHeadTrackingEnabled(
        JNIEnv* /*env*/, jobject /*thiz*/, jboolean enabled) {
    auto chain = getChainSnapshot();
    if (!chain) return;
    chain->setHeadTrackingEnabled((bool)enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_setHeadTrackingYaw(
        JNIEnv* /*env*/, jobject /*thiz*/, jfloat yawDeg) {
    auto chain = getChainSnapshot();
    if (!chain) return;
    chain->setHeadTrackingYaw((float)yawDeg);
}

static inline bool tryEnterProcess() noexcept {
    if (gShuttingDown.load(std::memory_order_acquire)) return false;
    gInFlight.fetch_add(1, std::memory_order_acq_rel);
    if (gShuttingDown.load(std::memory_order_acquire)) {
        gInFlight.fetch_sub(1, std::memory_order_acq_rel);
        return false;
    }
    return true;
}

static inline void leaveProcess() noexcept {
    gInFlight.fetch_sub(1, std::memory_order_acq_rel);
}

// ------------------------------------------------------
// PCM 처리 (Media3 adapter -> JNI)
// ------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_processBuffer(
        JNIEnv* env, jobject thiz, jlong bufferPtr, jint frames, jint sampleRate
) {
    (void)env; (void)thiz;

    if (!tryEnterProcess()) return;
    auto guard = std::unique_ptr<void, void(*)(void*)>(nullptr, [](void*) { leaveProcess(); });

    std::shared_ptr<AudioDspChain> chain; // shared_ptr snapshot
    {
        std::lock_guard<std::mutex> lock(gChainMutex);
        chain = gChain;
    }
    if (!chain) return;

    auto* p = reinterpret_cast<float*>(static_cast<intptr_t>(bufferPtr));
    if (!p || frames <= 0) return;

    chain->process(p, frames, sampleRate);
}

// ------------------------------------------------------
// Oboe Preview I/O (Settings preview)
// ------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_nativeStartAudioIO(
        JNIEnv* /*env*/, jobject /*thiz*/, jint sampleRate, jint framesPerCallback) {
    ensureSuperpoweredInit();

    std::lock_guard<std::mutex> lk(gAudioIOMutex);
    if (!gAudioIO) gAudioIO = std::make_unique<AudioIOEngine>();

    // ✅ 이미 실행 중이면: prepare(heavy mutation) 금지. 그대로 둠.
    if (gAudioIO->isRunning()) return;

    std::shared_ptr<AudioDspChain> chain;
    {
        std::lock_guard<std::mutex> lock(gChainMutex);
        chain = getOrCreateChainLocked((int)sampleRate); // 내부에서 prepare 수행
    }

    gAudioIO->setTestToneEnabled(false);
    gAudioIO->start((int32_t)sampleRate, (int32_t)framesPerCallback, chain);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_nativeStopAudioIO(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lk(gAudioIOMutex);
    if (gAudioIO) gAudioIO->stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_nativeAudioIOOnForeground(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lk(gAudioIOMutex);
    if (gAudioIO) gAudioIO->onForeground();
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_nativeAudioIOOnBackground(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lk(gAudioIOMutex);
    if (gAudioIO) gAudioIO->onBackground();
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_nativeSetTestToneEnabled(
        JNIEnv* /*env*/, jobject /*thiz*/, jboolean enabled) {
    std::lock_guard<std::mutex> lk(gAudioIOMutex);
    if (gAudioIO) gAudioIO->setTestToneEnabled((bool)enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_nativeSetTestToneFrequency(
        JNIEnv* /*env*/, jobject /*thiz*/, jfloat hz) {
    std::lock_guard<std::mutex> lk(gAudioIOMutex);
    if (gAudioIO) gAudioIO->setTestToneFrequency((float)hz);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_nativeSetTestToneLevel(
        JNIEnv* /*env*/, jobject /*thiz*/, jfloat level0to1) {
    std::lock_guard<std::mutex> lk(gAudioIOMutex);
    if (gAudioIO) gAudioIO->setTestToneLevel((float)level0to1);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_nativeEnqueuePreviewPcm(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong bufferPtr, jint frames, jint sampleRate) {

    if (bufferPtr == 0 || frames <= 0) return;

    AudioIOEngine* io = nullptr;
    {
        std::lock_guard<std::mutex> lk(gAudioIOMutex);
        io = gAudioIO ? gAudioIO.get() : nullptr;
    }
    if (!io || !io->isRunning()) return;

    const auto* pcm = reinterpret_cast<const float*>(bufferPtr);
    io->enqueuePreviewPcm(pcm, (int32_t)frames, (int32_t)sampleRate);
}
