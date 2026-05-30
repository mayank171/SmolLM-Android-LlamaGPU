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
        ModelInfo(
            id = "qwen2.5-0.5b-q4_k_m",
            name = "Qwen 2.5 0.5B",
            description = "Fast and efficient. Good for simple tasks and quick responses.",
            sizeInMB = 491,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            parameters = "0.5B",
            quantization = "Q4_K_M",
            maxContextSize = 32768  // Qwen 2.5 supports 32K context
        ),
        ModelInfo(
            id = "qwen2.5-1.5b-q4_k_m",
            name = "Qwen 2.5 1.5B",
            description = "Balanced performance. Better reasoning and knowledge.",
            sizeInMB = 1100,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            parameters = "1.5B",
            quantization = "Q4_K_M",
            maxContextSize = 32768  // Qwen 2.5 supports 32K context
        ),
        ModelInfo(
            id = "smollm-360m-q4_0",
            name = "SmolLM 360M",
            description = "Smallest and fastest. Basic conversations only.",
            sizeInMB = 219,
            downloadUrl = "https://huggingface.co/shubham0204/SmolLM-360M-Instruct-GGUF/resolve/main/SmolLM-360M-Instruct.Q4_0.gguf",
            fileName = "SmolLM-360M-Instruct.Q4_0.gguf",
            parameters = "360M",
            quantization = "Q4_0",
            maxContextSize = 2048  // SmolLM native context is 2K
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
            maxContextSize = 8192,  // Gemma 2 supports 8K context
            supportsMultiTurn = false
        )
    )
    
    fun getModelById(id: String): ModelInfo? = models.find { it.id == id }
    
    fun getModelByFileName(fileName: String): ModelInfo? = models.find { it.fileName == fileName }
}
