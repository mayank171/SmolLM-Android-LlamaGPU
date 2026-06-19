package io.shubham0204.startwithsmollm.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.shubham0204.startwithsmollm.benchmark.RagBenchmarkRunner

data class RagBenchmarkUiState(
    val isRunning: Boolean = false,
    val status: String = "Idle",
    val logLines: List<String> = emptyList(),
    val modelResults: List<RagBenchmarkRunner.ModelResult> = emptyList(),
    val reportPath: String? = null,
    val error: String? = null,
    val pickedUri: Uri? = null,
    val currentModelIdx: Int = 0,
    val totalModels: Int = 0,
    val currentQuestionIdx: Int = 0,
    val totalQuestions: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagBenchmarkScreen(
    state: RagBenchmarkUiState,
    onPickUri: (Uri) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(onPickUri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RAG Benchmark", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // PDF picker + Start row -----------------------------------------------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("1. Select benchmark PDF", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = state.pickedUri?.toString()?.take(80) ?: "No file selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("application/pdf")) },
                            enabled = !state.isRunning,
                            modifier = Modifier.weight(1f)
                        ) { Text("Pick PDF") }
                        Button(
                            onClick = onStart,
                            enabled = !state.isRunning && state.pickedUri != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.isRunning) "Running..." else "Start")
                        }
                    }
                }
            }

            // Status / progress -----------------------------------------------------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Status: ${state.status}", style = MaterialTheme.typography.titleSmall)
                    if (state.isRunning) {
                        if (state.totalModels > 0) {
                            Text("Model ${state.currentModelIdx}/${state.totalModels}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.totalQuestions > 0) {
                            Text("Question ${state.currentQuestionIdx}/${state.totalQuestions}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    state.error?.let {
                        Text("Error: $it", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    state.reportPath?.let {
                        Text("Report: $it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Results table ----------------------------------------------------------
            if (state.modelResults.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Results", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth()) {
                            HeaderCell("Model", 2.5f); HeaderCell("Score", 1f)
                            HeaderCell("TTFT", 1f); HeaderCell("ITL", 1f); HeaderCell("tok/s", 1f)
                        }
                        Divider()
                        state.modelResults.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Cell(r.modelName, 2.5f)
                                Cell("%.2f".format(r.avgScore), 1f)
                                Cell("${r.avgTtftMs}", 1f)
                                Cell("%.0f".format(r.avgItlMs), 1f)
                                Cell("%.1f".format(r.avgTokensPerSec), 1f)
                            }
                        }
                    }
                }
            }

            // Log box ----------------------------------------------------------------
            Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Logs (${state.logLines.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LaunchedEffect(state.logLines.size) {
                        if (state.logLines.isNotEmpty()) {
                            listState.scrollToItem(state.logLines.size - 1)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(8.dp)
                    ) {
                        items(state.logLines) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(label: String, weight: Float) {
    Text(
        label,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun RowScope.Cell(value: String, weight: Float) {
    Text(
        value,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodySmall
    )
}
