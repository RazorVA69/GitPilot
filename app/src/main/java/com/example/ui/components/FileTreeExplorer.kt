package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExplorerNode
import com.example.data.model.GitHubRepository
import com.example.data.model.GitTreeItem
import com.example.data.repository.GitHubRepository as RepoRepo
import com.example.ui.theme.GitHubBlue
import com.example.ui.theme.GitHubDarkBorder
import com.example.ui.theme.GitHubDarkCodeBg
import com.example.ui.theme.GitHubDarkSurface
import com.example.ui.theme.GitHubDarkSurfaceVariant
import com.example.ui.theme.GitHubDarkTextMuted
import com.example.ui.theme.GitHubDarkTextSecondary
import com.example.ui.theme.GitHubGreenBright
import com.example.ui.theme.GitHubRed
import com.example.ui.theme.GitHubYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTreeExplorer(
    repo: GitHubRepository?,
    selectedBranch: String,
    currentPath: String,
    rootNode: ExplorerNode?,
    rawTreeItems: List<GitTreeItem>,
    isLoadingTree: Boolean,
    searchQuery: String,
    matchingSearchFiles: List<GitTreeItem>,
    isBatchMode: Boolean,
    selectedFilePaths: Set<String>,
    onBranchClick: () -> Unit,
    onNavigateToDir: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFile: (GitTreeItem) -> Unit,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenNewFileDialog: () -> Unit,
    onToggleBatchMode: () -> Unit,
    onToggleSelectFile: (String) -> Unit,
    onSelectAllInDir: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenBatchDeleteModal: () -> Unit,
    onDeleteSingleFile: (path: String, sha: String) -> Unit,
    onToggleSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearchExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main Top Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = repo?.name ?: "Select a Repository",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (repo != null) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onBranchClick)
                                .testTag("branch_badge_btn"),
                            color = GitHubDarkSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedBranch,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = GitHubBlue,
                                    fontSize = 11.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Branch",
                                    tint = GitHubBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            },
            actions = {
                // Search toggle
                IconButton(
                    onClick = {
                        isSearchExpanded = !isSearchExpanded
                        if (!isSearchExpanded) onSearchChange("")
                    },
                    modifier = Modifier.testTag("file_search_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search Files",
                        tint = if (isSearchExpanded || searchQuery.isNotEmpty()) GitHubBlue else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Batch Mode toggle
                IconButton(
                    onClick = onToggleBatchMode,
                    modifier = Modifier.testTag("batch_mode_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = "Batch Actions",
                        tint = if (isBatchMode) GitHubGreenBright else MaterialTheme.colorScheme.onSurface
                    )
                }

                // New file / folder upload
                IconButton(
                    onClick = onOpenNewFileDialog,
                    modifier = Modifier.testTag("new_file_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New File / Upload",
                        tint = GitHubGreenBright
                    )
                }

                // Refresh tree
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_tree_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Right Sidebar Toggle (Repo Drawer)
                IconButton(
                    onClick = onToggleSidebar,
                    modifier = Modifier.testTag("toggle_repo_sidebar_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Repositories Sidebar",
                        tint = GitHubBlue
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Instant Global File Search Field
        AnimatedVisibility(
            visible = isSearchExpanded || searchQuery.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("instant_file_search_input"),
                placeholder = {
                    Text(
                        "Search all files in repository (e.g. MainActivity, .kt, package.json)...",
                        style = MaterialTheme.typography.bodySmall,
                        color = GitHubDarkTextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = GitHubBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = GitHubDarkTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GitHubBlue,
                    unfocusedBorderColor = GitHubDarkBorder,
                    focusedContainerColor = GitHubDarkSurfaceVariant,
                    unfocusedContainerColor = GitHubDarkSurfaceVariant
                )
            )
        }

        // Breadcrumbs Bar (only when not searching globally)
        if (searchQuery.isBlank() && repo != null) {
            BreadcrumbBar(
                repoName = repo.name,
                currentPath = currentPath,
                onNavigateToDir = onNavigateToDir,
                onNavigateUp = onNavigateUp
            )
        }

        // Batch Mode Action Bar
        if (isBatchMode) {
            BatchActionBar(
                selectedCount = selectedFilePaths.size,
                onSelectAll = onSelectAllInDir,
                onClear = onClearSelection,
                onDeleteSelected = onOpenBatchDeleteModal
            )
        }

        // Content Area
        Box(modifier = Modifier.weight(1f)) {
            if (isLoadingTree) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = GitHubBlue,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading repository tree...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GitHubDarkTextSecondary
                        )
                    }
                }
            } else if (repo == null) {
                // Empty Welcome State
                EmptyRepoWelcomeState(onToggleSidebar = onToggleSidebar)
            } else if (searchQuery.isNotBlank()) {
                // Search Results across entire tree
                SearchResultsList(
                    items = matchingSearchFiles,
                    searchQuery = searchQuery,
                    isBatchMode = isBatchMode,
                    selectedFilePaths = selectedFilePaths,
                    onOpenFile = onOpenFile,
                    onToggleSelect = onToggleSelectFile,
                    onDeleteSingle = onDeleteSingleFile
                )
            } else {
                // Current Directory Node Children
                val currentNode = rootNode?.let { RepoRepo.findNodeAtDirectory(it, currentPath) }
                if (currentNode == null || currentNode.children.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Empty Directory",
                                tint = GitHubDarkTextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (rawTreeItems.isEmpty()) "This repository is empty" else "Directory is empty",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GitHubDarkTextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onOpenNewFileDialog,
                                colors = ButtonDefaults.buttonColors(containerColor = GitHubGreenBright),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create File Here")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 72.dp)
                    ) {
                        items(currentNode.children, key = { it.path }) { childNode ->
                            val rawItem = rawTreeItems.firstOrNull { it.path == childNode.path }
                                ?: GitTreeItem(
                                    path = childNode.path,
                                    type = if (childNode.isDirectory) "tree" else "blob",
                                    sha = childNode.sha,
                                    size = childNode.size
                                )

                            ExplorerItemRow(
                                node = childNode,
                                rawItem = rawItem,
                                isBatchMode = isBatchMode,
                                isSelected = selectedFilePaths.contains(childNode.path),
                                onClick = {
                                    if (childNode.isDirectory) {
                                        onNavigateToDir(childNode.path)
                                    } else {
                                        if (isBatchMode) {
                                            onToggleSelectFile(childNode.path)
                                        } else {
                                            onOpenFile(rawItem)
                                        }
                                    }
                                },
                                onToggleSelect = { onToggleSelectFile(childNode.path) },
                                onDelete = { onDeleteSingleFile(childNode.path, childNode.sha) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbBar(
    repoName: String,
    currentPath: String,
    onNavigateToDir: (String) -> Unit,
    onNavigateUp: () -> Unit
) {
    val scrollState = rememberScrollState()
    val segments = remember(currentPath) {
        if (currentPath.isBlank()) emptyList() else currentPath.split('/')
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Root repo chip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onNavigateToDir("") }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Root",
                    tint = GitHubYellow,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = repoName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (segments.isEmpty()) GitHubBlue else MaterialTheme.colorScheme.onSurface
                )
            }

            // Path segments
            var accumulatedPath = ""
            for ((index, segment) in segments.withIndex()) {
                accumulatedPath = if (accumulatedPath.isEmpty()) segment else "$accumulatedPath/$segment"
                val thisPath = accumulatedPath
                val isLast = index == segments.size - 1

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "/",
                    tint = GitHubDarkTextMuted,
                    modifier = Modifier.size(14.dp)
                )

                Text(
                    text = segment,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    color = if (isLast) GitHubBlue else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onNavigateToDir(thisPath) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GitHubDarkSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, GitHubDarkBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = GitHubBlue
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onSelectAll) {
                    Text("Select All", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onClear) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
            }

            Button(
                onClick = onDeleteSelected,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GitHubRed,
                    disabledContainerColor = GitHubDarkBorder
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("batch_delete_action_btn")
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete ($selectedCount)", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ExplorerItemRow(
    node: ExplorerNode,
    rawItem: GitTreeItem,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val meta = FileIcons.getMeta(node.name, node.isDirectory)
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("file_row_${node.name}"),
        color = if (isSelected) GitHubBlue.copy(alpha = 0.12f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Batch Mode Checkbox
            if (isBatchMode && !node.isDirectory) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = GitHubBlue,
                        uncheckedColor = GitHubDarkBorder
                    ),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // File/Folder Icon
            Icon(
                imageVector = meta.icon,
                contentDescription = meta.label,
                tint = meta.color,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // File Name & Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (node.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (node.isDirectory) {
                        Text(
                            text = "${node.children.size} items",
                            style = MaterialTheme.typography.labelSmall,
                            color = GitHubDarkTextMuted
                        )
                    } else {
                        Text(
                            text = FileIcons.formatFileSize(node.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = GitHubDarkTextMuted
                        )
                        if (node.sha.isNotBlank()) {
                            Text(
                                text = "• ${node.sha.take(7)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = GitHubDarkTextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Directory Chevron or File Actions Menu
            if (node.isDirectory) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open Directory",
                    tint = GitHubDarkTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = GitHubDarkTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(GitHubDarkSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open / Edit", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showMenu = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete File", color = GitHubRed) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = GitHubRed, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(color = GitHubDarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
}

@Composable
private fun SearchResultsList(
    items: List<GitTreeItem>,
    searchQuery: String,
    isBatchMode: Boolean,
    selectedFilePaths: Set<String>,
    onOpenFile: (GitTreeItem) -> Unit,
    onToggleSelect: (String) -> Unit,
    onDeleteSingle: (path: String, sha: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = GitHubDarkSurfaceVariant
        ) {
            Text(
                text = "Found ${items.size} matching files in repository",
                style = MaterialTheme.typography.labelSmall,
                color = GitHubDarkTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No files match \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GitHubDarkTextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 72.dp)
            ) {
                items(items, key = { it.path }) { item ->
                    val isSelected = selectedFilePaths.contains(item.path)
                    val meta = FileIcons.getMeta(item.fileName, false)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isBatchMode) {
                                    onToggleSelect(item.path)
                                } else {
                                    onOpenFile(item)
                                }
                            },
                        color = if (isSelected) GitHubBlue.copy(alpha = 0.12f) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isBatchMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleSelect(item.path) },
                                    colors = CheckboxDefaults.colors(checkedColor = GitHubBlue),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Icon(
                                imageVector = meta.icon,
                                contentDescription = meta.label,
                                tint = meta.color,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GitHubDarkTextMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = FileIcons.formatFileSize(item.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = GitHubDarkTextMuted
                            )
                        }
                    }
                    HorizontalDivider(color = GitHubDarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun EmptyRepoWelcomeState(
    onToggleSidebar: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = GitHubBlue,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Fast GitHub File Explorer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select a repository from the right sidebar to browse folders, edit files, upload directories, and commit changes instantly.",
                style = MaterialTheme.typography.bodyMedium,
                color = GitHubDarkTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onToggleSidebar,
                colors = ButtonDefaults.buttonColors(containerColor = GitHubBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("welcome_open_sidebar_btn")
            ) {
                Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Repositories Sidebar")
            }
        }
    }
}
