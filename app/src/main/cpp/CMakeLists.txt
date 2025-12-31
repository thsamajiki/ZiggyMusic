cmake_minimum_required(VERSION 3.10)
project(ziggymusic LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# JNI libs dir (정규화)
file(TO_CMAKE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs" JNI_LIBS_DIR)
message(STATUS "JNI libs dir: ${JNI_LIBS_DIR}")

# PATH_TO_SUPERPOWERED: Gradle -D 인자 또는 환경변수에서 가져오기
if(NOT DEFINED PATH_TO_SUPERPOWERED AND DEFINED ENV{PATH_TO_SUPERPOWERED})
    set(PATH_TO_SUPERPOWERED $ENV{PATH_TO_SUPERPOWERED})
endif()

if(DEFINED PATH_TO_SUPERPOWERED)
    file(TO_CMAKE_PATH "${PATH_TO_SUPERPOWERED}" PATH_TO_SUPERPOWERED)
    message(STATUS "Using Superpowered path: ${PATH_TO_SUPERPOWERED}")
else()
    message(FATAL_ERROR "PATH_TO_SUPERPOWERED is not defined. Set it in Gradle (cmake.arguments) or as environment variable PATH_TO_SUPERPOWERED.")
endif()

# 1) 소스 파일은 명시 리스트로 고정
set(APP_SOURCES
        ${CMAKE_CURRENT_SOURCE_DIR}/AudioDspChain.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/AudioDspChainJni.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/BufferAddressHelper.cpp
)

add_library(ziggymusic_audio_dsp SHARED ${APP_SOURCES})

# 2) include 경로 (Superpowered 헤더 사용이 필수)
target_include_directories(ziggymusic_audio_dsp PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}"
        "${CMAKE_CURRENT_SOURCE_DIR}/OpenSource"
        "${CMAKE_CURRENT_SOURCE_DIR}/SLES"
        "${CMAKE_CURRENT_SOURCE_DIR}/android"
)

# 컴파일 옵션
target_compile_options(ziggymusic_audio_dsp PRIVATE -Wall -Wextra -Wno-unused-parameter)

# 필요시 심볼 가시성 줄이기
set_target_properties(ziggymusic_audio_dsp PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN YES
)

# 3) 시스템 라이브러리 링크
find_library(log-lib log)

# 4) Superpowered 정적 라이브러리 찾기/링크
if(DEFINED ANDROID_ABI)
    if(ANDROID_ABI STREQUAL "x86")
        set(SUPERPOWERED_SUFFIX "X86")
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(SUPERPOWERED_SUFFIX "X86_64")
    else()
        # arm64-v8a, armeabi-v7a etc.
        set(SUPERPOWERED_SUFFIX "${ANDROID_ABI}")
    endif()
else()
    set(SUPERPOWERED_SUFFIX "")
endif()

set(CANDIDATE_LIBS
        "${JNI_LIBS_DIR}/${ANDROID_ABI}/libSuperpoweredAndroid${SUPERPOWERED_SUFFIX}.a"
        "${JNI_LIBS_DIR}/${ANDROID_ABI}/libSuperpoweredAndroid.a"
        "${PATH_TO_SUPERPOWERED}/libSuperpoweredAndroid${SUPERPOWERED_SUFFIX}.a"
        "${PATH_TO_SUPERPOWERED}/libSuperpoweredAndroid.a"
        "${CMAKE_CURRENT_SOURCE_DIR}/libSuperpoweredAndroid${SUPERPOWERED_SUFFIX}.a"
        "${CMAKE_CURRENT_SOURCE_DIR}/libSuperpoweredAndroid.a"
)

set(SUPERPOWERED_LIB_PATH "")
foreach(p IN LISTS CANDIDATE_LIBS)
    if(EXISTS "${p}")
        set(SUPERPOWERED_LIB_PATH "${p}")
        break()
    endif()
endforeach()

if(NOT SUPERPOWERED_LIB_PATH)
    message(FATAL_ERROR
            "Superpowered static lib not found. Checked:\n  ${CANDIDATE_LIBS}\n"
            "Put ABI \".a\" files into `app/src/main/jniLibs/<abi>/` or set `PATH_TO_SUPERPOWERED` to folder containing the .a files."
    )
endif()

message(STATUS "Found Superpowered static lib: ${SUPERPOWERED_LIB_PATH}")
add_library(Superpowered STATIC IMPORTED)
set_target_properties(Superpowered PROPERTIES IMPORTED_LOCATION "${SUPERPOWERED_LIB_PATH}")

target_link_libraries(ziggymusic_audio_dsp PRIVATE
        Superpowered
        ${log-lib}
        android
        OpenSLES
)
