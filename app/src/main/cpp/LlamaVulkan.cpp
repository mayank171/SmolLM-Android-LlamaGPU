#include "LlamaVulkan.h"
#include <android/log.h>
#include <cstring>
#include <iomanip>
#include <iostream>
#include <sstream>

#define TAG "[LlamaVulkan]"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGw(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

bool LlamaVulkan::isVulkanAvailable() {
    // Load all backends first
    ggml_backend_load_all();
    
    // Check if Vulkan backend is registered
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto* reg = ggml_backend_reg_get(i);
        std::string name = ggml_backend_reg_name(reg);
        LOGi("Found backend: %s", name.c_str());
        if (name == "Vulkan") {
            return true;
        }
    }
    LOGi("Vulkan backend not available");
    return false;
}

std::string LlamaVulkan::getGPUInfo() {
    std::ostringstream info;
    info << "Available backends: ";
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto* reg = ggml_backend_reg_get(i);
        info << ggml_backend_reg_name(reg);
        if (i < ggml_backend_reg_count() - 1) {
            info << ", ";
        }
    }
    return info.str();
}

void LlamaVulkan::loadModel(const char* model_path, 
                            float temperature, int topK, float topP, float minP, float repeatPenalty,
                            bool storeChats, long contextSize, const char* chatTemplate, 
                            int nThreads, bool useMmap, bool useMlock, 
                            bool useGPU, int gpuLayers,
                            bool flashAttention, int kvCacheType) {
    LOGi("Loading model:"
         "\n\tmodel_path = %s"
         "\n\ttemperature = %.2f"
         "\n\ttopK = %d"
         "\n\ttopP = %.2f"
         "\n\tminP = %.2f"
         "\n\trepeatPenalty = %.2f"
         "\n\tcontextSize = %li"
         "\n\tnThreads = %d"
         "\n\tflashAttention = %d"
         "\n\tkvCacheType = %d",
         model_path, temperature, topK, topP, minP, repeatPenalty, 
         contextSize, nThreads, flashAttention, kvCacheType);
    
    // Store sampling params for sampler creation
    _temperature = temperature;
    _topK = topK;
    _topP = topP;
    _minP = minP;
    _repeatPenalty = repeatPenalty;

    // Load all available backends (including Vulkan if available)
    ggml_backend_load_all();
    
    // Log available backends
    LOGi("%s", getGPUInfo().c_str());

    // Check Vulkan availability
    bool vulkanAvailable = isVulkanAvailable();
    if (useGPU && !vulkanAvailable) {
        LOGw("Vulkan requested but not available, falling back to CPU");
        useGPU = false;
    }
    _useGPU = useGPU;

    // Create model params
    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = useMmap;
    model_params.use_mlock = useMlock;
    
    // GPU layer offloading
    if (useGPU && gpuLayers != 0) {
        model_params.n_gpu_layers = (gpuLayers < 0) ? 999 : gpuLayers; // -1 means all layers
        LOGi("Offloading %d layers to GPU", model_params.n_gpu_layers);
    } else {
        model_params.n_gpu_layers = 0;
    }

    _model = llama_model_load_from_file(model_path, model_params);
    if (!_model) {
        LOGe("Failed to load model from %s", model_path);
        throw std::runtime_error("loadModel() failed");
    }

    // Create context
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_batch = contextSize;
    ctx_params.n_threads = nThreads;
    ctx_params.no_perf = true;
    
    // Flash Attention - reduces memory usage, recommended for mobile
    ctx_params.flash_attn_type = flashAttention ? LLAMA_FLASH_ATTN_TYPE_ENABLED : LLAMA_FLASH_ATTN_TYPE_DISABLED;
    if (flashAttention) {
        LOGi("Flash Attention enabled");
    }
    
    // KV Cache quantization - reduces memory usage
    // kvCacheType: 1=F16, 8=Q8_0, 2=Q4_0
    ctx_params.type_k = static_cast<ggml_type>(kvCacheType);
    ctx_params.type_v = static_cast<ggml_type>(kvCacheType);
    const char* kvTypeName = (kvCacheType == 1) ? "F16" : (kvCacheType == 8) ? "Q8_0" : "Q4_0";
    LOGi("KV Cache type: %s", kvTypeName);
    
    _ctx = llama_init_from_model(_model, ctx_params);
    if (!_ctx) {
        LOGe("llama_init_from_model() returned null");
        throw std::runtime_error("llama_init_from_model() returned null");
    }

    // Create sampler chain - order matters!
    // Filter first (top-k, top-p, min-p), then adjust (temp, penalties), then sample
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    _sampler = llama_sampler_chain_init(sampler_params);
    
    // 1. Top-K: Keep only top K tokens (0 = disabled)
    if (topK > 0) {
        llama_sampler_chain_add(_sampler, llama_sampler_init_top_k(topK));
        LOGi("Sampler: Top-K = %d", topK);
    }
    
    // 2. Top-P (nucleus): Keep tokens covering P probability mass
    if (topP < 1.0f) {
        llama_sampler_chain_add(_sampler, llama_sampler_init_top_p(topP, 1));
        LOGi("Sampler: Top-P = %.2f", topP);
    }
    
    // 3. Min-P: Remove tokens with prob < minP * max_prob
    if (minP > 0.0f) {
        llama_sampler_chain_add(_sampler, llama_sampler_init_min_p(minP, 1));
        LOGi("Sampler: Min-P = %.2f", minP);
    }
    
    // 4. Temperature: Adjust randomness
    llama_sampler_chain_add(_sampler, llama_sampler_init_temp(temperature));
    LOGi("Sampler: Temperature = %.2f", temperature);
    
    // 5. Repeat penalty: Penalize repeated tokens
    if (repeatPenalty != 1.0f) {
        // Parameters: last_n tokens to check, repeat_penalty, frequency_penalty, presence_penalty
        llama_sampler_chain_add(_sampler, llama_sampler_init_penalties(
            64,              // last_n: look back 64 tokens
            repeatPenalty,   // repeat_penalty
            0.0f,            // frequency_penalty (disabled)
            0.0f             // presence_penalty (disabled)
        ));
        LOGi("Sampler: Repeat Penalty = %.2f", repeatPenalty);
    }
    
    // 6. Final sampling with random seed
    llama_sampler_chain_add(_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    _formattedMessages = std::vector<char>(llama_n_ctx(_ctx));
    _messages.clear();

    if (chatTemplate == nullptr || strlen(chatTemplate) == 0) {
        _chatTemplate = llama_model_chat_template(_model, nullptr);
    } else {
        _chatTemplate = strdup(chatTemplate);
    }
    this->_storeChats = storeChats;

    LOGi("Model loaded successfully. Using GPU: %s", _useGPU ? "YES" : "NO");
}

void LlamaVulkan::addChatMessage(const char* message, const char* role) {
    _messages.push_back({strdup(role), strdup(message)});
}

float LlamaVulkan::getResponseGenerationTime() const {
    return (float)_responseNumTokens / (_responseGenerationTime / 1e6);
}

int LlamaVulkan::getContextSizeUsed() const {
    return _nCtxUsed;
}

void LlamaVulkan::startCompletion(const char* query) {
    if (!_storeChats) {
        _formattedMessages.clear();
        _formattedMessages = std::vector<char>(llama_n_ctx(_ctx));
    }
    _responseGenerationTime = 0;
    _responseNumTokens = 0;
    addChatMessage(query, "user");
    
    // Apply chat template
    std::vector<common_chat_msg> messages;
    for (const llama_chat_message& message : _messages) {
        common_chat_msg msg;
        msg.role = message.role;
        msg.content = message.content;
        messages.push_back(msg);
    }
    common_chat_templates_inputs inputs;
    inputs.use_jinja = true;
    inputs.messages = messages;
    auto templates = common_chat_templates_init(_model, _chatTemplate);
    std::string prompt = common_chat_templates_apply(templates.get(), inputs).prompt;
    _promptTokens = common_tokenize(llama_model_get_vocab(_model), prompt, true, true);

    _batch = new llama_batch();
    _batch->token = _promptTokens.data();
    _batch->n_tokens = _promptTokens.size();
}

bool LlamaVulkan::_isValidUtf8(const char* response) {
    if (!response) return true;
    const unsigned char* bytes = (const unsigned char*)response;
    int num;
    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) return false;
            bytes += 1;
        }
    }
    return true;
}

