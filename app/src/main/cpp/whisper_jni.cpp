/**
 * Whisper JNI - Native bindings for whisper.cpp
 * 
 * Provides speech-to-text functionality using Whisper tiny model.
 */

#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Load Whisper model from file
 * @param modelPath Path to the whisper model file (.bin)
 * @return Pointer to whisper_context, or 0 on failure
 */
JNIEXPORT jlong JNICALL
Java_io_shubham0204_startwithsmollm_voice_WhisperManager_loadModel(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath) {
    
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading Whisper model from: %s", path);
    
    // Initialize whisper context with default parameters
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;  // CPU only for stability
    
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    
    env->ReleaseStringUTFChars(modelPath, path);
    
    if (ctx == nullptr) {
        LOGE("Failed to load Whisper model");
        return 0;
    }
    
    LOGI("Whisper model loaded successfully");
    return reinterpret_cast<jlong>(ctx);
}

/**
 * Transcribe audio samples to text
 * @param contextPtr Pointer to whisper_context
 * @param samples Audio samples as float array (16kHz, mono, normalized to [-1, 1])
 * @return Transcribed text
 */
JNIEXPORT jstring JNICALL
Java_io_shubham0204_startwithsmollm_voice_WhisperManager_transcribe(
        JNIEnv *env,
        jobject /* this */,
        jlong contextPtr,
        jfloatArray samples) {
    
    if (contextPtr == 0) {
        LOGE("Invalid context pointer");
        return env->NewStringUTF("");
    }
    
    struct whisper_context *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    
    // Get audio samples
    jsize n_samples = env->GetArrayLength(samples);
    jfloat *audio_data = env->GetFloatArrayElements(samples, nullptr);
    
    LOGI("Transcribing %d samples", n_samples);
    
    // Set up whisper parameters
    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    
    wparams.print_realtime   = false;
    wparams.print_progress   = false;
    wparams.print_timestamps = false;
    wparams.print_special    = false;
    wparams.translate        = false;
    wparams.language         = "en";  // English
    wparams.n_threads        = 4;     // Use 4 threads
    wparams.offset_ms        = 0;
    wparams.no_context       = true;
    wparams.single_segment   = true;
    
    // Run inference
    int result = whisper_full(ctx, wparams, audio_data, n_samples);
    
    env->ReleaseFloatArrayElements(samples, audio_data, 0);
    
    if (result != 0) {
        LOGE("Whisper inference failed with code: %d", result);
        return env->NewStringUTF("");
    }
    
    // Get transcription
    std::string transcription;
    int n_segments = whisper_full_n_segments(ctx);
    
    for (int i = 0; i < n_segments; i++) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text != nullptr) {
            transcription += text;
        }
    }
    
    // Trim whitespace
    size_t start = transcription.find_first_not_of(" \t\n\r");
    size_t end = transcription.find_last_not_of(" \t\n\r");
    if (start != std::string::npos && end != std::string::npos) {
        transcription = transcription.substr(start, end - start + 1);
    }
    
    LOGI("Transcription: %s", transcription.c_str());
    
    return env->NewStringUTF(transcription.c_str());
}

/**
 * Free Whisper model resources
 * @param contextPtr Pointer to whisper_context
 */
JNIEXPORT void JNICALL
Java_io_shubham0204_startwithsmollm_voice_WhisperManager_freeModel(
        JNIEnv *env,
        jobject /* this */,
        jlong contextPtr) {
    
    if (contextPtr == 0) {
        return;
    }
    
    struct whisper_context *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    whisper_free(ctx);
    
    LOGI("Whisper model freed");
}

} // extern "C"
