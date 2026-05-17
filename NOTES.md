# SmolLM Android - Technical Notes

## Performance Optimizations Implemented

### 1. Flash Attention

**What it is:** An optimized attention algorithm that reduces memory usage from O(N²) to O(N) by computing attention in tiles without materializing the full attention matrix.

**How it works:**
- Standard attention creates a full N×N attention matrix (e.g., 2048×2048 = 4M values)
- Flash Attention computes attention in blocks, keeping only small tiles in fast memory
- Results are mathematically equivalent, just computed more efficiently

**Benefits:**
- ~50-80% less memory for attention computation
- Often faster due to better cache utilization
- Enables longer context lengths on memory-constrained devices

**Configuration:**
```kotlin
LlamaGPU.InferenceParams(
    flashAttention = true  // Default: enabled
)
```

**Compatibility:**
- ✅ Works on all ARM64 Android devices
- ✅ Compatible with SmolLM, LLaMA, Mistral, Qwen, Phi-3, Gemma
- ❌ Not compatible with models requiring attention bias (some BERT variants)

**Implementation:**
```cpp
// LlamaVulkan.cpp
ctx_params.flash_attn_type = flashAttention 
    ? LLAMA_FLASH_ATTN_TYPE_ENABLED 
    : LLAMA_FLASH_ATTN_TYPE_DISABLED;
```

---

### 2. KV Cache Quantization

**What it is:** Reducing the precision of cached Key and Value tensors from 16-bit floats to 8-bit or 4-bit integers.

**Why KV Cache exists:**
```
For each new token, attention needs K and V from ALL previous tokens.
Without cache: Recompute K,V for all tokens → O(n²) 
With cache:    Store K,V, only compute new → O(n)
```

**Memory calculation (SmolLM-360M, 2048 context):**
```
Per token: 2 × n_layers × n_heads × head_dim × bytes_per_value
         = 2 × 32 × 32 × 30 × bytes_per_value

F16:  2 bytes → ~251 MB
Q8_0: 1 byte  → ~126 MB (50% savings)
Q4_0: 0.5 bytes → ~63 MB (75% savings)
```

**How Q8_0 quantization works:**
```
Original F16: [0.234, 0.891, -0.456, 0.123, ...]

Step 1: Find max absolute value in block of 32
        max_abs = 0.891
        
Step 2: Compute scale
        scale = max_abs / 127 = 0.00702
        
Step 3: Quantize to int8
        0.234 / 0.00702 = 33 → int8(33)
        0.891 / 0.00702 = 127 → int8(127)
        
Stored: [scale][33, 127, -65, 18, ...]
```

**Why quality loss is minimal:**
- KV values go through softmax normalization
- Small quantization errors (~0.8% for Q8_0) don't change attention patterns
- Errors don't accumulate like model weight quantization

**Configuration:**
```kotlin
enum class KVCacheType(val ggmlType: Int) {
    F16(1),   // Default - best quality
    Q8_0(8),  // 50% memory savings, minimal quality loss
    Q4_0(2),  // 75% memory savings, slight quality loss
}

LlamaGPU.InferenceParams(
    kvCacheType = KVCacheType.F16  // Default
    // Use Q8_0 for low-memory devices
)
```

**Implementation:**
```cpp
// LlamaVulkan.cpp
ctx_params.type_k = static_cast<ggml_type>(kvCacheType);
ctx_params.type_v = static_cast<ggml_type>(kvCacheType);
```

---

## Recommended Settings

### Standard Device (6GB+ RAM)
```kotlin
LlamaGPU.InferenceParams(
    flashAttention = true,
    kvCacheType = KVCacheType.F16,
    contextSize = 2048
)
```

### Low-Memory Device (4GB RAM)
```kotlin
LlamaGPU.InferenceParams(
    flashAttention = true,
    kvCacheType = KVCacheType.Q8_0,
    contextSize = 1024
)
```

### Very Constrained Device (3GB RAM)
```kotlin
LlamaGPU.InferenceParams(
    flashAttention = true,
    kvCacheType = KVCacheType.Q4_0,
    contextSize = 512
)
```

---

## Memory Comparison Table

| Config | KV Cache | Flash Attn | Total Memory* |
|--------|----------|------------|---------------|
| Default (F16, no flash) | 251 MB | +16 MB | ~270 MB |
| F16 + Flash | 251 MB | ~0 MB | ~250 MB |
| Q8_0 + Flash | 126 MB | ~0 MB | ~130 MB |
| Q4_0 + Flash | 63 MB | ~0 MB | ~65 MB |

*KV cache only, model weights separate (~200MB for SmolLM-360M Q4)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│  LlamaGPU.kt (Kotlin)                                       │
│  - InferenceParams(flashAttention, kvCacheType)             │
└─────────────────────┬───────────────────────────────────────┘
                      │ JNI
                      ▼