std::string LlamaVulkan::completionLoop() {
    uint32_t contextSize = llama_n_ctx(_ctx);
    _nCtxUsed = llama_memory_seq_pos_max(llama_get_memory(_ctx), 0) + 1;
    if (_nCtxUsed + _batch->n_tokens > contextSize) {
        throw std::runtime_error("context size reached");
    }

    auto start = ggml_time_us();
    if (llama_decode(_ctx, *_batch) < 0) {
        throw std::runtime_error("llama_decode() failed");
    }

    _currToken = llama_sampler_sample(_sampler, _ctx, -1);
    if (llama_vocab_is_eog(llama_model_get_vocab(_model), _currToken)) {
        addChatMessage(strdup(_response.data()), "assistant");
        _response.clear();
        return "[EOG]";
    }
    std::string piece = common_token_to_piece(_ctx, _currToken, true);
    auto end = ggml_time_us();
    _responseGenerationTime += (end - start);
    _responseNumTokens += 1;
    _cacheResponseTokens += piece;

    _batch->token = &_currToken;
    _batch->n_tokens = 1;

    if (_isValidUtf8(_cacheResponseTokens.c_str())) {
        _response += _cacheResponseTokens;
        std::string valid_utf8_piece = _cacheResponseTokens;
        _cacheResponseTokens.clear();
        return valid_utf8_piece;
    }

    return "";
}

