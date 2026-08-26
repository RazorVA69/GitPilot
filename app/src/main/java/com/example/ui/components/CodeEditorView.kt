package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectTransformGestures
import com.example.data.model.FileContentResponse
import com.example.data.model.GitTreeItem
import com.example.ui.components.editor.BracketMatcher
import com.example.ui.components.editor.CodeSyntaxVisualTransformation
import com.example.ui.components.editor.EditorFontFamily
import com.example.ui.components.editor.EditorTabsRow
import com.example.ui.components.editor.EditorTypographyModal
import com.example.ui.components.editor.FolderFilesDrawer
import com.example.ui.components.editor.SupportedLanguage
import com.example.ui.theme.GitAccent
import com.example.ui.theme.GitAccentSoft
import com.example.ui.theme.GitAppBg
import com.example.ui.theme.GitBg
import com.example.ui.theme.GitBorder
import com.example.ui.theme.GitBorderStrong
import com.example.ui.theme.GitButtonPrimary
import com.example.ui.theme.GitSurface
import com.example.ui.theme.GitSurface2
import com.example.ui.theme.GitTopBarButtonBg
import com.example.ui.theme.GitText1
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3
import com.example.ui.theme.GitYellow
import com.example.ui.theme.GitHubOrange
import com.example.ui.theme.Md3LightError
import com.example.ui.viewmodel.EditorTabInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorView(
    file: FileContentResponse?,
    filePath: String,
    content: String,
    originalContent: String,
    isLoading: Boolean,
    isDirty: Boolean,
    isMarkdownPreviewMode: Boolean,
    selectedBranch: String,
    initialLine: Int? = null,
    openTabs: List<EditorTabInfo> = emptyList(),
    pinnedFiles: Set<String> = emptySet(),
    allTreeItems: List<GitTreeItem> = emptyList(),
    onContentChange: (String) -> Unit,
    onToggleMarkdownPreview: () -> Unit,
    onOpenCommitDialog: () -> Unit,
    onClose: () -> Unit,
    onSelectTab: (String) -> Unit = {},
    onCloseTab: (String) -> Unit = {},
    onTogglePinTab: (String) -> Unit = {},
    onTogglePinFile: (String) -> Unit = {},
    onOpenFileFromFolder: (GitTreeItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    var fontSize by remember { mutableFloatStateOf(13.5f) }
    var selectedFontFamily by remember { mutableStateOf(EditorFontFamily.JETBRAINS_MONO) }
    var showTypographyModal by remember { mutableStateOf(false) }
    var isFolderDrawerOpen by remember { mutableStateOf(false) }

    var isWordWrapEnabled by remember { mutableStateOf(false) }
    var showLineNumbers by remember { mutableStateOf(true) }

    // Search and Replace State
    var isSearchVisible by remember { mutableStateOf(false) }
    var isReplaceMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    // Go To Line Dialog State
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var goToLineInput by remember { mutableStateOf("") }

    // Three-dot Menu State
    var showMoreMenu by remember { mutableStateOf(false) }

    // Text Field Value State
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = content, selection = TextRange(content.length)))
    }

    LaunchedEffect(content) {
        if (textFieldValue.text != content) {
            textFieldValue = textFieldValue.copy(text = content)
        }
    }

    LaunchedEffect(initialLine, content) {
        if (initialLine != null && initialLine > 0 && content.isNotEmpty()) {
            val lines = content.lines()
            val targetIdx = (initialLine - 1).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
            var charOffset = 0
            for (i in 0 until targetIdx) {
                charOffset += lines[i].length + 1
            }
            val lineEnd = charOffset + (lines.getOrNull(targetIdx)?.length ?: 0)
            textFieldValue = textFieldValue.copy(
                selection = TextRange(charOffset, lineEnd)
            )
            // Scroll to the target line smoothly
            val targetLine = targetIdx
            coroutineScope.launch {
                val approximateLineHeightPx = (fontSize * 1.5f * 2.5f).toInt()
                val targetScroll = (targetLine * approximateLineHeightPx - 100).coerceAtLeast(0)
                verticalScrollState.animateScrollTo(targetScroll.coerceIn(0, verticalScrollState.maxValue))
            }
        }
    }

    // Language-aware Syntax Highlighting & Bracket Matching
    val language = remember(filePath) {
        SupportedLanguage.fromFileName(filePath.substringAfterLast('/'))
    }

    val matchingBracketIndices = remember(textFieldValue.text, textFieldValue.selection) {
        BracketMatcher.findMatchingBracket(textFieldValue.text, textFieldValue.selection.start)
    }

    val visualTransformation = remember(language, matchingBracketIndices) {
        if (language == SupportedLanguage.PLAIN_TEXT || language == SupportedLanguage.MARKDOWN) {
            androidx.compose.ui.text.input.VisualTransformation.None
        } else {
            CodeSyntaxVisualTransformation(language, matchingBracketIndices)
        }
    }

    // Undo / Redo History Stacks with cursor position preservation
    data class EditorHistoryItem(
        val text: String,
        val selection: TextRange
    )

    val undoStack = remember { mutableStateListOf<EditorHistoryItem>() }
    val redoStack = remember { mutableStateListOf<EditorHistoryItem>() }

    fun handleTextChange(newText: String, previousSelection: TextRange = textFieldValue.selection) {
        if (newText != content) {
            undoStack.add(EditorHistoryItem(content, previousSelection))
            redoStack.clear()
            onContentChange(newText)
        }
    }

    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(EditorHistoryItem(content, textFieldValue.selection))
            val safeSelection = TextRange(
                previous.selection.start.coerceIn(0, previous.text.length),
                previous.selection.end.coerceIn(0, previous.text.length)
            )
            textFieldValue = TextFieldValue(text = previous.text, selection = safeSelection)
            onContentChange(previous.text)

            // Scroll editor to where the undo action was applied
            val targetChar = safeSelection.start
            val targetLine = previous.text.take(targetChar).count { it == '\n' }
            coroutineScope.launch {
                val approximateLineHeightPx = (fontSize * 1.5f * 2.5f).toInt()
                val targetScroll = (targetLine * approximateLineHeightPx - 100).coerceAtLeast(0)
                verticalScrollState.animateScrollTo(targetScroll.coerceIn(0, verticalScrollState.maxValue))
            }
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(EditorHistoryItem(content, textFieldValue.selection))
            val safeSelection = TextRange(
                next.selection.start.coerceIn(0, next.text.length),
                next.selection.end.coerceIn(0, next.text.length)
            )
            textFieldValue = TextFieldValue(text = next.text, selection = safeSelection)
            onContentChange(next.text)

            // Scroll editor to where the redo action was applied
            val targetChar = safeSelection.start
            val targetLine = next.text.take(targetChar).count { it == '\n' }
            coroutineScope.launch {
                val approximateLineHeightPx = (fontSize * 1.5f * 2.5f).toInt()
                val targetScroll = (targetLine * approximateLineHeightPx - 100).coerceAtLeast(0)
                verticalScrollState.animateScrollTo(targetScroll.coerceIn(0, verticalScrollState.maxValue))
            }
        }
    }

    fun handleEditorValueChange(newTfv: TextFieldValue) {
        val oldText = textFieldValue.text
        val newText = newTfv.text
        val oldSelection = textFieldValue.selection
        var adjustedTfv = newTfv

        // If a single character was typed
        if (newText.length == oldText.length + 1 && newTfv.selection.collapsed) {
            val cursor = newTfv.selection.start
            val insertedChar = newText.getOrNull(cursor - 1)

            if (insertedChar == '\n') {
                val indent = BracketMatcher.computeAutoIndent(oldText, oldSelection.start)
                if (indent.isNotEmpty()) {
                    val withIndent = newText.substring(0, cursor) + indent + newText.substring(cursor)
                    adjustedTfv = TextFieldValue(
                        text = withIndent,
                        selection = TextRange(cursor + indent.length)
                    )
                }
            } else if (insertedChar in listOf('(', '[', '{', '"', '\'', '`')) {
                val closer = when (insertedChar) {
                    '(' -> ")"
                    '[' -> "]"
                    '{' -> "}"
                    '"' -> "\""
                    '\'' -> "\'"
                    '`' -> "`"
                    else -> ""
                }
                if (closer.isNotEmpty()) {
                    val paired = newText.substring(0, cursor) + closer + newText.substring(cursor)
                    adjustedTfv = TextFieldValue(
                        text = paired,
                        selection = TextRange(cursor)
                    )
                }
            } else if (insertedChar in listOf(')', ']', '}', '"', '\'', '`')) {
                val nextCharInOld = oldText.getOrNull(oldSelection.start)
                if (nextCharInOld == insertedChar) {
                    adjustedTfv = TextFieldValue(
                        text = oldText,
                        selection = TextRange(oldSelection.start + 1)
                    )
                }
            }
        }

        textFieldValue = adjustedTfv
        if (adjustedTfv.text != content) {
            handleTextChange(adjustedTfv.text, oldSelection)
        }
    }

    val fileName = remember(filePath) { filePath.substringAfterLast('/') }
    val isMarkdown = remember(fileName) { fileName.endsWith(".md", ignoreCase = true) }
    val isImage = remember(fileName) {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        ext in listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico")
    }

    // High performance single-string line number generation for smooth 120 FPS scrolling
    val lineCount = remember(content) { content.count { it == '\n' } + 1 }
    val lineNumbersText = remember(lineCount) {
        val sb = StringBuilder(lineCount * 6)
        for (i in 1..lineCount) {
            sb.append(i).append('\n')
        }
        if (sb.isNotEmpty()) sb.setLength(sb.length - 1)
        sb.toString()
    }

    val charCount = remember(content) { content.length }

    val hasSelection = remember(textFieldValue.selection) {
        !textFieldValue.selection.collapsed && textFieldValue.selection.length > 0
    }

    fun copyText() {
        val textToCopy = if (hasSelection) {
            textFieldValue.getSelectedText().text
        } else {
            content
        }
        if (textToCopy.isNotEmpty()) {
            clipboardManager.setText(AnnotatedString(textToCopy))
        }
    }

    fun cutText() {
        if (hasSelection) {
            val selectedText = textFieldValue.getSelectedText().text
            clipboardManager.setText(AnnotatedString(selectedText))
            val start = textFieldValue.selection.min
            val end = textFieldValue.selection.max
            val prevSelection = textFieldValue.selection
            val newText = textFieldValue.text.removeRange(start, end)
            textFieldValue = textFieldValue.copy(
                text = newText,
                selection = TextRange(start)
            )
            handleTextChange(newText, prevSelection)
        }
    }

    fun pasteText() {
        val clip = clipboardManager.getText()?.text
        if (!clip.isNullOrEmpty()) {
            val prevSelection = textFieldValue.selection
            if (hasSelection) {
                val start = textFieldValue.selection.min
                val end = textFieldValue.selection.max
                val newText = textFieldValue.text.replaceRange(start, end, clip)
                textFieldValue = textFieldValue.copy(
                    text = newText,
                    selection = TextRange(start + clip.length)
                )
                handleTextChange(newText, prevSelection)
            } else {
                val cursor = textFieldValue.selection.end.coerceIn(0, textFieldValue.text.length)
                val newText = textFieldValue.text.substring(0, cursor) + clip + textFieldValue.text.substring(cursor)
                textFieldValue = textFieldValue.copy(
                    text = newText,
                    selection = TextRange(cursor + clip.length)
                )
                handleTextChange(newText, prevSelection)
            }
        }
    }

    fun clearText() {
        val prevSelection = textFieldValue.selection
        if (hasSelection) {
            val start = textFieldValue.selection.min
            val end = textFieldValue.selection.max
            val newText = textFieldValue.text.removeRange(start, end)
            textFieldValue = textFieldValue.copy(
                text = newText,
                selection = TextRange(start)
            )
            handleTextChange(newText, prevSelection)
        } else {
            textFieldValue = textFieldValue.copy(text = "", selection = TextRange.Zero)
            handleTextChange("", prevSelection)
        }
    }

    fun selectAllText() {
        textFieldValue = textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length))
    }

    fun deselectText() {
        textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.selection.end))
    }

    fun jumpToLine(targetLine: Int) {
        val clampedLine = targetLine.coerceIn(1, lineCount)
        var charIdx = 0
        var currentL = 1
        for (i in content.indices) {
            if (currentL == clampedLine) {
                charIdx = i
                break
            }
            if (content[i] == '\n') {
                currentL++
            }
        }
        textFieldValue = textFieldValue.copy(selection = TextRange(charIdx))
        coroutineScope.launch {
            val approxLineHeightPx = (fontSize * 3.8f).toInt()
            val targetScroll = (clampedLine - 1) * approxLineHeightPx
            verticalScrollState.animateScrollTo(targetScroll.coerceIn(0, verticalScrollState.maxValue))
        }
    }

    // Matches for search
    val matches = remember(content, searchQuery) {
        if (searchQuery.isBlank()) emptyList<Int>()
        else {
            val list = mutableListOf<Int>()
            var index = content.indexOf(searchQuery, ignoreCase = true)
            while (index >= 0) {
                list.add(index)
                index = content.indexOf(searchQuery, index + 1, ignoreCase = true)
            }
            list
        }
    }

    LaunchedEffect(searchQuery, currentMatchIndex, matches, isSearchVisible) {
        if (isSearchVisible && searchQuery.isNotEmpty() && matches.isNotEmpty()) {
            val safeIndex = currentMatchIndex.coerceIn(0, matches.size - 1)
            val matchPos = matches[safeIndex]
            textFieldValue = textFieldValue.copy(
                selection = TextRange(matchPos, matchPos + searchQuery.length)
            )
            // Scroll to the matched line
            val line = content.take(matchPos).count { it == '\n' }
            val approximateLineHeightPx = (fontSize * 1.5f * 2.5f).toInt()
            val targetScroll = (line * approximateLineHeightPx - 100).coerceAtLeast(0)
            verticalScrollState.animateScrollTo(targetScroll.coerceIn(0, verticalScrollState.maxValue))
        } else if (searchQuery.isEmpty() || !isSearchVisible) {
            if (!textFieldValue.selection.collapsed) {
                textFieldValue = textFieldValue.copy(
                    selection = TextRange(textFieldValue.selection.start)
                )
            }
        }
    }

    fun replaceCurrentMatch() {
        if (matches.isNotEmpty() && searchQuery.isNotEmpty()) {
            val prevSelection = textFieldValue.selection
            val matchPos = matches.getOrElse(currentMatchIndex.coerceIn(0, matches.size - 1)) { 0 }
            val newContent = content.substring(0, matchPos) + replaceQuery + content.substring(matchPos + searchQuery.length)
            handleTextChange(newContent, prevSelection)
        }
    }

    fun replaceAllMatches() {
        if (searchQuery.isNotEmpty()) {
            val prevSelection = textFieldValue.selection
            val newContent = content.replace(searchQuery, replaceQuery, ignoreCase = true)
            handleTextChange(newContent, prevSelection)
        }
    }

    // Go to Line Dialog
    if (showGoToLineDialog) {
        AlertDialog(
            onDismissRequest = {
                showGoToLineDialog = false
                goToLineInput = ""
            },
            containerColor = GitSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FormatListNumbered,
                        contentDescription = null,
                        tint = GitAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Go to Line",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GitText1
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter line number (1 – $lineCount):",
                        style = MaterialTheme.typography.bodySmall,
                        color = GitText2
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = goToLineInput,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                goToLineInput = input
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("e.g. 42", color = GitText3) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("go_to_line_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GitSurface,
                            unfocusedContainerColor = GitSurface,
                            focusedBorderColor = GitAccent,
                            unfocusedBorderColor = GitBorderStrong
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lineNum = goToLineInput.toIntOrNull()
                        if (lineNum != null) {
                            jumpToLine(lineNum)
                        }
                        showGoToLineDialog = false
                        goToLineInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GitAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Go", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showGoToLineDialog = false
                        goToLineInput = ""
                    }
                ) {
                    Text("Cancel", color = GitText2)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GitAppBg)
    ) {
        // TOP APP BAR
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FileIconForExtension(
                        extension = fileName.substringAfterLast('.', ""),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GitText1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isDirty) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(GitYellow, CircleShape)
                                )
                            }
                        }
                        Text(
                            text = filePath,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = GitText2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("editor_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GitText1
                    )
                }
            },
            actions = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    if (hasSelection) {
                        // CONTEXTUAL ACTIONS WHEN TEXT IS SELECTED (via Select All or touch selection)
                        // 1. CUT
                        Surface(
                            onClick = { cutText() },
                            shape = CircleShape,
                            color = GitTopBarButtonBg,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("editor_cut_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ContentCut,
                                    contentDescription = "Cut",
                                    tint = GitText1,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // 2. COPY SELECTION
                        Surface(
                            onClick = { copyText() },
                            shape = CircleShape,
                            color = GitAccentSoft,
                            border = BorderStroke(1.dp, GitAccent),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("editor_copy_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Selection",
                                    tint = GitAccent,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // 3. PASTE OVER SELECTION
                        Surface(
                            onClick = { pasteText() },
                            shape = CircleShape,
                            color = GitTopBarButtonBg,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("editor_paste_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = GitText1,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // 4. DESELECT BUTTON
                        Surface(
                            onClick = { deselectText() },
                            shape = CircleShape,
                            color = GitTopBarButtonBg,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("editor_deselect_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Deselect",
                                    tint = GitText2,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    } else {
                        // STANDARD TOP BAR ACTIONS (NO SELECTION ACTIVE)
                        // 1. UNDO BUTTON
                        Surface(
                            onClick = { performUndo() },
                            enabled = undoStack.isNotEmpty(),
                            shape = CircleShape,
                            color = if (undoStack.isNotEmpty()) GitTopBarButtonBg else GitTopBarButtonBg.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("editor_undo_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = if (undoStack.isNotEmpty()) GitText1 else GitText3,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // 2. REDO BUTTON
                        Surface(
                            onClick = { performRedo() },
                            enabled = redoStack.isNotEmpty(),
                            shape = CircleShape,
                            color = if (redoStack.isNotEmpty()) GitTopBarButtonBg else GitTopBarButtonBg.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("editor_redo_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo",
                                    tint = if (redoStack.isNotEmpty()) GitText1 else GitText3,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // 3. COPY BUTTON (Copies file content)
                        Surface(
                            onClick = { copyText() },
                            shape = CircleShape,
                            color = GitTopBarButtonBg,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("editor_copy_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy File",
                                    tint = GitText1,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // 4. PASTE BUTTON (Pastes at cursor or appends)
                        Surface(
                            onClick = { pasteText() },
                            shape = CircleShape,
                            color = GitTopBarButtonBg,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("editor_paste_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = GitText1,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    // 5. COMMIT BUTTON (ONLY SHOWN WHEN CHANGES EXIST / isDirty == true)
                    if (isDirty) {
                        Button(
                            onClick = onOpenCommitDialog,
                            enabled = !isLoading,
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("editor_commit_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GitAccent,
                                contentColor = Color.White,
                                disabledContainerColor = GitAccent.copy(alpha = 0.5f),
                                disabledContentColor = Color.White.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Commit",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }

                    // 6. THREE-DOT MORE OPTIONS MENU
                    Box {
                        Surface(
                            onClick = { showMoreMenu = true },
                            shape = CircleShape,
                            color = GitTopBarButtonBg,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("editor_more_options_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = GitText1,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                            shape = RoundedCornerShape(12.dp),
                            containerColor = GitSurface,
                            border = BorderStroke(1.dp, GitBorderStrong),
                            shadowElevation = 8.dp
                        ) {
                            // Select All
                            DropdownMenuItem(
                                text = { Text("Select All", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.SelectAll,
                                        contentDescription = null,
                                        tint = GitText2,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    selectAllText()
                                    showMoreMenu = false
                                }
                            )

                            // Go to Line
                            DropdownMenuItem(
                                text = { Text("Go to Line...", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.FormatListNumbered,
                                        contentDescription = null,
                                        tint = GitText2,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showGoToLineDialog = true
                                }
                            )

                            HorizontalDivider(color = GitBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                            // Find in File
                            DropdownMenuItem(
                                text = { Text("Find in File", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = GitText2,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    isSearchVisible = true
                                    isReplaceMode = false
                                    showMoreMenu = false
                                }
                            )

                            // Find & Replace
                            DropdownMenuItem(
                                text = { Text("Find & Replace", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.FindReplace,
                                        contentDescription = null,
                                        tint = GitText2,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    isSearchVisible = true
                                    isReplaceMode = true
                                    showMoreMenu = false
                                }
                            )

                            HorizontalDivider(color = GitBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                            // Typography & Fonts
                            DropdownMenuItem(
                                text = { Text("Typography & Font...", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.TextFields,
                                        contentDescription = null,
                                        tint = GitAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showTypographyModal = true
                                }
                            )

                            // Open Folder Files Drawer
                            DropdownMenuItem(
                                text = { Text("Browse Folder Files...", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = GitAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    isFolderDrawerOpen = true
                                }
                            )

                            HorizontalDivider(color = GitBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                            // Zoom In
                            DropdownMenuItem(
                                text = { Text("Zoom In (A+)", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ZoomIn,
                                        contentDescription = null,
                                        tint = GitText2,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    fontSize = (fontSize + 1.5f).coerceAtMost(24f)
                                    showMoreMenu = false
                                }
                            )

                            // Zoom Out
                            DropdownMenuItem(
                                text = { Text("Zoom Out (A-)", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ZoomOut,
                                        contentDescription = null,
                                        tint = GitText2,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    fontSize = (fontSize - 1.5f).coerceAtLeast(9f)
                                    showMoreMenu = false
                                }
                            )

                            // Reset Zoom
                            DropdownMenuItem(
                                text = { Text("Reset Zoom (13.5sp)", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        tint = GitText2,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    fontSize = 13.5f
                                    showMoreMenu = false
                                }
                            )

                            HorizontalDivider(color = GitBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                            // Word Wrap Toggle
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Word Wrap", color = GitText1, fontSize = 13.sp)
                                        if (isWordWrapEnabled) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = GitAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.WrapText,
                                        contentDescription = null,
                                        tint = GitText2,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    isWordWrapEnabled = !isWordWrapEnabled
                                    showMoreMenu = false
                                }
                            )

                            // Line Numbers Toggle
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Line Numbers", color = GitText1, fontSize = 13.sp)
                                        if (showLineNumbers) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = GitAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.WrapText,
                                        contentDescription = null,
                                        tint = GitText2,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showLineNumbers = !showLineNumbers
                                    showMoreMenu = false
                                }
                            )

                            if (isMarkdown) {
                                HorizontalDivider(color = GitBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isMarkdownPreviewMode) "Edit Mode" else "Preview Markdown",
                                            color = GitText1,
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Preview,
                                            contentDescription = null,
                                            tint = GitAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        onToggleMarkdownPreview()
                                        showMoreMenu = false
                                    }
                                )
                            }

                            if (isDirty) {
                                HorizontalDivider(color = GitBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Discard Changes", color = Md3LightError, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.RestartAlt,
                                            contentDescription = null,
                                            tint = Md3LightError,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        onContentChange(originalContent)
                                        textFieldValue = TextFieldValue(text = originalContent, selection = TextRange(originalContent.length))
                                        undoStack.clear()
                                        redoStack.clear()
                                        showMoreMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = GitAppBg,
                titleContentColor = GitText1
            )
        )

        // MULTI-FILE EDITOR TABS ROW
        if (openTabs.isNotEmpty()) {
            EditorTabsRow(
                tabs = openTabs,
                activeFilePath = filePath,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
                onTogglePinTab = onTogglePinTab,
                onOpenFolderDrawer = { isFolderDrawerOpen = true }
            )
        }

        HorizontalDivider(color = GitBorder, thickness = 0.5.dp)

        // EXPANDABLE SEARCH & REPLACE BAR
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GitSurface,
                border = BorderStroke(1.dp, GitBorderStrong)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Row 1: Find Input & Navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                currentMatchIndex = 0
                                if (it.isEmpty() && !textFieldValue.selection.collapsed) {
                                    textFieldValue = textFieldValue.copy(
                                        selection = TextRange(textFieldValue.selection.start)
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_find_input"),
                            placeholder = { Text("Find...", fontSize = 12.sp, color = GitText3) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = GitText2)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    Text(
                                        text = if (matches.isNotEmpty()) "${(currentMatchIndex + 1).coerceAtMost(matches.size)}/${matches.size}" else "0/0",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (matches.isNotEmpty()) GitAccent else Md3LightError,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = GitSurface,
                                unfocusedContainerColor = GitSurface,
                                focusedBorderColor = GitAccent,
                                unfocusedBorderColor = GitBorderStrong
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Match Prev
                        IconButton(
                            onClick = {
                                if (matches.isNotEmpty()) {
                                    currentMatchIndex = (currentMatchIndex - 1 + matches.size) % matches.size
                                }
                            },
                            enabled = matches.isNotEmpty(),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match", modifier = Modifier.size(20.dp), tint = GitText1)
                        }

                        // Match Next
                        IconButton(
                            onClick = {
                                if (matches.isNotEmpty()) {
                                    currentMatchIndex = (currentMatchIndex + 1) % matches.size
                                }
                            },
                            enabled = matches.isNotEmpty(),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match", modifier = Modifier.size(20.dp), tint = GitText1)
                        }

                        // Close Search
                        IconButton(
                            onClick = {
                                isSearchVisible = false
                                searchQuery = ""
                                replaceQuery = ""
                                if (!textFieldValue.selection.collapsed) {
                                    textFieldValue = textFieldValue.copy(
                                        selection = TextRange(textFieldValue.selection.start)
                                    )
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Find", modifier = Modifier.size(18.dp), tint = GitText2)
                        }
                    }

                    // Row 2: Replace Input & Action Buttons (if Replace Mode active)
                    if (isReplaceMode) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = replaceQuery,
                                onValueChange = { replaceQuery = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("editor_replace_input"),
                                placeholder = { Text("Replace with...", fontSize = 12.sp, color = GitText3) },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp), tint = GitText2)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = GitSurface,
                                    unfocusedContainerColor = GitSurface,
                                    focusedBorderColor = GitAccent,
                                    unfocusedBorderColor = GitBorderStrong
                                )
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Button(
                                onClick = { replaceCurrentMatch() },
                                enabled = matches.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GitButtonPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Replace", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Button(
                                onClick = { replaceAllMatches() },
                                enabled = matches.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GitButtonPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // EDITOR BODY (High Performance Pure White Layout)
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GitAccent)
                }
            } else if (isImage && file?.downloadUrl != null) {
                // Image preview viewer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = file.downloadUrl,
                        contentDescription = fileName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            } else if (isMarkdown && isMarkdownPreviewMode) {
                // Markdown Reader
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GitAppBg)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .padding(bottom = 120.dp)
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GitText1,
                        lineHeight = 22.sp
                    )
                }
            } else {
                // Code Editor with High Performance Synchronized Gutter & Custom Scrollbars
                val density = LocalDensity.current
                val isImeVisible = WindowInsets.ime.getBottom(density) > 0

                // Sync search match selection into textFieldValue
                LaunchedEffect(matches, currentMatchIndex) {
                    if (matches.isNotEmpty() && currentMatchIndex in matches.indices && searchQuery.isNotEmpty()) {
                        val matchStart = matches[currentMatchIndex]
                        val matchEnd = (matchStart + searchQuery.length).coerceAtMost(content.length)
                        textFieldValue = textFieldValue.copy(selection = TextRange(matchStart, matchEnd))
                    }
                }

                val cursorIndex = remember(textFieldValue.selection, textFieldValue.text) {
                    textFieldValue.selection.end.coerceIn(0, textFieldValue.text.length)
                }

                val cursorLine = remember(textFieldValue.text, cursorIndex) {
                    var line = 0
                    val text = textFieldValue.text
                    val limit = cursorIndex.coerceAtMost(text.length)
                    for (i in 0 until limit) {
                        if (text[i] == '\n') line++
                    }
                    line
                }

                // Ensure active cursor line is automatically scrolled into view when cursor line moves
                LaunchedEffect(cursorLine) {
                    val lineHeightPx = with(density) { (fontSize * 1.5).sp.toPx() }
                    val topPaddingPx = with(density) { 12.dp.toPx() }
                    val cursorY = (topPaddingPx + (cursorLine * lineHeightPx)).toInt()
                    val currentScroll = verticalScrollState.value
                    val topComfortMargin = with(density) { 36.dp.toPx() }.toInt()
                    val bottomComfortMargin = with(density) { 140.dp.toPx() }.toInt()

                    if (cursorY < currentScroll + topComfortMargin) {
                        val target = (cursorY - topComfortMargin).coerceAtLeast(0)
                        verticalScrollState.animateScrollTo(target.coerceIn(0, verticalScrollState.maxValue))
                    } else if (cursorY > currentScroll + 650 - bottomComfortMargin) {
                        val target = (cursorY - 200).coerceAtLeast(0)
                        verticalScrollState.animateScrollTo(target.coerceIn(0, verticalScrollState.maxValue))
                    }
                }

                // When keyboard appears, scroll the active cursor line above the keyboard
                LaunchedEffect(isImeVisible) {
                    if (isImeVisible) {
                        kotlinx.coroutines.delay(120)
                        val lineHeightPx = with(density) { (fontSize * 1.5).sp.toPx() }
                        val topPaddingPx = with(density) { 12.dp.toPx() }
                        val cursorY = (topPaddingPx + (cursorLine * lineHeightPx)).toInt()
                        val target = (cursorY - 140).coerceAtLeast(0)
                        verticalScrollState.animateScrollTo(target.coerceIn(0, verticalScrollState.maxValue))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GitAppBg)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom != 1f) {
                                    fontSize = (fontSize * zoom).coerceIn(8.5f, 32f)
                                }
                            }
                        }
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Line numbers column rendered in 1 single Text engine pass for instantaneous scrolling
                        if (showLineNumbers) {
                            val digits = remember(lineCount) { maxOf(2, lineCount.toString().length) }
                            val gutterWidth = remember(digits, fontSize) {
                                (digits * (fontSize * 0.62f) + 8f).dp
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(gutterWidth)
                                    .background(GitAppBg)
                                    .verticalScroll(verticalScrollState)
                                    .padding(top = 12.dp, bottom = 520.dp, start = 2.dp, end = 3.dp)
                            ) {
                                Text(
                                    text = lineNumbersText,
                                    fontSize = fontSize.sp,
                                    fontFamily = selectedFontFamily.fontFamily,
                                    color = GitText3,
                                    textAlign = TextAlign.End,
                                    lineHeight = (fontSize * 1.5).sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Gutter Divider
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(0.8.dp)
                                    .background(GitBorder)
                            )
                        }

                        // Text Editor Field with generous bottom padding (520dp) so IME keyboard never hides the end of file
                        val textModifier = if (isWordWrapEnabled) {
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(verticalScrollState)
                                .padding(top = 12.dp, bottom = 520.dp, start = 6.dp, end = 16.dp)
                        } else {
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(verticalScrollState)
                                .horizontalScroll(horizontalScrollState)
                                .padding(top = 12.dp, bottom = 520.dp, start = 6.dp, end = 24.dp)
                        }

                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { handleEditorValueChange(it) },
                            modifier = textModifier.testTag("code_editor_textarea"),
                            textStyle = TextStyle(
                                fontFamily = selectedFontFamily.fontFamily,
                                fontSize = fontSize.sp,
                                color = GitText1,
                                lineHeight = (fontSize * 1.5).sp
                            ),
                            visualTransformation = visualTransformation,
                            cursorBrush = SolidColor(GitAccent)
                        )
                    }

                    // Left Edge Gesture Area: swiping from left side opens files drawer
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(36.dp)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, dragAmount ->
                                    if (dragAmount > 12f) {
                                        change.consume()
                                        isFolderDrawerOpen = true
                                    }
                                }
                            }
                    )

                    // Vertical Right Scrollbar (Zero Recomposition via graphicsLayer)
                    EditorVerticalScrollbar(
                        scrollState = verticalScrollState
                    )

                    // Horizontal Bottom Scrollbar (Zero Recomposition via graphicsLayer)
                    if (!isWordWrapEnabled) {
                        EditorHorizontalScrollbar(
                            scrollState = horizontalScrollState
                        )
                    }
                }
            }
        }

        // BOTTOM STATUS BAR
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = GitSurface,
            border = BorderStroke(0.5.dp, GitBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$lineCount lines",
                        style = MaterialTheme.typography.labelSmall,
                        color = GitText2
                    )
                    Text(
                        text = "$charCount chars",
                        style = MaterialTheme.typography.labelSmall,
                        color = GitText2
                    )
                    Text(
                        text = FileIcons.formatFileSize(file?.size ?: charCount.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = GitText2
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UTF-8",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = GitText3
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(GitAccentSoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(GitAccent, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedBranch,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = GitAccent,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    // FOLDER FILES QUICK SWITCHER DRAWER
    FolderFilesDrawer(
        isOpen = isFolderDrawerOpen,
        currentFilePath = filePath,
        allTreeItems = allTreeItems,
        openTabs = openTabs,
        pinnedFiles = pinnedFiles,
        onSelectFile = onOpenFileFromFolder,
        onTogglePinFile = onTogglePinFile,
        onClose = { isFolderDrawerOpen = false }
    )

    // TYPOGRAPHY & FONT CONFIGURATION MODAL
    if (showTypographyModal) {
        EditorTypographyModal(
            currentFontSize = fontSize,
            currentFontFamily = selectedFontFamily,
            onFontSizeChange = { fontSize = it },
            onFontFamilyChange = { selectedFontFamily = it },
            onDismiss = { showTypographyModal = false }
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.EditorVerticalScrollbar(
    scrollState: androidx.compose.foundation.ScrollState
) {
    if (scrollState.maxValue <= 0) return

    BoxWithConstraints(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(8.dp)
            .padding(vertical = 4.dp, horizontal = 1.dp)
    ) {
        val trackHeight = maxHeight
        val thumbHeight = (trackHeight * 0.22f).coerceIn(36.dp, 100.dp)

        // Track background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.04f))
        )
        // Scrollbar Thumb: translated via graphicsLayer so NO recompositions happen on scroll!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbHeight)
                .graphicsLayer {
                    val maxVal = scrollState.maxValue
                    if (maxVal > 0) {
                        val fraction = (scrollState.value.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                        val trackHeightPx = trackHeight.toPx()
                        val thumbHeightPx = thumbHeight.toPx()
                        val availableTrack = (trackHeightPx - thumbHeightPx - 8.dp.toPx()).coerceAtLeast(0f)
                        translationY = availableTrack * fraction
                    }
                }
                .clip(RoundedCornerShape(4.dp))
                .background(GitText3.copy(alpha = 0.6f))
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.EditorHorizontalScrollbar(
    scrollState: androidx.compose.foundation.ScrollState
) {
    if (scrollState.maxValue <= 0) return

    BoxWithConstraints(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(8.dp)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        val trackWidth = maxWidth
        val thumbWidth = (trackWidth * 0.25f).coerceIn(40.dp, 120.dp)

        // Track background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.04f))
        )
        // Scrollbar Thumb: translated via graphicsLayer so NO recompositions happen on scroll!
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(thumbWidth)
                .graphicsLayer {
                    val maxVal = scrollState.maxValue
                    if (maxVal > 0) {
                        val fraction = (scrollState.value.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                        val trackWidthPx = trackWidth.toPx()
                        val thumbWidthPx = thumbWidth.toPx()
                        val availableTrack = (trackWidthPx - thumbWidthPx - 8.dp.toPx()).coerceAtLeast(0f)
                        translationX = availableTrack * fraction
                    }
                }
                .clip(RoundedCornerShape(4.dp))
                .background(GitText3.copy(alpha = 0.6f))
        )
    }
}
