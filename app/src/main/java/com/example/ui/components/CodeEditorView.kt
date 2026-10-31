package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setSelection
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.textSelectionRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.example.data.model.ConflictResolverUtil
import com.example.data.model.FileContentResponse
import com.example.data.model.GitTreeItem
import com.example.ui.components.editor.EditorController
import com.example.ui.components.editor.EditorFloatingScrollButtons
import com.example.ui.components.editor.EditorFontFamily
import com.example.ui.components.editor.EditorGoToLineDialog
import com.example.ui.components.editor.EditorQuickSymbolBar
import com.example.ui.components.editor.EditorSearchReplaceBar
import com.example.ui.components.editor.EditorTabsRow
import com.example.ui.components.editor.EditorTypographyModal
import com.example.ui.components.editor.EditorVirtualHorizontalScrollbar
import com.example.ui.components.editor.EditorVirtualVerticalScrollbar
import com.example.ui.components.editor.FolderFilesDrawer
import com.example.ui.components.editor.NativeCodeEditorView
import com.example.ui.components.editor.SupportedLanguage
import com.example.ui.theme.GitAccent
import com.example.ui.theme.GitAccentSoft
import com.example.ui.theme.GitAppBg
import com.example.ui.theme.GitBorder
import com.example.ui.theme.GitBorderStrong
import com.example.ui.theme.GitSurface
import com.example.ui.theme.GitText1
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3
import com.example.ui.theme.GitTopBarButtonBg
import com.example.ui.theme.GitYellow
import com.example.ui.theme.Md3LightError
import com.example.ui.viewmodel.EditorTabInfo
import kotlinx.coroutines.delay

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
    onOpenSearchAcrossFiles: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val latestOnContentChange by rememberUpdatedState(onContentChange)

    val editorPrefs = remember {
        context.getSharedPreferences("code_editor_settings", Context.MODE_PRIVATE)
    }

    var fontSize by rememberSaveable {
        mutableFloatStateOf(editorPrefs.getFloat("editor_font_size", 13.5f))
    }
    var selectedFontFamily by rememberSaveable {
        mutableStateOf(EditorFontFamily.JETBRAINS_MONO)
    }
    var isWordWrapEnabled by rememberSaveable {
        mutableStateOf(editorPrefs.getBoolean("editor_word_wrap", false))
    }
    var showLineNumbers by rememberSaveable {
        mutableStateOf(editorPrefs.getBoolean("editor_line_numbers", true))
    }
    var showQuickSymbolRow by rememberSaveable {
        mutableStateOf(editorPrefs.getBoolean("editor_quick_symbols", false))
    }
    var isSyntaxHighlightingEnabled by rememberSaveable {
        mutableStateOf(editorPrefs.getBoolean("syntax_highlighting_enabled", true))
    }

    var showTypographyModal by rememberSaveable { mutableStateOf(false) }
    var isFolderDrawerOpen by rememberSaveable { mutableStateOf(false) }
    var showGoToLineDialog by rememberSaveable { mutableStateOf(false) }
    var goToLineInput by rememberSaveable { mutableStateOf("") }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }

    // Search & Replace UI State
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var isReplaceMode by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var replaceQuery by rememberSaveable { mutableStateOf("") }
    var currentMatchIndex by rememberSaveable { mutableIntStateOf(0) }
    var searchMatches by remember { mutableStateOf(IntArray(0)) }
    val searchFocusRequester = remember { FocusRequester() }

    val controller = remember { EditorController() }

    val fileName by remember(filePath) { derivedStateOf { filePath.substringAfterLast('/') } }
    val language = remember(fileName) { SupportedLanguage.fromFileName(fileName) }
    val isMarkdown by remember(fileName) { derivedStateOf { fileName.endsWith(".md", ignoreCase = true) } }
    val isImage by remember(fileName) {
        derivedStateOf {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            ext in listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico")
        }
    }

    // Clear focus and hide IME when modal overlays open
    LaunchedEffect(isFolderDrawerOpen, showTypographyModal, showGoToLineDialog) {
        if (isFolderDrawerOpen || showTypographyModal || showGoToLineDialog) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    // Bind active file tab session once loaded
    LaunchedEffect(filePath, isLoading) {
        if (!isLoading && filePath.isNotEmpty()) {
            val tabInfo = openTabs.find { it.path == filePath }
            controller.switchToFile(
                filePath = filePath,
                content = content,
                savedScrollY = tabInfo?.scrollY ?: 0,
                savedScrollX = tabInfo?.scrollX ?: 0,
                savedSelStart = tabInfo?.selectionStart ?: 0,
                savedSelEnd = tabInfo?.selectionEnd ?: 0
            )
        }
    }

    // Sync genuine external content changes (e.g., Discard, Git pull) while ignoring ViewModel debounce echoes
    LaunchedEffect(filePath, content, isLoading) {
        if (!isLoading && filePath.isNotEmpty() && controller.activeFilePath == filePath) {
            controller.onExternalContentPropChanged(filePath, content)
        }
    }

    // Strictly one-shot initialLine jump (NEVER re-triggers during subsequent edits!)
    var consumedInitialLineTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(filePath, initialLine, isLoading) {
        if (initialLine == null) {
            consumedInitialLineTarget = null
            return@LaunchedEffect
        }
        if (!isLoading && filePath.isNotEmpty() && initialLine > 0) {
            val navKey = Pair(filePath, initialLine)
            if (consumedInitialLineTarget != navKey) {
                consumedInitialLineTarget = navKey
                if (controller.activeFilePath != filePath) {
                    controller.switchToFile(filePath, content)
                }
                controller.jumpToLine(initialLine, selectEntireLine = true)
            }
        }
    }

    // Adaptive debounced sync of document edits to ViewModel (zero full-String copies during fast typing!)
    LaunchedEffect(controller.localEditCounter, filePath) {
        val active = controller.activeSession ?: return@LaunchedEffect
        if (!active.hasPendingLocalSync) return@LaunchedEffect
        val debounceMs = if (controller.charCount > 200_000) 550L else 220L
        delay(debounceMs)
        controller.flushPendingSync(latestOnContentChange)
    }

    // Flush any unsaved local edits if composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            controller.flushPendingSync(latestOnContentChange)
        }
    }

    // Handle system Back cleanly (close search or flush edits before closing file)
    BackHandler {
        when {
            isFolderDrawerOpen -> isFolderDrawerOpen = false
            isSearchVisible -> {
                isSearchVisible = false
                searchQuery = ""
                replaceQuery = ""
                searchMatches = IntArray(0)
                controller.attachedView?.setSearchHighlights(IntArray(0), 0, -1)
            }
            else -> {
                controller.flushPendingSync(latestOnContentChange)
                onClose()
            }
        }
    }

    // Passive search match calculation (updates match badges & highlights WITHOUT moving cursor when typing in editor!)
    LaunchedEffect(isSearchVisible, searchQuery, controller.localEditCounter, filePath) {
        if (!isSearchVisible || searchQuery.isEmpty()) {
            searchMatches = IntArray(0)
            controller.attachedView?.setSearchHighlights(IntArray(0), 0, -1)
            return@LaunchedEffect
        }
        if (controller.charCount > 150_000) {
            delay(180L)
        }
        val buf = controller.activeSession?.buffer ?: return@LaunchedEffect
        val found = buf.findMatches(searchQuery, ignoreCase = true)
        searchMatches = found
        val safeIdx = if (found.isNotEmpty()) currentMatchIndex.coerceIn(0, found.size - 1) else -1
        if (safeIdx >= 0 && safeIdx != currentMatchIndex) {
            currentMatchIndex = safeIdx
        }
        controller.attachedView?.setSearchHighlights(found, searchQuery.length, safeIdx)
    }

    // Explicit search navigation helper — called ONLY on explicit user Find/Next/Prev/Replace actions
    fun revealExplicitSearchMatch(matches: IntArray, targetIndex: Int) {
        if (matches.isEmpty() || searchQuery.isEmpty()) {
            controller.attachedView?.setSearchHighlights(IntArray(0), 0, -1)
            return
        }
        val safeIdx = targetIndex.coerceIn(0, matches.size - 1)
        currentMatchIndex = safeIdx
        val matchStart = matches[safeIdx]
        val matchEnd = matchStart + searchQuery.length
        controller.attachedView?.setSearchHighlights(matches, searchQuery.length, safeIdx)
        controller.selectAndRevealRange(matchStart, matchEnd)
    }

    fun onUserEditedSearchQuery(newQuery: String) {
        searchQuery = newQuery
        currentMatchIndex = 0
        if (newQuery.isEmpty()) {
            searchMatches = IntArray(0)
            controller.attachedView?.setSearchHighlights(IntArray(0), 0, -1)
        } else {
            val buf = controller.activeSession?.buffer ?: return
            val found = buf.findMatches(newQuery, ignoreCase = true)
            searchMatches = found
            if (found.isNotEmpty()) {
                revealExplicitSearchMatch(found, 0)
            } else {
                controller.attachedView?.setSearchHighlights(IntArray(0), 0, -1)
            }
        }
    }

    fun replaceCurrentMatch() {
        val buf = controller.activeSession?.buffer ?: return
        if (searchMatches.isEmpty() || searchQuery.isEmpty()) return
        val safeIdx = currentMatchIndex.coerceIn(0, searchMatches.size - 1)
        val matchPos = searchMatches[safeIdx]
        controller.replaceRangeUserAction(matchPos, matchPos + searchQuery.length, replaceQuery)
        val updatedMatches = buf.findMatches(searchQuery, ignoreCase = true)
        searchMatches = updatedMatches
        if (updatedMatches.isNotEmpty()) {
            val nextIdx = safeIdx.coerceIn(0, updatedMatches.size - 1)
            revealExplicitSearchMatch(updatedMatches, nextIdx)
        } else {
            controller.attachedView?.setSearchHighlights(IntArray(0), 0, -1)
        }
    }

    fun replaceAllMatches() {
        val buf = controller.activeSession?.buffer ?: return
        if (searchQuery.isEmpty() || searchMatches.isEmpty()) return
        val updated = buf.getFullText().replace(searchQuery, replaceQuery, ignoreCase = true)
        controller.replaceAllUserAction(updated, resetUndo = false, moveCursorToStart = false)
        searchMatches = IntArray(0)
        controller.attachedView?.setSearchHighlights(IntArray(0), 0, -1)
    }

    val effectiveIsDirty = isDirty || (controller.activeSession?.hasPendingLocalSync == true && !controller.activeSession!!.buffer.isAtCleanVersion)

    if (showGoToLineDialog) {
        EditorGoToLineDialog(
            lineCount = controller.lineCount,
            input = goToLineInput,
            onInputChange = { goToLineInput = it },
            onConfirmLine = { lineNum ->
                controller.jumpToLine(lineNum, selectEntireLine = false)
                showGoToLineDialog = false
                goToLineInput = ""
            },
            onDismiss = {
                showGoToLineDialog = false
                goToLineInput = ""
            }
        )
    }

    val editorBgColor = GitAppBg
    val editorBorderColor = GitBorder

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .drawBehind { drawRect(editorBgColor) }
    ) {
        // TOP APP BAR
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(filePath) {
                            detectTapGestures(
                                onLongPress = {
                                    clipboardManager.setText(AnnotatedString(filePath))
                                    Toast.makeText(context, "Copied: $filePath", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
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
                            if (effectiveIsDirty) {
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
                    onClick = {
                        controller.flushPendingSync(latestOnContentChange)
                        onClose()
                    },
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
                    if (controller.hasSelection) {
                        // 1. CUT SELECTION
                        Surface(
                            onClick = { controller.cut() },
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
                            onClick = { controller.copy() },
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
                            onClick = { controller.paste() },
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

                        // 4. DESELECT
                        Surface(
                            onClick = { controller.deselect() },
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
                        // 1. UNDO
                        Surface(
                            onClick = { controller.undo() },
                            enabled = controller.canUndo,
                            shape = CircleShape,
                            color = if (controller.canUndo) GitTopBarButtonBg else GitTopBarButtonBg.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("editor_undo_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = if (controller.canUndo) GitText1 else GitText3,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // 2. REDO
                        Surface(
                            onClick = { controller.redo() },
                            enabled = controller.canRedo,
                            shape = CircleShape,
                            color = if (controller.canRedo) GitTopBarButtonBg else GitTopBarButtonBg.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("editor_redo_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo",
                                    tint = if (controller.canRedo) GitText1 else GitText3,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // 3. COPY FILE
                        Surface(
                            onClick = { controller.copy() },
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

                        // 4. PASTE
                        Surface(
                            onClick = { controller.paste() },
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

                    // 5. COMMIT BUTTON
                    if (effectiveIsDirty) {
                        Button(
                            onClick = {
                                controller.flushPendingSync(latestOnContentChange)
                                onOpenCommitDialog()
                            },
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
                            properties = PopupProperties(focusable = false),
                            shape = RoundedCornerShape(12.dp),
                            containerColor = GitSurface,
                            border = BorderStroke(1.dp, GitBorderStrong),
                            shadowElevation = 8.dp
                        ) {
                            DropdownMenuItem(
                                text = { Text("Select All", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.SelectAll, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    controller.selectAll()
                                    showMoreMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Go to Top", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.VerticalAlignTop, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMoreMenu = false
                                    controller.scrollToTop()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Go to Bottom (End)", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.VerticalAlignBottom, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMoreMenu = false
                                    controller.scrollToBottom()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Go to Line...", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showGoToLineDialog = true
                                }
                            )

                            HorizontalDivider(color = GitBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text("Find in File", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    isSearchVisible = true
                                    isReplaceMode = false
                                    showMoreMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Find & Replace", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.FindReplace, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    isSearchVisible = true
                                    isReplaceMode = true
                                    showMoreMenu = false
                                }
                            )

                            HorizontalDivider(color = GitBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text("Typography & Font...", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.TextFields, contentDescription = null, tint = GitAccent, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showTypographyModal = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Browse Folder Files...", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = GitAccent, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMoreMenu = false
                                    isFolderDrawerOpen = true
                                }
                            )

                            HorizontalDivider(color = GitBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text("Zoom In (A+)", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.ZoomIn, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    val next = (fontSize + 1.5f).coerceAtMost(24f)
                                    fontSize = next
                                    editorPrefs.edit().putFloat("editor_font_size", next).apply()
                                    showMoreMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Zoom Out (A-)", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.ZoomOut, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    val next = (fontSize - 1.5f).coerceAtLeast(9f)
                                    fontSize = next
                                    editorPrefs.edit().putFloat("editor_font_size", next).apply()
                                    showMoreMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Reset Zoom (13.5sp)", color = GitText1, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    fontSize = 13.5f
                                    editorPrefs.edit().putFloat("editor_font_size", 13.5f).apply()
                                    showMoreMenu = false
                                }
                            )

                            HorizontalDivider(color = GitBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Word Wrap", color = GitText1, fontSize = 13.sp)
                                        if (isWordWrapEnabled) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.WrapText, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    val next = !isWordWrapEnabled
                                    isWordWrapEnabled = next
                                    editorPrefs.edit().putBoolean("editor_word_wrap", next).apply()
                                    showMoreMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Line Numbers", color = GitText1, fontSize = 13.sp)
                                        if (showLineNumbers) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    val next = !showLineNumbers
                                    showLineNumbers = next
                                    editorPrefs.edit().putBoolean("editor_line_numbers", next).apply()
                                    showMoreMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Syntax Highlighting", color = GitText1, fontSize = 13.sp)
                                        if (isSyntaxHighlightingEnabled) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Code, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    val next = !isSyntaxHighlightingEnabled
                                    isSyntaxHighlightingEnabled = next
                                    editorPrefs.edit().putBoolean("syntax_highlighting_enabled", next).apply()
                                    showMoreMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Quick Symbol Row", color = GitText1, fontSize = 13.sp)
                                        if (showQuickSymbolRow) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.TextFields, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    val next = !showQuickSymbolRow
                                    showQuickSymbolRow = next
                                    editorPrefs.edit().putBoolean("editor_quick_symbols", next).apply()
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
                                        Icon(Icons.Default.Preview, contentDescription = null, tint = GitAccent, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        controller.flushPendingSync(latestOnContentChange)
                                        onToggleMarkdownPreview()
                                        showMoreMenu = false
                                    }
                                )
                            }

                            if (effectiveIsDirty) {
                                HorizontalDivider(color = GitBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Discard Changes", color = Md3LightError, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Md3LightError, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        controller.replaceAllUserAction(originalContent, resetUndo = true, moveCursorToStart = true)
                                        latestOnContentChange(originalContent)
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
                onSelectTab = { targetPath ->
                    controller.flushPendingSync(latestOnContentChange)
                    onSelectTab(targetPath)
                },
                onCloseTab = { targetPath ->
                    controller.flushPendingSync(latestOnContentChange)
                    controller.closeSession(targetPath)
                    onCloseTab(targetPath)
                },
                onTogglePinTab = onTogglePinTab,
                onOpenFolderDrawer = { isFolderDrawerOpen = true }
            )
        }

        HorizontalDivider(color = GitBorder, thickness = 0.5.dp)

        // INLINE CONFLICT RESOLUTION BANNER
        if (controller.hasConflictMarkers) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Md3LightError,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Merge conflict markers in file",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Md3LightError
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Triple("Accept Current", "ours", GitAccent),
                            Triple("Accept Incoming", "theirs", Color(0xFF2563EB)),
                            Triple("Accept Both", "both", GitText1)
                        ).forEach { (label, mode, tint) ->
                            Surface(
                                onClick = {
                                    val full = controller.activeSession?.buffer?.getFullText() ?: content
                                    val resolved = ConflictResolverUtil.resolveText(full, mode)
                                    controller.replaceAllUserAction(resolved, resetUndo = false, moveCursorToStart = false)
                                    latestOnContentChange(resolved)
                                },
                                shape = RoundedCornerShape(4.dp),
                                color = GitSurface,
                                border = BorderStroke(1.dp, GitBorder)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = tint,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // EXPANDABLE SEARCH & REPLACE BAR
        EditorSearchReplaceBar(
            visible = isSearchVisible,
            isReplaceMode = isReplaceMode,
            searchQuery = searchQuery,
            replaceQuery = replaceQuery,
            matchCount = searchMatches.size,
            currentMatchIndex = currentMatchIndex,
            searchFocusRequester = searchFocusRequester,
            onSearchQueryEdited = { onUserEditedSearchQuery(it) },
            onReplaceQueryEdited = { replaceQuery = it },
            onPreviousMatch = {
                if (searchMatches.isNotEmpty()) {
                    val prev = (currentMatchIndex - 1 + searchMatches.size) % searchMatches.size
                    revealExplicitSearchMatch(searchMatches, prev)
                }
            },
            onNextMatch = {
                if (searchMatches.isNotEmpty()) {
                    val next = (currentMatchIndex + 1) % searchMatches.size
                    revealExplicitSearchMatch(searchMatches, next)
                }
            },
            onReplaceCurrent = { replaceCurrentMatch() },
            onReplaceAll = { replaceAllMatches() },
            onClearOrClose = {
                if (searchQuery.isNotEmpty()) {
                    onUserEditedSearchQuery("")
                } else {
                    isSearchVisible = false
                    replaceQuery = ""
                    searchMatches = IntArray(0)
                    controller.attachedView?.setSearchHighlights(IntArray(0), 0, -1)
                }
            }
        )

        // EDITOR BODY
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveSnakeLoadingIndicator(
                        color = GitAccent,
                        modifier = Modifier.size(42.dp),
                        strokeWidth = 3.5.dp
                    )
                }
            } else if (isImage && file?.downloadUrl != null) {
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
                val previewText = controller.activeSession?.buffer?.getFullText() ?: content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GitAppBg)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .padding(bottom = 120.dp)
                ) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GitText1,
                        lineHeight = 22.sp
                    )
                }
            } else {
                val bgArgb = editorBgColor.toArgb()
                val borderArgb = editorBorderColor.toArgb()
                val textArgb = GitText1.toArgb()
                val accentArgb = GitAccent.toArgb()

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            NativeCodeEditorView(ctx).apply {
                                this.controller = controller
                                controller.attachedView = this
                                setThemeColors(bgArgb, borderArgb, textArgb, accentArgb)
                                setLanguage(language)
                                setTypography(fontSize, selectedFontFamily)
                                this.showLineNumbers = showLineNumbers
                                this.isWordWrapEnabled = isWordWrapEnabled
                                this.isSyntaxHighlightingEnabled = isSyntaxHighlightingEnabled
                                this.isReadOnlyMode = isFolderDrawerOpen
                                this.onImmediateContentSync = { synced ->
                                    latestOnContentChange(synced)
                                }
                                this.onFontSizePinchChanged = { newSize ->
                                    fontSize = newSize
                                    editorPrefs.edit().putFloat("editor_font_size", newSize).apply()
                                }
                                controller.activeSession?.let { bindSession(it) }
                            }
                        },
                        update = { view ->
                            view.controller = controller
                            controller.attachedView = view
                            view.onImmediateContentSync = { synced ->
                                latestOnContentChange(synced)
                            }
                            view.setThemeColors(bgArgb, borderArgb, textArgb, accentArgb)
                            view.setLanguage(language)
                            view.setTypography(fontSize, selectedFontFamily)
                            view.showLineNumbers = showLineNumbers
                            view.isWordWrapEnabled = isWordWrapEnabled
                            view.isSyntaxHighlightingEnabled = isSyntaxHighlightingEnabled
                            view.isReadOnlyMode = isFolderDrawerOpen
                        },
                        onRelease = { view ->
                            view.saveStateToSession()
                            if (controller.attachedView === view) {
                                controller.attachedView = null
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("code_editor_textarea")
                            .semantics {
                                val activeBuf = controller.activeSession?.buffer
                                val previewSlice = if (activeBuf != null) {
                                    activeBuf.subSequence(0, activeBuf.length.coerceAtMost(4096))
                                } else {
                                    content.take(4096)
                                }
                                editableText = AnnotatedString(previewSlice)
                                val view = controller.attachedView
                                if (view != null) {
                                    textSelectionRange = TextRange(view.selectionAnchor, view.selectionCaret)
                                }
                                setText { newAnnotated ->
                                    controller.replaceAllUserAction(
                                        newContent = newAnnotated.text,
                                        resetUndo = false,
                                        moveCursorToStart = false
                                    )
                                    latestOnContentChange(newAnnotated.text)
                                    true
                                }
                                setSelection { start, end, _ ->
                                    controller.attachedView?.imeSetSelection(start, end) ?: false
                                }
                            }
                    )

                    EditorFloatingScrollButtons(
                        controller = controller,
                        onScrollToTop = { controller.scrollToTop() },
                        onScrollToBottom = { controller.scrollToBottom() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 20.dp, end = 24.dp)
                    )

                    EditorVirtualVerticalScrollbar(controller = controller)

                    if (!isWordWrapEnabled) {
                        EditorVirtualHorizontalScrollbar(controller = controller)
                    }
                }
            }
        }

        // BOTTOM STATUS BAR & QUICK SYMBOLS BAR (Tied cleanly to IME)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            if (showQuickSymbolRow && !isLoading && !isImage && (!isMarkdown || !isMarkdownPreviewMode)) {
                EditorQuickSymbolBar(
                    onInsertSymbol = { symbol, offset ->
                        controller.insertSymbol(symbol, offset)
                    }
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ln ${controller.cursorLine}, Col ${controller.cursorColumn}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = GitAccent
                        )
                        Text(
                            text = "${controller.lineCount} lines",
                            style = MaterialTheme.typography.labelSmall,
                            color = GitText2
                        )
                        Text(
                            text = FileIcons.formatFileSize(controller.charCount.toLong()),
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
    }

    // FOLDER FILES QUICK SWITCHER DRAWER
    FolderFilesDrawer(
        isOpen = isFolderDrawerOpen,
        currentFilePath = filePath,
        allTreeItems = allTreeItems,
        openTabs = openTabs,
        pinnedFiles = pinnedFiles,
        onSelectFile = { item ->
            controller.flushPendingSync(latestOnContentChange)
            onOpenFileFromFolder(item)
        },
        onTogglePinFile = onTogglePinFile,
        onOpenSearchAcrossFiles = {
            controller.flushPendingSync(latestOnContentChange)
            isFolderDrawerOpen = false
            onOpenSearchAcrossFiles()
        },
        onClose = { isFolderDrawerOpen = false }
    )

    // TYPOGRAPHY & FONT CONFIGURATION MODAL
    if (showTypographyModal) {
        EditorTypographyModal(
            currentFontSize = fontSize,
            currentFontFamily = selectedFontFamily,
            onFontSizeChange = { nextSize ->
                fontSize = nextSize
                editorPrefs.edit().putFloat("editor_font_size", nextSize).apply()
            },
            onFontFamilyChange = { selectedFontFamily = it },
            onDismiss = { showTypographyModal = false }
        )
    }
}
