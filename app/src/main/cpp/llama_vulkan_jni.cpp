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
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_00024Companion_isVulkanAvailableNative(JNIEnv* env, jobject thiz) {
    return LlamaVulkan::isVulkanAvailable();
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_00024Companion_getGPUInfoNative(JNIEnv* env, jobject thiz) {
    std::string info = LlamaVulkan::getGPUInfo();
    return env->NewStringUTF(info.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_shubham0204_startwithsmollm_gpu_LlamaGPU_loadModel(
    JNIEnv* env, jobject thiz, 
    jstring modelPath, jfloat minP, jfloat temperature, 
    jboolean storeChats, jlong contextSize, jstring chatTemplate, 
    jint nThreads, jboolean useMmap, jboolean useMlock,
    jboolean useGPU, jint gpuLayers) {
    
    jboolean isCopy = true;
    const char* modelPathCstr = env->GetStringUTFChars(modelPath, &isCopy);
    const char* chatTemplateCstr = chatTemplate ? env->GetStringUTFChars(chatTemplate, &isCopy) : nullptr;
    
    auto* llamaVulkan = new LlamaVulkan();

    try {
        llamaVulkan->loadModel(
            modelPathCstr, minP, temperature, storeChats, contextSize, 
            chatTemplateCstr, nThreads, useMmap, useMlock, useGPU, gpuLayers
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
