package io.shubham0204.startwithsmollm.benchmark

import android.content.Context
import android.net.Uri
import android.util.Log
import io.shubham0204.smollm.GGUFReader
import io.shubham0204.startwithsmollm.data.AvailableModels
import io.shubham0204.startwithsmollm.data.DeviceCapabilities
import io.shubham0204.startwithsmollm.data.DeviceTier
import io.shubham0204.startwithsmollm.data.ModelDownloadManager
import io.shubham0204.startwithsmollm.data.ModelInfo
import io.shubham0204.startwithsmollm.gpu.KVCacheType
import io.shubham0204.startwithsmollm.gpu.LlamaGPU
import io.shubham0204.startwithsmollm.rag.RagConfig
import io.shubham0204.startwithsmollm.rag.RagEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs the RAG benchmark suite over every downloaded model.
 *
 * Flow:
 *  1. (Once) Add the user-provided document to the RAG engine and build embeddings.
 *  2. For each downloaded model:
 *      a. Load it on a dedicated [LlamaGPU] instance.
 *      b. Apply the per-model [RagConfig].
 *      c. For each benchmark question, run RAG retrieval + streamed inference.
 *      d. Capture TTFT, average ITL, tokens/sec, response, and key-phrase score.
 *      e. Unload before moving to the next model.
 *  3. Emit progress updates throughout, then a final [BenchmarkUpdate.Done] with
 *     the full report and the path of the report file written to filesDir.
 *
 * The runner uses ITS OWN [LlamaGPU] instance so it never collides with the
 * ViewModel's main `llamaGPU`. The caller MUST close/unload the main model
 * before invoking this (to free RAM and the GPU).
 */
class RagBenchmarkRunner(private val context: Context) {

    companion object {
        private const val TAG = "RagBenchmark"
        private const val BENCHMARK_SYSTEM_PROMPT =
            "You are a factual QA assistant. Use ONLY the provided context to answer. " +
            "If the answer is not in the context, say 'Not in context.' " +
            "Be concise (1-3 sentences). Quote specific numbers and terms exactly as they appear in the context. " +
            "Do not invent acronyms, numbers, or facts. Do not repeat yourself."
    }

    // ----- public types ------------------------------------------------------

    data class QuestionResult(
        val qid: String,
        val query: String,
        val expectedAnswer: String,
        val response: String,
        val ttftMs: Long,
        val avgItlMs: Double,
        val tokensPerSec: Double,
        val tokenCount: Int,
        val score: Float,                       // 0.0 .. 1.0 key-phrase recall
        val matchedPhrases: List<String>,
        val missedPhrases: List<String>,
        val retrievedChunks: Int,
        val ragLatencyMs: Long,
        val totalLatencyMs: Long,
        val errorMessage: String? = null
    )

    data class ModelResult(
        val modelId: String,
        val modelName: String,
        val params: String,
        val loadTimeMs: Long,
        val questions: List<QuestionResult>
    ) {
        val avgScore: Float
            get() = if (questions.isEmpty()) 0f
                else questions.map { it.score }.average().toFloat()
        val avgTtftMs: Long
            get() = if (questions.isEmpty()) 0L
                else questions.map { it.ttftMs }.average().toLong()
        val avgItlMs: Double
            get() = if (questions.isEmpty()) 0.0
                else questions.map { it.avgItlMs }.average()
        val avgTokensPerSec: Double
            get() = if (questions.isEmpty()) 0.0
                else questions.map { it.tokensPerSec }.average()
    }

    sealed interface BenchmarkUpdate {
        data class Log(val line: String) : BenchmarkUpdate
        data class Status(val message: String) : BenchmarkUpdate
        data class ModelStarted(val modelName: String, val index: Int, val total: Int) : BenchmarkUpdate
        data class QuestionStarted(val qid: String, val query: String, val index: Int, val total: Int) : BenchmarkUpdate
        data class QuestionFinished(val result: QuestionResult) : BenchmarkUpdate
        data class ModelFinished(val result: ModelResult) : BenchmarkUpdate
        data class Done(val results: List<ModelResult>, val reportPath: String) : BenchmarkUpdate
        data class Failed(val message: String) : BenchmarkUpdate
    }

    // ----- entry point -------------------------------------------------------

