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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearchVisible by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedRepoForActions by remember { mutableStateOf<GitHubRepository?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val allCount = remember(allRepositories) { allRepositories.size }
    val publicCount = remember(allRepositories) { allRepositories.count { !it.private } }
    val privateCount = remember(allRepositories) { allRepositories.count { it.private } }
    val forksCount = remember(allRepositories) { allRepositories.count { it.fork } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Repositories",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = GitText1
                        )
                        if (account != null) {
                            Text(
                                text = "@${account.username} · $allCount repos",
                                style = MaterialTheme.typography.labelSmall,
                                color = GitText2
                            )
                        }
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
                    IconButton(
                        onClick = {
                            isSearchVisible = !isSearchVisible
                            if (!isSearchVisible) onSearchChange("")
                        },
                        modifier = Modifier.testTag("repo_list_search_btn")
                    ) {
                        Icon(
                            imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchVisible || searchQuery.isNotEmpty()) GitAccent else GitText2
                        )
                    }

                    // Sort menu
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("repo_list_sort_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = GitText2
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Last Activity")
                                        if (sortOption == RepoSortOption.LAST_ACTIVITY) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(14.dp))
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Name (A → Z)")
                                        if (sortOption == RepoSortOption.NAME_ASC) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(14.dp))
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Name (Z → A)")
                                        if (sortOption == RepoSortOption.NAME_DESC) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(14.dp))
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Most Stars ⭐")
                                        if (sortOption == RepoSortOption.STARS_DESC) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(14.dp))
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

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("repo_list_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = GitText2
                        )
                    }

                    if (account != null) {
                        if (account.avatarUrl != null) {
                            AsyncImage(
                                model = account.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onOpenLeftDrawer)
                            )
                        } else {
                            Surface(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onOpenLeftDrawer),
                                shape = RoundedCornerShape(8.dp),
                                color = GitAccent
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = account.username.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GitSurface,
                    titleContentColor = GitText1
                )
            )
        },
        containerColor = GitBg,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Instant Search Field
            AnimatedVisibility(visible = isSearchVisible || searchQuery.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
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

            // Filter Chips Bar (HTML-style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        color = if (isSelected) GitSurface2 else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, GitBorderStrong) else BorderStroke(1.dp, GitBorder)
                    ) {
                        Text(
                            text = "$label · $count",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) GitText1 else GitText2,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = GitBorder, thickness = 1.dp)

            // Repositories List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Md3LightPrimary, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading repositories...", color = Md3LightTextSecondary)
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
                            tint = Md3LightTextTertiary,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No repositories match \"$searchQuery\"" else "No repositories found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Md3LightTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Check your filters or refresh to load repositories.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Md3LightTextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(repositories, key = { it.id }) { repo ->
                        RepositoryCard(
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
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (repo.private) Icons.Default.Lock else Icons.Default.Book,
                        contentDescription = null,
                        tint = Md3LightPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(repo.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select quick action for this repository:", fontSize = 13.sp, color = Md3LightTextSecondary)

                    Spacer(modifier = Modifier.height(4.dp))

                    // 1. PIN / UNPIN
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onTogglePinRepo(repo.id)
                                coroutineScope.launch { listState.animateScrollToItem(0) }
                                selectedRepoForActions = null
                            },
                        color = Md3LightSurfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = if (isPinned) GitHubYellow else Md3LightTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isPinned) "Unpin from Top" else "Pin to Top",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (isPinned) "Remove priority pin" else "Keep at the top of your sidebar & repo list",
                                    fontSize = 11.sp,
                                    color = Md3LightTextSecondary
                                )
                            }
                        }
                    }

                    // 2. WORKING REPO
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSetWorkingRepo(if (isWorking) null else repo.id)
                                coroutineScope.launch { listState.animateScrollToItem(0) }
                                selectedRepoForActions = null
                            },
                        color = if (isWorking) GitHubGreen.copy(alpha = 0.12f) else Md3LightSurfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (isWorking) GitHubGreen else Md3LightTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isWorking) "Remove Working Repository" else "Set as Working Repository",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isWorking) GitHubGreen else Md3LightTextPrimary
                                )
                                Text(
                                    text = "Auto-opens automatically whenever you launch the app",
                                    fontSize = 11.sp,
                                    color = Md3LightTextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedRepoForActions = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RepositoryCard(
    repo: GitHubRepository,
    isPinned: Boolean = false,
    isWorking: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("repo_card_${repo.name}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GitSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(
            1.dp,
            if (isWorking) GitAccent.copy(alpha = 0.8f) else if (isPinned) GitYellow.copy(alpha = 0.8f) else GitBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Repo Name & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when {
                            repo.private -> Icons.Default.Lock
                            repo.fork -> Icons.Default.ForkRight
                            else -> Icons.Default.Book
                        },
                        contentDescription = null,
                        tint = if (repo.private) GitYellow else GitAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.titleMedium,
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
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isWorking) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GitAccentSoft,
                            border = BorderStroke(1.dp, GitAccent.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = GitAccent, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Working", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GitAccent)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (repo.private) GitYellow.copy(alpha = 0.12f) else GitSurface2,
                        border = BorderStroke(
                            1.dp,
                            if (repo.private) GitYellow.copy(alpha = 0.3f) else GitBorder
                        )
                    ) {
                        Text(
                            text = if (repo.private) "Private" else "Public",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (repo.private) GitYellow else GitText2,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Description
            if (!repo.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = GitText2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata Row: Language, Stars, Forks, Default Branch
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
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(getLanguageColor(repo.language))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = repo.language,
                            style = MaterialTheme.typography.labelSmall,
                            color = GitText2
                        )
                    }
                }

                // Stars
                if (repo.stargazersCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Stars",
                            tint = GitYellow,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${repo.stargazersCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = GitText2
                        )
                    }
                }

                // Forks
                if (repo.forksCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ForkRight,
                            contentDescription = "Forks",
                            tint = GitText2,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${repo.forksCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = GitText2
                        )
                    }
                }

                // Default branch
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(GitAccent)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = repo.defaultBranch,
                        style = MaterialTheme.typography.labelSmall,
                        color = GitAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
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
