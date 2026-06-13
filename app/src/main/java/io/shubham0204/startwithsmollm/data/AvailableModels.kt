package io.shubham0204.startwithsmollm.data

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val sizeInMB: Int,
    val downloadUrl: String,
    val fileName: String,
    val parameters: String,
    val quantization: String,
    val maxContextSize: Int = 2048,
    val supportsMultiTurn: Boolean = true
)

object AvailableModels {
    val models = listOf(
        // Ultra-Small Models (< 500MB) - Best for low-end devices
        ModelInfo(
            id = "smollm-360m-q4_0",
            name = "SmolLM 360M",
            description = "Smallest and fastest. Basic conversations only.",
            sizeInMB = 219,
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM-360M-Instruct-GGUF/resolve/main/smollm-360m-instruct-add-basics-q4_0.gguf",
            fileName = "smollm-360m-instruct-add-basics-q4_0.gguf",
            parameters = "360M",
            quantization = "Q4_0",
            maxContextSize = 2048
        ),
        ModelInfo(
            id = "qwen2.5-0.5b-q4_k_m",
            name = "Qwen 2.5 0.5B",
            description = "Fast and efficient. Good for simple tasks and quick responses.",
            sizeInMB = 491,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            parameters = "0.5B",
            quantization = "Q4_K_M",
            maxContextSize = 32768
        ),
        
        // Small Models (500MB - 1GB) - Good balance
        ModelInfo(
            id = "tinyllama-1.1b-q4_k_m",
            name = "TinyLlama 1.1B",
            description = "Ultra-fast responses. Trained on 3T tokens. Great for quick tasks.",
            sizeInMB = 669,
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            parameters = "1.1B",
            quantization = "Q4_K_M",
            maxContextSize = 2048
        ),
        
        // Medium Models (1GB - 2GB) - Recommended for most devices
        ModelInfo(
            id = "qwen2.5-1.5b-q4_k_m",
            name = "Qwen 2.5 1.5B",
            description = "Balanced performance. Better reasoning and knowledge.",
            sizeInMB = 1100,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            parameters = "1.5B",
            quantization = "Q4_K_M",
            maxContextSize = 32768
        ),
        ModelInfo(
            id = "gemma-2-2b-q4_k_m",
            name = "Gemma 2 2B",
            description = "Google's model. Single-turn only (no conversation memory).",
            sizeInMB = 1500,
            downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            fileName = "gemma-2-2b-it-Q4_K_M.gguf",
            parameters = "2B",
            quantization = "Q4_K_M",
            maxContextSize = 8192,
            supportsMultiTurn = false
        ),
        
        // Large Models (2GB - 3GB) - High quality
        ModelInfo(
            id = "phi-3.5-mini-q4_k_m",
            name = "Phi-3.5 Mini",
            description = "Microsoft's latest. Excellent reasoning and coding abilities.",
            sizeInMB = 2300,
            downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
            fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            parameters = "3.8B",
            quantization = "Q4_K_M",
            maxContextSize = 4096
        ),
        ModelInfo(
            id = "llama-3.2-3b-q4_k_m",
            name = "Llama 3.2 3B",
            description = "Meta's mobile-optimized model. Excellent general performance.",
            sizeInMB = 2000,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            parameters = "3B",
            quantization = "Q4_K_M",
            maxContextSize = 8192
        ),
        ModelInfo(
            id = "qwen2.5-3b-q4_k_m",
            name = "Qwen 2.5 3B",
            description = "Powerful reasoning. Best quality in 3B class.",
            sizeInMB = 2100,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            parameters = "3B",
            quantization = "Q4_K_M",
            maxContextSize = 32768
        ),
        
        // Extra Large Models (4GB+) - For high-end devices only
        ModelInfo(
            id = "mistral-7b-instruct-q4_k_m",
            name = "Mistral 7B v0.3",
            description = "Top-tier 7B model. Strong reasoning and coding. Requires 6GB+ RAM.",
            sizeInMB = 4370,
            downloadUrl = "https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/mistral-7b-instruct-v0.3.Q4_K_M.gguf",
            fileName = "mistral-7b-instruct-v0.3.Q4_K_M.gguf",
            parameters = "7B",
            quantization = "Q4_K_M",
            maxContextSize = 8192
        ),
        ModelInfo(
            id = "openhermes-2.5-mistral-7b-q4_k_m",
            name = "OpenHermes 2.5 7B",
            description = "Fine-tuned for conversations. Very natural responses. Requires 6GB+ RAM.",
            sizeInMB = 4370,
            downloadUrl = "https://huggingface.co/TheBloke/OpenHermes-2.5-Mistral-7B-GGUF/resolve/main/openhermes-2.5-mistral-7b.Q4_K_M.gguf",
            fileName = "openhermes-2.5-mistral-7b.Q4_K_M.gguf",
            parameters = "7B",
            quantization = "Q4_K_M",
            maxContextSize = 8192
        )
    )
    
    fun getModelById(id: String): ModelInfo? = models.find { it.id == id }
    
    fun getModelByFileName(fileName: String): ModelInfo? = models.find { it.fileName == fileName }
}
