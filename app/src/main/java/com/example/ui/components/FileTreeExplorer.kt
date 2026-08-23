package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.DisposableEffect
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
import com.example.ui.theme.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.Folder
import com.example.ui.viewmodel.ClipboardState
import com.example.ui.viewmodel.FileTreeSortOption
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
    pinnedFolders: Set<String> = emptySet(),
    fileTreeSortOption: FileTreeSortOption = FileTreeSortOption.FOLDERS_FIRST,
    isFileTreeSortReversed: Boolean = false,
    clipboard: ClipboardState? = null,
    isPasting: Boolean = false,
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
    onCutItem: (path: String, isDirectory: Boolean, sha: String) -> Unit = { _, _, _ -> },
    onCopyItem: (path: String, isDirectory: Boolean, sha: String) -> Unit = { _, _, _ -> },
    onCutSelection: () -> Unit = {},
    onCopySelection: () -> Unit = {},
    onClearClipboard: () -> Unit = {},
    onPasteClipboard: (destinationDir: String) -> Unit = {},
    onTogglePinFolder: (String) -> Unit = {},
    onFileTreeSortChange: (FileTreeSortOption) -> Unit = {},
    onToggleFileTreeSortReverse: () -> Unit = {},
    onToggleLeftDrawer: () -> Unit,
    onOpenTerminal: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var folderForActionDialog by remember { mutableStateOf<ExplorerNode?>(null) }

    // Scroll state memory per directory path
    val folderScrollPositions = remember { mutableMapOf<String, Pair<Int, Int>>() }
    val folderListState = remember(currentPath) {
        LazyListState(
            firstVisibleItemIndex = folderScrollPositions[currentPath]?.first ?: 0,
            firstVisibleItemScrollOffset = folderScrollPositions[currentPath]?.second ?: 0
        )
    }

    DisposableEffect(currentPath) {
        onDispose {
            folderScrollPositions[currentPath] = Pair(
                folderListState.firstVisibleItemIndex,
                folderListState.firstVisibleItemScrollOffset
            )
        }
    }

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
            .background(GitSurface)
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
                            color = GitText1,
                            fontSize = 17.sp,
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
                                .height(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onBranchClick)
                                .testTag("branch_badge_btn"),
                            shape = RoundedCornerShape(6.dp),
                            color = GitTopBarButtonBg,
                            border = BorderStroke(1.dp, GitBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(GitText2, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = selectedBranch,
                                    fontFamily = FontFamily.Monospace,
                                    color = GitText1,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 85.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Branch",
                                    tint = GitText2,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Live Sync Status Pill
                        Surface(
                            modifier = Modifier
                                .height(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onRefresh),
                            shape = RoundedCornerShape(6.dp),
                            color = if (syncStatus == SyncStatus.ERROR) Color(0xFFFFEBEE) else GitAccentSoft,
                            border = BorderStroke(1.dp, if (syncStatus == SyncStatus.ERROR) Md3LightError.copy(alpha = 0.35f) else GitAccent.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (syncStatus == SyncStatus.SYNCING || isLoadingTree) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Syncing",
                                        tint = GitAccent,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .rotate(syncRotation)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Syncing",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GitAccent
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(
                                                color = if (syncStatus == SyncStatus.ERROR) Md3LightError else GitAccent,
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (syncStatus == SyncStatus.ERROR) "Sync error" else "Synced",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (syncStatus == SyncStatus.ERROR) Md3LightError else GitAccent
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
                    // Search Files Button
                    Surface(
                        onClick = { isSearchExpanded = !isSearchExpanded },
                        shape = CircleShape,
                        color = if (isSearchExpanded || searchQuery.isNotEmpty()) GitAccentSoft else GitTopBarButtonBg,
                        border = if (isSearchExpanded || searchQuery.isNotEmpty()) BorderStroke(1.dp, GitAccent) else null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .testTag("explorer_search_toggle_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search Files",
                                tint = if (isSearchExpanded || searchQuery.isNotEmpty()) GitAccent else GitText1,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Multi-Select Mode Button
                    Surface(
                        onClick = onToggleBatchMode,
                        shape = CircleShape,
                        color = if (isBatchMode) GitAccentSoft else GitTopBarButtonBg,
                        border = if (isBatchMode) BorderStroke(1.dp, GitAccent) else null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .testTag("explorer_multiselect_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Multi-Select Mode",
                                tint = if (isBatchMode) GitAccent else GitText1,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Sync & Refresh Button
                    Surface(
                        onClick = onRefresh,
                        shape = CircleShape,
                        color = if (syncStatus == SyncStatus.SYNCING || isLoadingTree) GitAccentSoft else GitTopBarButtonBg,
                        border = if (syncStatus == SyncStatus.SYNCING || isLoadingTree) BorderStroke(1.dp, GitAccent) else null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .testTag("explorer_sync_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Repository",
                                tint = if (syncStatus == SyncStatus.SYNCING || isLoadingTree) GitAccent else GitText1,
                                modifier = if (syncStatus == SyncStatus.SYNCING || isLoadingTree) {
                                    Modifier.size(17.dp).rotate(syncRotation)
                                } else {
                                    Modifier.size(17.dp)
                                }
                            )
                        }
                    }

                    // Add File / Folder / Upload Button
                    Surface(
                        onClick = onOpenNewFileDialog,
                        shape = CircleShape,
                        color = GitTopBarButtonBg,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .testTag("explorer_add_file_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add or Upload",
                                tint = GitText1,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Overflow Menu
                    Box {
                        Surface(
                            onClick = { showOverflowMenu = true },
                            shape = CircleShape,
                            color = GitTopBarButtonBg,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("explorer_overflow_menu_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = GitText1,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        shape = RoundedCornerShape(12.dp),
                        containerColor = GitSurface,
                        border = BorderStroke(1.dp, GitBorderStrong),
                        shadowElevation = 8.dp
                    ) {
                        Text(
                            text = "SORT BY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = GitText3,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Folders First", fontSize = 13.sp, color = GitText1)
                                    if (fileTreeSortOption == FileTreeSortOption.FOLDERS_FIRST) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                onFileTreeSortChange(FileTreeSortOption.FOLDERS_FIRST)
                                showOverflowMenu = false
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Files First", fontSize = 13.sp, color = GitText1)
                                    if (fileTreeSortOption == FileTreeSortOption.FILES_FIRST) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                onFileTreeSortChange(FileTreeSortOption.FILES_FIRST)
                                showOverflowMenu = false
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Name (A → Z)", fontSize = 13.sp, color = GitText1)
                                    if (fileTreeSortOption == FileTreeSortOption.NAME_ASC) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                onFileTreeSortChange(FileTreeSortOption.NAME_ASC)
                                showOverflowMenu = false
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Name (Z → A)", fontSize = 13.sp, color = GitText1)
                                    if (fileTreeSortOption == FileTreeSortOption.NAME_DESC) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                onFileTreeSortChange(FileTreeSortOption.NAME_DESC)
                                showOverflowMenu = false
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = GitBorder)

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Reverse Sort Order", fontSize = 13.sp, color = GitText1)
                                    if (isFileTreeSortReversed) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.SwapVert, contentDescription = null, tint = if (isFileTreeSortReversed) GitAccent else GitText2, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                onToggleFileTreeSortReverse()
                                showOverflowMenu = false
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = GitBorder)

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("GitHub Terminal", fontWeight = FontWeight.Bold, color = GitAccent, fontSize = 13.sp)
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Terminal, contentDescription = "Terminal", tint = GitAccent, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                onOpenTerminal()
                                showOverflowMenu = false
                            },
                            modifier = Modifier.testTag("menu_item_github_terminal")
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = GitBorder)

                        DropdownMenuItem(
                            text = { Text("Back to Repositories", fontSize = 13.sp, color = GitText1) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                onNavigateToReposList()
                                showOverflowMenu = false
                            }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = GitSurface,
            titleContentColor = GitText1
        )
    )

        HorizontalDivider(color = GitBorder, thickness = 0.5.dp)

        // Search Bar (if expanded)
        AnimatedVisibility(visible = isSearchExpanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GitSurface,
                border = BorderStroke(1.dp, GitBorderStrong)
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
                        placeholder = { Text("Search files by name or path...", fontSize = 13.sp, color = GitText3) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp), tint = GitText2)
                                }
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
                }
            }
        }

        // Breadcrumb Navigation Path Bar
        BreadcrumbBar(
            repoName = repo?.name ?: "Repository",
            currentPath = currentPath,
            onNavigateToDir = onNavigateToDir
        )

        // Pinned Folders Quick Access Bar
        if (pinnedFolders.isNotEmpty() || currentPath.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = GitAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Quick Folders:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GitText2
                    )
                }

                // Pinned Folder Pills (Smooth rounded pill shape with unpin support)
                pinnedFolders.forEach { folderPath ->
                    val displayName = folderPath.substringAfterLast('/')
                    val isCurrent = currentPath == folderPath

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNavigateToDir(folderPath) },
                        color = if (isCurrent) GitAccentSoft else GitSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCurrent) GitAccent else GitBorderStrong
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = GitAccent
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) GitAccent else GitText1
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Unpin",
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(CircleShape)
                                    .clickable { onTogglePinFolder(folderPath) },
                                tint = GitText3
                            )
                        }
                    }
                }

                // Pin Current Directory Quick Action (Smooth pill with clean borders)
                if (currentPath.isNotBlank() && !pinnedFolders.contains(currentPath)) {
                    val currentFolderName = currentPath.substringAfterLast('/')
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onTogglePinFolder(currentPath) },
                        color = GitAccentSoft,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GitAccent.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = GitAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Pin \"$currentFolderName\"",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GitAccent
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = GitBorder, thickness = 0.5.dp)
        }

        // Batch Mode Action Bar
        AnimatedVisibility(visible = isBatchMode) {
            BatchActionBar(
                selectedCount = selectedFilePaths.size,
                onSelectAll = onSelectAllInDir,
                onClear = onClearSelection,
                onCut = onCutSelection,
                onCopy = onCopySelection,
                onDelete = onOpenBatchDeleteModal
            )
        }

        // Clipboard Floating Paste Indicator Bar
        AnimatedVisibility(visible = clipboard != null) {
            clipboard?.let { clip ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    color = GitAccentSoft,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GitAccent.copy(alpha = 0.4f))
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
                                imageVector = if (clip.isCut) Icons.Default.ContentCut else Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = GitAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "${if (clip.isCut) "Cut" else "Copied"} ${clip.items.size} item(s)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GitAccent
                                )
                                Text(
                                    text = "Target: ${if (currentPath.isBlank()) "Root (/)" else "/$currentPath"}",
                                    fontSize = 10.sp,
                                    color = GitText2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = onClearClipboard,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Cancel", fontSize = 11.sp, color = GitText2)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { onPasteClipboard(currentPath) },
                                enabled = !isPasting,
                                colors = ButtonDefaults.buttonColors(containerColor = GitAccent),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                if (isPasting) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pasting...", fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Paste Here", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Main Explorer Content List
        Box(modifier = Modifier.weight(1f)) {
            if (isLoadingTree || (rootNode == null && rawTreeItems.isEmpty())) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = GitAccent,
                            modifier = Modifier.size(34.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading repository files...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = GitText2
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
                    clipboard = clipboard,
                    onOpenFile = onOpenFile,
                    onToggleSelect = onToggleSelectFile,
                    onToggleBatchMode = onToggleBatchMode,
                    onCutItem = onCutItem,
                    onCopyItem = onCopyItem,
                    onDeleteSingle = onDeleteSingleFile
                )
            } else {
                // Directory Node Children List (Files & Folders)
                val currentNode = rootNode?.let { RepoRepo.findNodeAtDirectory(it, currentPath) }
                val sortedChildren = remember(currentNode?.children, fileTreeSortOption, isFileTreeSortReversed) {
                    val list = currentNode?.children ?: emptyList()
                    val sorted = when (fileTreeSortOption) {
                        FileTreeSortOption.FOLDERS_FIRST -> list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        FileTreeSortOption.FILES_FIRST -> list.sortedWith(compareBy({ it.isDirectory }, { it.name.lowercase() }))
                        FileTreeSortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                        FileTreeSortOption.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
                    }
                    if (isFileTreeSortReversed) sorted.reversed() else sorted
                }

                if (currentNode == null || sortedChildren.isEmpty()) {
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
                                tint = GitText3,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (rawTreeItems.isEmpty()) "This repository is empty" else "Directory is empty",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GitText2
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onOpenNewFileDialog,
                                colors = ButtonDefaults.buttonColors(containerColor = GitAccent),
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
                        state = folderListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 72.dp)
                    ) {
                        items(sortedChildren, key = { it.path }) { childNode ->
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

                            val isFolderPinned = childNode.isDirectory && pinnedFolders.contains(childNode.path)

                            // Check if in clipboard for Cut/Copy visual indication
                            val isClipboardMatch = clipboard?.items?.any { clipItem ->
                                clipItem.path == childNode.path || (childNode.isDirectory && (clipItem.path == childNode.path || clipItem.path.startsWith("${childNode.path}/")))
                            } == true
                            val isCutClipboard = isClipboardMatch && clipboard?.isCut == true

                            ExplorerItemRow(
                                node = childNode,
                                rawItem = rawItem,
                                isBatchMode = isBatchMode,
                                isSelected = isSelected,
                                isPinned = isFolderPinned,
                                isInClipboard = isClipboardMatch,
                                isCutClipboard = isCutClipboard,
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
                                onLongClick = {
                                    if (!isBatchMode) {
                                        onToggleBatchMode()
                                    }
                                    if (childNode.isDirectory) {
                                        onToggleSelectFolder(childNode.path)
                                    } else {
                                        onToggleSelectFile(childNode.path)
                                    }
                                },
                                onToggleSelect = {
                                    if (childNode.isDirectory) {
                                        onToggleSelectFolder(childNode.path)
                                    } else {
                                        onToggleSelectFile(childNode.path)
                                    }
                                },
                                onCut = { onCutItem(childNode.path, childNode.isDirectory, childNode.sha) },
                                onCopy = { onCopyItem(childNode.path, childNode.isDirectory, childNode.sha) },
                                onDelete = { onDeleteSingleFile(childNode.path, childNode.sha) },
                                onTogglePinFolder = { onTogglePinFolder(childNode.path) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }

    // Folder Context Menu Dialog (for Pinning & Actions)
    folderForActionDialog?.let { folderNode ->
        val isPinned = pinnedFolders.contains(folderNode.path)
        AlertDialog(
            onDismissRequest = { folderForActionDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = GitAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(folderNode.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GitText1)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick action for this folder:", fontSize = 13.sp, color = GitText2)

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onTogglePinFolder(folderNode.path)
                                folderForActionDialog = null
                            },
                        color = if (isPinned) GitAccentSoft else GitSurface,
                        border = BorderStroke(1.dp, if (isPinned) GitAccent.copy(alpha = 0.3f) else GitBorderStrong)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = if (isPinned) GitAccent else GitText1,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isPinned) "Unpin Folder" else "Pin Folder to Quick Access",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = GitText1
                                )
                                Text(
                                    text = "Access this folder with one tap from the explorer header",
                                    fontSize = 11.sp,
                                    color = GitText2
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { folderForActionDialog = null }) {
                    Text("Close", color = GitText2)
                }
            }
        )
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
        color = GitSurface,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Root Indicator / Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onNavigateToDir("") }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(GitAccent, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = repoName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (segments.isEmpty()) GitText1 else GitAccent
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
                    tint = GitText3,
                    modifier = Modifier.size(14.dp)
                )

                Text(
                    text = seg,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                    color = if (isLast) GitText1 else GitAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onNavigateToDir(dirTarget) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
    HorizontalDivider(color = GitBorder, thickness = 0.5.dp)
}

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onCut: () -> Unit = {},
    onCopy: () -> Unit = {},
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GitSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, GitBorderStrong)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = GitAccent
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(
                        onClick = onSelectAll,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Select All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GitAccent)
                    }
                    TextButton(
                        onClick = onClear,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Clear", fontSize = 12.sp, color = GitText2)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onCut,
                        enabled = selectedCount > 0,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp), tint = GitText1)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cut", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GitText1)
                    }

                    OutlinedButton(
                        onClick = onCopy,
                        enabled = selectedCount > 0,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = GitText1)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GitText1)
                    }

                    Button(
                        onClick = onDelete,
                        enabled = selectedCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935),
                            disabledContainerColor = GitBorder
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete ($selectedCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorerItemRow(
    node: ExplorerNode,
    rawItem: GitTreeItem,
    isBatchMode: Boolean,
    isSelected: Boolean,
    isPinned: Boolean = false,
    isInClipboard: Boolean = false,
    isCutClipboard: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onToggleSelect: () -> Unit,
    onCut: () -> Unit = {},
    onCopy: () -> Unit = {},
    onDelete: () -> Unit,
    onTogglePinFolder: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val rowBackgroundColor = when {
        isInClipboard -> Color(0xFFFFEBEE)
        isSelected -> GitAccentSoft
        else -> Color.Transparent
    }

    val rowBorder = if (isInClipboard) {
        BorderStroke(1.dp, Md3LightError.copy(alpha = 0.5f))
    } else null

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = rowBackgroundColor,
        border = rowBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 11.dp)
                .testTag("explorer_item_${node.name}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBatchMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = GitAccent,
                        uncheckedColor = GitText3
                    ),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Icon (Outlined Green for Folder, Outlined line icons for Files)
            if (node.isDirectory) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = "Directory",
                    tint = if (isInClipboard) Md3LightError else GitAccent,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                FileIconForExtension(
                    extension = node.extension.ifBlank { rawItem.extension },
                    fileName = node.name,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name & Meta
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (node.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isInClipboard) Color(0xFFC62828) else GitText1,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = GitAccent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (isInClipboard) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Md3LightError.copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, Md3LightError.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (isCutClipboard) "CUT" else "COPIED",
                                color = Md3LightError,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                if (!node.isDirectory && node.size != null && node.size > 0) {
                    Text(
                        text = FileIcons.formatFileSize(node.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = GitText3,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            // Context Action Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions",
                        tint = GitText3,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = GitSurface,
                    border = BorderStroke(1.dp, GitBorderStrong),
                    shadowElevation = 8.dp
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContentCut, contentDescription = null, tint = GitText1, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Cut (Move)", fontSize = 13.sp, color = GitText1)
                            }
                        },
                        onClick = {
                            onCut()
                            showMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GitText1, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Copy", fontSize = 13.sp, color = GitText1)
                            }
                        },
                        onClick = {
                            onCopy()
                            showMenu = false
                        }
                    )

                    if (node.isDirectory) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PushPin, contentDescription = null, tint = if (isPinned) GitAccent else GitText1, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(if (isPinned) "Unpin Folder" else "Pin to Quick Folders", fontSize = 13.sp, color = if (isPinned) GitAccent else GitText1)
                                }
                            },
                            onClick = {
                                onTogglePinFolder()
                                showMenu = false
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = GitBorder)

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Md3LightError, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(if (node.isDirectory) "Delete Folder" else "Delete File", fontSize = 13.sp, color = Md3LightError)
                            }
                        },
                        onClick = {
                            onDelete()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = if (isBatchMode) 52.dp else 48.dp),
        color = GitBorder,
        thickness = 0.5.dp
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultsList(
    items: List<GitTreeItem>,
    searchQuery: String,
    isBatchMode: Boolean,
    selectedFilePaths: Set<String>,
    clipboard: ClipboardState? = null,
    onOpenFile: (GitTreeItem) -> Unit,
    onToggleSelect: (String) -> Unit,
    onToggleBatchMode: () -> Unit = {},
    onCutItem: (path: String, isDirectory: Boolean, sha: String) -> Unit = { _, _, _ -> },
    onCopyItem: (path: String, isDirectory: Boolean, sha: String) -> Unit = { _, _, _ -> },
    onDeleteSingle: (path: String, sha: String) -> Unit
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
                    tint = GitText3,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No files match \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = GitText1
                )
                Text(
                    text = "Try searching for another filename or path",
                    style = MaterialTheme.typography.bodySmall,
                    color = GitText2
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            items(items, key = { it.path }) { item ->
                val isSelected = selectedFilePaths.contains(item.path)
                val isClipboardMatch = clipboard?.items?.any { clipItem ->
                    clipItem.path == item.path || (item.isDirectory && (clipItem.path == item.path || clipItem.path.startsWith("${item.path}/")))
                } == true
                val isCutClipboard = isClipboardMatch && clipboard?.isCut == true
                var showMenu by remember { mutableStateOf(false) }

                val searchRowBg = when {
                    isClipboardMatch -> Color(0xFFFFEBEE)
                    isSelected -> GitAccentSoft
                    else -> Color.Transparent
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = searchRowBg,
                    border = if (isClipboardMatch) BorderStroke(1.dp, Md3LightError.copy(alpha = 0.5f)) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isBatchMode) onToggleSelect(item.path)
                                    else onOpenFile(item)
                                },
                                onLongClick = {
                                    if (!isBatchMode) {
                                        onToggleBatchMode()
                                    }
                                    onToggleSelect(item.path)
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isBatchMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelect(item.path) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = GitAccent,
                                    uncheckedColor = GitText3
                                ),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        FileIconForExtension(
                            extension = item.extension,
                            fileName = item.fileName,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isClipboardMatch) Color(0xFFC62828) else GitText1,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isClipboardMatch) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Md3LightError.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, Md3LightError.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = if (isCutClipboard) "CUT" else "COPIED",
                                            color = Md3LightError,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = item.path,
                                style = MaterialTheme.typography.labelSmall,
                                color = GitText2,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (item.size != null && item.size > 0) {
                            Text(
                                text = FileIcons.formatFileSize(item.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = GitText3,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More actions",
                                    tint = GitText3,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ContentCut, contentDescription = null, tint = GitText1, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Cut (Move)")
                                        }
                                    },
                                    onClick = {
                                        onCutItem(item.path, item.isDirectory, item.sha)
                                        showMenu = false
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GitText1, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Copy")
                                        }
                                    },
                                    onClick = {
                                        onCopyItem(item.path, item.isDirectory, item.sha)
                                        showMenu = false
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Delete", color = Color(0xFFE53935))
                                        }
                                    },
                                    onClick = {
                                        onDeleteSingle(item.path, item.sha)
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(start = 48.dp),
                    color = GitBorder,
                    thickness = 0.5.dp
                )
            }
        }
    }
}
