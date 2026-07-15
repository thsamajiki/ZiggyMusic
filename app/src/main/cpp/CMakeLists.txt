cmake_minimum_required(VERSION 3.10)
project(ziggymusic LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# DSP 성능 최적화를 위한 컴파일 플래그 설정
# -O3: 최대 최적화
# -ffast-math: 부동소수점 연산 속도 우선 (정밀도 약간 희생, 오디오에서는 허용됨)
# -funroll-loops: 반복문 펼치기 (CPU 파이프라인 최적화)
add_compile_options(-O3 -ffast-math -funroll-loops -fexceptions -frtti)

# Oboe 패키지 찾기 (Prefab 사용 시 필수)
find_package(oboe REQUIRED CONFIG)

# 1) 소스 파일은 명시 리스트로 고정
set(APP_SOURCES
        ${CMAKE_CURRENT_SOURCE_DIR}/AudioDspChain.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/AudioDspChainJni.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/BufferAddressHelper.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/AudioIoEngine.cpp
)

add_library(ziggymusic_audio_dsp SHARED ${APP_SOURCES})

# 2) include 경로
target_include_directories(ziggymusic_audio_dsp PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}"
        "${CMAKE_CURRENT_SOURCE_DIR}/SLES"
        "${CMAKE_CURRENT_SOURCE_DIR}/android"
)

# 컴파일 옵션
target_compile_options(ziggymusic_audio_dsp PRIVATE -Wall -Wextra -Wno-unused-parameter)

# 필요시 심볼 가시성 줄이기 (보안 및 충돌 방지)
set_target_properties(ziggymusic_audio_dsp PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN YES
)

# 3) 시스템 라이브러리 링크
find_library(log-lib log)

target_link_libraries(ziggymusic_audio_dsp PRIVATE
        ${log-lib}
        android
        OpenSLES
        oboe::oboe
)