    fun run(documentUri: Uri, ragEngine: RagEngine): Flow<BenchmarkUpdate> = callbackFlow {
        val emit: (BenchmarkUpdate) -> Unit = { upd ->
            val sent = trySend(upd).isSuccess
            if (!sent) Log.w(TAG, "Channel closed, dropping update: $upd")
        }
        val log: (String) -> Unit = { msg ->
            Log.d(TAG, msg)
            emit(BenchmarkUpdate.Log(msg))
        }

        try {
            log("╔══════════════════════════════════════════════════╗")
            log("║       🧪 RAG BENCHMARK STARTED                   ║")
            log("╚══════════════════════════════════════════════════╝")

            val downloadManager = ModelDownloadManager(context)
            val downloaded = downloadManager.getDownloadedModels()
            log("Downloaded models: ${downloaded.size}")
            downloaded.forEachIndexed { i, m -> log("  [${i + 1}] ${m.name} (${m.parameters}, ${m.sizeInMB}MB)") }

            if (downloaded.isEmpty()) {
                emit(BenchmarkUpdate.Failed("No downloaded models found. Download at least one model first."))
                close(); return@callbackFlow
            }

            // ---- Step 1: ingest the document into RAG --------------------------------
            emit(BenchmarkUpdate.Status("Embedding document into RAG..."))
            log("")
            log("▶ INGESTING DOCUMENT: $documentUri")
            val ingestStart = System.currentTimeMillis()
            val addResult = ragEngine.addDocument(documentUri)
            val ingestTime = System.currentTimeMillis() - ingestStart
            val addedDocumentId: String? = when (addResult) {
                is RagEngine.AddDocumentResult.Success -> {
                    log("✅ Document ingested in ${ingestTime}ms → ${addResult.document.name} (${addResult.document.chunkCount} chunks)")
                    addResult.document.id
                }
                is RagEngine.AddDocumentResult.Error -> {
                    log("❌ Document ingest failed: ${addResult.message}")
                    emit(BenchmarkUpdate.Failed("Failed to ingest document: ${addResult.message}"))
                    close(); return@callbackFlow
                }
            }

            // ---- Step 2: iterate models ---------------------------------------------
            val results = mutableListOf<ModelResult>()
            for ((mIdx, model) in downloaded.withIndex()) {
                emit(BenchmarkUpdate.ModelStarted(model.name, mIdx + 1, downloaded.size))
                log("")
                log("══════════════════════════════════════════════════")
                log("MODEL ${mIdx + 1}/${downloaded.size}: ${model.name}  [${model.parameters}, ${model.quantization}]")
                log("══════════════════════════════════════════════════")

                val modelResult = runForModel(model, ragEngine, downloadManager, log, emit)
                results.add(modelResult)
                emit(BenchmarkUpdate.ModelFinished(modelResult))
                log("✅ Done ${model.name}: score=${"%.2f".format(modelResult.avgScore)}  TTFT=${modelResult.avgTtftMs}ms  ITL=${"%.1f".format(modelResult.avgItlMs)}ms  ${"%.1f".format(modelResult.avgTokensPerSec)} tok/s")
            }

            // ---- Step 3: cleanup ingested doc ----------------------------------------
            addedDocumentId?.let {
                try { ragEngine.deleteDocument(it); log("🧹 Removed benchmark document from RAG store") } catch (_: Exception) {}
            }

            // ---- Step 4: write report -----------------------------------------------
            val reportPath = writeReport(results)
            log("")
            log("📄 Report written: $reportPath")
            emit(BenchmarkUpdate.Done(results, reportPath))
        } catch (t: Throwable) {
            Log.e(TAG, "Benchmark failed", t)
            emit(BenchmarkUpdate.Failed(t.message ?: t.javaClass.simpleName))
        } finally {
            close()
        }

        awaitClose { Log.d(TAG, "Benchmark flow closed") }
    }.flowOn(Dispatchers.IO)

    // ----- per-model -------------------------------------------------------------------

