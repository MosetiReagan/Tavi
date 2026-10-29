package com.example.tavi.ui.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tavi.model.Message
import com.example.tavi.model.MessageRole
import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolResult
import com.example.tavi.theme.BrandAccent
import com.example.tavi.theme.BrandPrimary
import com.example.tavi.theme.BrandSecondary
import com.example.tavi.theme.BrandViolet
import com.example.tavi.theme.SuccessGreen
import com.example.tavi.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val haptic = LocalHapticFeedback.current
    var copiedRecently by remember { mutableStateOf(false) }
    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

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
            modifier = Modifier
                .animateContentSize()
                .then(
                    if (isUser) {
                        Modifier.widthIn(min = 40.dp, max = 310.dp)
                    } else {
                        Modifier.fillMaxWidth(0.96f)
                    }
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (message.content.isNotEmpty()) {
                    if (isUser) {
                        FormattedMessageText(
                            rawText = message.content,
                            isUser = true
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = timeStr,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.End)
                        )
                    } else {
                        SelectionContainer {
                            FormattedMessageText(
                                rawText = message.content,
                                isUser = false
                            )
                        }
                    }
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeStr,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Copy Button (48dp touch target)
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(message.content))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    copiedRecently = true
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (copiedRecently) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy message",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (copiedRecently) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            // Speak Aloud Button (48dp touch target)
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSpeak()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Read aloud",
                                    modifier = Modifier.size(19.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Enhanced text renderer supporting code blocks, inline code, bold, italics, headers, and bullet points.
 */
@Composable
fun FormattedMessageText(
    rawText: String,
    isUser: Boolean
) {
    val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
    val codeBg = if (isUser) Color.Black.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface

    Column {
        val lines = rawText.split("\n")
        var inCodeBlock = false
        val codeBlockLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // Close code block
                    val fullCode = codeBlockLines.joinToString("\n")
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = codeBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = fullCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                codeBlockLines.add(line)
                continue
            }

            // Normal markdown line rendering
            val annotated = buildAnnotatedString {
                val lineTrim = line.trimStart()
                if (lineTrim.startsWith("### ")) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp))
                    append(lineTrim.removePrefix("### ").trim())
                    pop()
                } else if (lineTrim.startsWith("## ")) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp))
                    append(lineTrim.removePrefix("## ").trim())
                    pop()
                } else if (lineTrim.startsWith("# ")) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp))
                    append(lineTrim.removePrefix("# ").trim())
                    pop()
                } else if (lineTrim.startsWith("• ") || lineTrim.startsWith("- ") || lineTrim.startsWith("* ")) {
                    append("  •  ")
                    val content = lineTrim.removePrefix("• ").removePrefix("- ").removePrefix("* ").trim()
                    appendMarkdownFormatted(content, textColor)
                } else {
                    appendMarkdownFormatted(line, textColor)
                }
            }

            Text(
                text = annotated,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = textColor
            )
        }

        if (inCodeBlock && codeBlockLines.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = codeBg,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    text = codeBlockLines.joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

private fun AnnotatedString.Builder.appendMarkdownFormatted(text: String, defaultColor: Color) {
    var cursor = 0
    val regex = Regex("(\\*\\*([^*]+)\\*\\*)|(\\*([^*]+)\\*)|(`([^`]+)`)")
    val matches = regex.findAll(text)

    for (match in matches) {
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        when {
            match.groupValues[2].isNotEmpty() -> { // Bold
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(match.groupValues[2])
                pop()
            }
            match.groupValues[4].isNotEmpty() -> { // Italic
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(match.groupValues[4])
                pop()
            }
            match.groupValues[6].isNotEmpty() -> { // Inline code
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Black.copy(alpha = 0.1f)))
                append(" ${match.groupValues[6]} ")
                pop()
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
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
    var isExpanded by remember { mutableStateOf(false) }

    val (icon, label, accentColor) = when {
        call.toolName.startsWith("calendar") -> Triple(Icons.Default.CalendarMonth, "Calendar", BrandSecondary)
        call.toolName.startsWith("messages") -> Triple(Icons.Default.Sms, "Messages", BrandAccent)
        call.toolName.startsWith("contacts") -> Triple(Icons.Default.Contacts, "Contacts", BrandViolet)
        call.toolName.startsWith("reminders") -> Triple(Icons.Default.NotificationsActive, "Reminder", WarningAmber)
        call.toolName.startsWith("device_battery") -> Triple(Icons.Default.BatteryChargingFull, "Battery", SuccessGreen)
        call.toolName.startsWith("device_toggle") -> Triple(Icons.Default.FlashlightOn, "Flashlight", WarningAmber)
        call.toolName.startsWith("memory") -> Triple(Icons.Default.AutoAwesome, "Memory Store", BrandPrimary)
        else -> Triple(Icons.Default.AutoAwesome, "System Action", BrandPrimary)
    }

    Surface(
        onClick = { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(
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

                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Expandable details (Parameters and Execution Result JSON)
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(6.dp))
                    if (call.arguments.isNotEmpty()) {
                        Text(
                            text = "Tool Parameters:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = call.arguments.entries.joinToString("\n") { (k, v) -> "$k: $v" },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                    if (result != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Execution Result:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (result.isSuccess) result.resultJson else (result.errorMessage ?: "Failed"),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = if (result.isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
