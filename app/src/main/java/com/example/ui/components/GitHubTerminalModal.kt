package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.GitHubRepository
import kotlinx.coroutines.launch

// Terminal line types for colored CLI rendering
enum class TerminalLineType {
    PROMPT_COMMAND,
    OUTPUT_TEXT,
    OUTPUT_SUCCESS,
    OUTPUT_ERROR,
    OUTPUT_WARNING,
    OUTPUT_INFO,
    OUTPUT_DIFF_ADD,
    OUTPUT_DIFF_DEL,
    OUTPUT_DIFF_HEADER,
    OUTPUT_DIVIDER
}

data class TerminalLine(
    val id: Long = System.nanoTime(),
    val type: TerminalLineType,
    val text: String,
    val workingDir: String = "",
    val branch: String = ""
)

// Terminal Light Modern Beautiful color scheme (Clean modern CLI aesthetic)
private val TermBg = Color(0xFFF8FAFC)
private val TermSurface = Color(0xFFFFFFFF)
private val TermSurface2 = Color(0xFFF1F5F9)
private val TermSurfaceBorder = Color(0xFFE2E8F0)
private val TermPromptUser = Color(0xFF0F9D74)
private val TermPromptBranch = Color(0xFF7C3AED)
private val TermPromptPath = Color(0xFF0284C7)
private val TermText = Color(0xFF0F172A)
private val TermSuccess = Color(0xFF16A34A)
private val TermError = Color(0xFFDC2626)
private val TermWarning = Color(0xFFD97706)
private val TermInfo = Color(0xFF2563EB)
private val TermDim = Color(0xFF64748B)
private val TermDiffAdd = Color(0xFF15803D)
private val TermDiffDel = Color(0xFFB91C1C)
private val TermDiffHeader = Color(0xFF0284C7)

