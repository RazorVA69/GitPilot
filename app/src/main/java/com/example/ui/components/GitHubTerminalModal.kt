package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GitConflict
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

// Terminal Light Modern developer-tool aesthetic
private val TermBg = Color(0xFFF8FAFC)
private val TermSurface = Color(0xFFFFFFFF)
private val TermSurface2 = Color(0xFFF1F5F9)
private val TermSurfaceBorder = Color(0xFFE2E8F0)
private val TermPromptUser = Color(0xFF0F9D74)
private val TermPromptBranch = Color(0xFF7C3AED)
private val TermPromptPath = Color(0xFF0284C7)
private val TermText = Color(0xFF14141B)
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
    conflictedFiles: List<GitConflict> = emptyList(),
    isMergeConflict: Boolean = false,
    isRebaseConflict: Boolean = false,
    pendingCommands: List<String> = emptyList(),
    onResolveConflict: (String, String) -> Unit = { _, _ -> },
    onResolveAllConflicts: (String) -> Unit = {},
    onCommitResolvedConflicts: () -> Unit = {},
    onOpenInEditor: (String) -> Unit = {},
    onResumePendingQueue: () -> Unit = {},
    onAbortConflict: () -> Unit = {},
    onExecuteCommand: (String) -> Unit,
    onClearTerminal: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputCommandValue by remember { mutableStateOf(TextFieldValue("")) }
    val inputCommand = inputCommandValue.text
    var showScriptPreviewModal by remember { mutableStateOf(false) }
    val commandHistory = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var isFullscreen by remember { mutableStateOf(false) }

    fun setInputText(text: String, keepTop: Boolean = false) {
        val selection = if (keepTop || text.contains('\n')) TextRange(0) else TextRange(text.length)
        inputCommandValue = TextFieldValue(text = text, selection = selection)
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto scroll to bottom when new terminal output arrives
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    // Auto-focus terminal input on open
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: Exception) {}
    }

    val quickCommands = remember {
        listOf(
            "git remote add upstream https://github.com/MorpheApp/morphe-manager.git",
            "git fetch upstream",
            "git checkout dev",
            "git merge upstream/dev",
            "git rebase upstream/dev",
            "git rebase --continue",
            "git push origin dev --force",
            "git checkout --ours",
            "git checkout --theirs",
            "git status",
            "git add .",
            "git commit",
            "git pull",
            "git push",
            "git log --oneline -n 5",
            "git diff",
            "git grep",
            "git branch -a",
            "gh pr list",
            "gh repo view",
            "ls -la",
            "pwd",
            "help"
        )
    }

    val mobileShortcuts = remember {
        listOf(
            "upstream", "rebase", "--continue", "--abort", "--force", "remote", "fetch",
            "checkout", "--ours", "--theirs", "git", "status", "add .", "commit",
            "pull", "push", "branch", "log", "diff", "origin", "dev", "main",
            "gh", "pr", "issue", "merge", "run", "repo", "auth", "|", "&&",
            "clear", "help"
        )
    }

    fun submitCurrentCommand() {
        val cmd = inputCommandValue.text.trim()
        if (cmd.isNotBlank() && !isExecuting) {
            if (commandHistory.isEmpty() || commandHistory.last() != cmd) {
                commandHistory.add(cmd)
            }
            historyIndex = -1
            onExecuteCommand(cmd)
            inputCommandValue = TextFieldValue("")
            showScriptPreviewModal = false
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(if (isFullscreen) 0.dp else 8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Prevent dismiss on inside click
                }
                .testTag("github_terminal_card"),
            shape = RoundedCornerShape(if (isFullscreen) 0.dp else 12.dp),
            colors = CardDefaults.cardColors(containerColor = TermBg),
            border = BorderStroke(1.dp, TermSurfaceBorder)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Header Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = TermSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Terminal Title & Context
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                modifier = Modifier.size(30.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = TermPromptUser.copy(alpha = 0.12f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Terminal,
                                        contentDescription = "Terminal",
                                        tint = TermPromptUser,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = repo?.fullName ?: (repo?.name ?: "GitHub Terminal"),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TermText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = TermPromptBranch.copy(alpha = 0.12f)
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
                                    text = if (currentPath.isEmpty()) "workspace: ~/" else "workspace: ~/$currentPath",
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
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Paste button in header
                            IconButton(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text?.trim()
                                    if (!clipText.isNullOrEmpty()) {
                                        val combined = if (inputCommand.isEmpty()) clipText else "$inputCommand\n$clipText"
                                        setInputText(combined, keepTop = true)
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
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
                                    modifier = Modifier.size(17.dp)
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
                                    modifier = Modifier.size(17.dp)
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
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = TermSurfaceBorder)

                // 2. Quick Command Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TermSurface2)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 5.dp),
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
                            shape = RoundedCornerShape(4.dp),
                            color = TermSurface,
                            border = BorderStroke(1.dp, TermSurfaceBorder),
                            modifier = Modifier.clickable {
                                setInputText(cmd, keepTop = false)
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        ) {
                            Text(
                                text = cmd,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TermPromptUser,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = TermSurfaceBorder)

                // 3. Terminal Output Window (Wrapped in Box with weight(1f) to ensure clean layout)
                Box(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                        .background(TermBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                ) {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(terminalLines, key = { it.id }) { line ->
                                TerminalLineItem(line = line, username = repo?.owner?.login ?: "user")
                            }

                            if (isExecuting) {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = TermPromptUser,
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Running git operation...",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = TermDim
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = TermSurfaceBorder)

                // 4. Mobile Quick Token Shortcuts
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = TermSurface2
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Quick Paste Button in Shortcut Row
                        Surface(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrEmpty()) {
                                    val combined = if (inputCommand.isEmpty()) clip else "$inputCommand\n$clip"
                                    setInputText(combined, keepTop = true)
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            color = TermPromptUser.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, TermPromptUser.copy(alpha = 0.3f)),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = TermPromptUser,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Paste",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TermPromptUser
                                )
                            }
                        }

                        // Quick token chips
                        mobileShortcuts.forEach { token ->
                            Surface(
                                onClick = {
                                    val newText = if (inputCommand.isEmpty()) {
                                        token
                                    } else if (inputCommand.endsWith(" ") || token.startsWith("-") || token.startsWith("/") || token.startsWith("|")) {
                                        "$inputCommand$token"
                                    } else {
                                        "$inputCommand $token"
                                    }
                                    setInputText(newText, keepTop = false)
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                },
                                shape = RoundedCornerShape(4.dp),
                                color = TermSurface,
                                border = BorderStroke(1.dp, TermSurfaceBorder),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 7.dp)
                                ) {
                                    Text(
                                        text = token,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = if (token == "git" || token == "push" || token == "pull") FontWeight.Bold else FontWeight.Normal,
                                        color = if (token == "git") TermPromptUser else TermText
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = TermSurfaceBorder)

                // 4.5 Interactive Conflict Resolution Section
                if (isMergeConflict || isRebaseConflict || conflictedFiles.isNotEmpty()) {
                    TerminalConflictResolutionCard(
                        conflicts = conflictedFiles,
                        isMergeConflict = isMergeConflict,
                        isRebaseConflict = isRebaseConflict,
                        selectedBranch = selectedBranch,
                        pendingCommandCount = pendingCommands.size,
                        onResolveConflict = onResolveConflict,
                        onResolveAllConflicts = onResolveAllConflicts,
                        onCommitResolvedConflicts = onCommitResolvedConflicts,
                        onOpenInEditor = onOpenInEditor,
                        onResumePending = onResumePendingQueue,
                        onAbort = onAbortConflict
                    )
                    HorizontalDivider(color = TermSurfaceBorder)
                } else if (pendingCommands.isNotEmpty()) {
                    TerminalPendingQueueCard(
                        pendingCount = pendingCommands.size,
                        onResume = onResumePendingQueue
                    )
                    HorizontalDivider(color = TermSurfaceBorder)
                }

                // 5. Multi-Line Script Notice if input has multiple lines
                val nonBlankLines = remember(inputCommand) {
                    inputCommand.lines().map { it.trim() }.filter { it.isNotEmpty() }
                }
                val lineCount = nonBlankLines.size

                if (lineCount > 1) {
                    Surface(
                        color = TermSurface,
                        border = BorderStroke(1.dp, TermPromptUser.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = TermPromptUser,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Script: $lineCount commands ready",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = TermPromptUser,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Jump to Top cursor button
                                    Surface(
                                        onClick = {
                                            inputCommandValue = inputCommandValue.copy(selection = TextRange(0))
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        color = TermSurface2,
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Top", modifier = Modifier.size(14.dp), tint = TermText)
                                            Text("Top", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TermText)
                                        }
                                    }

                                    // Jump to End cursor button
                                    Surface(
                                        onClick = {
                                            inputCommandValue = inputCommandValue.copy(selection = TextRange(inputCommand.length))
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        color = TermSurface2,
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "End", modifier = Modifier.size(14.dp), tint = TermText)
                                            Text("End", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TermText)
                                        }
                                    }

                                    // Preview Steps toggle
                                    Surface(
                                        onClick = { showScriptPreviewModal = !showScriptPreviewModal },
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (showScriptPreviewModal) TermPromptUser.copy(alpha = 0.15f) else TermSurface2,
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (showScriptPreviewModal) "Hide Steps" else "Preview Steps",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = if (showScriptPreviewModal) TermPromptUser else TermText
                                            )
                                        }
                                    }

                                    // Clear button
                                    TextButton(
                                        onClick = { inputCommandValue = TextFieldValue("") },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("Clear", fontSize = 11.sp, color = TermError)
                                    }
                                }
                            }

                            // If preview is expanded, show the numbered list of commands
                            if (showScriptPreviewModal) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = TermSurface2,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = "Commands will execute 1-by-1 sequentially (auto-pausing if a conflict occurs):",
                                            fontSize = 10.sp,
                                            color = TermDim,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        nonBlankLines.forEachIndexed { idx, line ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 1.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${(idx + 1).toString().padStart(2, '0')}: ",
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = TermDim,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = line,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (line.startsWith("#")) TermDim else TermText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = TermSurfaceBorder)
                }

                // 6. Command Input Console Bar (Always pinned right above the keyboard, clear high-contrast styling)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = TermSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Emerald Prompt Symbol
                            Text(
                                text = "$",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TermPromptUser,
                                modifier = Modifier.padding(start = 2.dp, end = 6.dp)
                            )

                            // Command Text Field with clear high contrast text
                            OutlinedTextField(
                                value = inputCommandValue,
                                onValueChange = { newTfv ->
                                    val oldText = inputCommandValue.text
                                    val newText = newTfv.text
                                    // Gboard Paste or clipboard paste detection:
                                    // When user taps Gboard paste suggestion button, Gboard inserts the text
                                    // and places the selection at the very end of the text.
                                    // If the text contains multiple lines, this normally causes the text field to
                                    // scroll to the bottom lines (disorienting the user).
                                    // By setting selection to TextRange(0) upon pasting multi-line text,
                                    // the field stays pinned at line 1 (the top)!
                                    if (newText.length > oldText.length + 1 && newText.contains('\n')) {
                                        inputCommandValue = newTfv.copy(selection = TextRange(0))
                                    } else {
                                        inputCommandValue = newTfv
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = "Type or paste command (e.g. git status)...",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = TermDim
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = TermText,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (inputCommand.isNotEmpty()) {
                                            IconButton(
                                                onClick = { inputCommandValue = TextFieldValue("") },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear input",
                                                    tint = TermDim,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                maxLines = if (isFullscreen) 6 else 4,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = if (inputCommand.contains('\n')) ImeAction.Default else ImeAction.Send,
                                    keyboardType = KeyboardType.Text,
                                    autoCorrect = false
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        submitCurrentCommand()
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = TermSurface,
                                    unfocusedContainerColor = TermBg,
                                    focusedTextColor = TermText,
                                    unfocusedTextColor = TermText,
                                    focusedPlaceholderColor = TermDim,
                                    unfocusedPlaceholderColor = TermDim,
                                    cursorColor = TermPromptUser,
                                    focusedBorderColor = TermPromptUser,
                                    unfocusedBorderColor = TermSurfaceBorder
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .testTag("terminal_command_input")
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Direct Paste Button next to input
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TermSurface2,
                                border = BorderStroke(1.dp, TermSurfaceBorder),
                                modifier = Modifier
                                    .size(width = 40.dp, height = 40.dp)
                                    .clickable {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrEmpty()) {
                                            val combined = if (inputCommand.isEmpty()) clip else "$inputCommand\n$clip"
                                            setInputText(combined, keepTop = true)
                                            focusRequester.requestFocus()
                                            keyboardController?.show()
                                        }
                                    }
                                    .testTag("terminal_paste_btn")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste Clipboard",
                                        tint = TermPromptUser,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // History Navigation Buttons (if history exists)
                            if (commandHistory.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = TermSurface2,
                                    border = BorderStroke(1.dp, TermSurfaceBorder),
                                    modifier = Modifier
                                        .size(width = 32.dp, height = 40.dp)
                                        .clickable {
                                            if (historyIndex == -1) {
                                                historyIndex = commandHistory.size - 1
                                            } else if (historyIndex > 0) {
                                                historyIndex--
                                            }
                                            if (historyIndex in commandHistory.indices) {
                                                val histCmd = commandHistory[historyIndex]
                                                inputCommandValue = TextFieldValue(histCmd, selection = TextRange(histCmd.length))
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Previous Command",
                                            tint = TermDim,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            // Run / Execute Button
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (inputCommand.isNotBlank() && !isExecuting) TermText else TermSurface2,
                                border = BorderStroke(1.dp, if (inputCommand.isNotBlank() && !isExecuting) TermText else TermSurfaceBorder),
                                modifier = Modifier
                                    .height(40.dp)
                                    .clickable(
                                        enabled = inputCommand.isNotBlank() && !isExecuting,
                                        onClick = {
                                            submitCurrentCommand()
                                        }
                                    )
                                    .testTag("terminal_execute_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    if (isExecuting) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (lineCount > 1) Icons.Default.PlayArrow else Icons.Default.Send,
                                            contentDescription = "Execute",
                                            tint = if (inputCommand.isNotBlank()) Color.White else TermDim,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Run",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (inputCommand.isNotBlank()) Color.White else TermDim
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

@Composable
private fun TerminalLineItem(line: TerminalLine, username: String = "user") {
    when (line.type) {
        TerminalLineType.PROMPT_COMMAND -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                val promptText = buildAnnotatedString {
                    withStyle(SpanStyle(color = TermPromptUser, fontWeight = FontWeight.Bold)) {
                        append("$username@github")
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

@Composable
fun TerminalConflictResolutionCard(
    conflicts: List<GitConflict>,
    isMergeConflict: Boolean,
    isRebaseConflict: Boolean,
    selectedBranch: String = "main",
    pendingCommandCount: Int,
    onResolveConflict: (String, String) -> Unit,
    onResolveAllConflicts: (String) -> Unit = {},
    onCommitResolvedConflicts: () -> Unit = {},
    onOpenInEditor: (String) -> Unit,
    onResumePending: () -> Unit,
    onAbort: () -> Unit
) {
    val allResolved = conflicts.isNotEmpty() && conflicts.all { it.isResolved }
    val resolvedCount = conflicts.count { it.isResolved }

    Surface(
        color = TermSurface,
        border = BorderStroke(1.dp, if (allResolved) TermSuccess.copy(alpha = 0.4f) else TermWarning.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("terminal_conflict_resolution_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (allResolved) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (allResolved) TermSuccess else TermWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (allResolved) {
                            "All ${conflicts.size} Conflict(s) Resolved"
                        } else if (conflicts.size > 1) {
                            "${conflicts.size} Conflicts Detected ($resolvedCount/${conflicts.size} resolved)"
                        } else if (isRebaseConflict) {
                            "Rebase Conflict Detected"
                        } else {
                            "Merge Conflict Detected"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (allResolved) TermSuccess else TermText
                    )
                    if (pendingCommandCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = TermPromptUser.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "$pendingCommandCount queued",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TermPromptUser,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Abort action
                Surface(
                    onClick = onAbort,
                    shape = RoundedCornerShape(4.dp),
                    color = TermSurface2,
                    border = BorderStroke(1.dp, TermSurfaceBorder),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(
                        text = if (isRebaseConflict) "Abort Rebase" else "Abort Merge",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TermError,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (!allResolved) {
                Text(
                    text = "Git halted sequential execution because conflicting changes were found. Choose a resolution version below:",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = TermDim
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Batch quick resolution buttons if multiple conflicts
                if (conflicts.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = { onResolveAllConflicts("ours") },
                            shape = RoundedCornerShape(4.dp),
                            color = TermSurface2,
                            border = BorderStroke(1.dp, TermSurfaceBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .testTag("resolve_all_ours_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "Accept All Ours",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TermDiffAdd
                                )
                            }
                        }
                        Surface(
                            onClick = { onResolveAllConflicts("theirs") },
                            shape = RoundedCornerShape(4.dp),
                            color = TermSurface2,
                            border = BorderStroke(1.dp, TermSurfaceBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .testTag("resolve_all_theirs_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "Accept All Theirs",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TermInfo
                                )
                            }
                        }
                        Surface(
                            onClick = { onResolveAllConflicts("both") },
                            shape = RoundedCornerShape(4.dp),
                            color = TermSurface2,
                            border = BorderStroke(1.dp, TermSurfaceBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .testTag("resolve_all_both_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "Accept All Both",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TermText
                                )
                            }
                        }
                    }
                }
            }

            // Conflicted files list
            conflicts.forEachIndexed { index, conflict ->
                Surface(
                    color = TermBg,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (conflict.isResolved) TermSuccess.copy(alpha = 0.3f) else TermSurfaceBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (conflict.isResolved) Icons.Default.Check else Icons.Default.CallSplit,
                                    contentDescription = null,
                                    tint = if (conflict.isResolved) TermSuccess else TermPromptPath,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (conflicts.size > 1) {
                                    Text(
                                        text = "[${index + 1}/${conflicts.size}] ",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TermPromptUser
                                    )
                                }
                                Text(
                                    text = conflict.filePath,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TermText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (conflict.conflictMarkerCount > 1) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "(${conflict.conflictMarkerCount} markers)",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = TermDim
                                    )
                                }
                            }

                            if (conflict.isResolved) {
                                Surface(
                                    color = TermSuccess.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "✓ Resolved (${conflict.resolutionType ?: "ours"})",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TermSuccess,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (!conflict.isResolved) {
                            Spacer(modifier = Modifier.height(6.dp))

                            // Snippet Preview
                            if (conflict.oursSnippet.isNotEmpty() || conflict.theirsSnippet.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (conflict.oursSnippet.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(TermDiffAdd.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = "HEAD / dev (Ours)",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = TermDiffAdd
                                            )
                                            Text(
                                                text = conflict.oursSnippet,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = TermText,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (conflict.theirsSnippet.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(TermInfo.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = "upstream/dev (Theirs)",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = TermInfo
                                            )
                                            Text(
                                                text = conflict.theirsSnippet,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = TermText,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // Quick Resolution Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Surface(
                                    onClick = { onResolveConflict(conflict.filePath, "ours") },
                                    shape = RoundedCornerShape(4.dp),
                                    color = TermSurface,
                                    border = BorderStroke(1.dp, TermSurfaceBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .testTag("resolve_ours_${conflict.filePath}")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Accept Ours",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = TermDiffAdd
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { onResolveConflict(conflict.filePath, "theirs") },
                                    shape = RoundedCornerShape(4.dp),
                                    color = TermSurface,
                                    border = BorderStroke(1.dp, TermSurfaceBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .testTag("resolve_theirs_${conflict.filePath}")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Accept Theirs",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = TermInfo
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { onResolveConflict(conflict.filePath, "both") },
                                    shape = RoundedCornerShape(4.dp),
                                    color = TermSurface,
                                    border = BorderStroke(1.dp, TermSurfaceBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Accept Both",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = TermText
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { onOpenInEditor(conflict.filePath) },
                                    shape = RoundedCornerShape(4.dp),
                                    color = TermSurface,
                                    border = BorderStroke(1.dp, TermSurfaceBorder),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .height(28.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(12.dp), tint = TermText)
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "In Editor",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = TermText
                                        )
                                    }
                                }
                            }
                        } else {
                            // Already resolved - option to change or open in editor
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { onOpenInEditor(conflict.filePath) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("Open in Editor", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TermPromptUser)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Actions when all resolved
            if (allResolved) {
                Spacer(modifier = Modifier.height(8.dp))

                // Action 1: Commit & Push to GitHub remote
                Surface(
                    onClick = onCommitResolvedConflicts,
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0F9D74),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("terminal_commit_push_resolved_btn")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Sync",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Commit & Push Resolved Changes to GitHub ($selectedBranch)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Action 2: Resume remaining queue if any
                if (pendingCommandCount > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        onClick = onResumePending,
                        shape = RoundedCornerShape(6.dp),
                        color = TermText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("terminal_resume_script_btn")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Resume",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Resume Remaining $pendingCommandCount Command(s) in Queue",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalPendingQueueCard(
    pendingCount: Int,
    onResume: () -> Unit
) {
    Surface(
        color = Color(0xFFF0FDF4),
        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("terminal_pending_queue_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = TermPromptUser,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "$pendingCount command(s) pending in queue",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = TermText
                    )
                    Text(
                        text = "Paused after conflict resolution. Ready to proceed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TermDim
                    )
                }
            }

            Surface(
                onClick = onResume,
                shape = RoundedCornerShape(6.dp),
                color = TermText,
                modifier = Modifier.testTag("resume_pending_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Resume",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}


