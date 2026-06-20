cmake_minimum_required(VERSION 3.22.1)
project("llamavulkan")

# Path to llama.cpp (for headers only)
set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/../../../../llama.cpp)
get_filename_component(LLAMA_CPP_DIR ${LLAMA_CPP_DIR} ABSOLUTE)

# Path to whisper.cpp (for headers only)
set(WHISPER_CPP_DIR ${CMAKE_SOURCE_DIR}/../../../../whisper.cpp)
get_filename_component(WHISPER_CPP_DIR ${WHISPER_CPP_DIR} ABSOLUTE)

set(GGML_DIR ${LLAMA_CPP_DIR}/ggml)
set(COMMON_DIR ${LLAMA_CPP_DIR}/common)
set(VENDOR_DIR ${LLAMA_CPP_DIR}/vendor)

# Path to pre-built libraries (with Vulkan support)
set(PREBUILT_LIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
get_filename_component(PREBUILT_LIBS_DIR ${PREBUILT_LIBS_DIR} ABSOLUTE)

# Import pre-built libraries
add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION ${PREBUILT_LIBS_DIR}/libggml-base.so)

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION ${PREBUILT_LIBS_DIR}/libggml-cpu.so)

add_library(ggml-vulkan SHARED IMPORTED)
set_target_properties(ggml-vulkan PROPERTIES IMPORTED_LOCATION ${PREBUILT_LIBS_DIR}/libggml-vulkan.so)

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION ${PREBUILT_LIBS_DIR}/libggml.so)

add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION ${PREBUILT_LIBS_DIR}/libllama.so)

add_library(llama-common SHARED IMPORTED)
set_target_properties(llama-common PROPERTIES IMPORTED_LOCATION ${PREBUILT_LIBS_DIR}/libllama-common.so)

# Import Whisper pre-built library (for STT)
add_library(whisper SHARED IMPORTED)
set_target_properties(whisper PROPERTIES IMPORTED_LOCATION ${PREBUILT_LIBS_DIR}/libwhisper.so)

# Build our JNI library (LlamaGPU)
add_library(
    llamavulkan
    SHARED
    LlamaVulkan.cpp
    llama_vulkan_jni.cpp
)

# Build Whisper JNI library (STT)
add_library(
    whisper_jni
    SHARED
    whisper_jni.cpp
)

target_include_directories(
    llamavulkan
    PUBLIC
    ${COMMON_DIR}
    ${GGML_DIR}/include
    ${GGML_DIR}/src
    ${GGML_DIR}/src/ggml-cpu
    ${LLAMA_CPP_DIR}/include
    ${VENDOR_DIR}
)

target_include_directories(
    whisper_jni
    PUBLIC
    ${WHISPER_CPP_DIR}/include
    ${WHISPER_CPP_DIR}
    ${WHISPER_CPP_DIR}/ggml/include
)

# Compiler options for llamavulkan
target_compile_options(
    llamavulkan
    PUBLIC
    -fvisibility=hidden 
    -fvisibility-inlines-hidden
    -ffunction-sections 
    -fdata-sections
    -O3
)

# Compiler options for whisper_jni
target_compile_options(
    whisper_jni
    PUBLIC
    -fvisibility=hidden 
    -fvisibility-inlines-hidden
    -ffunction-sections 
    -fdata-sections
    -O3
)

# Link against pre-built libraries.
#
# IMPORTANT: ggml-vulkan is deliberately NOT linked here despite being built.
# The build script (scripts/build_llama_vulkan.sh) produces libggml-vulkan.so
# in jniLibs/ for devices that support it, but on Adreno GPUs in our test
# device the Vulkan backend crashes during ggml_backend_sched_reserve (SIGSEGV
# inside libggml-vulkan.so) — even when useGPU=false at the API level. This
# is because llama.cpp's backend scheduler auto-enumerates ALL registered
# backends, and registration happens via static constructor on .so load.
#
# To enable Vulkan on devices that support it, add `ggml-vulkan` to the
# target_link_libraries list below.
target_link_libraries(
    llamavulkan
    android
    log
    llama
    llama-common
    ggml
    ggml-base
    ggml-cpu
)

# Link Whisper library
target_link_libraries(
    whisper_jni
    android
    log
    whisper
)

# Linker options for llamavulkan
target_link_options(
    llamavulkan
    PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
)

# Linker options for whisper_jni
target_link_options(
    whisper_jni
    PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
)
