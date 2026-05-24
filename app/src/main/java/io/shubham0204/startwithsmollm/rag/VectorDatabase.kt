package io.shubham0204.startwithsmollm.rag

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SQLite-based vector database for storing and searching document chunks
 */
class VectorDatabase(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    
    companion object {
        private const val TAG = "VectorDatabase"
        private const val DATABASE_NAME = "rag_vectors.db"
        private const val DATABASE_VERSION = 1
        
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
    }
    
    private val embeddingModel = EmbeddingModel(context)
    
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
            
            // Insert chunks
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
            }
            
            db.setTransactionSuccessful()
            Log.d(TAG, "Added document ${document.name} with ${chunks.size} chunks")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding document: ${e.message}")
            false
        } finally {
            db.endTransaction()
        }
    }
    
    /**
     * Search for similar chunks using cosine similarity
     */
    fun search(queryEmbedding: FloatArray, topK: Int = 3, threshold: Float = 0.0f): List<ChunkSearchResult> {
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
                        documentName = it.getString(it.getColumnIndexOrThrow("doc_name"))
                    ))
                }
            }
        }
        
        // Sort by similarity and return top K
        return results
            .sortedByDescending { it.score }
            .take(topK)
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
