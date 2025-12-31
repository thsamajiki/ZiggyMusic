#include <jni.h>
#include <cstdint>

extern "C" JNIEXPORT jlong JNICALL
Java_com_hero_ziggymusic_audio_BufferAddressHelper_getDirectBufferAddress(JNIEnv *env, jclass /*cls*/, jobject byteBuffer) {
    if (byteBuffer == nullptr) return (jlong)0;
    void *addr = env->GetDirectBufferAddress(byteBuffer);
    if (addr == nullptr) {
        // not a direct buffer or failed -> return 0
        return (jlong)0;
    }
    // 안전한 변환: 포인터를 intptr_t로 캐스트한 뒤 jlong으로 변환
    return (jlong)(reinterpret_cast<intptr_t>(addr));
}