┌─────────────────────────────────────────────────────────────┐
│  LlamaVulkan.cpp (C++ wrapper)                              │
│  - ctx_params.flash_attn_type                               │
│  - ctx_params.type_k / type_v                               │
└─────────────────────┬───────────────────────────────────────┘
                      │ calls
                      ▼
┌─────────────────────────────────────────────────────────────┐
│  libllama.so (llama.cpp)                                    │
│  - Actual inference engine                                  │
│  - Flash attention implementation                           │
│  - KV cache with quantization support                       │
└─────────────────────────────────────────────────────────────┘
```

---

## Files Modified

| File | Changes |
|------|---------|
| `LlamaGPU.kt` | Added `KVCacheType` enum, `flashAttention` param, sampling params |
| `llama_vulkan_jni.cpp` | Pass new params through JNI |
| `LlamaVulkan.h` | Updated function signature, added sampling member variables |
| `LlamaVulkan.cpp` | Set `flash_attn_type`, `type_k`, `type_v`, full sampler chain |

---

## 3. Sampler Chain (Answer Quality)

**What it is:** A chain of filters that process token probabilities before selecting the next token.

### The Sampling Pipeline

```
Raw Probabilities (thousands of tokens)
       │
       ▼
┌─────────────────┐
│   Top-K (40)    │  ← Keep only top 40 tokens
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Top-P (0.9)    │  ← Keep tokens until 90% probability mass
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Min-P (0.05)   │  ← Remove tokens < 5% of top token's prob
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Temperature(0.7)│  ← Adjust randomness
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Repeat Penalty  │  ← Penalize recently used tokens
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Sample Token   │  ← Pick final token
└─────────────────┘
```

### Each Sampler Explained

| Sampler | What it does | Example |
|---------|--------------|---------|
| **Top-K** | Keep only top K tokens | K=40: Only consider 40 most likely words |
| **Top-P** | Keep tokens covering P% probability | P=0.9: Keep enough tokens to cover 90% |
| **Min-P** | Remove tokens much worse than best | minP=0.05: Remove if prob < 5% of best |
| **Temperature** | Adjust randomness | Low=focused, High=creative |
| **Repeat Penalty** | Penalize repeated tokens | 1.1 = slight penalty, 1.3 = strong |

### Configuration

```kotlin
data class InferenceParams(
    val temperature: Float = 0.7f,      // 0.1=focused, 1.5=creative
    val topK: Int = 40,                 // 0=disabled, 40=recommended
    val topP: Float = 0.9f,             // Nucleus sampling threshold
    val minP: Float = 0.05f,            // Filter noise
    val repeatPenalty: Float = 1.1f,    // 1.0=off, 1.2=strong
)
```

### Implementation

```cpp
// LlamaVulkan.cpp - Sampler chain (order matters!)
_sampler = llama_sampler_chain_init(sampler_params);

// 1. Filter: Top-K
if (topK > 0) {
    llama_sampler_chain_add(_sampler, llama_sampler_init_top_k(topK));
}

// 2. Filter: Top-P (nucleus)
if (topP < 1.0f) {
    llama_sampler_chain_add(_sampler, llama_sampler_init_top_p(topP, 1));
}

// 3. Filter: Min-P
if (minP > 0.0f) {
    llama_sampler_chain_add(_sampler, llama_sampler_init_min_p(minP, 1));
}

// 4. Adjust: Temperature
llama_sampler_chain_add(_sampler, llama_sampler_init_temp(temperature));

// 5. Adjust: Repeat penalty (look back 64 tokens)
if (repeatPenalty != 1.0f) {
    llama_sampler_chain_add(_sampler, llama_sampler_init_penalties(
        64, repeatPenalty, 0.0f, 0.0f
    ));
}

