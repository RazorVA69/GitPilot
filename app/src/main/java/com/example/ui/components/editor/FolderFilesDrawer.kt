package com.example.ui.components.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GitTreeItem
import com.example.ui.components.FileIconForExtension
import com.example.ui.components.FileIcons
import com.example.ui.theme.GitAccent
import com.example.ui.theme.GitAccentSoft
import com.example.ui.theme.GitAppBg
import com.example.ui.theme.GitBorder
import com.example.ui.theme.GitBorderStrong
import com.example.ui.theme.GitSurface
import com.example.ui.theme.GitSurface2
import com.example.ui.theme.GitText1
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3
import com.example.ui.theme.GitYellow
import com.example.ui.viewmodel.EditorTabInfo

@Composable
fun FolderFilesDrawer(
    isOpen: Boolean,
    currentFilePath: String,
    allTreeItems: List<GitTreeItem>,
    openTabs: List<EditorTabInfo>,
    pinnedFiles: Set<String>,
    onSelectFile: (GitTreeItem) -> Unit,
    onTogglePinFile: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val currentFolder = remember(currentFilePath) {
        if (currentFilePath.contains('/')) currentFilePath.substringBeforeLast('/') else ""
    }

    // Sibling files in the same folder
    val siblingFiles = remember(currentFolder, allTreeItems) {
        allTreeItems.filter { item ->
            !item.isDirectory && item.directoryPath == currentFolder
        }
    }

    // Pinned files across repo
    val pinnedTreeItems = remember(pinnedFiles, allTreeItems) {
        allTreeItems.filter { !it.isDirectory && pinnedFiles.contains(it.path) }
    }

    val filteredSiblings = remember(siblingFiles, searchQuery) {
        if (searchQuery.isBlank()) siblingFiles
        else siblingFiles.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
    }

    val filteredPinned = remember(pinnedTreeItems, searchQuery) {
        if (searchQuery.isBlank()) pinnedTreeItems
        else pinnedTreeItems.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
    }

    // Scrim overlay
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onClose() }
        )
    }

    // Left Drawer Panel
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 280.dp, max = 340.dp)
                .background(GitSurface),
            color = GitSurface,
            border = BorderStroke(1.dp, GitBorderStrong),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(GitAccentSoft, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = GitAccent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (currentFolder.isEmpty()) "Root Directory" else currentFolder.substringAfterLast('/'),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = GitText1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (currentFolder.isEmpty()) "/" else "/$currentFolder",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = GitText2,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = GitText2,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Filter Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Filter files in folder...",
                            color = GitText3,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = GitText2,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = GitText2,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(44.dp)
                        .testTag("folder_drawer_search_input"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GitAppBg,
                        unfocusedContainerColor = GitAppBg,
                        focusedBorderColor = GitAccent,
                        unfocusedBorderColor = GitBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = GitBorder, thickness = 0.5.dp)

                // Files List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // PINNED FILES SECTION
                    if (filteredPinned.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(GitAppBg)
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = GitAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PINNED FILES (${filteredPinned.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GitAccent,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        items(filteredPinned, key = { "pinned_${it.path}" }) { item ->
                            val isActive = item.path == currentFilePath
                            val matchingTab = openTabs.find { it.path == item.path }
                            val isDirty = matchingTab?.isDirty == true

                            FolderFileItemRow(
                                item = item,
                                isActive = isActive,
                                isDirty = isDirty,
                                isPinned = true,
                                onSelect = {
                                    onSelectFile(item)
                                    onClose()
                                },
                                onTogglePin = { onTogglePinFile(item.path) }
                            )
                        }

                        item {
                            HorizontalDivider(
                                color = GitBorder,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    // SIBLING FILES IN FOLDER
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GitAppBg)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = GitText2,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "FILES IN THIS FOLDER (${filteredSiblings.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = GitText2,
                                letterSpacing = 0.8.sp,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    if (filteredSiblings.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No files match '$searchQuery'" else "No other files in this folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GitText3,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        items(filteredSiblings, key = { it.path }) { item ->
                            val isActive = item.path == currentFilePath
                            val isPinned = pinnedFiles.contains(item.path)
                            val matchingTab = openTabs.find { it.path == item.path }
                            val isDirty = matchingTab?.isDirty == true

                            FolderFileItemRow(
                                item = item,
                                isActive = isActive,
                                isDirty = isDirty,
                                isPinned = isPinned,
                                onSelect = {
                                    onSelectFile(item)
                                    onClose()
                                },
                                onTogglePin = { onTogglePinFile(item.path) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderFileItemRow(
    item: GitTreeItem,
    isActive: Boolean,
    isDirty: Boolean,
    isPinned: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit
) {
    val ext = item.fileName.substringAfterLast('.', "")

    Surface(
        onClick = onSelect,
        color = if (isActive) GitAccentSoft else GitSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                FileIconForExtension(
                    extension = ext,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) GitAccent else GitText1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.5.sp
                        )

                        if (isDirty) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(GitYellow, CircleShape)
                            )
                        }
                    }

                    if (item.size != null) {
                        Text(
                            text = FileIcons.formatFileSize(item.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = GitText3,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(GitAccent)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "OPEN",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) "Unpin" else "Pin",
                        tint = if (isPinned) GitAccent else GitText3,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
