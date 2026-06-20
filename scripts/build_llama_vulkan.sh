#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────────────
# build_llama_vulkan.sh
# ────────────────────────────────────────────────────────────────────────────
# Cross-compiles llama.cpp for Android (arm64-v8a) with Vulkan GPU support
# enabled. Output .so files are placed into app/src/main/jniLibs/arm64-v8a/
# so they will be picked up by the Android build automatically.
#
# Requirements:
#   - Android NDK 26+ installed (we'll auto-detect from common SDK paths)
#   - cmake (4.0+ recommended)
#   - llama.cpp source checked out at <repo>/llama.cpp/
#
# Vulkan-specific notes:
#   - glslc (the GLSL → SPIR-V compiler used by ggml-vulkan) ships with the
#     Android NDK under shader-tools/. We add it to PATH for the build.
#   - The Android NDK already exposes Vulkan headers (vulkan/vulkan.h) and
#     libvulkan.so via the platform sysroot, so no separate Vulkan SDK install
#     is required.

set -euo pipefail

# ─── Paths ──────────────────────────────────────────────────────────────────
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LLAMA_DIR="${ROOT_DIR}/llama.cpp"
BUILD_DIR="${ROOT_DIR}/.vulkan-build/arm64-v8a"
OUT_DIR="${ROOT_DIR}/app/src/main/jniLibs/arm64-v8a"

# ─── Detect NDK ─────────────────────────────────────────────────────────────
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
    # Try the standard SDK location
    NDK_BASE="${HOME}/Library/Android/sdk/ndk"
    if [[ -d "${NDK_BASE}" ]]; then
        # Pick the highest installed NDK version
        ANDROID_NDK_HOME="${NDK_BASE}/$(ls -1 "${NDK_BASE}" | sort -V | tail -1)"
        echo "[i] Auto-detected NDK: ${ANDROID_NDK_HOME}"
    fi
fi

if [[ ! -d "${ANDROID_NDK_HOME:-}" ]]; then
    echo "[!] ANDROID_NDK_HOME not set and no NDK found in default location."
    echo "    Install via Android Studio → SDK Manager → SDK Tools → NDK"
    exit 1
fi

# Detect host platform for shader-tools
HOST_OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
HOST_ARCH="$(uname -m)"
if [[ "${HOST_OS}" == "darwin" ]]; then
    SHADER_HOST="darwin-x86_64"  # NDK ships only x86_64 binaries; Rosetta handles arm64
elif [[ "${HOST_OS}" == "linux" ]]; then
    SHADER_HOST="linux-x86_64"
else
    SHADER_HOST="${HOST_OS}-${HOST_ARCH}"
fi

GLSLC_DIR="${ANDROID_NDK_HOME}/shader-tools/${SHADER_HOST}"
if [[ ! -x "${GLSLC_DIR}/glslc" ]]; then
    echo "[!] glslc not found at ${GLSLC_DIR}/glslc"
    echo "    Newer NDK versions (r26+) include shader-tools. Try installing NDK r28."
    exit 1
fi
export PATH="${GLSLC_DIR}:${PATH}"
echo "[i] glslc: $(which glslc)"

# ─── Verify llama.cpp source ────────────────────────────────────────────────
if [[ ! -f "${LLAMA_DIR}/CMakeLists.txt" ]]; then
    echo "[!] llama.cpp source not found at ${LLAMA_DIR}"
    echo "    git submodule update --init  (or clone https://github.com/ggerganov/llama.cpp)"
    exit 1
fi

# ─── Configure ──────────────────────────────────────────────────────────────
mkdir -p "${BUILD_DIR}"
echo "[i] Configuring CMake for arm64-v8a + Vulkan..."

cmake -S "${LLAMA_DIR}" -B "${BUILD_DIR}" \
    -DCMAKE_TOOLCHAIN_FILE="${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-28 \
    -DCMAKE_BUILD_TYPE=Release \
    -DGGML_VULKAN=ON \
    -DGGML_OPENMP=OFF \
    -DLLAMA_BUILD_TESTS=OFF \
    -DLLAMA_BUILD_EXAMPLES=OFF \
    -DLLAMA_BUILD_TOOLS=OFF \
    -DLLAMA_BUILD_SERVER=OFF \
    -DBUILD_SHARED_LIBS=ON

# ─── Build ──────────────────────────────────────────────────────────────────
echo "[i] Building (this may take 5-15 minutes)..."
cmake --build "${BUILD_DIR}" --config Release -j

# ─── Stage output libs ──────────────────────────────────────────────────────
echo "[i] Staging .so files into ${OUT_DIR}..."
mkdir -p "${OUT_DIR}"

# Required libs for the JNI module (LlamaVulkan)
REQUIRED_LIBS=(
    "libllama.so"
    "libllama-common.so"
    "libggml.so"
    "libggml-base.so"
    "libggml-cpu.so"
    "libggml-vulkan.so"
)

for lib in "${REQUIRED_LIBS[@]}"; do
    found=$(find "${BUILD_DIR}" -name "${lib}" -print -quit)
    if [[ -z "${found}" ]]; then
        echo "[!] Missing build output: ${lib}"
        echo "    Build may have failed for this library."
        exit 1
    fi
    cp "${found}" "${OUT_DIR}/${lib}"
    size=$(du -h "${OUT_DIR}/${lib}" | cut -f1)
    echo "    ✓ ${lib} (${size})"
done

echo ""
echo "[✓] Done. Vulkan-enabled llama.cpp libs are in ${OUT_DIR}."
echo "[i] Next steps:"
echo "    1. Update app/src/main/cpp/CMakeLists.txt to link ggml-vulkan"
echo "    2. Set useGPU=true in LlamaGPU.kt InferenceParams"
echo "    3. ./gradlew assembleDebug && reinstall the app"
