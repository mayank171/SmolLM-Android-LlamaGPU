#pragma once
#include "chat.h"
#include "common.h"
#include "llama.h"
#include <string>
#include <vector>

/**
 * LlamaVulkan - GPU-accelerated LLM inference using Vulkan
 * 
 * This is a separate implementation from SmolLM that adds:
 * - Vulkan GPU acceleration support
 * - Fallback to CPU if GPU not available
 * - GPU device selection
 */
class LlamaVulkan {
    llama_context* _ctx = nullptr;
    llama_model*   _model = nullptr;
    llama_sampler* _sampler = nullptr;
    llama_token    _currToken;
    llama_batch*   _batch = nullptr;

    llama_batch g_batch;

    std::vector<llama_chat_message> _messages;
    std::vector<char> _formattedMessages;
    std::vector<llama_token> _promptTokens;
    const char* _chatTemplate = nullptr;

    std::string _response;
    std::string _cacheResponseTokens;
    bool _storeChats = true;
    bool _useGPU = false;

    int64_t _responseGenerationTime = 0;
    long    _responseNumTokens = 0;
    int _nCtxUsed = 0;
    
    // Sampling parameters
    float _temperature = 0.7f;
    int _topK = 40;
    float _topP = 0.9f;
    float _minP = 0.05f;
    float _repeatPenalty = 1.1f;

    bool _isValidUtf8(const char* response);

public:
    /**
     * Load model with optional GPU acceleration
     * @param useGPU If true, attempt to use Vulkan GPU. Falls back to CPU if unavailable.
     * @param gpuLayers Number of layers to offload to GPU (-1 = all)
     */
    void loadModel(const char* modelPath, 
                   float temperature, int topK, float topP, float minP, float repeatPenalty,
                   bool storeChats, long contextSize, const char* chatTemplate, 
                   int nThreads, bool useMmap, bool useMlock, bool useGPU, int gpuLayers,
                   bool flashAttention, int kvCacheType);

    void addChatMessage(const char* message, const char* role);
    float getResponseGenerationTime() const;
    int getContextSizeUsed() const;
    void startCompletion(const char* query);
    std::string completionLoop();
    void stopCompletion();
    
    // GPU-specific methods
    bool isUsingGPU() const { return _useGPU; }
    static bool isVulkanAvailable();
    static std::string getGPUInfo();

    ~LlamaVulkan();
};
