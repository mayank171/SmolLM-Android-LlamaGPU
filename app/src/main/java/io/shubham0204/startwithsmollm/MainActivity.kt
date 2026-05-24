package io.shubham0204.startwithsmollm

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Speed
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.shubham0204.startwithsmollm.ui.BenchmarkScreen
import io.shubham0204.startwithsmollm.ui.MarkdownText
import io.shubham0204.startwithsmollm.ui.ModelSelectionScreen
import io.shubham0204.startwithsmollm.ui.RagScreen
import io.shubham0204.startwithsmollm.ui.theme.SmolLMStarterTemplateTheme
import kotlinx.collections.immutable.ImmutableList

class MainActivity : ComponentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SmolLMStarterTemplateTheme {
                val appState by viewModel.appStateFlow.collectAsState()
                val context = LocalContext.current
                
                // Show toast when context is trimmed
                LaunchedEffect(appState.chatState.toastMessage) {
                    appState.chatState.toastMessage?.let { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        viewModel.onEvent(AppEvent.ClearToast)
                    }
                }
                
                AnimatedContent(
                    targetState = appState.currentScreen,
                    transitionSpec = {
                        if (targetState == AppScreen.CHAT) {
                            slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                        } else {
                            slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                        }
                    },
                    label = "screen_transition"
                ) { screen ->
                    when (screen) {
                        AppScreen.MODEL_SELECTION -> {
                            ModelSelectionScreen(
                                uiState = appState.modelSelectionState,
                                onDownloadClick = { model ->
                                    viewModel.onEvent(AppEvent.DownloadModel(model))
                                },
                                onDeleteClick = { model ->
                                    viewModel.onEvent(AppEvent.DeleteModel(model))
                                },
                                onStartChat = { model ->
                                    viewModel.onEvent(AppEvent.StartChat(model))
                                }
                            )
                        }
                        AppScreen.CHAT -> {
                            BackHandler {
                                viewModel.onEvent(AppEvent.BackToModelSelection)
                            }
                            
                            ChatScreen(
                                uiState = appState.chatState,
                                ragDocumentCount = appState.ragState.documents.size,
                                onBackClick = {
                                    viewModel.onEvent(AppEvent.BackToModelSelection)
                                },
                                onQuerySubmit = { query ->
                                    viewModel.onEvent(AppEvent.SubmitQuery(query))
                                },
                                onClearChat = {
                                    viewModel.onEvent(AppEvent.ClearChat)
                                },
                                onBenchmarkClick = {
                                    viewModel.onEvent(AppEvent.OpenBenchmark)
                                },
                                onRagClick = {
                                    viewModel.onEvent(AppEvent.OpenRag)
                                }
                            )
                        }
                        AppScreen.BENCHMARK -> {
                            BackHandler {
                                viewModel.onEvent(AppEvent.BackFromBenchmark)
                            }
                            BenchmarkScreen(
                                modelPath = viewModel.getCurrentModelPath(),
                                onBack = {
                                    viewModel.onEvent(AppEvent.BackFromBenchmark)
                                }
                            )
                        }
                        AppScreen.RAG -> {
                            BackHandler {
                                viewModel.onEvent(AppEvent.BackFromRag)
                            }
                            RagScreen(
                                documents = appState.ragState.documents,
                                stats = appState.ragState.stats,
                                isProcessing = appState.ragState.isProcessing,
                                ragEnabled = appState.chatState.ragEnabled,
                                onRagEnabledChange = { enabled ->
                                    viewModel.onEvent(AppEvent.SetRagEnabled(enabled))
                                },
                                onAddDocument = { uri ->
                                    viewModel.onEvent(AppEvent.AddDocument(uri))
                                },
                                onDeleteDocument = { docId ->
                                    viewModel.onEvent(AppEvent.DeleteDocument(docId))
                                },
                                onDeleteAllDocuments = {
                                    viewModel.onEvent(AppEvent.DeleteAllDocuments)
                                },
                                onBack = {
                                    viewModel.onEvent(AppEvent.BackFromRag)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ChatScreen(
        uiState: ChatUIState,
        ragDocumentCount: Int = 0,
        onBackClick: () -> Unit,
        onQuerySubmit: (String) -> Unit,
        onClearChat: () -> Unit,
        onBenchmarkClick: () -> Unit,
        onRagClick: () -> Unit = {}
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = uiState.currentModelName.ifEmpty { "AI Assistant" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = when (uiState.modelLoadingState) {
                                        ModelLoadingState.LOADING -> "Loading model..."
                                        ModelLoadingState.SUCCESS -> {
                                            val ragStatus = if (uiState.ragEnabled) " • RAG" else ""
                                            "Context: ${uiState.contextUsagePercent}%$ragStatus"
                                        }
                                        ModelLoadingState.FAILURE -> "Failed to load"
                                        ModelLoadingState.NOT_LOADED -> "Initializing..."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.contextUsagePercent >= 80) {
                                        MaterialTheme.colorScheme.error
                                    } else if (uiState.ragEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    },
                    actions = {
                        // RAG button
                        IconButton(onClick = onRagClick) {
                            Box {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Knowledge Base",
                                    tint = if (uiState.ragEnabled) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (ragDocumentCount > 0) {
                                    Badge(
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Text("$ragDocumentCount")
                                    }
                                }
                            }
                        }
                        // Benchmark button
                        IconButton(onClick = onBenchmarkClick) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Benchmark",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (uiState.messages.isNotEmpty()) {
                            IconButton(onClick = onClearChat) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear Chat",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                ChatMessagesList(
                    messages = uiState.messages,
                    modelInferenceState = uiState.modelInferenceState,
                    modifier = Modifier.weight(1f)
                )
                MessageInput(
                    modelLoadingState = uiState.modelLoadingState,
                    onQuerySubmit = onQuerySubmit
                )
            }
        }
    }

    @Composable
    private fun ChatMessagesList(
        messages: ImmutableList<ChatMessage>,
        modelInferenceState: ModelInferenceState,
        modifier: Modifier = Modifier
    ) {
        val listState = rememberLazyListState()
        
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }

        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            items(messages) { message ->
                ChatMessageBubble(message)
            }
            
            if (modelInferenceState == ModelInferenceState.LOADING) {
                item {
                    ThinkingIndicator()
                }
            }
            
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    @Composable
    private fun ChatMessageBubble(message: ChatMessage) {
        val isHuman = message.userRole == UserRole.HUMAN
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isHuman) Arrangement.End else Arrangement.Start
        ) {
            if (isHuman) {
                // User message - simple bubble
                Card(
                    modifier = Modifier.widthIn(max = 300.dp),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 4.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                // AI response - full width with markdown support
                Column {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.citations.isEmpty()) 16.dp else 4.dp,
                            bottomEnd = if (message.citations.isEmpty()) 16.dp else 4.dp
                        ),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        MarkdownText(
                            text = message.content,
                            modifier = Modifier.padding(12.dp),
                            defaultColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Citations section
                    if (message.citations.isNotEmpty()) {
                        CitationsSection(citations = message.citations)
                    }
                }
            }
        }
    }
    
    @Composable
    private fun CitationsSection(citations: List<io.shubham0204.startwithsmollm.rag.Citation>) {
        var expanded by remember { mutableStateOf(false) }
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 4.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Header row - clickable to expand
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Source,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Sources (${citations.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Expanded citations
                if (expanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    citations.forEach { citation ->
                        CitationItem(citation = citation)
                        if (citation != citations.last()) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun CitationItem(citation: io.shubham0204.startwithsmollm.rag.Citation) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[${citation.index}] ${citation.documentName}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${(citation.score * 100).toInt()}% match",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = citation.chunkText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    private fun ThinkingIndicator() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Thinking...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    private fun MessageInput(
        modelLoadingState: ModelLoadingState,
        onQuerySubmit: (String) -> Unit
    ) {
        var queryText by remember { mutableStateOf("") }
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    modifier = Modifier.weight(1f),
                    enabled = modelLoadingState == ModelLoadingState.SUCCESS,
                    placeholder = {
                        Text(
                            text = when (modelLoadingState) {
                                ModelLoadingState.LOADING -> "Loading model..."
                                ModelLoadingState.SUCCESS -> "Type a message..."
                                ModelLoadingState.FAILURE -> "Model failed to load"
                                ModelLoadingState.NOT_LOADED -> "Initializing..."
                            }
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    singleLine = false,
                    maxLines = 4
                )
                
                when (modelLoadingState) {
                    ModelLoadingState.LOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    ModelLoadingState.SUCCESS -> {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (queryText.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            IconButton(
                                onClick = {
                                    if (queryText.isNotBlank()) {
                                        onQuerySubmit(queryText)
                                        queryText = ""
                                    }
                                },
                                enabled = queryText.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = if (queryText.isNotBlank()) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                    else -> {
                        // NOT_LOADED and FAILURE cases
                    }
                }
            }
        }
    }
}
