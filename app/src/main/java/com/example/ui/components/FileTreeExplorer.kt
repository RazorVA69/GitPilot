package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.draw.rotate
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
import com.example.ui.viewmodel.SyncStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTreeExplorer(
    repo: GitHubRepository?,
    selectedBranch: String,
    currentPath: String,
    rootNode: ExplorerNode?,
    rawTreeItems: List<GitTreeItem>,
    isLoadingTree: Boolean,
    syncStatus: SyncStatus = SyncStatus.IDLE,
    lastSyncedAt: Long? = null,
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
    onToggleSelectFolder: (String) -> Unit,
    onSelectAllInDir: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenBatchDeleteModal: () -> Unit,
    onDeleteSingleFile: (path: String, sha: String) -> Unit,
    onToggleLeftDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearchExpanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Md3LightBackground)
    ) {
        // Main Top Bar
        TopAppBar(
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = repo?.name ?: "File Explorer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Md3LightTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        // Branch Chip
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
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Branch",
                                    tint = Md3LightPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Live Sync Status Pill
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onRefresh),
                            color = when (syncStatus) {
                                SyncStatus.SYNCING -> Md3LightSurfaceVariant
                                SyncStatus.SYNCED -> Color(0xFFE8F5E9)
                                SyncStatus.ERROR -> Color(0xFFFFEBEE)
                                SyncStatus.IDLE -> Md3LightSurfaceVariant
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (syncStatus == SyncStatus.SYNCING || isLoadingTree) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Syncing",
                                        tint = GitHubBlue,
                                        modifier = Modifier
                                            .size(11.dp)
                                            .rotate(syncRotation)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Syncing...",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GitHubBlue
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                color = if (syncStatus == SyncStatus.ERROR) Md3LightError else Color(0xFF2E7D32),
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = formatSyncTime(lastSyncedAt),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (syncStatus == SyncStatus.ERROR) Md3LightError else Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onToggleLeftDrawer,
                    modifier = Modifier.testTag("explorer_drawer_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Repositories Menu",
                        tint = Md3LightTextPrimary
                    )
                }
            },
            actions = {
                // Back to Repositories
                IconButton(onClick = onNavigateToReposList) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Repositories",
                        tint = Md3LightTextSecondary
                    )
                }

                // Search Files in Tree
                IconButton(
                    onClick = { isSearchExpanded = !isSearchExpanded },
                    modifier = Modifier.testTag("explorer_search_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search files",
                        tint = if (isSearchExpanded) Md3LightPrimary else Md3LightTextSecondary
                    )
                }

                // Multi-select Batch Mode Toggle
                IconButton(
                    onClick = onToggleBatchMode,
                    modifier = Modifier.testTag("explorer_batch_mode_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = "Batch selection mode",
                        tint = if (isBatchMode) Md3LightPrimary else Md3LightTextSecondary
                    )
                }

                // Add File / Folder / Upload Button
                IconButton(
                    onClick = onOpenNewFileDialog,
                    modifier = Modifier.testTag("explorer_add_file_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add or Upload",
                        tint = GitHubGreen
                    )
                }

                // Refresh / Sync
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh tree",
                        tint = Md3LightTextSecondary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Md3LightSurface,
                titleContentColor = Md3LightTextPrimary
            )
        )

        HorizontalDivider(color = Md3LightOutlineVariant, thickness = 0.5.dp)

        // Search Bar (if expanded)
        AnimatedVisibility(visible = isSearchExpanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Md3LightSurfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tree_search_input"),
                        placeholder = { Text("Search files by name or path...", fontSize = 13.sp) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Md3LightTextSecondary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Md3LightPrimary,
                            unfocusedBorderColor = Md3LightOutline
                        )
                    )
                }
            }
        }

        // Breadcrumb Navigation Path Bar
        BreadcrumbBar(
            repoName = repo?.name ?: "Repository",
            currentPath = currentPath,
            onNavigateToDir = onNavigateToDir
        )

        // Batch Mode Action Bar
        AnimatedVisibility(visible = isBatchMode) {
            BatchActionBar(
                selectedCount = selectedFilePaths.size,
                onSelectAll = onSelectAllInDir,
                onClear = onClearSelection,
                onDelete = onOpenBatchDeleteModal
            )
        }

        // Main Explorer Content List
        Box(modifier = Modifier.weight(1f)) {
            if (isLoadingTree && rawTreeItems.isEmpty()) {
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
                            text = "Loading repository files...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Md3LightTextSecondary
                        )
                    }
                }
            } else if (searchQuery.isNotBlank()) {
                // Search Results Flat List
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
                // Directory Node Children List (Files & Folders)
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
                                Text("Upload / Create File Here", fontWeight = FontWeight.Bold)
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

                            // Check if this file or folder is selected
                            val isSelected = if (childNode.isDirectory) {
                                selectedFilePaths.contains(childNode.path) ||
                                        (rawTreeItems.any { it.path.startsWith("${childNode.path}/") } &&
                                                rawTreeItems.filter { it.path.startsWith("${childNode.path}/") }.all { selectedFilePaths.contains(it.path) })
                            } else {
                                selectedFilePaths.contains(childNode.path)
                            }

                            ExplorerItemRow(
                                node = childNode,
                                rawItem = rawItem,
                                isBatchMode = isBatchMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isBatchMode) {
                                        if (childNode.isDirectory) {
                                            onToggleSelectFolder(childNode.path)
                                        } else {
                                            onToggleSelectFile(childNode.path)
                                        }
                                    } else {
                                        if (childNode.isDirectory) {
                                            onNavigateToDir(childNode.path)
                                        } else {
                                            onOpenFile(rawItem)
                                        }
                                    }
                                },
                                onToggleSelect = {
                                    if (childNode.isDirectory) {
                                        onToggleSelectFolder(childNode.path)
                                    } else {
                                        onToggleSelectFile(childNode.path)
                                    }
                                },
                                onDelete = { onDeleteSingleFile(childNode.path, childNode.sha) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSyncTime(timestamp: Long?): String {
    if (timestamp == null) return "Auto Sync"
    val diff = (System.currentTimeMillis() - timestamp) / 1000
    return when {
        diff < 60 -> "Synced just now"
        diff < 3600 -> "Synced ${diff / 60}m ago"
        else -> "Synced"
    }
}

@Composable
private fun BreadcrumbBar(
    repoName: String,
    currentPath: String,
    onNavigateToDir: (String) -> Unit
) {
    val segments = remember(currentPath) {
        if (currentPath.isBlank()) emptyList()
        else currentPath.split('/')
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Md3LightSurface,
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Root Icon & Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onNavigateToDir("") }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Root",
                    tint = GitHubYellow,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = repoName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (segments.isEmpty()) Md3LightTextPrimary else Md3LightPrimary
                )
            }

            // Subdirectories breadcrumbs
            var accumulated = ""
            for (i in segments.indices) {
                val seg = segments[i]
                accumulated = if (accumulated.isEmpty()) seg else "$accumulated/$seg"
                val dirTarget = accumulated
                val isLast = i == segments.size - 1

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Md3LightTextTertiary,
                    modifier = Modifier.size(14.dp)
                )

                Text(
                    text = seg,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                    color = if (isLast) Md3LightTextPrimary else Md3LightPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onNavigateToDir(dirTarget) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
    HorizontalDivider(color = Md3LightOutlineVariant, thickness = 0.5.dp)
}

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Md3LightSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, Md3LightOutlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                Spacer(modifier = Modifier.width(16.dp))
                TextButton(onClick = onSelectAll) {
                    Text("Select All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onClear) {
                    Text("Clear", fontSize = 12.sp, color = Md3LightTextSecondary)
                }
            }

            Button(
                onClick = onDelete,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Md3LightError,
                    disabledContainerColor = Md3LightOutlineVariant
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete ($selectedCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("explorer_item_${node.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isBatchMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Md3LightPrimary,
                    uncheckedColor = Md3LightOutline
                ),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Icon
        if (node.isDirectory) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Directory",
                tint = GitHubYellow,
                modifier = Modifier.size(24.dp)
            )
        } else {
            FileIconForExtension(
                extension = node.extension.ifBlank { rawItem.extension },
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Name & Meta Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (node.isDirectory) FontWeight.Bold else FontWeight.Medium,
                color = Md3LightTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (node.isDirectory) {
                    val count = node.children.size
                    Text(
                        text = "$count ${if (count == 1) "item" else "items"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Md3LightTextSecondary
                    )
                } else {
                    val sizeFormatted = formatFileSize(node.size ?: rawItem.size ?: 0L)
                    Text(
                        text = sizeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = Md3LightTextSecondary
                    )
                    if (rawItem.sha.length >= 7) {
                        Text(
                            text = " • ${rawItem.sha.take(7)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Md3LightTextTertiary
                        )
                    }
                }
            }
        }

        // Trailing affordance
        if (node.isDirectory) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Md3LightTextTertiary,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Md3LightTextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open File") },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete File", color = Md3LightError) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = if (isBatchMode) 68.dp else 48.dp),
        color = Md3LightOutlineVariant.copy(alpha = 0.5f),
        thickness = 0.5.dp
    )
}

@Composable
private fun SearchResultsList(
    items: List<GitTreeItem>,
    searchQuery: String,
    isBatchMode: Boolean,
    selectedFilePaths: Set<String>,
    onOpenFile: (GitTreeItem) -> Unit,
    onToggleSelect: (String) -> Unit,
    onDeleteSingle: (String, String) -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Md3LightTextTertiary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No files match \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Md3LightTextSecondary
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            items(items, key = { it.path }) { item ->
                val node = ExplorerNode(
                    path = item.path,
                    name = item.fileName,
                    isDirectory = false,
                    sha = item.sha,
                    size = item.size,
                    extension = item.extension
                )

                ExplorerItemRow(
                    node = node,
                    rawItem = item,
                    isBatchMode = isBatchMode,
                    isSelected = selectedFilePaths.contains(item.path),
                    onClick = {
                        if (isBatchMode) onToggleSelect(item.path)
                        else onOpenFile(item)
                    },
                    onToggleSelect = { onToggleSelect(item.path) },
                    onDelete = { onDeleteSingle(item.path, item.sha) }
                )
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val group = digitGroups.coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, group.toDouble())
    return String.format("%.1f %s", value, units[group]).replace(".0 ", " ")
}
