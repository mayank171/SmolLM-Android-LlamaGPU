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

void LlamaVulkan::loadModel(const char* model_path, float minP, float temperature, 
                            bool storeChats, long contextSize, const char* chatTemplate, 
                            int nThreads, bool useMmap, bool useMlock, 
                            bool useGPU, int gpuLayers) {
    LOGi("Loading model with Vulkan support:"
         "\n\tmodel_path = %s"
         "\n\tminP = %f"
         "\n\ttemperature = %f"
         "\n\tstoreChats = %d"
         "\n\tcontextSize = %li"
         "\n\tnThreads = %d"
         "\n\tuseGPU = %d"
         "\n\tgpuLayers = %d",
         model_path, minP, temperature, storeChats, contextSize, nThreads, useGPU, gpuLayers);

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
    
    _ctx = llama_init_from_model(_model, ctx_params);
    if (!_ctx) {
        LOGe("llama_init_from_model() returned null");
        throw std::runtime_error("llama_init_from_model() returned null");
    }

    // Create sampler
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    _sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(_sampler, llama_sampler_init_temp(temperature));
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
