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
import com.example.ui.theme.GitHubGreen
import com.example.ui.theme.GitHubYellow
import com.example.ui.theme.Md3LightBackground
import com.example.ui.theme.Md3LightError
import com.example.ui.theme.Md3LightOutline
import com.example.ui.theme.Md3LightOutlineVariant
import com.example.ui.theme.Md3LightPrimary
import com.example.ui.theme.Md3LightPrimaryContainer
import com.example.ui.theme.Md3LightSurface
import com.example.ui.theme.Md3LightSurfaceVariant
import com.example.ui.theme.Md3LightTextPrimary
import com.example.ui.theme.Md3LightTextSecondary
import com.example.ui.theme.Md3LightTextTertiary

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
    onNavigateToReposList: () -> Unit,
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
    onToggleLeftDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearchExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Md3LightBackground)
    ) {
        // Main Top Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = repo?.name ?: "File Explorer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Md3LightTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (repo != null) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onBranchClick)
                                .testTag("branch_badge_btn"),
                            color = Md3LightPrimaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedBranch,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = Md3LightPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Branch",
                                    tint = Md3LightPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onToggleLeftDrawer,
                    modifier = Modifier.testTag("explorer_open_left_drawer_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Left Repositories Sidebar",
                        tint = Md3LightPrimary
                    )
                }
            },
            actions = {
                // Back to Repos List button
                IconButton(
                    onClick = onNavigateToReposList,
                    modifier = Modifier.testTag("back_to_repos_list_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "All Repos",
                        tint = Md3LightTextSecondary
                    )
                }

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
                        tint = if (isSearchExpanded || searchQuery.isNotEmpty()) Md3LightPrimary else Md3LightTextSecondary
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
                        tint = if (isBatchMode) GitHubGreen else Md3LightTextSecondary
                    )
                }

                // New file / upload
                IconButton(
                    onClick = onOpenNewFileDialog,
                    modifier = Modifier.testTag("new_file_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New File / Upload",
                        tint = GitHubGreen
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
                        tint = Md3LightTextSecondary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Md3LightSurface,
                titleContentColor = Md3LightTextPrimary
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
                        "Search all files in repository (e.g. MainActivity, .kt)...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Md3LightTextTertiary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Md3LightPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Md3LightTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Md3LightPrimary,
                    unfocusedBorderColor = Md3LightOutline,
                    focusedContainerColor = Md3LightSurface,
                    unfocusedContainerColor = Md3LightSurface
                )
            )
        }

        // Breadcrumbs Bar
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
                            color = Md3LightPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading repository file tree...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Md3LightTextSecondary
                        )
                    }
                }
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
                                tint = Md3LightTextTertiary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (rawTreeItems.isEmpty()) "This repository is empty" else "Directory is empty",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Md3LightTextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onOpenNewFileDialog,
                                colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create File Here", fontWeight = FontWeight.Bold)
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
        color = Md3LightSurfaceVariant,
        shadowElevation = 1.dp
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
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onNavigateToDir("") }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
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
                    color = if (segments.isEmpty()) Md3LightPrimary else Md3LightTextPrimary
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
                    tint = Md3LightTextTertiary,
                    modifier = Modifier.size(14.dp)
                )

                Text(
                    text = segment,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    color = if (isLast) Md3LightPrimary else Md3LightTextPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onNavigateToDir(thisPath) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
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
        color = Md3LightSurface,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Md3LightOutlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Md3LightPrimary
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
                    containerColor = Md3LightError,
                    disabledContainerColor = Md3LightOutlineVariant
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("batch_delete_action_btn")
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete ($selectedCount)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
        color = if (isSelected) Md3LightPrimaryContainer else Md3LightSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Batch Checkbox
            if (isBatchMode && !node.isDirectory) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(checkedColor = Md3LightPrimary),
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
                    color = Md3LightTextPrimary,
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
                            color = Md3LightTextSecondary
                        )
                    } else {
                        Text(
                            text = FileIcons.formatFileSize(node.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = Md3LightTextSecondary
                        )
                        if (node.sha.isNotBlank()) {
                            Text(
                                text = "• ${node.sha.take(7)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Md3LightTextTertiary,
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
                    tint = Md3LightTextTertiary,
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
                            tint = Md3LightTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Md3LightSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open / Edit", color = Md3LightTextPrimary) },
                            onClick = {
                                showMenu = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete File", color = Md3LightError) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Md3LightError, modifier = Modifier.size(18.dp))
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
    HorizontalDivider(color = Md3LightOutlineVariant.copy(alpha = 0.6f), thickness = 0.5.dp)
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
            color = Md3LightSurfaceVariant
        ) {
            Text(
                text = "Found ${items.size} matching files in repository",
                style = MaterialTheme.typography.labelSmall,
                color = Md3LightTextSecondary,
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
                    color = Md3LightTextSecondary
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
                        color = if (isSelected) Md3LightPrimaryContainer else Md3LightSurface
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
                                    colors = CheckboxDefaults.colors(checkedColor = Md3LightPrimary),
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
                                    color = Md3LightTextPrimary
                                )
                                Text(
                                    text = item.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Md3LightTextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = FileIcons.formatFileSize(item.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = Md3LightTextSecondary
                            )
                        }
                    }
                    HorizontalDivider(color = Md3LightOutlineVariant.copy(alpha = 0.6f), thickness = 0.5.dp)
                }
            }
        }
    }
}