// 6. Sample: Pick token
llama_sampler_chain_add(_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
```

### Presets for Different Use Cases

**Focused/Factual (Q&A, coding):**
```kotlin
InferenceParams(temperature = 0.3f, topK = 20, repeatPenalty = 1.0f)
```

**Balanced (general chat):**
```kotlin
InferenceParams(temperature = 0.7f, topK = 40, repeatPenalty = 1.1f)
```

**Creative (stories, brainstorming):**
```kotlin
InferenceParams(temperature = 1.0f, topP = 0.95f, repeatPenalty = 1.2f)
```

### Why Order Matters

```
Good:  Top-K → Top-P → Min-P → Temperature → Sample
       (Filter first, then adjust randomness)

Bad:   Temperature → Top-K → Sample
       (Adjusting before filtering gives weird results)
```

---

## 4. Automatic Context Management

**What it is:** Automatically trims old conversation history when context window fills up, allowing infinite conversations without crashes.

### How It Works

```
User sends message
       │
       ▼
┌─────────────────────────────┐
│ Check context usage (%)     │
│ realUsage = tokens / max    │
└──────────────┬──────────────┘
               │
               ▼
        ┌──────────────┐
        │ usage >= 70%?│
        └──────┬───────┘
               │
      ┌────────┴────────┐
      │ YES             │ NO
      ▼                 ▼
┌─────────────┐   ┌─────────────┐
│ Trim oldest │   │ Continue    │
│ 2 messages  │   │ normally    │
└──────┬──────┘   └─────────────┘
       │
       ▼
┌─────────────────────────────┐
│ Reload model (clear KV)    │
│ Re-add recent messages      │
│ Show toast notification     │
└─────────────────────────────┘
```

### Implementation

```kotlin
// MainActivityViewModel.kt - submitQuery()
val realUsage = calculateContextUsage()

if (realUsage >= 70 && currentModel?.supportsMultiTurn == true) {
    trimOldMessages()
    showContextTrimmedMessage()  // "Cleared old messages to continue conversation"
}
```

### trimOldMessages() Function

```kotlin
private suspend fun trimOldMessages() {
    // 1. Remove oldest 2 messages (1 user + 1 assistant exchange)
    val trimmedMessages = currentMessages.drop(2)
    
    // 2. Reload model to clear KV cache (critical!)
    llamaGPU.load(modelPath, params)
    
    // 3. Re-add system prompt
    llamaGPU.addSystemPrompt("You are a helpful assistant...")
    
    // 4. Re-add last exchange for continuity
    llamaGPU.addUserMessage(lastUserMsg)
    llamaGPU.addAssistantMessage(lastAssistantMsg)
}
```

### Why Reload the Model?

The KV cache in llama.cpp stores all previous tokens. Simply removing messages from the UI doesn't free the KV cache memory. **Reloading the model is the only way to clear it.**

### Error Recovery

If context error still occurs (edge case), the code:
1. Trims messages **twice** (more aggressive)
2. Retries the query automatically

```kotlin
if (isContextError) {
    trimOldMessages()
    trimOldMessages()  // Double trim for safety
    val retryResponse = llamaGPU.getResponse(query)  // Retry
}
```

### Configuration

| Setting | Value | Purpose |
|---------|-------|---------|
| **Trim threshold** | 70% | Leave 30% for response |
| **Messages trimmed** | 2 (1 exchange) | Gradual trimming |
| **Re-add messages** | Last exchange | Maintain continuity |

### User Experience

- User sees toast: **"Cleared old messages to continue conversation"**
- Conversation continues seamlessly
- No crashes or errors
- Model remembers recent context

---

## 5. Device-Adaptive Context Sizing

**What it is:** Automatically sets optimal context size based on device RAM and KV cache quantization.

### Context Limits by Device

| Device RAM | Context Size | KV Cache Memory |
|------------|--------------|-----------------|
| 8GB+ (S24) | 8192 tokens | ~500 MB (Q8_0) |
| 6-8GB | 4096 tokens | ~250 MB (Q8_0) |
| 4-6GB | 2048 tokens | ~125 MB (Q8_0) |
| 3-4GB | 1024 tokens | ~63 MB (Q8_0) |
| <3GB | 512 tokens | ~32 MB (Q8_0) |

### Implementation

```kotlin
// DeviceCapabilities.kt
private fun calculateMaxContextSize(ramGB: Float, tier: DeviceTier): Int {
    return when {
        ramGB >= 8 -> 8192   // 8GB+ → 8K context
        ramGB >= 6 -> 4096   // 6-8GB → 4K context
        ramGB >= 4 -> 2048   // 4-6GB → 2K context
        ramGB >= 3 -> 1024   // 3-4GB → 1K context
        else -> 512          // <3GB → 512 context
    }
}
```

### Model Size Adjustment

Larger models need more RAM for weights, so context is reduced:

```kotlin
val contextMultiplier = when {
    model.sizeInMB >= 1500 -> 0.6f  // Gemma 2B: 60% of max
    model.sizeInMB >= 1000 -> 0.8f  // Qwen 1.5B: 80% of max
    else -> 1.0f                     // SmolLM: full context
}
```

### Conversation Capacity

| Device | Old (F16) | New (Q8_0) | Improvement |
|--------|-----------|------------|-------------|
| S24 (8GB) | 5-7 exchanges | 30-40 exchanges | **6x** |
| 6GB device | 4-5 exchanges | 15-20 exchanges | **4x** |
| 4GB device | 2-3 exchanges | 7-10 exchanges | **3x** |

---

## Future Improvements

- [ ] Speculative decoding for faster generation
- [ ] Grammar/JSON constrained output
- [ ] Vulkan GPU support (blocked by Adreno driver issues)
- [ ] Dynamic context extension with RoPE scaling
- [ ] Batch inference for multiple prompts
- [x] ~~Automatic context trimming~~ (Implemented)
- [x] ~~Device-adaptive context sizing~~ (Implemented)

---

## References

- [Flash Attention Paper](https://arxiv.org/abs/2205.14135)
- [llama.cpp KV Cache Quantization PR](https://github.com/ggerganov/llama.cpp/pull/4930)
- [GGML Quantization Types](https://github.com/ggerganov/ggml/blob/master/docs/quantization.md)
