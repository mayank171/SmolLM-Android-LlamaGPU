package io.shubham0204.startwithsmollm.data

enum class ModelCapability {
    CHAT,           // Basic conversation
    REASONING,      // Logic and problem solving
    CODING,         // Code generation and explanation
    MATH,           // Mathematical calculations
    CREATIVE,       // Creative writing
    SUMMARIZATION,  // Text summarization
    KNOWLEDGE,      // Factual knowledge
    OCR             // Image text extraction (via Whisper/ML Kit)
}

enum class ModelReliability {
    LOW,            // May hallucinate, basic accuracy
    MEDIUM,         // Decent accuracy, occasional errors
    HIGH,           // Good accuracy, reliable
    VERY_HIGH       // Excellent accuracy, very reliable
}

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
    val supportsMultiTurn: Boolean = true,
    val supportsRag: Boolean = true,
    val capabilities: Set<ModelCapability> = setOf(ModelCapability.CHAT),
    val reliability: ModelReliability = ModelReliability.MEDIUM,
    val bestFor: String = "General chat",
    val notGoodFor: String = ""
)

object AvailableModels {
    val models = listOf(
        // Ultra-Small Models (< 500MB) - Best for low-end devices
        ModelInfo(
            id = "smollm-135m-q4_k_m",
            name = "SmolLM 135M",
            description = "Tiniest model. No RAG support. Basic chat only.",
            sizeInMB = 105,
            downloadUrl = "https://huggingface.co/QuantFactory/SmolLM-135M-GGUF/resolve/main/SmolLM-135M.Q4_K_M.gguf",
            fileName = "SmolLM-135M.Q4_K_M.gguf",
            parameters = "135M",
            quantization = "Q4_K_M",
            maxContextSize = 2048,
            supportsRag = false,
            capabilities = setOf(ModelCapability.CHAT),
            reliability = ModelReliability.LOW,
            bestFor = "Quick summaries, simple Q&A, text completion",
            notGoodFor = "RAG, complex reasoning, coding, math, factual questions"
        ),
        ModelInfo(
            id = "smollm-360m-q8_0",
            name = "SmolLM 360M",
            description = "Smallest instruct model. No RAG support. Basic chat only.",
            sizeInMB = 386,
            downloadUrl = "https://huggingface.co/QuantFactory/SmolLM-360M-Instruct-GGUF/resolve/main/SmolLM-360M-Instruct.Q8_0.gguf",
            fileName = "SmolLM-360M-Instruct.Q8_0.gguf",
            parameters = "360M",
            quantization = "Q8_0",
            maxContextSize = 2048,
            supportsRag = false,
            capabilities = setOf(ModelCapability.CHAT),
            reliability = ModelReliability.LOW,
            bestFor = "Basic chat, simple tasks, low-end devices",
            notGoodFor = "RAG, coding, math, complex reasoning, factual accuracy"
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
            maxContextSize = 4096,
            capabilities = setOf(ModelCapability.CHAT, ModelCapability.SUMMARIZATION, ModelCapability.KNOWLEDGE, ModelCapability.OCR),
            reliability = ModelReliability.MEDIUM,
            bestFor = "Quick responses, simple coding help, general knowledge, image text",
            notGoodFor = "Complex math, advanced coding, detailed reasoning"
        ),
        
        // Small Models (500MB - 1GB) - Good balance
        ModelInfo(
            id = "tinyllama-1.1b-q4_k_m",
            name = "TinyLlama 1.1B",
            description = "Ultra-fast responses. Trained on 3T tokens. Great for quick tasks.",
            sizeInMB = 661,
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            parameters = "1.1B",
            quantization = "Q4_K_M",
            maxContextSize = 2048,
            capabilities = setOf(ModelCapability.CHAT, ModelCapability.CREATIVE, ModelCapability.SUMMARIZATION),
            reliability = ModelReliability.MEDIUM,
            bestFor = "Creative writing, casual chat, brainstorming",
            notGoodFor = "Factual accuracy, coding, math calculations"
        ),
        
        // Medium Models (1GB - 2GB) - Recommended for most devices
        ModelInfo(
            id = "qwen2.5-1.5b-q4_k_m",
            name = "Qwen 2.5 1.5B",
            description = "Balanced performance. Better reasoning and knowledge.",
            sizeInMB = 1147,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            parameters = "1.5B",
            quantization = "Q4_K_M",
            maxContextSize = 4096,
            capabilities = setOf(ModelCapability.CHAT, ModelCapability.REASONING, ModelCapability.CODING, ModelCapability.MATH, ModelCapability.KNOWLEDGE, ModelCapability.OCR),
            reliability = ModelReliability.MEDIUM,
            bestFor = "General assistant, basic coding, simple math, knowledge Q&A, OCR",
            notGoodFor = "Complex multi-step reasoning, advanced algorithms"
        ),
        ModelInfo(
            id = "gemma-2-2b-q4_k_m",
            name = "Gemma 2 2B",
            description = "Google's model. Single-turn only (no conversation memory).",
            sizeInMB = 1610,
            downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            fileName = "gemma-2-2b-it-Q4_K_M.gguf",
            parameters = "2B",
            quantization = "Q4_K_M",
            maxContextSize = 4096,
            supportsMultiTurn = false,
            capabilities = setOf(ModelCapability.CHAT, ModelCapability.KNOWLEDGE, ModelCapability.CREATIVE, ModelCapability.OCR),
            reliability = ModelReliability.HIGH,
            bestFor = "Single questions, factual answers, creative writing, image text",
            notGoodFor = "Multi-turn conversations, context-dependent tasks"
        ),
        
        // Large Models (2GB - 3GB) - High quality
        ModelInfo(
            id = "phi-3.5-mini-q4_k_m",
            name = "Phi-3.5 Mini",
            description = "Microsoft's latest. Excellent reasoning and coding abilities.",
            sizeInMB = 2447,
            downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
            fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            parameters = "3.8B",
            quantization = "Q4_K_M",
            maxContextSize = 4096,
            capabilities = setOf(ModelCapability.CHAT, ModelCapability.REASONING, ModelCapability.CODING, ModelCapability.MATH, ModelCapability.KNOWLEDGE, ModelCapability.OCR),
            reliability = ModelReliability.HIGH,
            bestFor = "Coding assistance, math problems, logical reasoning, technical Q&A, OCR",
            notGoodFor = "Low-end devices (needs more RAM)"
        ),
        ModelInfo(
            id = "llama-3.2-3b-q4_k_m",
            name = "Llama 3.2 3B",
            description = "Meta's mobile-optimized model. Excellent general performance.",
            sizeInMB = 2068,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            parameters = "3B",
            quantization = "Q4_K_M",
            maxContextSize = 8192,
            capabilities = setOf(ModelCapability.CHAT, ModelCapability.REASONING, ModelCapability.CREATIVE, ModelCapability.KNOWLEDGE, ModelCapability.SUMMARIZATION, ModelCapability.OCR),
            reliability = ModelReliability.HIGH,
            bestFor = "General assistant, conversations, creative writing, summaries, OCR",
            notGoodFor = "Advanced math, complex coding algorithms"
        ),
        ModelInfo(
            id = "qwen2.5-3b-q4_k_m",
            name = "Qwen 2.5 3B",
            description = "Powerful reasoning. Best quality in 3B class.",
            sizeInMB = 2150,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            parameters = "3B",
            quantization = "Q4_K_M",
            maxContextSize = 4096,
            capabilities = setOf(ModelCapability.CHAT, ModelCapability.REASONING, ModelCapability.CODING, ModelCapability.MATH, ModelCapability.KNOWLEDGE, ModelCapability.CREATIVE, ModelCapability.OCR),
            reliability = ModelReliability.HIGH,
            bestFor = "Coding, math, reasoning, long documents, technical tasks, OCR",
            notGoodFor = "Needs more RAM than smaller models"
        ),
        
        // Extra Large Models (4GB+) - For high-end devices only
        ModelInfo(
            id = "mistral-7b-instruct-q4_k_m",
            name = "Mistral 7B v0.3",
            description = "Top-tier 7B model. Strong reasoning and coding. Requires 8GB+ RAM.",
            sizeInMB = 4403,
            downloadUrl = "https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/mistral-7b-instruct-v0.3.Q4_K_M.gguf",
            fileName = "mistral-7b-instruct-v0.3.Q4_K_M.gguf",
            parameters = "7B",
            quantization = "Q4_K_M",
            maxContextSize = 1024,
            capabilities = setOf(ModelCapability.CHAT, ModelCapability.REASONING, ModelCapability.CODING, ModelCapability.MATH, ModelCapability.KNOWLEDGE, ModelCapability.CREATIVE, ModelCapability.SUMMARIZATION, ModelCapability.OCR),
            reliability = ModelReliability.VERY_HIGH,
            bestFor = "Complex coding, advanced math, detailed analysis, professional tasks, OCR",
            notGoodFor = "Devices with <8GB RAM (may crash), long conversations"
        ),
        ModelInfo(
            id = "openhermes-2.5-mistral-7b-q4_k_m",
            name = "OpenHermes 2.5 7B",
            description = "Fine-tuned for conversations. Very natural responses. Requires 8GB+ RAM.",
            sizeInMB = 4209,
            downloadUrl = "https://huggingface.co/TheBloke/OpenHermes-2.5-Mistral-7B-GGUF/resolve/main/openhermes-2.5-mistral-7b.Q4_K_M.gguf",
            fileName = "openhermes-2.5-mistral-7b.Q4_K_M.gguf",
            parameters = "7B",
            quantization = "Q4_K_M",
            maxContextSize = 1024,
            capabilities = setOf(ModelCapability.CHAT, ModelCapability.REASONING, ModelCapability.CREATIVE, ModelCapability.KNOWLEDGE, ModelCapability.SUMMARIZATION, ModelCapability.OCR),
            reliability = ModelReliability.VERY_HIGH,
            bestFor = "Natural conversations, roleplay, creative writing, detailed explanations, OCR",
            notGoodFor = "Devices with <8GB RAM (may crash), pure coding tasks"
        )
    )
    
    fun getModelById(id: String): ModelInfo? = models.find { it.id == id }
    
    fun getModelByFileName(fileName: String): ModelInfo? = models.find { it.fileName == fileName }
}
