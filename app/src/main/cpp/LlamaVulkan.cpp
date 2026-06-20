#include "LlamaVulkan.h"
#include <android/log.h>
#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <iomanip>
#include <iostream>
#include <pthread.h>
#include <sched.h>
#include <sstream>
#include <thread>
#include <unistd.h>
#include <vector>

#define TAG "[LlamaVulkan]"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGw(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// ─────────────────────────────────────────────────────────────────────────────
// CPU AFFINITY: Pin threads to performance cores
// ─────────────────────────────────────────────────────────────────────────────
// Most Android phones use big.LITTLE: a mix of perf cores (high freq) and
// efficiency cores (low freq). By default the kernel schedules llama.cpp's
// worker threads across ALL cores — any thread that lands on an efficiency
// core drags down the entire prefill/decode batch (synchronization at every
// matmul). Pinning to only the perf cores typically yields 1.5-2x speedup on
// big.LITTLE devices with zero quality cost.
//
// Detection strategy: read cpuinfo_max_freq for each core, pick those at or
// above 90% of the max frequency. This automatically picks the "prime + perf"
// cluster on phones like Snapdragon 8/7-series and Dimensity 9000-series.
//
// Must be called from the THREAD that will later spawn llama.cpp workers —
// they inherit the cpuset on creation (Linux default behavior).
//
// Returns the number of cores pinned, so caller can size its threadpool to
// match (avoiding catastrophic over-subscription where N threads thrash on
// M<N cores — e.g. 6 threads on 2 perf cores is *worse* than no pinning).
static int pinToPerformanceCores() {
    int numCores = static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN));
    if (numCores <= 0) return 0;

    std::vector<long> coreFreqs(numCores, 0);
    long maxFreq = 0;
    for (int i = 0; i < numCores; i++) {
        char path[128];
        std::snprintf(path, sizeof(path),
                      "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i);
        FILE* f = std::fopen(path, "r");
        if (f) {
            long freq = 0;
            if (std::fscanf(f, "%ld", &freq) == 1) {
                coreFreqs[i] = freq;
                if (freq > maxFreq) maxFreq = freq;
            }
            std::fclose(f);
        }
    }

    if (maxFreq <= 0) {
        LOGw("CPU affinity: could not read cpufreq, leaving default scheduling");
        return 0;
    }

    // Pin to cores at >= 90% of max frequency (catches prime + perf cluster).
    long threshold = maxFreq * 9 / 10;
    cpu_set_t mask;
    CPU_ZERO(&mask);
    int pinned = 0;
    std::string pinnedList;
    for (int i = 0; i < numCores; i++) {
        if (coreFreqs[i] >= threshold) {
            CPU_SET(i, &mask);
            pinned++;
            if (!pinnedList.empty()) pinnedList += ",";
            pinnedList += std::to_string(i);
        }
    }

    if (pinned == 0) {
        LOGw("CPU affinity: no cores above threshold, skipping pin");
        return 0;
    }

    // Apply affinity to the CURRENT thread. Child worker threads created by
    // llama.cpp/ggml after this call will inherit the same cpuset on Linux.
    if (sched_setaffinity(0, sizeof(mask), &mask) == 0) {
        LOGi("CPU affinity: pinned to %d perf cores [%s] (max=%ldHz, threshold=%ldHz)",
             pinned, pinnedList.c_str(), maxFreq, threshold);
        return pinned;
    } else {
        LOGw("CPU affinity: sched_setaffinity failed (errno=%d)", errno);
        return 0;
    }
}

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
    // Pin this thread (and any worker threads spawned during model load) to perf cores.
    // Llama.cpp's ggml threadpool inherits affinity from its creator on Linux/Android.
    int pinnedCores = pinToPerformanceCores();

    // CRITICAL: cap nThreads to the perf-core count to avoid over-subscription.
    // Spawning more threads than available cores causes constant preemption and
    // is significantly *worse* than running fewer threads. On a 2-perf-core device
    // calling with nThreads=6 made TTFT worse than no affinity at all.
    if (pinnedCores > 0 && nThreads > pinnedCores) {
        LOGi("Capping nThreads %d -> %d (perf core count) to avoid oversubscription",
             nThreads, pinnedCores);
        nThreads = pinnedCores;
    }
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
    // ⚡ Prefill thread count. Normally prefill is compute-bound and benefits from
    // more threads. BUT if we've pinned to a restricted cpuset (perf cores only),
    // spawning more threads than available cores causes oversubscription — those
    // threads time-slice on the same cores and *slow down* prefill.
    //
    // Rule:
    //   - If affinity is set (pinnedCores > 0): n_threads_batch == nThreads (== pinnedCores)
    //   - If affinity is NOT set: n_threads_batch can be 2x nThreads up to hwCores
    int hwCores = static_cast<int>(std::thread::hardware_concurrency());
    if (hwCores <= 0) hwCores = nThreads;
    int batchThreads;
    if (pinnedCores > 0) {
        // Affinity restricts us to pinnedCores. Going above this just causes contention.
        batchThreads = nThreads;
    } else {
        batchThreads = std::max(nThreads, std::min(hwCores, nThreads * 2));
    }
    ctx_params.n_threads_batch = batchThreads;
    LOGi("Threading: n_threads=%d (generation), n_threads_batch=%d (prefill, hwCores=%d, pinned=%d)",
         nThreads, batchThreads, hwCores, pinnedCores);
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
    // Re-pin the CURRENT inference thread to perf cores. The Kotlin Flow runs on
    // a coroutine dispatcher thread which may differ between calls, so we ensure
    // affinity is set per-inference. The native ggml threadpool was already created
    // during loadModel with proper affinity; this just keeps the JNI caller pinned
    // so any synchronization the calling thread does also runs on perf cores.
    pinToPerformanceCores();
    if (!_storeChats) {
        _formattedMessages.clear();
        _formattedMessages = std::vector<char>(llama_n_ctx(_ctx));
    }
    _responseGenerationTime = 0;
    _responseNumTokens = 0;
    addChatMessage(query, "user");
    
    LOGi("=== START COMPLETION: _nCtxUsed = %d, _contextShifted = %s ===", 
         _nCtxUsed, _contextShifted ? "true" : "false");
    
    size_t newTokensStart = 0;
    size_t newTokensCount = 0;
    
    // FAST PATH: After context shift, only process the new query
    if (_contextShifted) {
        // _nCtxUsed already set correctly by shiftContext()
        LOGi("Fast path after context shift: processing only new query");
        LOGi("  _nCtxUsed before fast path: %d", _nCtxUsed);
        
        // Tokenize ONLY the new user message with chat template
        std::vector<common_chat_msg> newMessages;
        common_chat_msg msg;
        msg.role = "user";
        msg.content = query;
        newMessages.push_back(msg);
        
        common_chat_templates_inputs inputs;
        inputs.use_jinja = true;
        inputs.messages = newMessages;
        inputs.add_generation_prompt = true;  // Add assistant prompt
        auto templates = common_chat_templates_init(_model, _chatTemplate);
        std::string newPrompt = common_chat_templates_apply(templates.get(), inputs).prompt;
        
        // Tokenize only the new query
        std::vector<llama_token> newTokens = common_tokenize(llama_model_get_vocab(_model), newPrompt, false, true);
        
        LOGi("  _cachedTokens.size() before append: %zu", _cachedTokens.size());
        
        // Append to cached tokens
        _cachedTokens.insert(_cachedTokens.end(), newTokens.begin(), newTokens.end());
        _promptTokens = newTokens;  // Only process these new tokens
        
        newTokensStart = 0;
        newTokensCount = newTokens.size();
        
        LOGi("  _cachedTokens.size() after append: %zu", _cachedTokens.size());
        LOGi("  Context shift fast path: processing %zu new tokens only", newTokensCount);
        LOGi("  _nCtxUsed after fast path (unchanged): %d", _nCtxUsed);
        
        // Clear the flag for next time
        _contextShifted = false;
        
    } else {
        // NORMAL PATH: Full prompt regeneration with caching
        LOGi("Normal path: regenerating full prompt");
        LOGi("  _nCtxUsed before normal path: %d", _nCtxUsed);
        LOGi("  _cachedTokens.size(): %zu", _cachedTokens.size());
        
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
        
        LOGi("  _promptTokens.size(): %zu", _promptTokens.size());

        // PROMPT CACHING: Find how many tokens match the cached tokens
        size_t commonPrefix = 0;
        size_t minLen = std::min(_cachedTokens.size(), _promptTokens.size());
        for (size_t i = 0; i < minLen; i++) {
            if (_cachedTokens[i] == _promptTokens[i]) {
                commonPrefix++;
            } else {
                break;
            }
        }
        
        // If cache diverged, we need to clear KV cache from that point
        if (commonPrefix < _cachedTokens.size()) {
            // Clear KV cache entries after the common prefix
            llama_memory_t mem = llama_get_memory(_ctx);
            if (mem && commonPrefix > 0) {
                // Remove tokens from commonPrefix onwards
                llama_memory_seq_rm(mem, 0, commonPrefix, -1);
            } else if (mem && commonPrefix == 0) {
                // Complete mismatch, clear everything
                llama_memory_clear(mem, true);
            }
        }
        
        // Calculate how many NEW tokens to process
        newTokensStart = commonPrefix;
        newTokensCount = _promptTokens.size() - commonPrefix;
        
        LOGi("  commonPrefix: %zu, newTokensCount: %zu", commonPrefix, newTokensCount);
        LOGi("  Prompt caching: %zu cached, %zu new tokens (total: %zu)", 
             commonPrefix, newTokensCount, _promptTokens.size());
        
        // Update cached tokens for next time
        _cachedTokens = _promptTokens;
        
        // Initialize _nCtxUsed on first query (when it's 0)
        // Otherwise keep the current value (it's tracking correctly)
        int oldNCtxUsed = _nCtxUsed;
        if (_nCtxUsed == 0 && commonPrefix > 0) {
            _nCtxUsed = commonPrefix;
            LOGi("  Initialized _nCtxUsed: %d -> %d (first query with cache)", oldNCtxUsed, _nCtxUsed);
        } else {
            LOGi("  Keeping _nCtxUsed: %d (will add %zu new tokens in completionLoop)", _nCtxUsed, newTokensCount);
        }
    }
    
    // Create batch with only NEW tokens
    _batch = new llama_batch();
    if (newTokensCount > 0) {
        // Point to the new tokens portion
        _batch->token = _promptTokens.data() + newTokensStart;
        _batch->n_tokens = newTokensCount;
    } else {
        // Edge case: all tokens cached (shouldn't happen normally)
        _batch->token = _promptTokens.data() + _promptTokens.size() - 1;
        _batch->n_tokens = 1;
    }
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
    
    // Only increment context on first call (processing prompt) or when generating new tokens
    // For prompt: _batch->n_tokens > 1, for generation: _batch->n_tokens == 1
    int tokensToAdd = _batch->n_tokens;
    int oldNCtxUsed = _nCtxUsed;
    
    if (_nCtxUsed + tokensToAdd > contextSize) {
        throw std::runtime_error("context size reached");
    }

    auto start = ggml_time_us();
    if (llama_decode(_ctx, *_batch) < 0) {
        throw std::runtime_error("llama_decode() failed");
    }
    
    // Increment context usage after successful decode
    _nCtxUsed += tokensToAdd;
    
    if (tokensToAdd > 1) {
        // Processing prompt batch
        LOGi("completionLoop: processed %d prompt tokens, _nCtxUsed: %d -> %d", 
             tokensToAdd, oldNCtxUsed, _nCtxUsed);
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
        
        // Update cached tokens to include the assistant's response
        // This ensures next query can reuse the full conversation KV cache
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
        std::string fullPrompt = common_chat_templates_apply(templates.get(), inputs).prompt;
        _cachedTokens = common_tokenize(llama_model_get_vocab(_model), fullPrompt, true, true);
        
        LOGi("Updated prompt cache: %zu tokens", _cachedTokens.size());
        
        // Update _nCtxUsed to match the actual KV cache size
        // This ensures accurate context tracking even when response is stopped midway
        int oldNCtxUsed = _nCtxUsed;
        _nCtxUsed = _cachedTokens.size();
        LOGi("Updated _nCtxUsed after stop: %d -> %d (synced with cache)", oldNCtxUsed, _nCtxUsed);
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
    _cachedTokens.clear();  // Clear prompt cache
    
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

int LlamaVulkan::shiftContext(int keepFirstN, int removeNextN) {
    if (!_ctx) {
        LOGe("Cannot shift context: context not initialized");
        return -1;
    }
    
    llama_memory_t mem = llama_get_memory(_ctx);
    if (!mem) {
        LOGe("Cannot shift context: memory not available");
        return -1;
    }
    
    int currentUsed = llama_memory_seq_pos_max(mem, 0) + 1;
    LOGi("Context shift: current=%d, keepFirst=%d, removeNext=%d", 
         currentUsed, keepFirstN, removeNextN);
    
    if (keepFirstN + removeNextN > currentUsed) {
        LOGw("Shift request exceeds context size, adjusting");
        removeNextN = currentUsed - keepFirstN;
        if (removeNextN <= 0) {
            LOGw("Nothing to remove");
            return currentUsed;
        }
    }
    
    // Remove tokens from position keepFirstN to keepFirstN + removeNextN
    // This preserves tokens 0 to keepFirstN-1 (e.g., system prompt)
    // and shifts tokens after keepFirstN + removeNextN forward
    bool success = llama_memory_seq_rm(mem, 0, keepFirstN, keepFirstN + removeNextN);
    if (!success) {
        LOGe("llama_memory_seq_rm failed");
        return -1;
    }
    
    // Update cached tokens to reflect the removal
    // Keep tokens before keepFirstN and after (keepFirstN + removeNextN)
    if (_cachedTokens.size() > (size_t)(keepFirstN + removeNextN)) {
        // Erase the removed section
        _cachedTokens.erase(
            _cachedTokens.begin() + keepFirstN,
            _cachedTokens.begin() + keepFirstN + removeNextN
        );
        LOGi("Updated cached tokens: %zu remaining", _cachedTokens.size());
    } else {
        // Can't update properly - keep what we have, incremental processing will handle it
        LOGi("Keeping partial cached tokens: %zu (incremental processing will continue)", _cachedTokens.size());
    }
    
    // Update context size - after removing tokens, the new size is reduced
    _nCtxUsed = currentUsed - removeNextN;
    
    // Set flag to skip full prompt regeneration on next query
    _contextShifted = true;
    
    LOGi("Context shifted successfully: now using %d tokens (freed %d)", 
         _nCtxUsed, removeNextN);
    
    return _nCtxUsed;
}

void LlamaVulkan::removeOldestMessages(int count) {
    if (count <= 0 || _messages.empty()) return;
    
    int toRemove = std::min(count, (int)_messages.size());
    LOGi("Removing %d oldest messages from chat history", toRemove);
    
    // Free memory for messages being removed
    for (int i = 0; i < toRemove; i++) {
        free(const_cast<char*>(_messages[i].role));
        free(const_cast<char*>(_messages[i].content));
    }
    
    // Erase from vector
    _messages.erase(_messages.begin(), _messages.begin() + toRemove);
    LOGi("Chat history now has %zu messages", _messages.size());
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

// Background summarization implementation
std::string LlamaVulkan::summarizeMessages(int startIdx, int count) {
    if (startIdx < 0 || count <= 0 || startIdx + count > _messages.size()) {
        LOGe("Invalid summarization range: startIdx=%d, count=%d, total=%zu", 
             startIdx, count, _messages.size());
        return "";
    }
    
    LOGi("=== SUMMARIZING MESSAGES: startIdx=%d, count=%d ===", startIdx, count);
    
    // Build conversation text from messages
    std::string conversationText = "";
    for (int i = startIdx; i < startIdx + count; i++) {
        const auto& msg = _messages[i];
        conversationText += std::string(msg.role) + ": " + std::string(msg.content) + "\n\n";
    }
    
    // Create summarization prompt
    std::string summaryPrompt = 
        "You are a conversation summarizer. Create a concise summary of the following conversation, "
        "preserving all important facts, context, and key points.\n\n"
        "Conversation:\n" + conversationText + "\n"
        "Provide a summary in 2-3 sentences:\n";
    
    LOGi("Summarization prompt length: %zu characters", summaryPrompt.length());
    
    // Tokenize the prompt
    std::vector<llama_token> promptTokens = common_tokenize(
        llama_model_get_vocab(_model), 
        summaryPrompt, 
        true, true
    );
    
    LOGi("Prompt tokens: %zu", promptTokens.size());
    
    // Create batch for summarization
    llama_batch summaryBatch = {};
    summaryBatch.token = promptTokens.data();
    summaryBatch.n_tokens = promptTokens.size();
    
    // Process the prompt
    if (llama_decode(_ctx, summaryBatch) < 0) {
        LOGe("Failed to decode summarization prompt");
        return "";
    }
    
    // Generate summary (max 150 tokens)
    std::string summary = "";
    int maxSummaryTokens = 150;
    llama_token currentToken;
    
    for (int i = 0; i < maxSummaryTokens; i++) {
        currentToken = llama_sampler_sample(_sampler, _ctx, -1);
        
        // Check for end of generation
        if (llama_vocab_is_eog(llama_model_get_vocab(_model), currentToken)) {
            break;
        }
        
        // Convert token to text
        std::string piece = common_token_to_piece(_ctx, currentToken, true);
        summary += piece;
        
        // Prepare for next token
        summaryBatch.token = &currentToken;
        summaryBatch.n_tokens = 1;
        
        if (llama_decode(_ctx, summaryBatch) < 0) {
            LOGe("Failed to decode during summary generation");
            break;
        }
    }
    
    // Trim whitespace
    summary.erase(0, summary.find_first_not_of(" \n\r\t"));
    summary.erase(summary.find_last_not_of(" \n\r\t") + 1);
    
    LOGi("Generated summary (%zu chars): %s", summary.length(), summary.c_str());
    
    return summary;
}

void LlamaVulkan::rebuildCacheWithSummary(const char* summary, int keepRecentN) {
    LOGi("=== REBUILDING CACHE WITH SUMMARY ===");
    LOGi("Summary: %s", summary);
    LOGi("Keeping recent %d messages", keepRecentN);
    
    if (keepRecentN < 0 || keepRecentN > _messages.size()) {
        LOGe("Invalid keepRecentN: %d (total messages: %zu)", keepRecentN, _messages.size());
        return;
    }
    
    // Clear KV cache
    llama_memory_t mem = llama_get_memory(_ctx);
    if (mem) {
        llama_memory_clear(mem, true);
        LOGi("KV cache cleared");
    }
    
    // Build new message list: [System + Summary + Recent messages]
    std::vector<llama_chat_message> newMessages;
    
    // Keep system message if it exists
    if (!_messages.empty() && strcmp(_messages[0].role, "system") == 0) {
        newMessages.push_back(_messages[0]);
    }
    
    // Add summary as a system message
    llama_chat_message summaryMsg;
    summaryMsg.role = strdup("system");
    summaryMsg.content = strdup((std::string("Earlier conversation summary: ") + summary).c_str());
    newMessages.push_back(summaryMsg);
    
    // Add recent messages
    int startIdx = _messages.size() - keepRecentN;
    for (int i = startIdx; i < _messages.size(); i++) {
        newMessages.push_back(_messages[i]);
    }
    
    LOGi("New message count: %zu (was %zu)", newMessages.size(), _messages.size());
    
    // Update internal message list
    _messages = newMessages;
    
    // Rebuild prompt with chat template
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
    std::string fullPrompt = common_chat_templates_apply(templates.get(), inputs).prompt;
    
    // Tokenize full prompt
    _cachedTokens = common_tokenize(llama_model_get_vocab(_model), fullPrompt, true, true);
    
    LOGi("Rebuilding KV cache with %zu tokens", _cachedTokens.size());
    
    // Process all tokens to rebuild KV cache
    llama_batch rebuildBatch = {};
    rebuildBatch.token = _cachedTokens.data();
    rebuildBatch.n_tokens = _cachedTokens.size();
    
    if (llama_decode(_ctx, rebuildBatch) < 0) {
        LOGe("Failed to rebuild KV cache");
        return;
    }
    
    // Update context usage
    _nCtxUsed = _cachedTokens.size();
    
    LOGi("KV cache rebuilt successfully: %d tokens", _nCtxUsed);
    LOGi("Context usage: %d%%", (_nCtxUsed * 100) / llama_n_ctx(_ctx));
}
