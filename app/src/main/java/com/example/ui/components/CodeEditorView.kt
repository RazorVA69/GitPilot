package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.FileContentResponse
import com.example.ui.theme.GitHubBlue
import com.example.ui.theme.GitHubGreen
import com.example.ui.theme.GitHubOrange
import com.example.ui.theme.Md3LightCodeBg
import com.example.ui.theme.Md3LightCodeBorder
import com.example.ui.theme.Md3LightCodeGutter
import com.example.ui.theme.Md3LightError
import com.example.ui.theme.Md3LightOutline
import com.example.ui.theme.Md3LightOutlineVariant
import com.example.ui.theme.Md3LightPrimary
import com.example.ui.theme.Md3LightSurface
import com.example.ui.theme.Md3LightSurfaceVariant
import com.example.ui.theme.Md3LightTextPrimary
import com.example.ui.theme.Md3LightTextSecondary
import com.example.ui.theme.Md3LightTextTertiary

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
    onContentChange: (String) -> Unit,
    onToggleMarkdownPreview: () -> Unit,
    onOpenCommitDialog: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var fontSize by remember { mutableFloatStateOf(13.5f) }
    var isWordWrapEnabled by remember { mutableStateOf(false) }
    var showLineNumbers by remember { mutableStateOf(true) }

    // Search and Replace State
    var isSearchVisible by remember { mutableStateOf(false) }
    var isReplaceMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    // Three-dot Menu State
    var showMoreMenu by remember { mutableStateOf(false) }

    // Undo / Redo History Stacks
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }

    fun handleTextChange(newText: String) {
        if (newText != content) {
            undoStack.add(content)
            redoStack.clear()
            onContentChange(newText)
        }
    }

    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(content)
            onContentChange(previous)
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(content)
            onContentChange(next)
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

    fun replaceCurrentMatch() {
        if (matches.isNotEmpty() && searchQuery.isNotEmpty()) {
            val matchPos = matches.getOrElse(currentMatchIndex.coerceIn(0, matches.size - 1)) { 0 }
            val newContent = content.substring(0, matchPos) + replaceQuery + content.substring(matchPos + searchQuery.length)
            handleTextChange(newContent)
        }
    }

    fun replaceAllMatches() {
        if (searchQuery.isNotEmpty()) {
            val newContent = content.replace(searchQuery, replaceQuery, ignoreCase = true)
            handleTextChange(newContent)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Md3LightCodeBg)
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
                                color = Md3LightTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isDirty) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(GitHubOrange, CircleShape)
                                )
                            }
                        }
                        Text(
                            text = filePath,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Md3LightTextSecondary,
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
                        tint = Md3LightTextPrimary
                    )
                }
            },
            actions = {
                // 1. UNDO BUTTON
                IconButton(
                    onClick = { performUndo() },
                    enabled = undoStack.isNotEmpty(),
                    modifier = Modifier.testTag("editor_undo_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (undoStack.isNotEmpty()) Md3LightTextPrimary else Md3LightTextTertiary
                    )
                }

                // 2. REDO BUTTON
                IconButton(
                    onClick = { performRedo() },
                    enabled = redoStack.isNotEmpty(),
                    modifier = Modifier.testTag("editor_redo_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (redoStack.isNotEmpty()) Md3LightTextPrimary else Md3LightTextTertiary
                    )
                }

                // 3. SEARCH BUTTON
                IconButton(
                    onClick = {
                        isSearchVisible = !isSearchVisible
                        isReplaceMode = false
                    },
                    modifier = Modifier.testTag("editor_search_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isSearchVisible && !isReplaceMode) Md3LightPrimary else Md3LightTextSecondary
                    )
                }

                // 4. REPLACE BUTTON
                IconButton(
                    onClick = {
                        isSearchVisible = true
                        isReplaceMode = true
                    },
                    modifier = Modifier.testTag("editor_replace_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.FindReplace,
                        contentDescription = "Replace",
                        tint = if (isSearchVisible && isReplaceMode) Md3LightPrimary else Md3LightTextSecondary
                    )
                }

                // 5. PROMINENT COMMIT BUTTON (OUTSIDE)
                Button(
                    onClick = onOpenCommitDialog,
                    enabled = !isLoading,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .testTag("editor_commit_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDirty) GitHubGreen else GitHubBlue
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isDirty) "Commit" else "Save",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }

                // 6. THREE-DOT MORE OPTIONS MENU
                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.testTag("editor_more_options_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Md3LightTextPrimary
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        // Zoom In
                        DropdownMenuItem(
                            text = { Text("Zoom In (A+)") },
                            leadingIcon = { Icon(Icons.Default.ZoomIn, contentDescription = null) },
                            onClick = {
                                fontSize = (fontSize + 1.5f).coerceAtMost(24f)
                                showMoreMenu = false
                            }
                        )

                        // Zoom Out
                        DropdownMenuItem(
                            text = { Text("Zoom Out (A-)") },
                            leadingIcon = { Icon(Icons.Default.ZoomOut, contentDescription = null) },
                            onClick = {
                                fontSize = (fontSize - 1.5f).coerceAtLeast(9f)
                                showMoreMenu = false
                            }
                        )

                        // Reset Zoom
                        DropdownMenuItem(
                            text = { Text("Reset Zoom (13.5sp)") },
                            onClick = {
                                fontSize = 13.5f
                                showMoreMenu = false
                            }
                        )

                        HorizontalDivider()

                        // Word Wrap Toggle
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Word Wrap")
                                    if (isWordWrapEnabled) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitHubBlue, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.WrapText, contentDescription = null) },
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
                                    Text("Line Numbers")
                                    if (showLineNumbers) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitHubBlue, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                showLineNumbers = !showLineNumbers
                                showMoreMenu = false
                            }
                        )

                        if (isMarkdown) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (isMarkdownPreviewMode) "Edit Mode" else "Preview Markdown") },
                                leadingIcon = { Icon(Icons.Default.Preview, contentDescription = null) },
                                onClick = {
                                    onToggleMarkdownPreview()
                                    showMoreMenu = false
                                }
                            )
                        }

                        if (isDirty) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Discard Changes", color = Md3LightError) },
                                leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Md3LightError) },
                                onClick = {
                                    onContentChange(originalContent)
                                    undoStack.clear()
                                    redoStack.clear()
                                    showMoreMenu = false
                                }
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Md3LightSurface,
                titleContentColor = Md3LightTextPrimary
            )
        )

        HorizontalDivider(color = Md3LightOutlineVariant, thickness = 0.5.dp)

        // EXPANDABLE SEARCH & REPLACE BAR
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Md3LightSurfaceVariant,
                shadowElevation = 2.dp
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
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_find_input"),
                            placeholder = { Text("Find...", fontSize = 12.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = Md3LightTextSecondary)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    Text(
                                        text = if (matches.isNotEmpty()) "${(currentMatchIndex + 1).coerceAtMost(matches.size)}/${matches.size}" else "0/0",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (matches.isNotEmpty()) GitHubBlue else Md3LightError,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Md3LightPrimary,
                                unfocusedBorderColor = Md3LightOutline
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
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match", modifier = Modifier.size(20.dp))
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
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match", modifier = Modifier.size(20.dp))
                        }

                        // Close Search
                        IconButton(
                            onClick = {
                                isSearchVisible = false
                                searchQuery = ""
                                replaceQuery = ""
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Find", modifier = Modifier.size(18.dp))
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
                                placeholder = { Text("Replace with...", fontSize = 12.sp) },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp), tint = Md3LightTextSecondary)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = Md3LightPrimary,
                                    unfocusedBorderColor = Md3LightOutline
                                )
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Button(
                                onClick = { replaceCurrentMatch() },
                                enabled = matches.isNotEmpty(),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GitHubBlue),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Replace", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Button(
                                onClick = { replaceAllMatches() },
                                enabled = matches.isNotEmpty(),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GitHubBlue),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // EDITOR BODY (High Performance Layout)
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Md3LightPrimary)
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
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Md3LightTextPrimary,
                        lineHeight = 22.sp
                    )
                }
            } else {
                // Code Editor with High Performance Synchronized Gutter
                val verticalScrollState = rememberScrollState()
                val horizontalScrollState = rememberScrollState()

                Row(modifier = Modifier.fillMaxSize()) {
                    // Line numbers column rendered in 1 single Text engine pass for instantaneous scrolling
                    if (showLineNumbers) {
                        val digits = remember(lineCount) { lineCount.toString().length }
                        val gutterWidth = (digits * 9 + 18).dp.coerceAtLeast(36.dp)

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(gutterWidth)
                                .background(Md3LightCodeGutter)
                                .verticalScroll(verticalScrollState)
                                .padding(top = 12.dp, bottom = 12.dp, start = 4.dp, end = 6.dp)
                        ) {
                            Text(
                                text = lineNumbersText,
                                fontSize = fontSize.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Md3LightTextTertiary,
                                textAlign = TextAlign.End,
                                lineHeight = (fontSize * 1.5).sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Gutter Divider
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(Md3LightCodeBorder)
                        )
                    }

                    // Text Editor Field
                    val textModifier = if (isWordWrapEnabled) {
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScrollState)
                            .padding(12.dp)
                    } else {
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScrollState)
                            .horizontalScroll(horizontalScrollState)
                            .padding(12.dp)
                    }

                    BasicTextField(
                        value = content,
                        onValueChange = { handleTextChange(it) },
                        modifier = textModifier.testTag("code_editor_textarea"),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            color = Md3LightTextPrimary,
                            lineHeight = (fontSize * 1.5).sp
                        ),
                        cursorBrush = SolidColor(GitHubBlue)
                    )
                }
            }
        }

        // BOTTOM STATUS BAR
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Md3LightSurface,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Md3LightOutlineVariant)
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
                        color = Md3LightTextSecondary
                    )
                    Text(
                        text = "$charCount chars",
                        style = MaterialTheme.typography.labelSmall,
                        color = Md3LightTextSecondary
                    )
                    Text(
                        text = FileIcons.formatFileSize(file?.size ?: charCount.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Md3LightTextSecondary
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
                        color = Md3LightTextTertiary
                    )
                    Text(
                        text = "branch: $selectedBranch",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = GitHubBlue
                    )
                }
            }
        }
    }
}
