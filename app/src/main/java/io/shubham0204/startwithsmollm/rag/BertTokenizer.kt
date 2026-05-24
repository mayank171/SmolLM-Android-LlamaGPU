package io.shubham0204.startwithsmollm.rag

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Simple BERT tokenizer for all-MiniLM-L6-v2 model
 * Implements WordPiece tokenization
 */
class BertTokenizer(context: Context) {
    
    companion object {
        private const val TAG = "BertTokenizer"
        private const val VOCAB_FILE = "vocab.txt"
        private const val UNK_TOKEN = "[UNK]"
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val PAD_TOKEN = "[PAD]"
        private const val MAX_LENGTH = 128  // Max sequence length for the model
    }
    
    private val vocab: Map<String, Int>
    private val unkId: Int
    private val clsId: Int
    private val sepId: Int
    private val padId: Int
    
    init {
        vocab = loadVocab(context)
        unkId = vocab[UNK_TOKEN] ?: 100
        clsId = vocab[CLS_TOKEN] ?: 101
        sepId = vocab[SEP_TOKEN] ?: 102
        padId = vocab[PAD_TOKEN] ?: 0
        Log.d(TAG, "Loaded vocabulary with ${vocab.size} tokens")
    }
    
    private fun loadVocab(context: Context): Map<String, Int> {
        val vocabMap = mutableMapOf<String, Int>()
        try {
            context.assets.open(VOCAB_FILE).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var index = 0
                    reader.forEachLine { line ->
                        vocabMap[line] = index
                        index++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load vocab: ${e.message}")
            // Return basic vocab if file not found
            return createBasicVocab()
        }
        return vocabMap
    }
    
    private fun createBasicVocab(): Map<String, Int> {
        // Minimal vocab for fallback
        return mapOf(
            PAD_TOKEN to 0,
            UNK_TOKEN to 100,
            CLS_TOKEN to 101,
            SEP_TOKEN to 102
        )
    }
    
    /**
     * Tokenize text and return input IDs, attention mask, and token type IDs
     */
    fun encode(text: String): TokenizerOutput {
        val tokens = tokenize(text)
        
        // Add special tokens: [CLS] tokens [SEP]
        val inputIds = mutableListOf(clsId)
        tokens.take(MAX_LENGTH - 2).forEach { token ->
            inputIds.add(vocab[token] ?: unkId)
        }
        inputIds.add(sepId)
        
        // Create attention mask (1 for real tokens, 0 for padding)
        val attentionMask = MutableList(inputIds.size) { 1L }
        
        // Pad to MAX_LENGTH
        while (inputIds.size < MAX_LENGTH) {
            inputIds.add(padId)
            attentionMask.add(0L)
        }
        
        // Token type IDs (all 0 for single sentence)
        val tokenTypeIds = LongArray(MAX_LENGTH) { 0L }
        
        return TokenizerOutput(
            inputIds = inputIds.map { it.toLong() }.toLongArray(),
            attentionMask = attentionMask.toLongArray(),
            tokenTypeIds = tokenTypeIds
        )
    }
    
    /**
     * WordPiece tokenization
     */
    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        
        // Basic preprocessing
        val cleanedText = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .trim()
        
        // Split into words
        val words = cleanedText.split(Regex("\\s+"))
        
        for (word in words) {
            if (word.isEmpty()) continue
            
            // Try to find the word in vocab
            if (vocab.containsKey(word)) {
                tokens.add(word)
            } else {
                // WordPiece: break into subwords
                val subTokens = wordPieceTokenize(word)
                tokens.addAll(subTokens)
            }
        }
        
        return tokens
    }
    
    /**
     * Break a word into WordPiece tokens
     */
    private fun wordPieceTokenize(word: String): List<String> {
        val tokens = mutableListOf<String>()
        var start = 0
        
        while (start < word.length) {
            var end = word.length
            var found = false
            
            while (start < end) {
                val substr = if (start > 0) "##${word.substring(start, end)}" else word.substring(start, end)
                
                if (vocab.containsKey(substr)) {
                    tokens.add(substr)
                    found = true
                    break
                }
                end--
            }
            
            if (!found) {
                // Character not in vocab, use UNK
                tokens.add(UNK_TOKEN)
                start++
            } else {
                start = end
            }
        }
        
        return tokens
    }
    
    data class TokenizerOutput(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TokenizerOutput
            return inputIds.contentEquals(other.inputIds)
        }

        override fun hashCode(): Int = inputIds.contentHashCode()
    }
}
