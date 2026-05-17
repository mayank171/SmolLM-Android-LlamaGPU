# SmolLM Android with LlamaGPU

An Android application for running Small Language Models (SLMs) locally on your device. This project extends the original [SmolLM-Android-Starter-Template](https://github.com/shubham0204/SmolLM-Android-Starter-Template) with a custom JNI wrapper for llama.cpp, providing an alternative inference path.

<p align="center">
  <img src="screenshots/app_preview.png" alt="App Preview" width="300"/>
</p>

## Features

- 🤖 **Run LLMs locally** - No internet required for inference
- 📱 **Model Selection** - Download and manage multiple GGUF models
- 💬 **Chat Interface** - Clean Material 3 chat UI with markdown support
- 📊 **Benchmark Screen** - Compare inference performance between backends
- 🔄 **Dual Backend Support** - SmolLM (original) and LlamaGPU (custom JNI)

## Architecture

This project uses two inference backends:

| Backend | Description | Library |
|---------|-------------|---------|
| **SmolLM** | Original AAR from SmolChat | `io.shubham0204.smollm` |
| **LlamaGPU** | Custom JNI wrapper for llama.cpp | `llamavulkan` native lib |

The LlamaGPU backend provides:
- Direct integration with llama.cpp
- Configurable inference parameters
- Kotlin coroutines & Flow support
- Benchmark capabilities

## Project Structure

```
app/
├── src/main/
│   ├── cpp/                    # Native C++ code
│   │   ├── CMakeLists.txt      # CMake build config
│   │   ├── LlamaVulkan.cpp     # llama.cpp wrapper
│   │   ├── LlamaVulkan.h
│   │   └── llama_vulkan_jni.cpp # JNI bindings
│   ├── java/.../
│   │   ├── gpu/
│   │   │   └── LlamaGPU.kt     # Kotlin wrapper for native lib
│   │   ├── ui/
│   │   │   ├── BenchmarkScreen.kt
│   │   │   ├── ModelSelectionScreen.kt
│   │   │   └── MarkdownText.kt
│   │   ├── MainActivity.kt
│   │   └── MainActivityViewModel.kt
│   └── jniLibs/arm64-v8a/      # Pre-built native libraries
│       ├── libggml-base.so
│       ├── libggml-cpu.so
│       ├── libggml.so
│       ├── libllama.so
│       └── libllama-common.so
└── libs/
    └── smollm-debug.aar        # Original SmolLM library
```

## Building

### Prerequisites

- Android Studio Hedgehog or later
- Android NDK 28.x
- CMake 3.22.1+
- Min SDK: 26 (Android 8.0)
- Target SDK: 36

### Build Steps

1. Clone the repository:
```bash
git clone https://github.com/mayank171/SmolLM-Android-LlamaGPU.git
cd SmolLM-Android-LlamaGPU
```

2. Open in Android Studio

3. Sync Gradle and build:
```bash
./gradlew assembleDebug
```

### Building Native Libraries (Optional)

If you need to rebuild the native libraries:

```bash
# Clone llama.cpp
git clone https://github.com/ggml-org/llama.cpp.git

# Set NDK path
export ANDROID_NDK=~/Library/Android/sdk/ndk/28.2.13676358

# Configure for Android
cmake -B build-android \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DGGML_VULKAN=OFF \
  -DGGML_OPENMP=OFF \
  -DLLAMA_BUILD_COMMON=ON

# Build
cmake --build build-android --config Release -j8 -- ggml llama llama-common

# Copy to jniLibs
cp build-android/bin/*.so app/src/main/jniLibs/arm64-v8a/
```

## Usage

### Using LlamaGPU in Your Code

```kotlin
val llamaGPU = LlamaGPU()

// Load model
llamaGPU.load(
    modelPath = "/path/to/model.gguf",
    params = LlamaGPU.InferenceParams(
        contextSize = 2048,
        numThreads = 4,
        temperature = 0.7f
    )
)

// Get response as Flow
llamaGPU.getResponseAsFlow("Hello, how are you?")
    .collect { token ->
        print(token)
    }

// Clean up
llamaGPU.close()
```

### Benchmark

The app includes a benchmark screen to compare SmolLM vs LlamaGPU performance:

1. Load a model from the Model Selection screen
2. Tap the ⚡ (speedometer) icon in the chat screen
3. Run the benchmark to see tokens/second for each backend

## Supported Models

Any GGUF format model compatible with llama.cpp:

- SmolLM (135M, 360M, 1.7B)
- Qwen2 (0.5B, 1.5B)
- Phi-3 Mini
- Gemma 2B
- And more...

## Credits

This project builds upon:

- **[SmolLM-Android-Starter-Template](https://github.com/shubham0204/SmolLM-Android-Starter-Template)** - Original starter template by [@shubham0204](https://github.com/shubham0204)
- **[SmolChat-Android](https://github.com/shubham0204/SmolChat-Android)** - Full-featured Android LLM chat app
- **[llama.cpp](https://github.com/ggml-org/llama.cpp)** - LLM inference in C/C++
- **[GGML](https://github.com/ggml-org/ggml)** - Tensor library for machine learning

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Author

**Mayank Mewar** - [@mayank171](https://github.com/mayank171)

---

<p align="center">
  Made with ❤️ for the Android & AI community
</p>