    private suspend fun runForModel(
        model: ModelInfo,
        ragEngine: RagEngine,
        downloadManager: ModelDownloadManager,
        log: (String) -> Unit,
        emit: (BenchmarkUpdate) -> Unit
    ): ModelResult {
        val llama = LlamaGPU()
        val modelPath = downloadManager.getModelPath(model.fileName)
        val deviceProfile = DeviceCapabilities.getDeviceProfile(context)

        // Compute safe context size like the main app
        val reader = try { GGUFReader().apply { load(modelPath) } } catch (e: Exception) {
            log("⚠️ GGUFReader failed: ${e.message}")
            null
        }
        val chatTemplate = reader?.getChatTemplate()
        val ggufContextSize = reader?.getContextSize()
        val deviceOptimalContext = DeviceCapabilities.getContextSizeForModel(model, deviceProfile)
        val safeContextSize = minOf(
            ggufContextSize ?: model.maxContextSize.toLong(),
            model.maxContextSize.toLong(),
            deviceOptimalContext.toLong()
        ).toInt()
        log("Context size: $safeContextSize, threads: ${deviceProfile.optimalThreads}")

        // Apply the model-appropriate RAG config (chunkSize, topK, finalTopK, ...)
        val ragConfig = RagConfig.forModel(model.parameters, safeContextSize)
        ragEngine.updateConfig(ragConfig)
        log("RAG config: chunk=${ragConfig.chunkSize}, topK=${ragConfig.topK}, finalTopK=${ragConfig.finalTopK}, threshold=${ragConfig.similarityThreshold}")

        // Load model ------------------------------------------------------------
        val loadStart = System.currentTimeMillis()
        try {
            llama.load(
                modelPath = modelPath,
                params = LlamaGPU.InferenceParams(
                    minP = 0.05f,
                    temperature = 0.4f,                 // Moderate-low temp for factual QA (0.2 was too restrictive)
                    topK = 30,                          // Slightly tighter than default 40
                    repeatPenalty = 1.15f,              // Stronger anti-repetition (was 1.1)
                    storeChats = false,                 // benchmark: independent prompts
                    contextSize = safeContextSize.toLong(),
                    chatTemplate = chatTemplate,
                    numThreads = deviceProfile.optimalThreads,
                    useMmap = true,
                    useMlock = deviceProfile.deviceTier == DeviceTier.HIGH,
                    flashAttention = true,
                    kvCacheType = KVCacheType.Q8_0
                )
            )
            if (!model.id.contains("gemma")) {
                llama.addSystemPrompt(BENCHMARK_SYSTEM_PROMPT)
            }
        } catch (e: Exception) {
            log("❌ Model load failed: ${e.message}")
            return ModelResult(model.id, model.name, model.parameters, 0L, emptyList())
        }
        val loadTimeMs = System.currentTimeMillis() - loadStart
        log("✅ Loaded in ${loadTimeMs}ms")

        // Run each question -----------------------------------------------------
        val qResults = mutableListOf<QuestionResult>()
        val total = RagBenchmark.questions.size
        for ((qIdx, qa) in RagBenchmark.questions.withIndex()) {
            emit(BenchmarkUpdate.QuestionStarted(qa.id, qa.query, qIdx + 1, total))
            log("")
            log("───────── Q${qIdx + 1}/$total [${qa.id}] ─────────")
            log("Q: ${qa.query}")

            val qResult = try {
                runSingleQuery(llama, ragEngine, qa, model, safeContextSize, log)
            } catch (e: Exception) {
                Log.e(TAG, "Query ${qa.id} failed", e)
                log("❌ Query failed: ${e.message}")
                QuestionResult(
                    qid = qa.id, query = qa.query, expectedAnswer = qa.expectedAnswer,
                    response = "", ttftMs = 0L, avgItlMs = 0.0, tokensPerSec = 0.0,
                    tokenCount = 0, score = 0f, matchedPhrases = emptyList(),
                    missedPhrases = qa.keyPhrases, retrievedChunks = 0,
                    ragLatencyMs = 0L, totalLatencyMs = 0L, errorMessage = e.message
                )
            }
            qResults.add(qResult)
            emit(BenchmarkUpdate.QuestionFinished(qResult))
            log("Score: ${"%.2f".format(qResult.score)}  TTFT=${qResult.ttftMs}ms  ITL=${"%.1f".format(qResult.avgItlMs)}ms  toks=${qResult.tokenCount}  ${"%.1f".format(qResult.tokensPerSec)} tok/s")
            log("Matched: ${qResult.matchedPhrases}")
            log("Missed:  ${qResult.missedPhrases}")
            log("A: ${qResult.response.take(200).replace("\n", " ")}…")
        }

        // Unload ----------------------------------------------------------------
        try { llama.close(); log("🧹 Unloaded ${model.name}") } catch (e: Exception) {
            log("⚠️ Unload error: ${e.message}")
        }
        // hint GC between heavy models so we don't OOM
        System.gc()

        return ModelResult(model.id, model.name, model.parameters, loadTimeMs, qResults)
    }

    // ----- per-question ---------------------------------------------------------

