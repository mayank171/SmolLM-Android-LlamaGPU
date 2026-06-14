#include "LlamaVulkan.h"
#include <jni.h>

/**
 * JNI bindings for LlamaVulkan - GPU-accelerated LLM inference
 * 
 * Package: io.shubham0204.startwithsmollm.gpu
 * Class: LlamaGPU
 * 
 * This is a separate JNI from SmolLM that adds GPU support.
 * If GPU fails, the app can fall back to the original SmolLM.
 */

extern "C" JNIEXPORT jboolean JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_isVulkanAvailableNative(JNIEnv* env, jclass clazz) {
    return LlamaVulkan::isVulkanAvailable();
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_getGPUInfoNative(JNIEnv* env, jclass clazz) {
    std::string info = LlamaVulkan::getGPUInfo();
    return env->NewStringUTF(info.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_loadModel(
    JNIEnv* env, jobject thiz, 
    jstring modelPath, 
    jfloat temperature, jint topK, jfloat topP, jfloat minP, jfloat repeatPenalty,
    jboolean storeChats, jlong contextSize, jstring chatTemplate, 
    jint nThreads, jboolean useMmap, jboolean useMlock,
    jboolean useGPU, jint gpuLayers,
    jboolean flashAttention, jint kvCacheType) {
    
    jboolean isCopy = true;
    const char* modelPathCstr = env->GetStringUTFChars(modelPath, &isCopy);
    const char* chatTemplateCstr = chatTemplate ? env->GetStringUTFChars(chatTemplate, &isCopy) : nullptr;
    
    auto* llamaVulkan = new LlamaVulkan();

    try {
        llamaVulkan->loadModel(
            modelPathCstr, 
            temperature, topK, topP, minP, repeatPenalty,
            storeChats, contextSize, chatTemplateCstr, 
            nThreads, useMmap, useMlock, useGPU, gpuLayers,
            flashAttention, kvCacheType
        );
    } catch (std::exception& error) {
        env->ReleaseStringUTFChars(modelPath, modelPathCstr);
        if (chatTemplateCstr) env->ReleaseStringUTFChars(chatTemplate, chatTemplateCstr);
        delete llamaVulkan;
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return 0;
    }

    env->ReleaseStringUTFChars(modelPath, modelPathCstr);
    if (chatTemplateCstr) env->ReleaseStringUTFChars(chatTemplate, chatTemplateCstr);
    return reinterpret_cast<jlong>(llamaVulkan);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_isUsingGPU(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    return llamaVulkan->isUsingGPU();
}

extern "C" JNIEXPORT void JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_addChatMessage(
    JNIEnv* env, jobject thiz, jlong modelPtr, jstring message, jstring role) {
    
    jboolean isCopy = true;
    const char* messageCstr = env->GetStringUTFChars(message, &isCopy);
    const char* roleCstr = env->GetStringUTFChars(role, &isCopy);
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    llamaVulkan->addChatMessage(messageCstr, roleCstr);
    env->ReleaseStringUTFChars(message, messageCstr);
    env->ReleaseStringUTFChars(role, roleCstr);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_getResponseGenerationSpeed(
    JNIEnv* env, jobject thiz, jlong modelPtr) {
    
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    return llamaVulkan->getResponseGenerationTime();
}

extern "C" JNIEXPORT jint JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_getContextSizeUsed(
    JNIEnv* env, jobject thiz, jlong modelPtr) {
    
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    return llamaVulkan->getContextSizeUsed();
}

extern "C" JNIEXPORT void JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_close(
    JNIEnv* env, jobject thiz, jlong modelPtr) {
    
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    delete llamaVulkan;
}

extern "C" JNIEXPORT void JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_startCompletion(
    JNIEnv* env, jobject thiz, jlong modelPtr, jstring prompt) {
    
    jboolean isCopy = true;
    const char* promptCstr = env->GetStringUTFChars(prompt, &isCopy);
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    
    try {
        llamaVulkan->startCompletion(promptCstr);
    } catch (std::exception& error) {
        env->ReleaseStringUTFChars(prompt, promptCstr);
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return;
    }
    env->ReleaseStringUTFChars(prompt, promptCstr);
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_completionLoop(
    JNIEnv* env, jobject thiz, jlong modelPtr) {
    
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    try {
        std::string response = llamaVulkan->completionLoop();
        return env->NewStringUTF(response.c_str());
    } catch (std::exception& error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_stopCompletion(
    JNIEnv* env, jobject thiz, jlong modelPtr) {
    
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    llamaVulkan->stopCompletion();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_saveState(
    JNIEnv* env, jobject thiz, jlong modelPtr, jstring path) {
    
    jboolean isCopy = true;
    const char* pathCstr = env->GetStringUTFChars(path, &isCopy);
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    
    bool success = llamaVulkan->saveState(pathCstr);
    env->ReleaseStringUTFChars(path, pathCstr);
    return success;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_loadState(
    JNIEnv* env, jobject thiz, jlong modelPtr, jstring path) {
    
    jboolean isCopy = true;
    const char* pathCstr = env->GetStringUTFChars(path, &isCopy);
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    
    bool success = llamaVulkan->loadState(pathCstr);
    env->ReleaseStringUTFChars(path, pathCstr);
    return success;
}

extern "C" JNIEXPORT void JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_clearChat(
    JNIEnv* env, jobject thiz, jlong modelPtr) {
    
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    llamaVulkan->clearChat();
}

extern "C" JNIEXPORT jint JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_shiftContext(
    JNIEnv* env, jobject thiz, jlong modelPtr, jint keepFirstN, jint removeNextN) {
    
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    return llamaVulkan->shiftContext(keepFirstN, removeNextN);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_getMessageCount(
    JNIEnv* env, jobject thiz, jlong modelPtr) {
    
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    return llamaVulkan->getMessageCount();
}

extern "C" JNIEXPORT void JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_removeOldestMessages(
    JNIEnv* env, jobject thiz, jlong modelPtr, jint count) {
    
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    llamaVulkan->removeOldestMessages(count);
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_summarizeMessages(
    JNIEnv* env, jobject thiz, jlong modelPtr, jint startIdx, jint count) {
    
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    std::string summary = llamaVulkan->summarizeMessages(startIdx, count);
    return env->NewStringUTF(summary.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_rebuildCacheWithSummary(
    JNIEnv* env, jobject thiz, jlong modelPtr, jstring summary, jint keepRecentN) {
    
    auto* llamaVulkan = reinterpret_cast<LlamaVulkan*>(modelPtr);
    const char* summaryStr = env->GetStringUTFChars(summary, nullptr);
    llamaVulkan->rebuildCacheWithSummary(summaryStr, keepRecentN);
    env->ReleaseStringUTFChars(summary, summaryStr);
}
