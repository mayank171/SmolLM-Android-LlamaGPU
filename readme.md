# SmolLM Android with LlamaGPU

An Android application for running Small Language Models (SLMs) locally on your device. This project extends the original [SmolLM-Android-Starter-Template](https://github.com/shubham0204/SmolLM-Android-Starter-Template) with a custom JNI wrapper for llama.cpp, providing an alternative inference path.

<p align="center">
  <img src="screenshots/app_preview.png" alt="App Preview" width="300"/>
</p>

## Features

- 🤖 **Run LLMs locally** - No internet required for inference
- 📱 **Model Selection** - Download and manage multiple GGUF models
- 💬 **Chat Interface** - Clean Material 3 chat UI with markdown support
- � **RAG Support** - Upload documents and chat with your data
- � **Benchmark Screen** - Test inference performance
- 📊 **Performance Dashboard** - Real-time profiling and metrics (DEBUG builds)
- ⚡ **Optimized Inference** - Flash Attention, KV Cache quantization, device-adaptive context

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
│   ├── assets/                 # RAG embedding model
│   │   ├── all-MiniLM-L6-v2.onnx
│   │   └── vocab.txt
│   ├── cpp/                    # Native C++ code
│   │   ├── CMakeLists.txt      # CMake build config
│   │   ├── LlamaVulkan.cpp     # llama.cpp wrapper
│   │   ├── LlamaVulkan.h
│   │   └── llama_vulkan_jni.cpp # JNI bindings
│   ├── java/.../
│   │   ├── gpu/
│   │   │   └── LlamaGPU.kt     # Kotlin wrapper for native lib
│   │   ├── rag/                # RAG implementation
│   │   │   ├── RagEngine.kt    # Main RAG orchestration
│   │   │   ├── EmbeddingModel.kt # ONNX embeddings
│   │   │   ├── VectorDatabase.kt # Vector storage & search
│   │   │   ├── TextChunker.kt  # Document chunking
│   │   │   └── DocumentParser.kt # File parsing
│   │   ├── ui/
│   │   │   ├── BenchmarkScreen.kt
│   │   │   ├── RagScreen.kt    # Document management
│   │   │   ├── ModelSelectionScreen.kt
│   │   │   └── MarkdownText.kt
│   │   ├── MainActivity.kt
│   │   └── MainActivityViewModel.kt
│   └── jniLibs/arm64-v8a/      # Pre-built native libraries
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

### RAG (Retrieval Augmented Generation)

Chat with your documents! The app supports uploading files and using them as context for conversations.

**Supported file types:**
- PDF documents (with table & image extraction)
- Text files (.txt)
- Markdown files (.md)
- Images (JPG, PNG) with OCR

**How to use:**
1. Load a model and start a chat
2. Tap the 📚 (documents) icon in the chat screen
3. Upload your documents
4. Enable RAG toggle in the chat
5. Ask questions about your documents!

**Advanced Features:**
- 📊 **Table Extraction**: Automatically detects and preserves table structure from PDFs
- 🖼️ **Image OCR**: Extracts text from diagrams and images using ML Kit
- 🔍 **Hybrid Search**: Combines BM25 keyword search with semantic embeddings (Reciprocal Rank Fusion)
- ✂️ **Smart Chunking**: Sentence-aware chunking that never breaks mid-sentence
- 📝 **Caption Detection**: Preserves table titles and figure captions

**How it works:**
- Documents are parsed with enhanced extraction (text, tables, images)
- Content is split into semantic chunks (sentence-aware)
- Chunks are indexed in both vector database (embeddings) and BM25 (keywords)
- Queries use hybrid search for best accuracy (95%+ on mixed queries)
- Retrieved context is injected into the prompt with citations

**Technical Stack:**
- Embeddings: ONNX all-MiniLM-L6-v2 (on-device)
- OCR: Google ML Kit Text Recognition
- PDF: PDFBox-Android
- Search: Custom BM25 + Vector similarity with RRF

### Benchmark

Test inference performance:

1. Load a model from the Model Selection screen
2. Tap the ⚡ (speedometer) icon in the chat screen
3. Run the benchmark to see tokens/second

### Performance Dashboard (DEBUG builds only)

Monitor real-time performance metrics for RAG and LLM operations:

**How to access:**
1. Build and run a DEBUG version of the app
2. Tap the 📊 (Analytics) icon in the chat screen
3. View live metrics as you use the app

**Metrics tracked:**

**RAG Performance:**
- **Document Processing**: Time to parse and extract content (Target: < 2000ms)
- **Embedding Generation**: Time to generate embeddings (Target: < 30ms)
- **Search Performance**: Time to retrieve relevant chunks (Target: < 100ms)
- **Total RAG Query Time**: End-to-end RAG latency (Target: < 150ms)

**LLM Inference Performance:**
- **Total Generation Time**: Complete response time (Target: < 5000ms)
- **Tokens per Second**: Generation speed (Target: > 10 tok/s)
- **RAM Usage**: Memory consumption (Target: < 200MB)
- **Battery Drain**: Estimated consumption per 1000 tokens (Target: < 5mAh/1K)

**Features:**
- ✓ Real-time updates during app usage
- ✓ Color-coded status indicators (Good/Acceptable/Slow)
- ✓ Industry-standard benchmark targets
- ✓ Zero overhead in RELEASE builds (dashboard hidden)

**Technical Details:**
- Profiling system uses Strategy and Observer patterns
- Metrics aggregated via composition-based profiled wrappers
- Performance targets based on UX research and industry benchmarks
- See `PERFORMANCE_DASHBOARD_PR.md` for complete documentation

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
