package com.example.tavi.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.tavi.Settings
import com.example.tavi.model.Message
import com.example.tavi.model.MessageRole
import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolResult
import com.example.tavi.theme.BrandAccent
import com.example.tavi.theme.BrandPrimary
import com.example.tavi.theme.BrandPrimaryVariant
import com.example.tavi.theme.BrandSecondary
import com.example.tavi.theme.BrandViolet
import com.example.tavi.theme.SuccessGreen
import com.example.tavi.theme.WarningAmber
import com.example.tavi.workflow.PlanSource
import com.example.tavi.workflow.StepExecutionStatus
import com.example.tavi.workflow.WorkflowPlan
import com.example.tavi.workflow.WorkflowState
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<com.example.tavi.model.ConversationSession?>(null) }
    var showTopMenu by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val sharedText by com.example.tavi.MainActivity.sharedIncomingText.collectAsStateWithLifecycle()
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrEmpty()) {
            inputText = sharedText!!
            com.example.tavi.MainActivity.sharedIncomingText.value = null
        }
    }

    // Auto-scroll to bottom on new messages or streaming tokens
    LaunchedEffect(uiState.messages.size, uiState.isStreaming) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(320.dp)
            ) {
                // Drawer Brand Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Image(
                                painter = painterResource(id = com.example.tavi.R.drawable.tavi_avatar),
                                contentDescription = "Tavi Mascot",
                                modifier = Modifier
                                    .padding(4.dp)
                                    .fillMaxSize()
                            )
                        }
                        Column {
                            Text(
                                text = "Tavi Assistant",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "100% On-Device • Private AI",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Prominent "+ Start New Chat" Button
                    Button(
                        onClick = {
                            viewModel.startNewChat()
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandViolet
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Chat",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Start New Chat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.8.dp
                )

                // Conversation History List
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "CHAT HISTORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )

                    if (uiState.conversations.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No saved conversations",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(uiState.conversations, key = { it.id }) { conv ->
                                val isSelected = conv.id == uiState.currentConversationId
                                Surface(
                                    onClick = {
                                        viewModel.selectConversation(conv.id)
                                        coroutineScope.launch { drawerState.close() }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) BrandViolet.copy(alpha = 0.16f) else Color.Transparent,
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, BrandViolet.copy(alpha = 0.45f)) else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = if (isSelected) BrandViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = conv.title,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val timeStr = remember(conv.updatedAt) {
                                                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(conv.updatedAt))
                                            }
                                            Text(
                                                text = timeStr,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Delete this specific chat safely
                                        IconButton(
                                            onClick = { conversationToDelete = conv },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Chat",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.8.dp
                )

                // Drawer Bottom Actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            onItemClick(com.example.tavi.Capabilities)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Security & Capabilities",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            onItemClick(Settings)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Settings",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                BadgedBox(
                                    badge = {
                                        if (uiState.conversations.size > 1) {
                                            Badge(containerColor = BrandViolet) {
                                                Text(
                                                    text = "${uiState.conversations.size}",
                                                    fontSize = 9.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Chat History",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        },
                        title = {
                            Column(
                                modifier = Modifier.clickable { coroutineScope.launch { drawerState.open() } }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(24.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Image(
                                            painter = painterResource(id = com.example.tavi.R.drawable.tavi_avatar),
                                            contentDescription = null,
                                            modifier = Modifier.padding(2.dp).fillMaxSize()
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = uiState.currentConversationTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        letterSpacing = 0.2.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (uiState.isOffline) SuccessGreen else BrandAccent)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Gemma 3 1B • On-Device",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            // 1-Tap "+ New" Chat Button right on Top Bar!
                            Surface(
                                onClick = { viewModel.startNewChat() },
                                shape = RoundedCornerShape(10.dp),
                                color = BrandViolet.copy(alpha = 0.18f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandViolet.copy(alpha = 0.5f)),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "New Chat",
                                        tint = BrandViolet,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "New",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandViolet
                                    )
                                }
                            }

                            // Clean Overflow Menu (No exposed destructive delete button!)
                            Box {
                                IconButton(onClick = { showTopMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More Options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showTopMenu,
                                    onDismissRequest = { showTopMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Clear Current Chat") },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                                        onClick = {
                                            showTopMenu = false
                                            showClearConfirmDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Security & Capabilities") },
                                        leadingIcon = { Icon(Icons.Default.Security, null) },
                                        onClick = {
                                            showTopMenu = false
                                            onItemClick(com.example.tavi.Capabilities)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Default.Settings, null) },
                                        onClick = {
                                            showTopMenu = false
                                            onItemClick(Settings)
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.8.dp
                    )
                }
            },
            bottomBar = {
            ModernInputDock(
                inputText = inputText,
                isStreaming = uiState.isStreaming,
                isListening = uiState.isListening,
                statusBanner = uiState.statusBanner,
                onTextChanged = { inputText = it },
                onSendClicked = {
                    if (inputText.isNotBlank() && !uiState.isStreaming) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                onQuickPromptClicked = { prompt ->
                    viewModel.sendMessage(prompt)
                },
                onVoiceToggle = { viewModel.toggleVoiceInput() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val userHasMessages = uiState.messages.any { it.role == MessageRole.USER }

            if (!userHasMessages) {
                // Polished Hero Empty State
                HeroEmptyState(
                    onSuggestionClicked = { prompt ->
                        viewModel.sendMessage(prompt)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        ModernMessageBubble(
                            message = message,
                            onSpeak = { viewModel.speakLastMessage() }
                        )
                    }

                    uiState.activeWorkflow?.let { workflow ->
                        item {
                            WorkflowReviewCard(
                                workflow = workflow,
                                onConfirm = { stepId -> viewModel.confirmWorkflowStep(stepId, true) },
                                onCancelConfirm = { stepId -> viewModel.confirmWorkflowStep(stepId, false) },
                                onCancelWorkflow = { viewModel.cancelWorkflow() },
                                onDismiss = { viewModel.dismissWorkflow() }
                            )
                        }
                    }

                    if (uiState.isStreaming && uiState.messages.none { it.isStreaming }) {
                        item {
                            AssistantThinkingIndicator()
                        }
                    }
                }
            }

            // Consequential Action Confirmation Dialog
            uiState.pendingConfirmation?.let { confReq ->
                AlertDialog(
                    onDismissRequest = { viewModel.confirmPendingAction(false) },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(WarningAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    },
                    title = {
                        Text(
                            confReq.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = confReq.details,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🔒 Consequential Action: This local operation modifies device state or communicates externally. Tap confirm to execute.",
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmPendingAction(true) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Confirm & Execute", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.confirmPendingAction(false) }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }

            // Clear Current Conversation Confirmation Dialog
            if (showClearConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showClearConfirmDialog = false },
                    icon = {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = { Text("Clear This Conversation?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "This permanently clears the messages in \"${uiState.currentConversationTitle}\". Other saved chats and memories remain intact.",
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.clearCurrentConversation()
                                showClearConfirmDialog = false
                            }
                        ) {
                            Text("Clear Chat", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Delete Specific Conversation Confirmation Dialog
            conversationToDelete?.let { targetConv ->
                AlertDialog(
                    onDismissRequest = { conversationToDelete = null },
                    icon = {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = { Text("Delete Conversation?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "\"${targetConv.title}\" and all of its messages will be permanently removed from your device.",
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteConversation(targetConv.id)
                                conversationToDelete = null
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { conversationToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
}

private fun getGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
}

/**
 * Flagship Hero Empty State with ambient glow and clean prompt cards.
 */
@Composable
private fun HeroEmptyState(
    onSuggestionClicked: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Halo & Tavi Mascot Hero
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(BrandPrimary.copy(alpha = 0.35f), Color.Transparent)
                        )
                    )
            )
            Surface(
                modifier = Modifier.size(76.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    Brush.linearGradient(listOf(BrandPrimary, BrandViolet, BrandAccent))
                ),
                shadowElevation = 6.dp
            ) {
                Image(
                    painter = painterResource(id = com.example.tavi.R.drawable.tavi_avatar),
                    contentDescription = "Tavi Friendly AI Mascot",
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize()
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "${getGreeting()}, Reagan",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            letterSpacing = (-0.5).sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Your private on-device assistant.\nPowered locally by Gemma 3 1B with 100% offline privacy.",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Quick Category Chips
        val quickChips = listOf(
            "📅 Today's Agenda",
            "💬 Draft SMS",
            "🧠 What do you remember?",
            "🔋 Battery & WiFi",
            "💡 Flashlight"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickChips.forEach { chip ->
                Surface(
                    onClick = { onSuggestionClicked(chip) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Text(
                        text = chip,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Curated Action Cards Grid
        val starterPrompts = listOf(
            Triple(Icons.Default.CalendarMonth, "What's on my agenda today?", "Check calendar schedule & events"),
            Triple(Icons.Default.Sms, "Message Dennis: Are we still meeting?", "Draft an SMS with on-device confirmation"),
            Triple(Icons.Default.AutoAwesome, "What do you remember about me?", "Inspect your saved preferences & profile facts"),
            Triple(Icons.Default.BatteryChargingFull, "Check device battery and WiFi status", "Monitor local device health")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            starterPrompts.forEach { (icon, title, desc) ->
                Surface(
                    onClick = { onSuggestionClicked(title) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = desc,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern message bubble with markdown formatting, copy-to-clipboard, and tool action badges.
 */
@Composable
fun ModernMessageBubble(
    message: Message,
    onSpeak: () -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val clipboardManager = LocalClipboardManager.current
    var copiedRecently by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 5.dp, start = 2.dp)
            ) {
                Surface(
                    modifier = Modifier.size(20.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Image(
                        painter = painterResource(id = com.example.tavi.R.drawable.tavi_avatar),
                        contentDescription = "Tavi",
                        modifier = Modifier
                            .padding(2.dp)
                            .fillMaxSize()
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Tavi",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Local",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Surface(
            shape = if (isUser) {
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
            } else {
                RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            },
            color = if (isUser) BrandViolet else MaterialTheme.colorScheme.surfaceVariant,
            border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            shadowElevation = if (isUser) 1.dp else 0.dp,
            modifier = if (isUser) {
                Modifier.widthIn(min = 40.dp, max = 310.dp)
            } else {
                Modifier.fillMaxWidth(0.96f)
            }
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (message.content.isNotEmpty()) {
                    FormattedMessageText(
                        rawText = message.content,
                        isUser = isUser
                    )
                }

                // Streaming 3-dot animation
                if (message.isStreaming) {
                    Spacer(Modifier.height(8.dp))
                    BouncingDotsIndicator()
                }

                // Render Action / Tool Execution Badges
                if (message.toolCalls.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    for (i in message.toolCalls.indices) {
                        val call = message.toolCalls[i]
                        val result = message.toolResults.getOrNull(i)
                        ToolActionBadge(call = call, result = result)
                    }
                }

                // Action Bar for Assistant Responses
                if (!isUser && message.content.isNotEmpty() && !message.isStreaming) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Copy Button
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.content))
                                copiedRecently = true
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = if (copiedRecently) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy message",
                                modifier = Modifier.size(14.dp),
                                tint = if (copiedRecently) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(Modifier.width(6.dp))

                        // Speak Aloud Button
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Read aloud",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Text renderer supporting bold text, bullet points, and clean typography.
 */
@Composable
private fun FormattedMessageText(
    rawText: String,
    isUser: Boolean
) {
    val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
    val bulletColor = if (isUser) Color.White.copy(alpha = 0.8f) else BrandPrimary

    val formatted = remember(rawText) {
        buildAnnotatedString {
            val parts = rawText.split("\n")
            parts.forEachIndexed { idx, line ->
                val trimmed = line.trimStart()
                if (trimmed.startsWith("•") || trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                    // Bullet point
                    append("  •  ")
                    val content = trimmed.removePrefix("•").removePrefix("- ").removePrefix("* ").trim()
                    appendMarkdownFormatted(content, textColor)
                } else if (trimmed.startsWith("### ")) {
                    // Subheader
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp))
                    append(trimmed.removePrefix("### ").trim())
                    pop()
                } else {
                    appendMarkdownFormatted(line, textColor)
                }
                if (idx < parts.size - 1) {
                    append("\n")
                }
            }
        }
    }

    Text(
        text = formatted,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = textColor
    )
}

private fun AnnotatedString.Builder.appendMarkdownFormatted(text: String, defaultColor: Color) {
    // Simple regex matching **bold**
    val regex = Regex("\\*\\*(.*?)\\*\\*")
    var lastIndex = 0
    val matches = regex.findAll(text)

    for (match in matches) {
        if (match.range.first > lastIndex) {
            append(text.substring(lastIndex, match.range.first))
        }
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(match.groupValues[1])
        pop()
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

/**
 * Animated 3-dot bouncing wave indicator for token streaming.
 */
@Composable
fun BouncingDotsIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncing_dots")

    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 140, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 280, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .scale(1f + dot1Offset / -10f)
                .clip(CircleShape)
                .background(BrandPrimary)
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .scale(1f + dot2Offset / -10f)
                .clip(CircleShape)
                .background(BrandPrimary)
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .scale(1f + dot3Offset / -10f)
                .clip(CircleShape)
                .background(BrandPrimary)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Thinking...",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Modern native Tool Action card with status chips.
 */
@Composable
fun ToolActionBadge(call: ToolCall, result: ToolResult?) {
    val isSuccess = result?.isSuccess == true
    val requiresConfirm = result?.requiresUserConfirmation == true

    val (icon, label, accentColor) = when {
        call.toolName.startsWith("calendar") -> Triple(Icons.Default.CalendarMonth, "Calendar", Color(0xFF3B82F6))
        call.toolName.startsWith("messages") -> Triple(Icons.Default.Sms, "Messages", Color(0xFF06B6D4))
        call.toolName.startsWith("contacts") -> Triple(Icons.Default.Contacts, "Contacts", Color(0xFF8B5CF6))
        call.toolName.startsWith("reminders") -> Triple(Icons.Default.NotificationsActive, "Reminder", WarningAmber)
        call.toolName.startsWith("device_battery") -> Triple(Icons.Default.BatteryChargingFull, "Battery", SuccessGreen)
        call.toolName.startsWith("device_toggle") -> Triple(Icons.Default.FlashlightOn, "Flashlight", WarningAmber)
        call.toolName.startsWith("memory") -> Triple(Icons.Default.AutoAwesome, "Memory Store", BrandPrimary)
        else -> Triple(Icons.Default.AutoAwesome, "System Action", BrandPrimary)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = call.toolName,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))

            // Status Pill
            if (requiresConfirm) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WarningAmber.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, WarningAmber.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Confirmation", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WarningAmber)
                    }
                }
            } else if (isSuccess) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SuccessGreen.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, SuccessGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Verified", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                    }
                }
            }
        }
    }
}

/**
 * Assistant Thinking Indicator when awaiting initial token.
 */
@Composable
fun AssistantThinkingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = BrandPrimary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Tavi is thinking locally...",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Modern floating pill container with quick action pills and dynamic action send button.
 */
@Composable
fun ModernInputDock(
    inputText: String,
    isStreaming: Boolean,
    isListening: Boolean,
    statusBanner: String?,
    onTextChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onQuickPromptClicked: (String) -> Unit,
    onVoiceToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Status banner if present
        AnimatedVisibility(visible = !statusBanner.isNullOrBlank()) {
            statusBanner?.let { banner ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandPrimary.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Text(
                        text = banner,
                        color = BrandPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Quick Suggestion Chips Row above Input Box
        val quickChips = listOf(
            "📅 Agenda" to "What's on my agenda today?",
            "💡 Flashlight" to "Turn on the flashlight",
            "🔋 Battery" to "Check device battery status",
            "🧠 Memories" to "What do you remember about me?"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickChips.forEach { (label, prompt) ->
                Surface(
                    onClick = { onQuickPromptClicked(prompt) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Floating Pill Input Container
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice Mic Button
                val voicePulse = rememberInfiniteTransition(label = "voicePulse")
                val micScale by voicePulse.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isListening) 1.22f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "micScale"
                )

                IconButton(
                    onClick = onVoiceToggle,
                    modifier = Modifier
                        .size(38.dp)
                        .scale(micScale)
                        .clip(CircleShape)
                        .background(
                            if (isListening) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.surface
                        )
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = if (isListening) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))

                // Text Input Field
                TextField(
                    value = inputText,
                    onValueChange = onTextChanged,
                    placeholder = {
                        Text(
                            "Ask Tavi or request an action...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = BrandPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() && !isStreaming) {
                                onSendClicked()
                            }
                        }
                    )
                )

                Spacer(Modifier.width(4.dp))

                // Dynamic Action / Send Button
                val canSend = inputText.isNotBlank() && !isStreaming
                IconButton(
                    onClick = onSendClicked,
                    enabled = canSend || isStreaming,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) BrandPrimaryVariant
                            else if (isStreaming) BrandPrimary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                ) {
                    if (isStreaming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = BrandPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Send",
                            tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkflowReviewCard(
    workflow: WorkflowPlan,
    onConfirm: (String) -> Unit,
    onCancelConfirm: (String) -> Unit,
    onCancelWorkflow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateColor = when (workflow.state) {
        WorkflowState.COMPLETED -> Color(0xFF2E7D32)
        WorkflowState.EXECUTING, WorkflowState.VERIFYING -> BrandPrimary
        WorkflowState.AWAITING_CONFIRMATION -> Color(0xFFE65100)
        WorkflowState.AWAITING_USER_INPUT -> BrandAccent
        WorkflowState.PARTIALLY_COMPLETED -> Color(0xFFF57C00)
        WorkflowState.FAILED -> MaterialTheme.colorScheme.error
        WorkflowState.CANCELLED -> Color.Gray
        WorkflowState.PLANNING -> BrandPrimary
    }

    val stateText = when (workflow.state) {
        WorkflowState.COMPLETED -> "COMPLETED & VERIFIED"
        WorkflowState.EXECUTING -> "EXECUTING STEP ${workflow.currentStepIndex + 1}/${workflow.steps.size}"
        WorkflowState.VERIFYING -> "VERIFYING RESULT"
        WorkflowState.AWAITING_CONFIRMATION -> "ACTION REQUIRES CONFIRMATION"
        WorkflowState.AWAITING_USER_INPUT -> "INPUT REQUIRED"
        WorkflowState.PARTIALLY_COMPLETED -> "PARTIALLY COMPLETED"
        WorkflowState.FAILED -> "WORKFLOW FAILED"
        WorkflowState.CANCELLED -> "CANCELLED"
        WorkflowState.PLANNING -> "PLANNING"
    }

    val planSourceText = when (workflow.planSource) {
        PlanSource.DETERMINISTIC -> "Deterministic Fast-Path"
        PlanSource.GEMMA_ON_DEVICE -> "Gemma 3 1B On-Device Plan"
        PlanSource.USER_ROUTINE -> "Configured Routine"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Title & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workflow.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = planSourceText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = stateColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = stateText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = stateColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Progress bar
            if (workflow.state == WorkflowState.EXECUTING || workflow.state == WorkflowState.VERIFYING) {
                val progress = if (workflow.steps.isNotEmpty()) {
                    (workflow.currentStepIndex.toFloat() / workflow.steps.size.toFloat()).coerceIn(0.1f, 0.95f)
                } else 0.5f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = BrandPrimary,
                    trackColor = BrandPrimary.copy(alpha = 0.2f)
                )
            }

            // Steps list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                workflow.steps.forEachIndexed { idx, step ->
                    val isPendingStep = workflow.state == WorkflowState.AWAITING_CONFIRMATION && step.id == workflow.pendingConfirmationStepId
                    val stepBg = if (isPendingStep) {
                        Color(0xFFE65100).copy(alpha = 0.10f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = stepBg,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (step.isReadOnly) Icons.Default.Visibility else Icons.Default.Edit,
                                contentDescription = if (step.isReadOnly) "Read-Only" else "Modifies Data",
                                tint = if (step.isReadOnly) BrandPrimary else Color(0xFFE65100),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${idx + 1}. ${step.title}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = if (step.isReadOnly) "Read-Only" else "Action",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (step.requiredPermission != null) {
                                    Text(
                                        text = "Permission: ${step.requiredPermission}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                if (step.verificationDetail != null) {
                                    Text(
                                        text = step.verificationDetail,
                                        fontSize = 11.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                } else if (step.errorMessage != null) {
                                    Text(
                                        text = step.errorMessage,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // Status badge / icon
                            when (step.status) {
                                StepExecutionStatus.VERIFIED -> Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp)
                                )
                                StepExecutionStatus.COMPLETED_UNVERIFIED -> Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Completed",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp)
                                )
                                StepExecutionStatus.EXECUTING, StepExecutionStatus.VERIFYING -> CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandPrimary
                                )
                                StepExecutionStatus.AWAITING_CONFIRMATION -> Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Confirmation Needed",
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(18.dp)
                                )
                                StepExecutionStatus.FAILED -> Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Failed",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                StepExecutionStatus.SKIPPED, StepExecutionStatus.CANCELLED -> Text(
                                    "Skipped",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                StepExecutionStatus.PENDING -> Text(
                                    "Pending",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            // Awaiting Confirmation UI
            if (workflow.state == WorkflowState.AWAITING_CONFIRMATION && workflow.pendingConfirmationStepId != null) {
                val step = workflow.steps.find { it.id == workflow.pendingConfirmationStepId }
                Surface(
                    color = Color(0xFFE65100).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Consequential Action Authorization",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFE65100)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Approval is strictly scoped to this action:\nAction: ${step?.title ?: "Action"}\nParameters: ${step?.arguments}",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { onCancelConfirm(workflow.pendingConfirmationStepId) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Cancel", fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { onConfirm(workflow.pendingConfirmationStepId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Text("Confirm & Execute", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Awaiting User Input prompt
            if (workflow.state == WorkflowState.AWAITING_USER_INPUT && workflow.missingInputPrompt != null) {
                Surface(
                    color = BrandAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Additional Detail Needed",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = BrandAccent
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = workflow.missingInputPrompt,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Dismiss / Cancel Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (workflow.state == WorkflowState.COMPLETED || workflow.state == WorkflowState.FAILED || workflow.state == WorkflowState.CANCELLED || workflow.state == WorkflowState.PARTIALLY_COMPLETED) {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss", fontSize = 12.sp)
                    }
                } else if (workflow.state == WorkflowState.EXECUTING || workflow.state == WorkflowState.VERIFYING) {
                    TextButton(onClick = onCancelWorkflow) {
                        Text("Cancel Workflow", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
