package io.shubham0204.startwithsmollm.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.shubham0204.startwithsmollm.rag.Document
import io.shubham0204.startwithsmollm.rag.DocumentType
import io.shubham0204.startwithsmollm.rag.RagEngine
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagScreen(
    documents: List<Document>,
    stats: RagEngine.RagStats,
    isProcessing: Boolean,
    ragEnabled: Boolean,
    onRagEnabledChange: (Boolean) -> Unit,
    onAddDocument: (Uri) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onDeleteAllDocuments: () -> Unit,
    onBack: () -> Unit
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var documentToDelete by remember { mutableStateOf<Document?>(null) }
    
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onAddDocument(it) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Knowledge Base") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (documents.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Delete All")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    documentPicker.launch(arrayOf(
                        "text/plain",
                        "text/markdown",
                        "application/pdf",
                        "image/jpeg",
                        "image/png",
                        "image/webp",
                        "image/bmp"
                    ))
                },
                icon = { Icon(Icons.Default.Add, "Add") },
                text = { Text("Add Document") },
                expanded = !isProcessing
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // RAG Toggle Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (ragEnabled) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RAG Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (ragEnabled) 
                                "Answers will use your documents" 
                            else 
                                "Using model knowledge only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = ragEnabled,
                        onCheckedChange = onRagEnabledChange,
                        enabled = documents.isNotEmpty()
                    )
                }
            }
            
            // Stats Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            icon = Icons.Default.Description,
                            value = "${stats.documentCount}",
                            label = "Documents"
                        )
                        StatItem(
                            icon = Icons.Default.Layers,
                            value = "${stats.totalChunks}",
                            label = "Chunks"
                        )
                        StatItem(
                            icon = Icons.Default.Storage,
                            value = String.format("%.1f MB", stats.databaseSizeMB),
                            label = "Size"
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Embedding type indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (stats.usingNeuralEmbeddings) 
                                Icons.Default.Psychology 
                            else 
                                Icons.Default.TextFields,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (stats.usingNeuralEmbeddings)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Embeddings: ${stats.embeddingType}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (stats.usingNeuralEmbeddings)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Processing indicator
            if (isProcessing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Text(
                    text = "Processing document...",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            // Documents List
            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No documents yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Add PDF or TXT files to enable RAG",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(documents, key = { it.id }) { document ->
                        DocumentCard(
                            document = document,
                            onDelete = { documentToDelete = document }
                        )
                    }
                }
            }
        }
    }
    
    // Delete single document dialog
    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("Delete Document") },
            text = { Text("Delete \"${doc.name}\" and all its chunks?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDocument(doc.id)
                        documentToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete all dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Documents") },
            text = { Text("This will delete all ${stats.documentCount} documents and ${stats.totalChunks} chunks. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllDocuments()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DocumentCard(
    document: Document,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document type icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = when (document.type) {
                            DocumentType.PDF -> MaterialTheme.colorScheme.errorContainer
                            DocumentType.TXT -> MaterialTheme.colorScheme.primaryContainer
                            DocumentType.MARKDOWN -> MaterialTheme.colorScheme.tertiaryContainer
                            DocumentType.IMAGE -> MaterialTheme.colorScheme.secondaryContainer
                            DocumentType.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (document.type) {
                        DocumentType.PDF -> Icons.Default.PictureAsPdf
                        DocumentType.TXT -> Icons.Default.TextSnippet
                        DocumentType.MARKDOWN -> Icons.Default.Code
                        DocumentType.IMAGE -> Icons.Default.Image
                        DocumentType.UNKNOWN -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = when (document.type) {
                        DocumentType.PDF -> MaterialTheme.colorScheme.error
                        DocumentType.TXT -> MaterialTheme.colorScheme.primary
                        DocumentType.MARKDOWN -> MaterialTheme.colorScheme.tertiary
                        DocumentType.IMAGE -> MaterialTheme.colorScheme.secondary
                        DocumentType.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Document info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${document.chunkCount} chunks • ${formatSize(document.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Added ${dateFormat.format(Date(document.addedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
