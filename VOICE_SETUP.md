# Voice Support Setup Guide

This guide explains how to set up voice input (Whisper STT) and voice output (Android TTS) for the SmolLM app.

## Overview

| Feature | Library | Size | Status |
|---------|---------|------|--------|
| **Speech-to-Text** | Whisper.cpp (tiny) | 75 MB | Requires setup |
| **Text-to-Speech** | Android TTS | 0 MB | Ready to use |

## Quick Start (TTS Only)

If you just want **voice output** (AI speaks responses), it works out of the box!

The app uses Android's built-in TTS which requires no additional setup.

## Full Setup (STT + TTS)

### Step 1: Clone whisper.cpp

```bash
cd /path/to/your/projects
git clone https://github.com/ggerganov/whisper.cpp.git
```

Make sure it's at the same level as your Android project:
```
/your/projects/
├── SmolLM-Android-Starter-Template/
├── llama.cpp/
└── whisper.cpp/    ← Clone here
```

### Step 2: Download Whisper Tiny Model

```bash
cd whisper.cpp
bash ./models/download-ggml-model.sh tiny
```

This downloads `ggml-tiny.bin` (~75MB).

### Step 3: Convert Model for Android

```bash
# Rename for the app
cp models/ggml-tiny.bin models/whisper-tiny.bin
```

### Step 4: Build Whisper for Android

```bash
cd whisper.cpp

# Create build directory
mkdir build-android && cd build-android

# Configure for Android (arm64)
cmake .. \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 \
    -DBUILD_SHARED_LIBS=ON \
    -DWHISPER_BUILD_EXAMPLES=OFF \
    -DWHISPER_BUILD_TESTS=OFF

# Build
make -j$(nproc)
```

### Step 5: Copy Libraries to App

```bash
# Copy the built library
cp libwhisper.so /path/to/SmolLM-Android-Starter-Template/app/src/main/jniLibs/arm64-v8a/

# Copy the model to assets
cp ../models/whisper-tiny.bin /path/to/SmolLM-Android-Starter-Template/app/src/main/assets/
```

### Step 6: Build the App

```bash
cd /path/to/SmolLM-Android-Starter-Template
./gradlew assembleDebug
```

## Usage

### Voice Input (STT)

```kotlin
// In your UI
Button(onClick = { viewModel.startVoiceInput() }) {
    Icon(Icons.Default.Mic)
}

// When user releases button
Button(onClick = { viewModel.stopVoiceInputAndSubmit() }) {
    Text("Send")
}
```

### Voice Output (TTS)

```kotlin
// Speak any text
viewModel.speakText("Hello, how can I help?")

// Stop speaking
viewModel.stopSpeaking()

// Toggle auto-speak for LLM responses
viewModel.toggleAutoSpeak()
```

### Voice State

```kotlin
val voiceState by viewModel.voiceState.collectAsState()

// Check states
voiceState.isListening      // Recording audio
voiceState.isTranscribing   // Processing with Whisper
voiceState.isSpeaking       // TTS is speaking
voiceState.autoSpeak        // Auto-speak LLM responses
voiceState.error            // Any error message
```

## Permissions

The app needs microphone permission for voice input:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Request at runtime:
```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    if (isGranted) {
        viewModel.startVoiceInput()
    }
}

// Check and request
if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
    == PackageManager.PERMISSION_GRANTED) {
    viewModel.startVoiceInput()
} else {
    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
}
```

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    User Interface                       │
│  [🎤 Mic Button]  [⌨️ Text Input]  [🔊 Speaker Toggle]  │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                   VoiceManager                          │
│  ┌─────────────────┐    ┌─────────────────┐            │
│  │  WhisperManager │    │   TTSManager    │            │
│  │  (STT - 75MB)   │    │  (Android TTS)  │            │
│  └────────┬────────┘    └────────┬────────┘            │
└───────────┼──────────────────────┼──────────────────────┘
            │                      │
            ▼                      ▼
┌─────────────────────────────────────────────────────────┐
│                 MainActivityViewModel                   │
│                                                         │
│  Voice Input → submitQuery() → LLM → speakText()       │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

## Resource Usage

| Component | RAM | Storage | Battery |
|-----------|-----|---------|---------|
| Whisper Tiny | +200 MB | +75 MB | +5%/min recording |
| Android TTS | +50 MB | 0 MB | +2%/response |
| **Total** | **+250 MB** | **+75 MB** | Moderate |

### With LLM:
```
Qwen 2.5 1.5B:  ~1.4 GB RAM
+ Whisper:      +0.2 GB RAM
+ TTS:          +0.05 GB RAM
─────────────────────────────
Total:          ~1.65 GB RAM
```

Still fits comfortably on 6-8GB devices!

## Troubleshooting

### "Failed to load Whisper model"
- Check that `whisper-tiny.bin` is in `app/src/main/assets/`
- Check that `libwhisper.so` is in `app/src/main/jniLibs/arm64-v8a/`

### "Microphone permission denied"
- Request permission at runtime before calling `startVoiceInput()`

### "No audio recorded"
- Check microphone is not being used by another app
- Check device volume is not muted

### TTS not speaking
- Check device TTS settings (Settings → Accessibility → Text-to-Speech)
- Try downloading offline TTS voice data

### Transcription is slow
- Whisper Tiny takes ~2-5 seconds for 10 seconds of audio
- This is normal for CPU inference

## Alternative: Android SpeechRecognizer

If you don't want to set up Whisper, you can use Android's built-in speech recognition:

```kotlin
// Uses Google's speech recognition (requires internet or offline model download)
val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
```

Pros:
- No additional setup
- Smaller app size

Cons:
- May require internet
- Less accurate than Whisper
- Privacy concerns (audio sent to Google)

## Files Created

```
app/src/main/java/io/shubham0204/startwithsmollm/voice/
├── VoiceManager.kt      # Unified voice interface
├── WhisperManager.kt    # STT using Whisper.cpp
└── TTSManager.kt        # TTS using Android TTS

app/src/main/cpp/
└── whisper_jni.cpp      # JNI bindings for Whisper

app/src/main/assets/
└── whisper-tiny.bin     # Whisper model (you need to add this)

app/src/main/jniLibs/arm64-v8a/
└── libwhisper.so        # Whisper library (you need to build this)
```

## Next Steps

1. **Build Whisper** following the steps above
2. **Add UI** for voice input button
3. **Request permissions** at runtime
4. **Test** voice input and output

The TTS (voice output) works immediately. Voice input requires building Whisper.
