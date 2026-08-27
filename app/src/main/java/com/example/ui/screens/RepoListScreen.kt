package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.AccountEntity
import com.example.data.model.GitHubRepository
import com.example.ui.theme.*
import com.example.ui.viewmodel.RepoFilterType
import com.example.ui.viewmodel.RepoSortOption
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(
    account: AccountEntity?,
    repositories: List<GitHubRepository>,
    allRepositories: List<GitHubRepository> = repositories,
    isLoading: Boolean,
    searchQuery: String,
    filterType: RepoFilterType,
    sortOption: RepoSortOption = RepoSortOption.LAST_ACTIVITY,
    pinnedRepoIds: Set<Long> = emptySet(),
    workingRepoId: Long? = null,
    onSearchChange: (String) -> Unit,
    onFilterChange: (RepoFilterType) -> Unit,
    onSortChange: (RepoSortOption) -> Unit = {},
    onTogglePinRepo: (Long) -> Unit = {},
    onSetWorkingRepo: (Long?) -> Unit = {},
    onSelectRepo: (GitHubRepository) -> Unit,
    onRefresh: () -> Unit,
    onOpenLeftDrawer: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenAccountSwitcher: () -> Unit = {},
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var selectedRepoForActions by remember { mutableStateOf<GitHubRepository?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val allCount by remember(allRepositories) { derivedStateOf { allRepositories.size } }
    val publicCount by remember(allRepositories) { derivedStateOf { allRepositories.count { !it.private } } }
    val privateCount by remember(allRepositories) { derivedStateOf { allRepositories.count { it.private } } }
    val forksCount by remember(allRepositories) { derivedStateOf { allRepositories.count { it.fork } } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Repositories",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = GitText1,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "@${account?.username ?: "BlazeFTL"} · $allCount repos",
                            style = MaterialTheme.typography.labelSmall,
                            color = GitText2,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onOpenLeftDrawer,
                        modifier = Modifier.testTag("repo_list_open_drawer_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Left Sidebar",
                            tint = GitText1
                        )
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        // Search Action
                        Surface(
                            onClick = {
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible) onSearchChange("")
                            },
                            shape = CircleShape,
                            color = if (isSearchVisible || searchQuery.isNotEmpty()) GitAccentSoft else GitTopBarButtonBg,
                            border = if (isSearchVisible || searchQuery.isNotEmpty()) BorderStroke(1.dp, GitAccent) else null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("repo_list_search_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = if (isSearchVisible || searchQuery.isNotEmpty()) GitAccent else GitText1,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // Sort Action & Dropdown Menu
                        Box {
                            Surface(
                                onClick = { showSortMenu = true },
                                shape = CircleShape,
                                color = if (sortOption != RepoSortOption.LAST_ACTIVITY) GitAccentSoft else GitTopBarButtonBg,
                                border = if (sortOption != RepoSortOption.LAST_ACTIVITY) BorderStroke(1.dp, GitAccent) else null,
                                modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("repo_list_sort_btn")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort repositories",
                                        tint = if (sortOption != RepoSortOption.LAST_ACTIVITY) GitAccent else GitText1,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                shape = RoundedCornerShape(12.dp),
                                containerColor = GitSurface,
                                border = BorderStroke(1.dp, GitBorderStrong),
                                shadowElevation = 8.dp
                            ) {
                                Text(
                                    text = "SORT REPOSITORIES",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GitText3,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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
                                            Text("Last Activity", fontSize = 13.sp, color = GitText1)
                                            if (sortOption == RepoSortOption.LAST_ACTIVITY) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSortChange(RepoSortOption.LAST_ACTIVITY)
                                        showSortMenu = false
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
                                            if (sortOption == RepoSortOption.NAME_ASC) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSortChange(RepoSortOption.NAME_ASC)
                                        showSortMenu = false
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
                                            if (sortOption == RepoSortOption.NAME_DESC) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSortChange(RepoSortOption.NAME_DESC)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Most Stars", fontSize = 13.sp, color = GitText1)
                                            if (sortOption == RepoSortOption.STARS_DESC) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSortChange(RepoSortOption.STARS_DESC)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }

                        // Refresh Action
                        Surface(
                            onClick = onRefresh,
                            shape = CircleShape,
                            color = GitTopBarButtonBg,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("repo_list_refresh_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh repositories",
                                    tint = GitText1,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // Settings Button
                        Surface(
                            onClick = onOpenSettings,
                            shape = CircleShape,
                            color = GitTopBarButtonBg,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .testTag("repo_list_settings_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = GitText1,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // User Profile Picture Avatar (Tapping opens Account Switcher)
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onOpenAccountSwitcher)
                                .testTag("repo_list_avatar_btn"),
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, GitAccent.copy(alpha = 0.5f)),
                            color = GitAccent
                        ) {
                            if (account?.avatarUrl != null) {
                                AsyncImage(
                                    model = account.avatarUrl,
                                    contentDescription = "Profile picture - Switch account",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = account?.username?.take(1)?.uppercase() ?: "B",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
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
        },
        containerColor = GitAppBg,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(GitAppBg)
        ) {
            // Instant Search Field
            AnimatedVisibility(visible = isSearchVisible || searchQuery.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("repo_list_search_input"),
                    placeholder = { Text("Filter repositories...", color = GitText3, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = GitText2, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GitAccent,
                        unfocusedBorderColor = GitBorderStrong,
                        focusedContainerColor = GitSurface,
                        unfocusedContainerColor = GitSurface
                    )
                )
            }

            // Filter Chips Bar (Centered with Accent on Selected Chip)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val filterItems = listOf(
                    Triple(RepoFilterType.ALL, "All", allCount),
                    Triple(RepoFilterType.PUBLIC, "Public", publicCount),
                    Triple(RepoFilterType.PRIVATE, "Private", privateCount),
                    Triple(RepoFilterType.FORKS, "Forks", forksCount)
                )

                filterItems.forEach { (type, label, count) ->
                    val isSelected = filterType == type
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onFilterChange(type) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) GitAccentSoft else GitSurface,
                        border = if (isSelected) BorderStroke(1.dp, GitAccent) else BorderStroke(1.dp, GitBorderStrong)
                    ) {
                        Text(
                            text = "$label · $count",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) GitAccent else GitText2,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            HorizontalDivider(color = GitBorder, thickness = 1.dp)

            // Repositories List (Continuous Flat List with Dividers matching SS 2)
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = GitAccent, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading repositories...", color = GitText2, fontSize = 13.sp)
                    }
                }
            } else if (repositories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = GitText3,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No repositories match \"$searchQuery\"" else "No repositories found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GitText1
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Check your filters or refresh to load repositories.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GitText2
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GitSurface),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(repositories, key = { _, repo -> repo.id }) { index, repo ->
                        RepositoryRowItem(
                            repo = repo,
                            isPinned = pinnedRepoIds.contains(repo.id),
                            isWorking = workingRepoId == repo.id,
                            onClick = { onSelectRepo(repo) },
                            onLongClick = { selectedRepoForActions = repo },
                            modifier = Modifier.animateItem(
                                placementSpec = tween(
                                    durationMillis = 350,
                                    easing = FastOutSlowInEasing
                                ),
                                fadeInSpec = tween(
                                    durationMillis = 250,
                                    easing = FastOutSlowInEasing
                                ),
                                fadeOutSpec = tween(
                                    durationMillis = 250,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        )
                        HorizontalDivider(
                            color = GitBorder,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }

    // Repository Context Menu Dialog
    selectedRepoForActions?.let { repo ->
        val isPinned = pinnedRepoIds.contains(repo.id)
        val isWorking = workingRepoId == repo.id

        AlertDialog(
            onDismissRequest = { selectedRepoForActions = null },
            shape = RoundedCornerShape(14.dp),
            containerColor = GitSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (repo.private) Icons.Default.Lock else Icons.Default.Book,
                        contentDescription = null,
                        tint = GitAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = repo.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = GitText1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select action for repository:",
                        fontSize = 13.sp,
                        color = GitText2
                    )

                    // 1. PIN / UNPIN
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onTogglePinRepo(repo.id)
                                coroutineScope.launch { listState.animateScrollToItem(0) }
                                selectedRepoForActions = null
                            },
                        color = if (isPinned) GitAccentSoft else GitSurface,
                        border = BorderStroke(1.dp, if (isPinned) GitAccent.copy(alpha = 0.3f) else GitBorderStrong)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = if (isPinned) GitAccent else GitText1,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isPinned) "Unpin from Top" else "Pin to Top",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = if (isPinned) GitAccent else GitText1
                                )
                                Text(
                                    text = if (isPinned) "Remove priority pin" else "Keep at the top of your sidebar & repo list",
                                    fontSize = 11.5.sp,
                                    color = GitText2
                                )
                            }
                        }
                    }

                    // 2. WORKING REPO
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSetWorkingRepo(if (isWorking) null else repo.id)
                                coroutineScope.launch { listState.animateScrollToItem(0) }
                                selectedRepoForActions = null
                            },
                        color = if (isWorking) GitAccentSoft else GitSurface,
                        border = BorderStroke(1.dp, if (isWorking) GitAccent.copy(alpha = 0.3f) else GitBorderStrong)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (isWorking) GitAccent else GitText1,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isWorking) "Remove Working Repository" else "Set as Working Repository",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = if (isWorking) GitAccent else GitText1
                                )
                                Text(
                                    text = "Auto-opens automatically whenever you launch the app",
                                    fontSize = 11.5.sp,
                                    color = GitText2
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { selectedRepoForActions = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", color = GitAccent, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RepositoryRowItem(
    repo: GitHubRepository,
    isPinned: Boolean = false,
    isWorking: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("repo_row_${repo.name}"),
        color = GitAppBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
        ) {
            // Repo Name Row with 3-dot Menu Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = repo.name,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = GitText1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = GitYellow,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    if (isWorking) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Working",
                            tint = GitAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onLongClick,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("repo_row_menu_${repo.name}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Repository actions",
                        tint = GitText3,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Description Row (SS 2 format)
            if (!repo.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = repo.description,
                    fontSize = 13.sp,
                    color = GitText2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata Row (SS 2 format: Language dot + Name, Star count, Branch dot + Name)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Language
                if (!repo.language.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(getLanguageColor(repo.language))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = repo.language,
                            fontSize = 12.sp,
                            color = GitText2
                        )
                    }
                }

                // Stars (SS 2: outlined star ☆ + count in accent color)
                if (repo.stargazersCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = "Stars",
                            tint = GitAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${repo.stargazersCount}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = GitAccent
                        )
                    }
                }

                // Default branch with green dot
                if (repo.defaultBranch.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(GitAccent)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = repo.defaultBranch,
                            fontSize = 12.sp,
                            color = GitAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

fun getLanguageColor(language: String?): Color {
    return when (language?.lowercase()) {
        "kotlin" -> Color(0xFFA97BFF)
        "java" -> Color(0xFFB07219)
        "python" -> Color(0xFF3572A5)
        "javascript" -> Color(0xFFF1E05A)
        "typescript" -> Color(0xFF3178C6)
        "html" -> Color(0xFFE34C26)
        "css" -> Color(0xFF563D7C)
        "c++", "cpp" -> Color(0xFFF34B7D)
        "c" -> Color(0xFF555555)
        "c#" -> Color(0xFF178600)
        "go" -> Color(0xFF00ADD8)
        "rust" -> Color(0xFFDEA584)
        "ruby" -> Color(0xFF701516)
        "swift" -> Color(0xFFF05138)
        "dart" -> Color(0xFF00B4AB)
        "shell", "bash" -> Color(0xFF89E051)
        else -> Color(0xFF8B949E)
    }
}
