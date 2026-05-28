package io.shubham0204.startwithsmollm.rag

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SQLite-based vector database with hybrid search capabilities
 * Combines semantic search (embeddings) with BM25 keyword search
 */
class VectorDatabase(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    
    companion object {
        private const val TAG = "VectorDatabase"
        private const val DATABASE_NAME = "rag_vectors.db"
        private const val DATABASE_VERSION = 2
        
        // Tables
        private const val TABLE_DOCUMENTS = "documents"
        private const val TABLE_CHUNKS = "chunks"
        
        // Document columns
        private const val COL_DOC_ID = "id"
        private const val COL_DOC_NAME = "name"
        private const val COL_DOC_PATH = "path"
        private const val COL_DOC_TYPE = "type"
        private const val COL_DOC_ADDED_AT = "added_at"
        private const val COL_DOC_CHUNK_COUNT = "chunk_count"
        private const val COL_DOC_SIZE = "size_bytes"
        
        // Chunk columns
        private const val COL_CHUNK_ID = "id"
        private const val COL_CHUNK_DOC_ID = "document_id"
        private const val COL_CHUNK_TEXT = "text"
        private const val COL_CHUNK_POSITION = "position"
        private const val COL_CHUNK_START = "start_char"
        private const val COL_CHUNK_END = "end_char"
        private const val COL_CHUNK_EMBEDDING = "embedding"
        
        // Hybrid search weights
        private const val SEMANTIC_WEIGHT = 0.6f
        private const val BM25_WEIGHT = 0.4f
        private const val RRF_K = 60  // Reciprocal Rank Fusion constant
    }
    
    private val embeddingModel = EmbeddingModel(context)
    private val bm25Search = BM25Search()
    
    override fun onCreate(db: SQLiteDatabase) {
        // Create documents table
        db.execSQL("""
            CREATE TABLE $TABLE_DOCUMENTS (
                $COL_DOC_ID TEXT PRIMARY KEY,
                $COL_DOC_NAME TEXT NOT NULL,
                $COL_DOC_PATH TEXT,
                $COL_DOC_TYPE TEXT NOT NULL,
                $COL_DOC_ADDED_AT INTEGER NOT NULL,
                $COL_DOC_CHUNK_COUNT INTEGER DEFAULT 0,
                $COL_DOC_SIZE INTEGER DEFAULT 0
            )
        """)
        
        // Create chunks table
        db.execSQL("""
            CREATE TABLE $TABLE_CHUNKS (
                $COL_CHUNK_ID TEXT PRIMARY KEY,
                $COL_CHUNK_DOC_ID TEXT NOT NULL,
                $COL_CHUNK_TEXT TEXT NOT NULL,
                $COL_CHUNK_POSITION INTEGER NOT NULL,
                $COL_CHUNK_START INTEGER NOT NULL,
                $COL_CHUNK_END INTEGER NOT NULL,
                $COL_CHUNK_EMBEDDING BLOB NOT NULL,
                FOREIGN KEY ($COL_CHUNK_DOC_ID) REFERENCES $TABLE_DOCUMENTS($COL_DOC_ID) ON DELETE CASCADE
            )
        """)
        
        // Create index for faster lookups
        db.execSQL("CREATE INDEX idx_chunks_doc_id ON $TABLE_CHUNKS($COL_CHUNK_DOC_ID)")
        
        Log.d(TAG, "Database created")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CHUNKS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DOCUMENTS")
        onCreate(db)
    }
    
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
    
    /**
     * Add a document and its chunks to the database
     * Also indexes chunks in BM25 for keyword search
     */
    fun addDocument(document: Document, chunks: List<Chunk>): Boolean {
        val db = writableDatabase
        
        return try {
            db.beginTransaction()
            
            // Insert document
            val docValues = ContentValues().apply {
                put(COL_DOC_ID, document.id)
                put(COL_DOC_NAME, document.name)
                put(COL_DOC_PATH, document.path)
                put(COL_DOC_TYPE, document.type.name)
                put(COL_DOC_ADDED_AT, document.addedAt)
                put(COL_DOC_CHUNK_COUNT, chunks.size)
                put(COL_DOC_SIZE, document.sizeBytes)
            }
            db.insert(TABLE_DOCUMENTS, null, docValues)
            
            // Insert chunks and index in BM25
            for (chunk in chunks) {
                val chunkValues = ContentValues().apply {
                    put(COL_CHUNK_ID, chunk.id)
                    put(COL_CHUNK_DOC_ID, chunk.documentId)
                    put(COL_CHUNK_TEXT, chunk.text)
                    put(COL_CHUNK_POSITION, chunk.position)
                    put(COL_CHUNK_START, chunk.startChar)
                    put(COL_CHUNK_END, chunk.endChar)
                    put(COL_CHUNK_EMBEDDING, floatArrayToBytes(chunk.embedding))
                }
                db.insert(TABLE_CHUNKS, null, chunkValues)
                
                // Index in BM25 for keyword search
                bm25Search.indexDocument(chunk.id, chunk.text)
            }
            
            db.setTransactionSuccessful()
            Log.d(TAG, "Added document ${document.name} with ${chunks.size} chunks (BM25 indexed)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding document: ${e.message}")
            false
        } finally {
            db.endTransaction()
        }
    }
    
    /**
     * Semantic search using cosine similarity
     */
    fun searchSemantic(queryEmbedding: FloatArray, topK: Int = 10, threshold: Float = 0.0f): List<ChunkSearchResult> {
        val db = readableDatabase
        val results = mutableListOf<ChunkSearchResult>()
        
        val cursor = db.rawQuery("""
            SELECT c.*, d.$COL_DOC_NAME as doc_name
            FROM $TABLE_CHUNKS c
            JOIN $TABLE_DOCUMENTS d ON c.$COL_CHUNK_DOC_ID = d.$COL_DOC_ID
        """, null)
        
        cursor.use {
            while (it.moveToNext()) {
                val embeddingBytes = it.getBlob(it.getColumnIndexOrThrow(COL_CHUNK_EMBEDDING))
                val chunkEmbedding = bytesToFloatArray(embeddingBytes)
                
                val similarity = embeddingModel.cosineSimilarity(queryEmbedding, chunkEmbedding)
                
                if (similarity >= threshold) {
                    val chunk = Chunk(
                        id = it.getString(it.getColumnIndexOrThrow(COL_CHUNK_ID)),
                        documentId = it.getString(it.getColumnIndexOrThrow(COL_CHUNK_DOC_ID)),
                        text = it.getString(it.getColumnIndexOrThrow(COL_CHUNK_TEXT)),
                        position = it.getInt(it.getColumnIndexOrThrow(COL_CHUNK_POSITION)),
                        startChar = it.getInt(it.getColumnIndexOrThrow(COL_CHUNK_START)),
                        endChar = it.getInt(it.getColumnIndexOrThrow(COL_CHUNK_END)),
                        embedding = chunkEmbedding
                    )
                    
                    results.add(ChunkSearchResult(
                        chunk = chunk,
                        score = similarity,
                        documentName = it.getString(it.getColumnIndexOrThrow("doc_name")),
                        searchType = SearchType.SEMANTIC
                    ))
                }
            }
        }
        
        return results.sortedByDescending { it.score }.take(topK)
    }
    
    /**
     * BM25 keyword search
     */
    fun searchBM25(query: String, topK: Int = 10): List<ChunkSearchResult> {
        val bm25Results = bm25Search.search(query, topK)
        if (bm25Results.isEmpty()) return emptyList()
        
        val db = readableDatabase
        val results = mutableListOf<ChunkSearchResult>()
        
        // Normalize BM25 scores to 0-1 range
        val maxScore = bm25Results.maxOfOrNull { it.score } ?: 1f
        
        for (bm25Result in bm25Results) {
            val cursor = db.rawQuery("""
                SELECT c.*, d.$COL_DOC_NAME as doc_name
                FROM $TABLE_CHUNKS c
                JOIN $TABLE_DOCUMENTS d ON c.$COL_CHUNK_DOC_ID = d.$COL_DOC_ID
                WHERE c.$COL_CHUNK_ID = ?
            """, arrayOf(bm25Result.documentId))
            
            cursor.use {
                if (it.moveToFirst()) {
                    val embeddingBytes = it.getBlob(it.getColumnIndexOrThrow(COL_CHUNK_EMBEDDING))
                    val chunk = Chunk(
                        id = it.getString(it.getColumnIndexOrThrow(COL_CHUNK_ID)),
                        documentId = it.getString(it.getColumnIndexOrThrow(COL_CHUNK_DOC_ID)),
                        text = it.getString(it.getColumnIndexOrThrow(COL_CHUNK_TEXT)),
                        position = it.getInt(it.getColumnIndexOrThrow(COL_CHUNK_POSITION)),
                        startChar = it.getInt(it.getColumnIndexOrThrow(COL_CHUNK_START)),
                        endChar = it.getInt(it.getColumnIndexOrThrow(COL_CHUNK_END)),
                        embedding = bytesToFloatArray(embeddingBytes)
                    )
                    
                    results.add(ChunkSearchResult(
                        chunk = chunk,
                        score = bm25Result.score / maxScore,  // Normalize to 0-1
                        documentName = it.getString(it.getColumnIndexOrThrow("doc_name")),
                        searchType = SearchType.BM25
                    ))
                }
            }
        }
        
        return results
    }
    
    /**
     * Hybrid search combining semantic and BM25 using Reciprocal Rank Fusion (RRF)
     * 
     * RRF Score = Σ (1 / (k + rank_i)) for each ranking
     * This method is robust and doesn't require score normalization
     */
    fun searchHybrid(
        query: String,
        queryEmbedding: FloatArray,
        topK: Int = 5,
        semanticWeight: Float = SEMANTIC_WEIGHT,
        bm25Weight: Float = BM25_WEIGHT
    ): List<ChunkSearchResult> {
        // Get results from both search methods
        val semanticResults = searchSemantic(queryEmbedding, topK * 2)
        val bm25Results = searchBM25(query, topK * 2)
        
        Log.d(TAG, "Hybrid search: ${semanticResults.size} semantic, ${bm25Results.size} BM25 results")
        
        // Calculate RRF scores
        val rrfScores = mutableMapOf<String, Float>()
        val chunkMap = mutableMapOf<String, ChunkSearchResult>()
        
        // Add semantic results with RRF
        for ((rank, result) in semanticResults.withIndex()) {
            val rrfScore = semanticWeight / (RRF_K + rank + 1)
            rrfScores[result.chunk.id] = (rrfScores[result.chunk.id] ?: 0f) + rrfScore
            chunkMap[result.chunk.id] = result
        }
        
        // Add BM25 results with RRF
        for ((rank, result) in bm25Results.withIndex()) {
            val rrfScore = bm25Weight / (RRF_K + rank + 1)
            rrfScores[result.chunk.id] = (rrfScores[result.chunk.id] ?: 0f) + rrfScore
            if (result.chunk.id !in chunkMap) {
                chunkMap[result.chunk.id] = result
            }
        }
        
        // Sort by RRF score and return top K
        return rrfScores.entries
            .sortedByDescending { it.value }
            .take(topK)
            .mapNotNull { (chunkId, rrfScore) ->
                chunkMap[chunkId]?.copy(
                    score = rrfScore,
                    searchType = SearchType.HYBRID
                )
            }
    }
    
    /**
     * Legacy search method for backward compatibility
     */
    fun search(queryEmbedding: FloatArray, topK: Int = 3, threshold: Float = 0.0f): List<ChunkSearchResult> {
        return searchSemantic(queryEmbedding, topK, threshold)
    }
    
    /**
     * Get all documents
     */
    fun getAllDocuments(): List<Document> {
        val db = readableDatabase
        val documents = mutableListOf<Document>()
        
        val cursor = db.query(TABLE_DOCUMENTS, null, null, null, null, null, "$COL_DOC_ADDED_AT DESC")
        
        cursor.use {
            while (it.moveToNext()) {
                documents.add(Document(
                    id = it.getString(it.getColumnIndexOrThrow(COL_DOC_ID)),
                    name = it.getString(it.getColumnIndexOrThrow(COL_DOC_NAME)),
                    path = it.getString(it.getColumnIndexOrThrow(COL_DOC_PATH)) ?: "",
                    type = DocumentType.valueOf(it.getString(it.getColumnIndexOrThrow(COL_DOC_TYPE))),
                    addedAt = it.getLong(it.getColumnIndexOrThrow(COL_DOC_ADDED_AT)),
                    chunkCount = it.getInt(it.getColumnIndexOrThrow(COL_DOC_CHUNK_COUNT)),
                    sizeBytes = it.getLong(it.getColumnIndexOrThrow(COL_DOC_SIZE))
                ))
            }
        }
        
        return documents
    }
    
    /**
     * Delete a document and all its chunks
     */
    fun deleteDocument(documentId: String): Boolean {
        val db = writableDatabase
        val deleted = db.delete(TABLE_DOCUMENTS, "$COL_DOC_ID = ?", arrayOf(documentId))
        Log.d(TAG, "Deleted document $documentId: ${deleted > 0}")
        return deleted > 0
    }
    
    /**
     * Delete all documents and chunks
     */
    fun deleteAllDocuments(): Int {
        val db = writableDatabase
        val deleted = db.delete(TABLE_DOCUMENTS, null, null)
        Log.d(TAG, "Deleted all $deleted documents")
        return deleted
    }
    
    /**
     * Get total chunk count
     */
    fun getTotalChunkCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_CHUNKS", null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }
    
    /**
     * Get database size in bytes
     */
    fun getDatabaseSize(): Long {
        val dbFile = readableDatabase.path?.let { java.io.File(it) }
        return dbFile?.length() ?: 0
    }
    
    // Utility functions for embedding serialization
    private fun floatArrayToBytes(array: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(array.size * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        for (f in array) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }
    
    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val array = FloatArray(bytes.size / 4)
        for (i in array.indices) {
            array[i] = buffer.getFloat()
        }
        return array
    }
}
