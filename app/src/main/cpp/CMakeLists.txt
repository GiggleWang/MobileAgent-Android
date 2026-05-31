cmake_minimum_required(VERSION 3.22.1)
project(vlmjni)

# ---------------------------------------------------------------------------
# llama.cpp build options. These MUST be set before add_subdirectory() so they
# take effect as the subproject configures. libmtmd (the multimodal library)
# lives under llama.cpp/tools/, which is only added when both LLAMA_BUILD_COMMON
# and LLAMA_BUILD_TOOLS are ON — so we force them on here. Everything else we
# don't need is turned off to keep the build small.
# ---------------------------------------------------------------------------
set(LLAMA_BUILD_COMMON   ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    ON  CACHE BOOL "" FORCE)   # required to build libmtmd
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    ON  CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)   # NDK OpenMP isn't wired up
set(GGML_LLAMAFILE       OFF CACHE BOOL "" FORCE)   # llamafile unsupported on Android

# ---------------------------------------------------------------------------
# Force an OPTIMIZED native build even for the `debug` app variant. Android
# Gradle passes CMAKE_BUILD_TYPE=Debug for debuggable variants, which compiles
# llama.cpp/ggml at -O0 — ggml's matmul kernels then run 10-30x slower, making
# on-device inference look "frozen". We add -O3 + -DNDEBUG here (BEFORE
# add_subdirectory so it propagates to every ggml/llama/mtmd target). These
# options come after CMake's -O0 in the compile line, so the last -O wins.
# Release builds already get -O3; this just stops debug from being unusable.
# ---------------------------------------------------------------------------
if(NOT CMAKE_BUILD_TYPE STREQUAL "Release" AND NOT CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo")
    add_compile_options(-O3 -DNDEBUG)
endif()

# 16 KB page alignment (Android 15+). This is inherited by llama.cpp targets
# created below, not just our JNI bridge.
add_link_options(
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384")

# The llama.cpp source tree (git submodule).
add_subdirectory(llama.cpp)

add_library(vlmjni SHARED vlm_jni.cpp)

target_include_directories(vlmjni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/tools/mtmd)

find_library(log-lib log)

# mtmd PUBLIC-links llama + ggml, so linking mtmd gives us those symbols too.
# mtmd-helper is compiled into the mtmd target (there is no separate helper lib).
target_link_libraries(vlmjni PRIVATE mtmd llama ${log-lib})
