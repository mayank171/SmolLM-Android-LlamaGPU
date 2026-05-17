cmake_minimum_required(VERSION 3.22.1)
project("llamavulkan")

# Path to llama.cpp (for headers only)
set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/../../../../llama.cpp)
get_filename_component(LLAMA_CPP_DIR ${LLAMA_CPP_DIR} ABSOLUTE)

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

# Build our JNI library
add_library(
    llamavulkan
    SHARED
    LlamaVulkan.cpp
    llama_vulkan_jni.cpp
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

# Compiler options
target_compile_options(
    llamavulkan
    PUBLIC
    -fvisibility=hidden 
    -fvisibility-inlines-hidden
    -ffunction-sections 
    -fdata-sections
    -O3
)

# Link against pre-built libraries (CPU only - Vulkan crashes on some Adreno GPUs)
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

# Linker options
target_link_options(
    llamavulkan
    PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
)