@Composable
fun GitHubTerminalModal(
    repo: GitHubRepository?,
    selectedBranch: String,
    currentPath: String,
    terminalLines: List<TerminalLine>,
    isExecuting: Boolean,
    onExecuteCommand: (String) -> Unit,
    onClearTerminal: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputCommand by remember { mutableStateOf("") }
    val commandHistory = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Auto scroll to bottom when new terminal output arrives
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    val quickCommands = remember {
        listOf(
            "git status",
            "git pull",
            "git branch",
            "git log --oneline -n 5",
            "git add .",
            "git commit -m \"Update from GitExplorer\"",
            "git push",
            "git diff",
            "ls -la",
            "pwd",
            "git remote -v",
            "help"
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(if (isFullscreen) 0.dp else 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (isFullscreen) 1f else 0.94f)
                    .testTag("github_terminal_card"),
                shape = RoundedCornerShape(if (isFullscreen) 0.dp else 16.dp),
                colors = CardDefaults.cardColors(containerColor = TermBg),
                border = BorderStroke(1.dp, TermSurfaceBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = TermSurface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Terminal Title & Context
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = TermPromptUser.copy(alpha = 0.15f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Terminal,
                                            contentDescription = "Terminal",
                                            tint = TermPromptUser,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = repo?.name ?: "GitHub Terminal",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TermText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = TermPromptBranch.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = selectedBranch,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TermPromptBranch,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (currentPath.isEmpty()) "~/" else "~/$currentPath",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = TermDim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Header Actions
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Paste & Run from clipboard
                                IconButton(
                                    onClick = {
                                        val clipText = clipboardManager.getText()?.text?.trim()
                                        if (!clipText.isNullOrEmpty()) {
                                            inputCommand = clipText
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste Clipboard",
                                        tint = TermDim,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                // Copy Terminal Logs
                                IconButton(
                                    onClick = {
                                        val allLogs = terminalLines.joinToString("\n") { it.text }
                                        clipboardManager.setText(AnnotatedString(allLogs))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Logs",
                                        tint = TermDim,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                // Clear Screen
                                IconButton(
                                    onClick = onClearTerminal,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Clear Terminal",
                                        tint = TermDim,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Toggle Fullscreen
                                IconButton(
                                    onClick = { isFullscreen = !isFullscreen },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "Fullscreen",
                                        tint = TermDim,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Close Modal
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = TermDim,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = TermSurfaceBorder)

                    // Quick Command Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TermBg)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "QUICK:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TermDim
                        )

                        quickCommands.forEach { cmd ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = TermSurface,
                                border = BorderStroke(1.dp, TermSurfaceBorder),
                                modifier = Modifier.clickable {
                                    inputCommand = cmd
                                }
                            ) {
                                Text(
                                    text = cmd,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TermPromptUser,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = TermSurfaceBorder)

                    // Terminal Output Window
                    SelectionContainer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(TermBg)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(terminalLines, key = { it.id }) { line ->
                                TerminalLineItem(line = line)
                            }

                            if (isExecuting) {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = TermPromptUser,
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Running git operation...",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = TermDim
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = TermSurfaceBorder)

                    // Multi-Line Notice if Input has multiple lines
                    val lineCount = remember(inputCommand) {
                        inputCommand.lines().count { it.isNotBlank() }
                    }

                    if (lineCount > 1) {
                        Surface(
                            color = TermSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = TermSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Multi-Line Script: $lineCount commands ready to run sequentially",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = TermSuccess,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                TextButton(
                                    onClick = { inputCommand = "" },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Clear All", fontSize = 11.sp, color = TermError)
                                }
                            }
                        }
                        HorizontalDivider(color = TermSurfaceBorder)
                    }

                    // Command Input Console Row
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = TermSurface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Prompt label
                            Text(
                                text = "$",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TermPromptUser,
                                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp, end = 6.dp)
                            )

                            // Command Text Field (supports multi-line paste & manual multi-line typing)
                            OutlinedTextField(
                                value = inputCommand,
                                onValueChange = { inputCommand = it },
                                placeholder = {
                                    Text(
                                        text = "Enter or paste git command(s)...",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = TermDim
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = TermText,
                                    fontSize = 13.sp
                                ),
                                maxLines = if (isFullscreen) 6 else 4,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = if (lineCount <= 1) ImeAction.Send else ImeAction.Default,
                                    keyboardType = KeyboardType.Ascii
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (inputCommand.isNotBlank() && !isExecuting) {
                                            val cmd = inputCommand.trim()
                                            commandHistory.add(cmd)
                                            historyIndex = -1
                                            onExecuteCommand(cmd)
                                            inputCommand = ""
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = TermBg,
                                    unfocusedContainerColor = TermBg,
                                    cursorColor = TermPromptUser,
                                    focusedBorderColor = TermPromptUser,
                                    unfocusedBorderColor = TermSurfaceBorder
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("terminal_command_input")
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // History Navigation & Execute Actions
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (commandHistory.isNotEmpty()) {
                                    Row {
                                        // History UP
                                        IconButton(
                                            onClick = {
                                                if (commandHistory.isNotEmpty()) {
                                                    if (historyIndex == -1) historyIndex = commandHistory.size - 1
                                                    else if (historyIndex > 0) historyIndex--
                                                    inputCommand = commandHistory[historyIndex]
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Prev Command",
                                                tint = TermDim,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        // History DOWN
                                        IconButton(
                                            onClick = {
                                                if (commandHistory.isNotEmpty() && historyIndex != -1) {
                                                    if (historyIndex < commandHistory.size - 1) {
                                                        historyIndex++
                                                        inputCommand = commandHistory[historyIndex]
                                                    } else {
                                                        historyIndex = -1
                                                        inputCommand = ""
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Next Command",
                                                tint = TermDim,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                // Execute Button
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (inputCommand.isNotBlank() && !isExecuting) TermPromptUser else TermSurfaceBorder,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clickable(
                                            enabled = inputCommand.isNotBlank() && !isExecuting,
                                            onClick = {
                                                val cmd = inputCommand.trim()
                                                if (cmd.isNotEmpty()) {
                                                    commandHistory.add(cmd)
                                                    historyIndex = -1
                                                    onExecuteCommand(cmd)
                                                    inputCommand = ""
                                                }
                                            }
                                        )
                                        .testTag("terminal_execute_btn")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isExecuting) {
                                            CircularProgressIndicator(
                                                color = TermText,
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (lineCount > 1) Icons.Default.PlayArrow else Icons.Default.Send,
                                                contentDescription = "Execute",
                                                tint = if (inputCommand.isNotBlank()) Color.White else TermDim,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalLineItem(line: TerminalLine) {
    when (line.type) {
        TerminalLineType.PROMPT_COMMAND -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                val promptText = buildAnnotatedString {
                    withStyle(SpanStyle(color = TermPromptUser, fontWeight = FontWeight.Bold)) {
                        append("user@github")
                    }
                    withStyle(SpanStyle(color = TermDim)) {
                        append(":")
                    }
                    withStyle(SpanStyle(color = TermPromptPath, fontWeight = FontWeight.Medium)) {
                        append(if (line.workingDir.isEmpty()) "~" else "~/${line.workingDir}")
                    }
                    withStyle(SpanStyle(color = TermDim)) {
                        append(" (")
                    }
                    withStyle(SpanStyle(color = TermPromptBranch, fontWeight = FontWeight.Bold)) {
                        append(line.branch.ifEmpty { "main" })
                    }
                    withStyle(SpanStyle(color = TermDim)) {
                        append(")$ ")
                    }
                    withStyle(SpanStyle(color = TermText, fontWeight = FontWeight.Bold)) {
                        append(line.text)
                    }
                }

                Text(
                    text = promptText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        TerminalLineType.OUTPUT_SUCCESS -> {
            Text(
                text = line.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TermSuccess,
                lineHeight = 16.sp
            )
        }

        TerminalLineType.OUTPUT_ERROR -> {
            Text(
                text = line.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TermError,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        TerminalLineType.OUTPUT_WARNING -> {
            Text(
                text = line.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TermWarning,
                lineHeight = 16.sp
            )
        }

        TerminalLineType.OUTPUT_INFO -> {
            Text(
                text = line.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TermInfo,
                lineHeight = 16.sp
            )
        }

        TerminalLineType.OUTPUT_DIFF_ADD -> {
            Text(
                text = line.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TermDiffAdd,
                lineHeight = 16.sp
            )
        }

        TerminalLineType.OUTPUT_DIFF_DEL -> {
            Text(
                text = line.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TermDiffDel,
                lineHeight = 16.sp
            )
        }

        TerminalLineType.OUTPUT_DIFF_HEADER -> {
            Text(
                text = line.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TermDiffHeader,
                fontWeight = FontWeight.Bold,
                lineHeight = 16.sp
            )
        }

        TerminalLineType.OUTPUT_DIVIDER -> {
            HorizontalDivider(
                color = TermSurfaceBorder,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        TerminalLineType.OUTPUT_TEXT -> {
            Text(
                text = line.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TermText,
                lineHeight = 16.sp
            )
        }
    }
}