void LlamaVulkan::stopCompletion() {
    if (_storeChats) {
        addChatMessage(_response.c_str(), "assistant");
    }
    _response.clear();
}

bool LlamaVulkan::saveState(const char* path) {
    if (!_ctx) {
        LOGe("Cannot save state: context not initialized");
        return false;
    }
    
    LOGi("Saving state to: %s", path);
    
    // Get state size
    size_t state_size = llama_state_get_size(_ctx);
    LOGi("State size: %zu bytes (%.2f MB)", state_size, state_size / (1024.0 * 1024.0));
    
    // Allocate buffer
    std::vector<uint8_t> state_data(state_size);
    
    // Save state to buffer
    size_t saved_size = llama_state_get_data(_ctx, state_data.data(), state_size);
    if (saved_size == 0) {
        LOGe("Failed to get state data");
        return false;
    }
    
    // Write to file
    FILE* file = fopen(path, "wb");
    if (!file) {
        LOGe("Failed to open file for writing: %s", path);
        return false;
    }
    
    size_t written = fwrite(state_data.data(), 1, saved_size, file);
    fclose(file);
    
    if (written != saved_size) {
        LOGe("Failed to write complete state: wrote %zu of %zu bytes", written, saved_size);
        return false;
    }
    
    LOGi("State saved successfully: %zu bytes", saved_size);
    return true;
}

bool LlamaVulkan::loadState(const char* path) {
    if (!_ctx) {
        LOGe("Cannot load state: context not initialized");
        return false;
    }
    
    LOGi("Loading state from: %s", path);
    
    // Open file
    FILE* file = fopen(path, "rb");
    if (!file) {
        LOGe("Failed to open state file: %s", path);
        return false;
    }
    
    // Get file size
    fseek(file, 0, SEEK_END);
    size_t file_size = ftell(file);
    fseek(file, 0, SEEK_SET);
    
    LOGi("State file size: %zu bytes (%.2f MB)", file_size, file_size / (1024.0 * 1024.0));
    
    // Read file
    std::vector<uint8_t> state_data(file_size);
    size_t read_size = fread(state_data.data(), 1, file_size, file);
    fclose(file);
    
    if (read_size != file_size) {
        LOGe("Failed to read complete state: read %zu of %zu bytes", read_size, file_size);
        return false;
    }
    
    // Load state
    size_t loaded = llama_state_set_data(_ctx, state_data.data(), file_size);
    if (loaded == 0) {
        LOGe("Failed to set state data");
        return false;
    }
    
    LOGi("State loaded successfully: %zu bytes", loaded);
    return true;
}

void LlamaVulkan::clearChat() {
    LOGi("Clearing chat history (%zu messages)", _messages.size());
    
    // Free message memory
    for (llama_chat_message& message : _messages) {
        free(const_cast<char*>(message.role));
        free(const_cast<char*>(message.content));
    }
    _messages.clear();
    _formattedMessages.clear();
    _response.clear();
    _cacheResponseTokens.clear();
    
    // Reset context (clear KV cache) - use new API
    if (_ctx) {
        llama_memory_t mem = llama_get_memory(_ctx);
        if (mem) {
            llama_memory_clear(mem, true);
        }
    }
    
    _nCtxUsed = 0;
    LOGi("Chat cleared");
}

LlamaVulkan::~LlamaVulkan() {
    for (llama_chat_message& message : _messages) {
        free(const_cast<char*>(message.role));
        free(const_cast<char*>(message.content));
    }
    if (_ctx) llama_free(_ctx);
    if (_model) llama_model_free(_model);
    if (_batch) delete _batch;
    if (_sampler) llama_sampler_free(_sampler);
}