    private suspend fun runSingleQuery(
        llama: LlamaGPU,
        ragEngine: RagEngine,
        qa: RagBenchmark.QA,
        model: ModelInfo,
        maxContextSize: Int,
        log: (String) -> Unit
    ): QuestionResult {
        val tStart = System.currentTimeMillis()

        // 0. Clear chat between every query — benchmark questions are independent,
        //    so we don't want prior Q/A history accumulating in the KV cache.
        //    This is the most reliable way to prevent "context size reached" errors.
        try {
            llama.clearChat()
            if (!model.id.contains("gemma")) {
                llama.addSystemPrompt(BENCHMARK_SYSTEM_PROMPT)
            }
        } catch (e: Exception) {
            log("⚠️ clearChat failed: ${e.message}")
        }

        // 1. RAG retrieval
        val ragStart = System.currentTimeMillis()
        val ragResult = ragEngine.query(qa.query)
        val ragLatency = System.currentTimeMillis() - ragStart
        log("RAG: ${ragResult.retrievedChunks.size} chunks in ${ragLatency}ms; augmented prompt = ${ragResult.augmentedPrompt.length} chars")

        // 2. Stream tokens & measure TTFT / ITL
        var ttft: Long? = null
        var lastTokenTs = 0L
        val itls = mutableListOf<Long>()
        var tokenCount = 0
        val responseBuilder = StringBuilder()
        val inferStart = System.currentTimeMillis()

        llama.getResponseAsFlow(ragResult.augmentedPrompt).collect { piece ->
            val now = System.currentTimeMillis()
            if (ttft == null) {
                ttft = now - inferStart
            } else {
                itls.add(now - lastTokenTs)
            }
            lastTokenTs = now
            tokenCount++
            responseBuilder.append(piece)
        }

        val totalTime = System.currentTimeMillis() - inferStart
        val totalLatency = System.currentTimeMillis() - tStart
        val avgItl = if (itls.isNotEmpty()) itls.average() else 0.0
        val tps = if (totalTime > 0) tokenCount / (totalTime / 1000.0) else 0.0
        val response = responseBuilder.toString()

        val s = RagBenchmark.scoreResponse(response, qa)
        return QuestionResult(
            qid = qa.id, query = qa.query, expectedAnswer = qa.expectedAnswer,
            response = response,
            ttftMs = ttft ?: 0L, avgItlMs = avgItl, tokensPerSec = tps, tokenCount = tokenCount,
            score = s.score, matchedPhrases = s.matchedPhrases, missedPhrases = s.missedPhrases,
            retrievedChunks = ragResult.retrievedChunks.size,
            ragLatencyMs = ragLatency, totalLatencyMs = totalLatency
        )
    }

    // ----- report --------------------------------------------------------------

    private fun writeReport(results: List<ModelResult>): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.filesDir, "rag_benchmark_$ts.txt")
        val sb = StringBuilder()
        sb.appendLine("RAG Benchmark Report — $ts")
        sb.appendLine("====================================================")
        sb.appendLine()
        sb.appendLine("SUMMARY")
        sb.appendLine("Model                              | Score | TTFT(ms) | ITL(ms) | tok/s | LoadMs")
        sb.appendLine("-----------------------------------+-------+----------+---------+-------+--------")
        for (m in results) {
            sb.appendLine(
                "%-34s | %5.2f | %8d | %7.1f | %5.1f | %6d".format(
                    m.modelName.take(34), m.avgScore, m.avgTtftMs, m.avgItlMs, m.avgTokensPerSec, m.loadTimeMs
                )
            )
        }
        sb.appendLine()
        for (m in results) {
            sb.appendLine("====================================================")
            sb.appendLine("Model: ${m.modelName}  [${m.params}]   loadTime=${m.loadTimeMs}ms")
            sb.appendLine("AvgScore=%.2f  AvgTTFT=%dms  AvgITL=%.1fms  Avg=%.1f tok/s".format(
                m.avgScore, m.avgTtftMs, m.avgItlMs, m.avgTokensPerSec))
            sb.appendLine()
            for (q in m.questions) {
                sb.appendLine("[${q.qid}] score=${"%.2f".format(q.score)}  ttft=${q.ttftMs}ms  itl=${"%.1f".format(q.avgItlMs)}ms  tok/s=${"%.1f".format(q.tokensPerSec)}  toks=${q.tokenCount}  chunks=${q.retrievedChunks}  ragMs=${q.ragLatencyMs}")
                sb.appendLine("  Q: ${q.query}")
                sb.appendLine("  Expected: ${q.expectedAnswer}")
                sb.appendLine("  Got:      ${q.response.replace("\n", " ")}")
                sb.appendLine("  Matched:  ${q.matchedPhrases}")
                sb.appendLine("  Missed:   ${q.missedPhrases}")
                q.errorMessage?.let { sb.appendLine("  Error: $it") }
                sb.appendLine()
            }
        }
        file.writeText(sb.toString())
        return file.absolutePath
    }
}
