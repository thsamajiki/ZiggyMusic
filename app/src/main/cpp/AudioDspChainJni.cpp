#include <jni.h>
#include <memory>
#include <mutex>
#include <atomic>
#include "Superpowered.h"
#include "AudioDspChain.h"

static std::shared_ptr<AudioDspChain> chain;
static std::mutex chainMutex;
static std::once_flag superpoweredInitFlag;

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_createChain(JNIEnv *env, jobject jobj, jint sampleRate) {
    std::call_once(superpoweredInitFlag, [](){
        Superpowered::Initialize("ExampleLicenseKey-WillExpire-OnNextUpdate");
    });

    std::lock_guard<std::mutex> lock(chainMutex);
    if (!chain) chain = std::make_shared<AudioDspChain>((unsigned int)sampleRate);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_destroyChain(JNIEnv *env, jobject jobj) {
    std::lock_guard<std::mutex> lock(chainMutex);
    chain.reset(); // shared_ptr 참조 해제: 더 이상 참조가 없으면 자동 삭제되어 안전함
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_setEQBand(JNIEnv *env, jobject jobj, jint bandIndex, jfloat gainDb) {
    std::lock_guard<std::mutex> lock(chainMutex);
    if (chain) chain->setEQBand((int)bandIndex, (float)gainDb);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_setCompressor(
        JNIEnv *env, jobject jobj,
        jfloat thresholdDb, jfloat ratio, jfloat attackMs, jfloat releaseMs, jfloat makeupDb) {
    std::lock_guard<std::mutex> lock(chainMutex);
    if (chain) chain->setCompressor((float)thresholdDb, (float)ratio, (float)attackMs, (float)releaseMs, (float)makeupDb);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_setReverb(
        JNIEnv *env, jobject jobj,
        jboolean enabled, jfloat wet) {
    std::lock_guard<std::mutex> lock(chainMutex);
    if (chain) chain->setReverb((bool)enabled, (float)wet);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hero_ziggymusic_audio_AudioProcessorChainController_processBuffer(
        JNIEnv *env, jobject jobj,
        jlong bufferPtr,
        jint frames, jint sampleRate) {
    if (bufferPtr == 0) return;
    if (frames <= 0 || sampleRate <= 0) return;

    auto* buf = reinterpret_cast<float*>(static_cast<intptr_t>(bufferPtr));

    std::shared_ptr<AudioDspChain> localChain;
    {
        std::lock_guard<std::mutex> lock(chainMutex);
        localChain = chain; // 스냅샷 확보: destroyChain()과 경쟁해도 안전
    }

    if (localChain) {
        localChain->process(buf, (unsigned int)frames, (unsigned int)sampleRate);
    }
}
