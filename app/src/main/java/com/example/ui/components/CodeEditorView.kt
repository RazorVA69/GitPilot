package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
    var showFindReplace by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    val fileName = remember(filePath) { filePath.substringAfterLast('/') }
    val isMarkdown = remember(fileName) { fileName.endsWith(".md", ignoreCase = true) }
    val isImage = remember(fileName) {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        ext in listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico")
    }

    val lines = remember(content) {
        val count = content.count { it == '\n' } + 1
        (1..count).toList()
    }

    val charCount = remember(content) { content.length }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Md3LightCodeBg)
    ) {
        // Editor Header Toolbar
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val meta = FileIcons.getMeta(fileName, false)
                    Icon(
                        imageVector = meta.icon,
                        contentDescription = null,
                        tint = meta.color,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Md3LightTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isDirty) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(GitHubOrange)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Modified",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GitHubOrange,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = filePath,
                            style = MaterialTheme.typography.labelSmall,
                            color = Md3LightTextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Explorer",
                        tint = Md3LightTextPrimary
                    )
                }
            },
            actions = {
                if (!isImage) {
                    // Find & Replace
                    IconButton(
                        onClick = { showFindReplace = !showFindReplace },
                        modifier = Modifier.testTag("editor_find_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FindReplace,
                            contentDescription = "Find & Replace",
                            tint = if (showFindReplace) Md3LightPrimary else Md3LightTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Markdown Preview toggle
                    if (isMarkdown) {
                        IconButton(
                            onClick = onToggleMarkdownPreview,
                            modifier = Modifier.testTag("editor_markdown_preview_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Preview,
                                contentDescription = "Preview Markdown",
                                tint = if (isMarkdownPreviewMode) Md3LightPrimary else Md3LightTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Undo if dirty
                    if (isDirty) {
                        IconButton(
                            onClick = { onContentChange(originalContent) },
                            modifier = Modifier.testTag("editor_revert_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Undo,
                                contentDescription = "Revert Changes",
                                tint = Md3LightTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Font Zoom
                    IconButton(
                        onClick = { if (fontSize > 9f) fontSize -= 1.5f },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("A-", color = Md3LightTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = { if (fontSize < 24f) fontSize += 1.5f },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("A+", color = Md3LightTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Commit Button
                Button(
                    onClick = onOpenCommitDialog,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDirty) GitHubGreen else Md3LightPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("editor_commit_save_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isDirty) "Commit Changes" else "Commit",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Md3LightSurface,
                titleContentColor = Md3LightTextPrimary
            )
        )

        // Find and Replace Strip
        AnimatedVisibility(visible = showFindReplace) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Md3LightSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, Md3LightOutlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        placeholder = { Text("Find...", fontSize = 12.sp, color = Md3LightTextTertiary) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        textStyle = TextStyle(fontSize = 12.sp, color = Md3LightTextPrimary, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Md3LightPrimary,
                            unfocusedBorderColor = Md3LightOutline
                        )
                    )

                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        placeholder = { Text("Replace with...", fontSize = 12.sp, color = Md3LightTextTertiary) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        textStyle = TextStyle(fontSize = 12.sp, color = Md3LightTextPrimary, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Md3LightPrimary,
                            unfocusedBorderColor = Md3LightOutline
                        )
                    )

                    Button(
                        onClick = {
                            if (findText.isNotEmpty()) {
                                val updated = content.replace(findText, replaceText)
                                onContentChange(updated)
                            }
                        },
                        enabled = findText.isNotEmpty(),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Md3LightPrimary),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Replace All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = { showFindReplace = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Md3LightTextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Editor Body or Preview
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Md3LightPrimary, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading file...", color = Md3LightTextSecondary)
                    }
                }
            } else if (isImage && file?.downloadUrl != null) {
                // Image Viewer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
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
                // Rendered Markdown Preview
                MarkdownPreviewPane(content = content)
            } else {
                // Code Editor with Synchronized Line Numbers Gutter
                val verticalScrollState = rememberScrollState()
                val horizontalScrollState = rememberScrollState()

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScrollState)
                ) {
                    // Line Number Gutter
                    Column(
                        modifier = Modifier
                            .background(Md3LightCodeGutter)
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        for (lineNumber in lines) {
                            Text(
                                text = lineNumber.toString(),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * 1.5).sp,
                                    color = Md3LightTextTertiary,
                                    textAlign = TextAlign.End
                                )
                            )
                        }
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Md3LightCodeBorder)
                    )

                    // Code Input Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScrollState)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = content,
                            onValueChange = onContentChange,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.5).sp,
                                color = Md3LightTextPrimary
                            ),
                            cursorBrush = SolidColor(Md3LightPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("code_editor_text_field")
                        )
                    }
                }
            }
        }

        // Status Bar Footer
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Md3LightSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, Md3LightOutlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${lines.size} lines",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Md3LightTextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "$charCount chars",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Md3LightTextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = FileIcons.formatFileSize(file?.size ?: charCount.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Md3LightTextSecondary,
                        fontSize = 11.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "UTF-8",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Md3LightTextTertiary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "branch: $selectedBranch",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Md3LightPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownPreviewPane(content: String) {
    val scrollState = rememberScrollState()
    val lines = remember(content) { content.lines() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (line in lines) {
            when {
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# "),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Md3LightTextPrimary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    HorizontalDivider(color = Md3LightOutlineVariant)
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Md3LightTextPrimary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                    HorizontalDivider(color = Md3LightOutlineVariant)
                }
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Md3LightTextPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("• ", color = Md3LightPrimary, fontWeight = FontWeight.Bold)
                        Text(line.substring(2), style = MaterialTheme.typography.bodyMedium, color = Md3LightTextPrimary)
                    }
                }
                line.startsWith("```") -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        color = Md3LightSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Md3LightOutlineVariant)
                    ) {
                        Text(
                            text = line,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = GitHubGreen),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                line.isBlank() -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                else -> {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Md3LightTextPrimary
                    )
                }
            }
        }
    }
}
