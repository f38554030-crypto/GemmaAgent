cmake_minimum_required(VERSION 3.22.1)
project(gemmachat_llama C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Selalu kompilasi llama.cpp dengan optimasi penuh, bahkan saat assembleDebug.
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
set(CMAKE_C_FLAGS_DEBUG   "-O3 -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG" CACHE STRING "" FORCE)

# ---- Konfigurasi llama.cpp untuk HP (CPU saja, tanpa unduhan saat build) ----
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON     ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP        OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI         OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL          OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS       OFF CACHE BOOL "" FORCE)
set(LLAMA_LLGUIDANCE       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_IS_DEV     OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI      OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL        OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE         ON  CACHE BOOL "" FORCE)
# MediaTek Helio G96 (Cortex-A76 + A55) = ARMv8.2-A: NEON + DOTPROD + FP16
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif()

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/llama.cpp build-llama)

add_library(gemmachat_llama SHARED llama_jni.cpp)

target_include_directories(gemmachat_llama PRIVATE
        ${CMAKE_CURRENT_LIST_DIR}/llama.cpp/include
        ${CMAKE_CURRENT_LIST_DIR}/llama.cpp/common
        ${CMAKE_CURRENT_LIST_DIR}/llama.cpp/ggml/include)

target_link_libraries(gemmachat_llama llama llama-common android log)

# Halaman memori 16 KB (wajib untuk Android 15+ pada perangkat tertentu)
target_link_options(gemmachat_llama PRIVATE "-Wl,-z,max-page-size=16384")
