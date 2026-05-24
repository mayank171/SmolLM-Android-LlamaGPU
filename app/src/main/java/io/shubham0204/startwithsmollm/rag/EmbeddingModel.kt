package io.shubham0204.startwithsmollm.rag

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer

/**
 * Neural embedding model using ONNX Runtime with all-MiniLM-L6-v2
 * Falls back to TF-IDF if ONNX model is not available
 */
class EmbeddingModel(private val context: Context) {
    
    companion object {
        private const val TAG = "EmbeddingModel"
        const val EMBEDDING_DIM = 384  // all-MiniLM-L6-v2 dimension
        private const val MODEL_FILE = "all-MiniLM-L6-v2.onnx"
    }
    
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: BertTokenizer? = null
    private var useOnnx = false
    private var isInitialized = false
    
    // TF-IDF fallback vocabulary
    private val tfIdfVocab = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "data", "model", "system", "user", "file", "code", "function", "class", "method",
        "value", "type", "name", "list", "array", "string", "number", "object", "key",
        "error", "result", "input", "output", "process", "request", "response", "server",
        "client", "database", "query", "table", "column", "row", "index", "search",
        "text", "document", "page", "content", "information", "question", "answer"
    )
    private val tfIdfVocabIndex = tfIdfVocab.mapIndexed { index, word -> word to index }.toMap()
    
    /**
     * Initialize the embedding model
     * Tries ONNX first, falls back to TF-IDF
     */
    fun initialize(): Boolean {
        if (isInitialized) return true
        
        // Try to load ONNX model
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            
            // Check if model exists in assets
            val modelExists = context.assets.list("")?.contains(MODEL_FILE) == true
            
            if (modelExists) {
                val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
                ortSession = ortEnvironment?.createSession(modelBytes)
                tokenizer = BertTokenizer(context)
                useOnnx = true
                Log.d(TAG, "ONNX model loaded successfully (Neural embeddings enabled)")
            } else {
                Log.w(TAG, "ONNX model not found in assets, using TF-IDF fallback")
                useOnnx = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ONNX model: ${e.message}, using TF-IDF fallback")
            useOnnx = false
        }
        
        isInitialized = true
        return true
    }
    
    /**
     * Generate embedding for a single text
     */
    fun embed(text: String): FloatArray {
        if (!isInitialized) initialize()
        
        return if (useOnnx) {
            generateOnnxEmbedding(text)
        } else {
            generateTfIdfEmbedding(text)
        }
    }
    
    /**
     * Generate embeddings for multiple texts (batch)
     */
    fun embedBatch(texts: List<String>): List<FloatArray> {
        return texts.map { embed(it) }
    }
    
    /**
     * Generate embedding using ONNX model
     */
    private fun generateOnnxEmbedding(text: String): FloatArray {
        val session = ortSession ?: return generateTfIdfEmbedding(text)
        val env = ortEnvironment ?: return generateTfIdfEmbedding(text)
        val tok = tokenizer ?: return generateTfIdfEmbedding(text)
        
        return try {
            // Tokenize
            val encoded = tok.encode(text)
            
            // Create tensors
            val inputIdsTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(encoded.inputIds),
                longArrayOf(1, encoded.inputIds.size.toLong())
            )
            val attentionMaskTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(encoded.attentionMask),
                longArrayOf(1, encoded.attentionMask.size.toLong())
            )
            val tokenTypeIdsTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(encoded.tokenTypeIds),
                longArrayOf(1, encoded.tokenTypeIds.size.toLong())
            )
            
            // Run inference
            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeIdsTensor
            )
            
            val results = session.run(inputs)
            
            // Get output - shape is [1, seq_len, 384]
            // We need to mean pool over the sequence dimension
            val output = results[0].value as Array<Array<FloatArray>>
            val sequenceOutput = output[0]  // [seq_len, 384]
            
            // Mean pooling with attention mask
            val embedding = FloatArray(EMBEDDING_DIM)
            var validTokens = 0
            
            for (i in sequenceOutput.indices) {
                if (encoded.attentionMask[i] == 1L) {
                    for (j in 0 until EMBEDDING_DIM) {
                        embedding[j] += sequenceOutput[i][j]
                    }
                    validTokens++
                }
            }
            
            // Average
            if (validTokens > 0) {
                for (j in 0 until EMBEDDING_DIM) {
                    embedding[j] /= validTokens
                }
            }
            
            // L2 normalize
            normalize(embedding)
            
            // Clean up
            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
            results.close()
            
            embedding
        } catch (e: Exception) {
            Log.e(TAG, "ONNX inference failed: ${e.message}, falling back to TF-IDF")
            generateTfIdfEmbedding(text)
        }
    }
    
    /**
     * TF-IDF based embedding (fallback)
     */
    private fun generateTfIdfEmbedding(text: String): FloatArray {
        val embedding = FloatArray(EMBEDDING_DIM)
        val words = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
        
        if (words.isEmpty()) return embedding
        
        val wordCounts = words.groupingBy { it }.eachCount()
        val totalWords = words.size.toFloat()
        
        for ((word, count) in wordCounts) {
            val vocabIndex = tfIdfVocabIndex[word]
            if (vocabIndex != null && vocabIndex < EMBEDDING_DIM) {
                val tf = count / totalWords
                val idf = 1.0f + kotlin.math.ln(tfIdfVocab.size.toFloat() / (1 + vocabIndex))
                embedding[vocabIndex] = tf * idf
            } else {
                val hash = word.hashCode().and(0x7FFFFFFF) % EMBEDDING_DIM
                embedding[hash] += 1.0f / totalWords
            }
        }
        
        // Add bigram features
        for (i in 0 until words.size - 1) {
            val bigram = "${words[i]}_${words[i + 1]}"
            val hash = bigram.hashCode().and(0x7FFFFFFF) % EMBEDDING_DIM
            embedding[hash] += 0.5f / totalWords
        }
        
        normalize(embedding)
        return embedding
    }
    
    /**
     * L2 normalize a vector in place
     */
    private fun normalize(vector: FloatArray) {
        var sum = 0.0f
        for (v in vector) sum += v * v
        val norm = kotlin.math.sqrt(sum)
        if (norm > 0) {
            for (i in vector.indices) vector[i] /= norm
        }
    }
    
    /**
     * Compute cosine similarity between two embeddings
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }
    
    /**
     * Check if using neural embeddings
     */
    fun isUsingNeuralEmbeddings(): Boolean = useOnnx
    
    fun close() {
        ortSession?.close()
        ortEnvironment?.close()
        ortSession = null
        ortEnvironment = null
        isInitialized = false
    }
}
