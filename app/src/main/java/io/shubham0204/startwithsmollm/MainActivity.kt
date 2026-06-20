package io.shubham0204.startwithsmollm

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.unit.sp
import io.shubham0204.startwithsmollm.ui.BenchmarkScreen
import io.shubham0204.startwithsmollm.ui.InferenceInsightsScreen
import io.shubham0204.startwithsmollm.ui.MarkdownText
import io.shubham0204.startwithsmollm.ui.ModelSelectionScreen
import io.shubham0204.startwithsmollm.ui.RagScreen
import io.shubham0204.startwithsmollm.ui.RagBenchmarkScreen
import io.shubham0204.startwithsmollm.ui.theme.SmolLMStarterTemplateTheme
import io.shubham0204.startwithsmollm.voice.VoiceManager
import io.shubham0204.startwithsmollm.data.ExpertMode
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SmolLMStarterTemplateTheme {
                val appState by viewModel.appStateFlow.collectAsState()
                val context = LocalContext.current

                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
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
                            
                            val voiceState by viewModel.voiceState.collectAsState()
                            val isExpertMode by viewModel.isExpertMode.collectAsState()
                            val inferenceMetrics by viewModel.inferenceMetrics.collectAsState()
                            
                            ChatScreen(
                                uiState = appState.chatState,
                                ragDocumentCount = appState.ragState.documents.size,
                                voiceState = voiceState,
                                isExpertMode = isExpertMode,
                                inferenceMetrics = inferenceMetrics,
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
                                },
                                onStartVoiceInput = {
                                    viewModel.startVoiceInput()
                                },
                                onStopVoiceInput = {
                                    viewModel.stopVoiceInputAndSubmit()
                                },
                                onCancelVoiceInput = {
                                    viewModel.cancelVoiceInput()
                                },
                                onToggleAutoSpeak = {
                                    viewModel.toggleAutoSpeak()
                                },
                                onStopSpeaking = {
                                    viewModel.stopSpeaking()
                                },
                                onModelNameTap = {
                                    viewModel.onModelNameTap()
                                },
                                onImageQuery = { uri, question, preExtracted ->
                                    viewModel.onEvent(AppEvent.ProcessImageQuery(uri, question, preExtracted))
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
                                },
                                onOpenBenchmark = {
                                    viewModel.onEvent(AppEvent.OpenRagBenchmark)
                                }
                            )
                        }
                        AppScreen.RAG_BENCHMARK -> {
                            BackHandler {
                                viewModel.onEvent(AppEvent.BackFromRagBenchmark)
                            }
                            val ragBenchmarkState by viewModel.ragBenchmarkState.collectAsState()
                            RagBenchmarkScreen(
                                state = ragBenchmarkState,
                                onPickUri = { uri ->
                                    viewModel.onEvent(AppEvent.PickRagBenchmarkUri(uri))
                                },
                                onStart = { viewModel.onEvent(AppEvent.StartRagBenchmark) },
                                onBack = { viewModel.onEvent(AppEvent.BackFromRagBenchmark) }
                            )
                        }
                        AppScreen.PERFORMANCE_DASHBOARD -> {
                            BackHandler {
                                viewModel.onEvent(AppEvent.BackFromPerformanceDashboard)
                            }
                            io.shubham0204.startwithsmollm.ui.MinimalPerformanceDashboard(
                                onDismiss = {
                                    viewModel.onEvent(AppEvent.BackFromPerformanceDashboard)
                                }
                            )
                        }
                    }
                }
                
                // Themed toast overlay (covers all screens)
                io.shubham0204.startwithsmollm.ui.AppToastHost(
                    message = appState.chatState.toastMessage,
                    type = io.shubham0204.startwithsmollm.ui.ToastType.INFO,
                    onDismiss = { viewModel.onEvent(AppEvent.ClearToast) }
                )
                } // end Box
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ChatScreen(
        uiState: ChatUIState,
        ragDocumentCount: Int = 0,
        voiceState: VoiceManager.VoiceState = VoiceManager.VoiceState(),
        isExpertMode: Boolean = false,
        inferenceMetrics: io.shubham0204.startwithsmollm.ui.InferenceMetrics = io.shubham0204.startwithsmollm.ui.InferenceMetrics(),
        onBackClick: () -> Unit,
        onQuerySubmit: (String) -> Unit,
        onClearChat: () -> Unit,
        onBenchmarkClick: () -> Unit,
        onRagClick: () -> Unit = {},
        onStartVoiceInput: () -> Unit = {},
        onStopVoiceInput: () -> Unit = {},
        onCancelVoiceInput: () -> Unit = {},
        onToggleAutoSpeak: () -> Unit = {},
        onStopSpeaking: () -> Unit = {},
        onModelNameTap: () -> Unit = {},
        onImageQuery: (Uri, String, String?) -> Unit = { _, _, _ -> }
    ) {
        // State for showing inference insights dialog
        var showInferenceInsights by remember { mutableStateOf(false) }
        
        // Show Inference Insights dialog when expert mode is enabled
        if (showInferenceInsights && isExpertMode) {
            InferenceInsightsScreen(
                metrics = inferenceMetrics,
                onDismiss = { showInferenceInsights = false }
            )
            return
        }
        var showOverflowMenu by remember { mutableStateOf(false) }
        Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    title = {
                        Column(
                            modifier = Modifier.clickable { onModelNameTap() }
                        ) {
                            Text(
                                text = uiState.currentModelName.ifEmpty { "AI Assistant" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Status dot
                                val statusColor = when {
                                    uiState.modelLoadingState == ModelLoadingState.LOADING -> androidx.compose.ui.graphics.Color(0xFFFFA726)
                                    uiState.modelLoadingState == ModelLoadingState.FAILURE -> MaterialTheme.colorScheme.error
                                    uiState.contextUsagePercent >= 80 -> MaterialTheme.colorScheme.error
                                    uiState.modelLoadingState == ModelLoadingState.SUCCESS -> androidx.compose.ui.graphics.Color(0xFF43A047)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(statusColor, androidx.compose.foundation.shape.CircleShape)
                                )
                                Text(
                                    text = when (uiState.modelLoadingState) {
                                        ModelLoadingState.LOADING -> "Loading…"
                                        ModelLoadingState.SUCCESS -> "${uiState.contextUsagePercent}% context"
                                        ModelLoadingState.FAILURE -> "Failed"
                                        ModelLoadingState.NOT_LOADED -> "Initializing…"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        // RAG button — kept visible because badge shows doc count
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
                        
                        // Overflow menu for everything else
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Benchmark") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Speed, contentDescription = null)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onBenchmarkClick()
                                    }
                                )
                                if (isExpertMode) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Inference insights") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            showInferenceInsights = true
                                        }
                                    )
                                }
                                if (uiState.messages.isNotEmpty()) {
                                    androidx.compose.material3.HorizontalDivider()
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Clear chat", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            onClearChat()
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
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
                    modelInferenceState = uiState.modelInferenceState,
                    voiceState = voiceState,
                    onQuerySubmit = onQuerySubmit,
                    onStartVoiceInput = onStartVoiceInput,
                    onStopVoiceInput = onStopVoiceInput,
                    onCancelVoiceInput = onCancelVoiceInput,
                    onToggleAutoSpeak = onToggleAutoSpeak,
                    onStopSpeaking = onStopSpeaking,
                    onStopGeneration = { viewModel.stopGeneration() },
                    onImageQuery = onImageQuery
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
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }
            
            items(messages) { message ->
                ChatMessageBubble(
                    message = message,
                    onSpeakClick = { text -> 
                        viewModel.speakText(text)
                    }
                )
            }
            
            if (modelInferenceState == ModelInferenceState.LOADING) {
                item {
                    ThinkingIndicator()
                }
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    @Composable
    private fun ChatMessageBubble(
        message: ChatMessage,
        onSpeakClick: ((String) -> Unit)? = null
    ) {
        val isHuman = message.userRole == UserRole.HUMAN
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = if (isHuman) Arrangement.End else Arrangement.Start
        ) {
            if (isHuman) {
                // User message - modern bubble with tail (right-aligned)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(start = 32.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 22.dp,
                            topEnd = 22.dp,
                            bottomStart = 22.dp,
                            bottomEnd = 6.dp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = 18.dp,
                                vertical = 12.dp
                            )
                        ) {
                            // Show image indicator if this is an image query
                            if (message.imageUri != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "Image attached",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            } else {
                // AI response - modern bubble with speak button
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.98f)
                        .padding(end = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 20.dp
                        ),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column {
                            MarkdownText(
                                text = message.content,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 12.dp,
                                    bottom = 8.dp
                                ),
                                defaultColor = MaterialTheme.colorScheme.onSurface
                            )
                            
                            // Speak button
                            if (onSpeakClick != null && message.content.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = { onSpeakClick(message.content) },
                                        modifier = Modifier.size(36.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeUp,
                                                contentDescription = "Speak this message",
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Citations section
                    if (message.citations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
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
                        text = "${"%.1f".format(citation.score * 100)}% match",
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp
                ),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimatedTypingDots()
                    Text(
                        text = "Thinking",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MessageInput(
        modelLoadingState: ModelLoadingState,
        modelInferenceState: ModelInferenceState,
        voiceState: VoiceManager.VoiceState = VoiceManager.VoiceState(),
        onQuerySubmit: (String) -> Unit,
        onStartVoiceInput: () -> Unit = {},
        onStopVoiceInput: () -> Unit = {},
        onCancelVoiceInput: () -> Unit = {},
        onToggleAutoSpeak: () -> Unit = {},
        onStopSpeaking: () -> Unit = {},
        onStopGeneration: () -> Unit = {},
        onImageQuery: (Uri, String, String?) -> Unit = { _, _, _ -> }
    ) {
        var queryText by remember { mutableStateOf("") }
        val context = LocalContext.current
        
        // Image input state
        var showImageSourceSheet by remember { mutableStateOf(false) }
        var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
        var showImagePreview by remember { mutableStateOf(false) }
        var isProcessingImage by remember { mutableStateOf(false) }
        var extractedImageText by remember { mutableStateOf<String?>(null) }
        
        // Image input + query processor (processor lives here so we can pre-extract OCR in preview)
        val imageInputManager = remember { io.shubham0204.startwithsmollm.image.ImageInputManager(context) }
        val imageQueryProcessor = remember { io.shubham0204.startwithsmollm.image.ImageQueryProcessor(context) }
        
        // Pre-extract OCR as soon as the preview dialog opens (parallel with user typing question).
        // This is the P0 TTFT win: by the time user hits Send, OCR is already done.
        LaunchedEffect(showImagePreview, selectedImageUri) {
            if (showImagePreview && selectedImageUri != null && extractedImageText == null) {
                isProcessingImage = true
                val uri = selectedImageUri!!
                val text = imageQueryProcessor.extractTextOnly(uri)
                // Guard against stale results if user dismissed/changed image
                if (selectedImageUri == uri && showImagePreview) {
                    extractedImageText = text ?: ""  // empty string = OCR done, no text found
                    isProcessingImage = false
                }
            }
        }
        
        // Camera launcher
        val cameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                imageInputManager.getCurrentPhotoUri()?.let { uri ->
                    selectedImageUri = uri
                    showImagePreview = true
                }
            }
        }
        
        // Gallery launcher
        val galleryLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            uri?.let {
                selectedImageUri = it
                showImagePreview = true
            }
        }
        
        // Camera permission launcher
        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                imageInputManager.createCameraImageUri()?.let { uri ->
                    cameraLauncher.launch(uri)
                }
            } else {
                Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Permission launcher for microphone
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                onStartVoiceInput()
            } else {
                Toast.makeText(context, "Microphone permission required for voice input", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Image source bottom sheet
        if (showImageSourceSheet) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showImageSourceSheet = false }
            ) {
                io.shubham0204.startwithsmollm.ui.ImageSourceBottomSheet(
                    onCameraClick = {
                        showImageSourceSheet = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            imageInputManager.createCameraImageUri()?.let { uri ->
                                cameraLauncher.launch(uri)
                            }
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onGalleryClick = {
                        showImageSourceSheet = false
                        galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onDismiss = { showImageSourceSheet = false }
                )
            }
        }
        
        // Image preview dialog
        val currentImageUri = selectedImageUri
        if (showImagePreview && currentImageUri != null) {
            io.shubham0204.startwithsmollm.ui.ImagePreviewDialog(
                imageUri = currentImageUri,
                extractedText = extractedImageText,
                isProcessing = isProcessingImage,
                onDismiss = {
                    showImagePreview = false
                    selectedImageUri = null
                    extractedImageText = null
                    isProcessingImage = false
                },
                onSend = { question ->
                    // Pass pre-extracted text to skip redundant OCR in ViewModel
                    val preExtracted = extractedImageText?.takeIf { it.isNotBlank() }
                    onImageQuery(currentImageUri, question, preExtracted)
                    showImagePreview = false
                    selectedImageUri = null
                    extractedImageText = null
                }
            )
        }
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp
        ) {
            Column {
                // Voice status indicator
                if (voiceState.isListening || voiceState.isTranscribing || voiceState.isSpeaking) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (voiceState.isListening) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "🎤 Listening...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else if (voiceState.isTranscribing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Processing speech...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else if (voiceState.isSpeaking) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Speaking...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        // Cancel/Stop button
                        IconButton(
                            onClick = {
                                when {
                                    voiceState.isListening -> onCancelVoiceInput()
                                    voiceState.isSpeaking -> onStopSpeaking()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pill-shaped input: TextField in a Surface, no border
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        androidx.compose.material3.TextField(
                            value = queryText,
                            onValueChange = { queryText = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = modelLoadingState == ModelLoadingState.SUCCESS &&
                                      !voiceState.isListening &&
                                      modelInferenceState != ModelInferenceState.LOADING,
                            placeholder = {
                                Text(
                                    text = when {
                                        modelInferenceState == ModelInferenceState.LOADING -> "Generating…"
                                        voiceState.isListening -> "Listening…"
                                        voiceState.isTranscribing -> "Processing…"
                                        modelLoadingState == ModelLoadingState.LOADING -> "Loading model…"
                                        modelLoadingState == ModelLoadingState.SUCCESS -> "Message"
                                        modelLoadingState == ModelLoadingState.FAILURE -> "Model failed to load"
                                        else -> "Initializing…"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingIcon = {
                                IconButton(
                                    onClick = { showImageSourceSheet = true },
                                    enabled = modelLoadingState == ModelLoadingState.SUCCESS &&
                                              modelInferenceState != ModelInferenceState.LOADING
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Add image",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                            ),
                            singleLine = false,
                            maxLines = 5
                        )
                    }
                    
                    when (modelLoadingState) {
                        ModelLoadingState.LOADING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        ModelLoadingState.SUCCESS -> {
                            // Voice input button
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (voiceState.isListening) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                }
                            ) {
                                IconButton(
                                    onClick = {
                                        if (voiceState.isListening) {
                                            // Stop recording and transcribe
                                            onStopVoiceInput()
                                        } else {
                                            // Check permission and start recording
                                            if (ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED
                                            ) {
                                                onStartVoiceInput()
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                    enabled = !voiceState.isTranscribing
                                ) {
                                    Icon(
                                        imageVector = if (voiceState.isListening) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = if (voiceState.isListening) "Stop recording" else "Voice input",
                                        tint = if (voiceState.isListening) {
                                            MaterialTheme.colorScheme.onError
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    )
                                }
                            }
                            
                            // Send/Stop button
                            if (modelInferenceState == ModelInferenceState.LOADING) {
                                // Stop button during generation
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.error
                                ) {
                                    IconButton(
                                        onClick = onStopGeneration
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop generation",
                                            tint = MaterialTheme.colorScheme.onError
                                        )
                                    }
                                }
                            } else {
                                // Send button
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
                        }
                        else -> {
                            // NOT_LOADED and FAILURE cases
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AnimatedTypingDots() {
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
        val dotColor = MaterialTheme.colorScheme.primary
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { index ->
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(
                            durationMillis = 600,
                            delayMillis = index * 150,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                        ),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "dot$index"
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = dotColor.copy(alpha = alpha),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}
