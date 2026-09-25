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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
    onOpenSearchAcrossFiles: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentFolder = remember(currentFilePath) {
        if (currentFilePath.contains('/')) currentFilePath.substringBeforeLast('/') else ""
    }

    var selectedFolder by remember(currentFolder) { mutableStateOf(currentFolder) }
    var searchQuery by remember { mutableStateOf("") }
    var searchAcrossRepo by remember { mutableStateOf(false) }
    var isPathDropdownExpanded by remember { mutableStateOf(false) }

    // Keep selectedFolder in sync if currentFilePath changes externally
    LaunchedEffect(currentFolder) {
        selectedFolder = currentFolder
    }

    // Extract all unique directory paths from repository tree
    val availableDirectories by remember(allTreeItems) {
        derivedStateOf {
            val set = sortedSetOf<String>(String.CASE_INSENSITIVE_ORDER)
            set.add("") // Root directory
            for (item in allTreeItems) {
                if (item.isDirectory) {
                    set.add(item.path)
                } else if (item.directoryPath.isNotEmpty()) {
                    var p = item.directoryPath
                    while (p.isNotEmpty()) {
                        set.add(p)
                        p = if (p.contains('/')) p.substringBeforeLast('/') else ""
                    }
                }
            }
            set.toList()
        }
    }

    // Files in the currently selected folder
    val currentFolderFiles = remember(selectedFolder, allTreeItems) {
        allTreeItems.filter { item ->
            !item.isDirectory && item.directoryPath == selectedFolder
        }
    }

    // Pinned files across repo
    val pinnedTreeItems = remember(pinnedFiles, allTreeItems) {
        allTreeItems.filter { !it.isDirectory && pinnedFiles.contains(it.path) }
    }

    // Filtered files depending on search scope (selected folder vs across repo)
    val displayedFiles = remember(selectedFolder, allTreeItems, searchQuery, searchAcrossRepo) {
        if (searchAcrossRepo) {
            val allFiles = allTreeItems.filter { !it.isDirectory }
            if (searchQuery.isBlank()) allFiles
            else allFiles.filter {
                it.fileName.contains(searchQuery, ignoreCase = true) ||
                it.path.contains(searchQuery, ignoreCase = true)
            }
        } else {
            if (searchQuery.isBlank()) currentFolderFiles
            else currentFolderFiles.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
        }
    }

    val filteredPinned = remember(pinnedTreeItems, searchQuery, searchAcrossRepo) {
        if (searchAcrossRepo && searchQuery.isNotBlank()) {
            emptyList()
        } else if (searchQuery.isBlank()) pinnedTreeItems
        else pinnedTreeItems.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
    }

    val focusManager = LocalFocusManager.current

    // Scrim overlay
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.zIndex(99f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.50f))
                .clickable {
                    focusManager.clearFocus(force = true)
                    isPathDropdownExpanded = false
                    onClose()
                }
        )
    }

    // Left Drawer Panel (Slides smoothly from left side)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f),
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = modifier
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 290.dp, max = 350.dp)
                    .background(GitSurface),
                color = GitSurface,
                border = BorderStroke(1.dp, GitBorderStrong),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 8.dp, bottom = 8.dp)
                ) {
                    // Header with Folder Dropdown & Close Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                onClick = { isPathDropdownExpanded = !isPathDropdownExpanded },
                                shape = RoundedCornerShape(8.dp),
                                color = GitSurface2,
                                border = BorderStroke(1.dp, if (isPathDropdownExpanded) GitAccent else GitBorder)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(GitAccentSoft, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = GitAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (selectedFolder.isEmpty()) "Root Directory" else selectedFolder.substringAfterLast('/'),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = GitText1,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 12.5.sp
                                        )
                                        Text(
                                            text = if (selectedFolder.isEmpty()) "/" else "/$selectedFolder",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = GitText2,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Folder",
                                        tint = GitText2,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // PATH SELECTOR DROPDOWN MENU
                            DropdownMenu(
                                expanded = isPathDropdownExpanded,
                                onDismissRequest = { isPathDropdownExpanded = false },
                                modifier = Modifier
                                    .background(GitSurface)
                                    .widthIn(min = 260.dp, max = 320.dp)
                                    .heightIn(max = 380.dp)
                            ) {
                                Text(
                                    text = "SELECT REPOSITORY FOLDER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GitText3,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                                HorizontalDivider(color = GitBorder, thickness = 0.5.dp)

                                availableDirectories.forEach { dir ->
                                    val isSelected = dir == selectedFolder
                                    val folderName = if (dir.isEmpty()) "Root Directory" else dir.substringAfterLast('/')

                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Default.FolderOpen else Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = if (isSelected) GitAccent else GitText2,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = folderName,
                                                        color = if (isSelected) GitAccent else GitText1,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = if (dir.isEmpty()) "/" else "/$dir",
                                                        fontFamily = FontFamily.Monospace,
                                                        color = GitText3,
                                                        fontSize = 10.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = GitAccent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedFolder = dir
                                            searchAcrossRepo = false
                                            isPathDropdownExpanded = false
                                        },
                                        colors = MenuDefaults.itemColors(
                                            textColor = GitText1
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

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

                    Spacer(modifier = Modifier.height(6.dp))

                    // Search Scope Toggle (In Folder vs Across Repo)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = { searchAcrossRepo = false },
                            shape = RoundedCornerShape(6.dp),
                            color = if (!searchAcrossRepo) GitAccentSoft else GitSurface2,
                            border = BorderStroke(1.dp, if (!searchAcrossRepo) GitAccent else GitBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 5.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (!searchAcrossRepo) GitAccent else GitText2,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "In Folder",
                                    fontSize = 11.sp,
                                    fontWeight = if (!searchAcrossRepo) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!searchAcrossRepo) GitAccent else GitText2
                                )
                            }
                        }

                        Surface(
                            onClick = { searchAcrossRepo = true },
                            shape = RoundedCornerShape(6.dp),
                            color = if (searchAcrossRepo) GitAccentSoft else GitSurface2,
                            border = BorderStroke(1.dp, if (searchAcrossRepo) GitAccent else GitBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 5.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TravelExplore,
                                    contentDescription = null,
                                    tint = if (searchAcrossRepo) GitAccent else GitText2,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Across Repo",
                                    fontSize = 11.sp,
                                    fontWeight = if (searchAcrossRepo) FontWeight.Bold else FontWeight.Normal,
                                    color = if (searchAcrossRepo) GitAccent else GitText2
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Search Filter Input Box
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = GitAppBg,
                        border = BorderStroke(1.dp, if (searchQuery.isNotEmpty()) GitAccent else GitBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (searchAcrossRepo) Icons.Default.TravelExplore else Icons.Default.Search,
                                contentDescription = null,
                                tint = if (searchQuery.isNotEmpty()) GitAccent else GitText2,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = if (searchAcrossRepo) "Find file across repo by name..." else "Filter files in this folder...",
                                        color = GitText3,
                                        fontSize = 12.sp
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    textStyle = TextStyle(
                                        color = GitText1,
                                        fontSize = 12.sp
                                    ),
                                    cursorBrush = SolidColor(GitAccent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("folder_drawer_search_input")
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = GitText2,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Optional Shortcut to Full-Content Search across files
                    if (onOpenSearchAcrossFiles != null) {
                        Surface(
                            onClick = onOpenSearchAcrossFiles,
                            color = GitSurface2,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, GitBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = GitAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Search file contents across repo...",
                                    fontSize = 11.sp,
                                    color = GitText1,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
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
                                    showPath = searchAcrossRepo,
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

                        // SIBLING FILES IN FOLDER OR ALL REPO FILES
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(GitAppBg)
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (searchAcrossRepo) Icons.Default.TravelExplore else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = GitText2,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (searchAcrossRepo) "ALL REPO FILES (${displayedFiles.size})" else "FILES IN THIS FOLDER (${displayedFiles.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GitText2,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        if (displayedFiles.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotEmpty()) "No files match '$searchQuery'" else if (searchAcrossRepo) "No files found in repo" else "No files in this folder",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GitText3,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            items(displayedFiles, key = { it.path }) { item ->
                                val isActive = item.path == currentFilePath
                                val isPinned = pinnedFiles.contains(item.path)
                                val matchingTab = openTabs.find { it.path == item.path }
                                val isDirty = matchingTab?.isDirty == true

                                FolderFileItemRow(
                                    item = item,
                                    isActive = isActive,
                                    isDirty = isDirty,
                                    isPinned = isPinned,
                                    showPath = searchAcrossRepo || item.directoryPath != selectedFolder,
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
}

@Composable
private fun FolderFileItemRow(
    item: GitTreeItem,
    isActive: Boolean,
    isDirty: Boolean,
    isPinned: Boolean,
    showPath: Boolean = false,
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

                    if (showPath && item.directoryPath.isNotEmpty()) {
                        Text(
                            text = "/${item.directoryPath}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = GitText3,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (item.size != null) {
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
