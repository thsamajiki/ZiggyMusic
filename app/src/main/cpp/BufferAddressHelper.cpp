#include <jni.h>
#include <cstdint>

// JNI를 통해 Java의 DirectByteBuffer에서 네이티브 포인터(주소)를 얻어 jlong으로 반환하는 헬퍼 함수.
extern "C" JNIEXPORT jlong JNICALL
Java_com_hero_ziggymusic_audio_BufferAddressHelper_getDirectBufferAddress(JNIEnv *env, jclass /*cls*/, jobject byteBuffer) {
    // 버퍼가 null이면 0 반환
    if (byteBuffer == nullptr) return (jlong)0;

    // DirectByteBuffer의 네이티브 주소를 얻음.
    // 실패하면 nullptr을 반환하므로 검사 필요.
    void *addr = env->GetDirectBufferAddress(byteBuffer);
    if (addr == nullptr) {
        // Direct buffer가 아니거나 주소 획득 실패 -> 0 반환
        return (jlong)0;
    }

    // 안전한 변환: 포인터를 intptr_t로 캐스트한 후 jlong으로 변환하여 반환.
    // 이 방식은 포인터 크기와 jlong 크기가 다를 수 있는 플랫폼에서도 정의된 변환을 수행.
    return (jlong)(reinterpret_cast<intptr_t>(addr));
}

