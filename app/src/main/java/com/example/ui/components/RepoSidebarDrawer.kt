package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.AccountEntity
import com.example.data.model.GitHubRepository
import com.example.ui.screens.getLanguageColor
import com.example.ui.theme.GitHubBlue
import com.example.ui.theme.GitHubGreen
import com.example.ui.theme.GitHubYellow
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
import com.example.ui.viewmodel.RepoFilterType
import com.example.ui.viewmodel.RepoSortOption

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RepoSidebarDrawer(
    account: AccountEntity?,
    repositories: List<GitHubRepository>,
    allRepositories: List<GitHubRepository> = repositories,
    selectedRepo: GitHubRepository?,
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
    onNavigateToAllRepos: () -> Unit,
    onRefreshRepos: () -> Unit,
    onOpenLogin: () -> Unit,
    onCloseSidebar: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedRepoForActions by remember { mutableStateOf<GitHubRepository?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val allCount = remember(allRepositories) { allRepositories.size }
    val publicCount = remember(allRepositories) { allRepositories.count { !it.private } }
    val privateCount = remember(allRepositories) { allRepositories.count { it.private } }
    val forksCount = remember(allRepositories) { allRepositories.count { it.fork } }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .testTag("repo_left_sidebar_drawer"),
        color = Md3LightSurface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding()
        ) {
            // Header: Account Info & Actions
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = Md3LightSurfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (account?.avatarUrl != null) {
                                AsyncImage(
                                    model = account.avatarUrl,
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Md3LightOutline),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = account?.name ?: (account?.username ?: "Public Mode"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Md3LightTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (account != null) "@${account.username}" else "Not logged in",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Md3LightTextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = onCloseSidebar,
                            modifier = Modifier.testTag("close_left_sidebar_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Sidebar", tint = Md3LightTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons (All Repos / Switch Account / Logout)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onNavigateToAllRepos()
                                onCloseSidebar()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("All Repos", fontSize = 12.sp, maxLines = 1)
                        }

                        IconButton(
                            onClick = onOpenLogin,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Account", tint = Md3LightTextPrimary)
                        }

                        IconButton(
                            onClick = onLogout,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Md3LightError)
                        }
                    }
                }
            }

            HorizontalDivider(color = Md3LightOutlineVariant)

            // Search Bar & Sort Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sidebar_repo_search_input"),
                    placeholder = { Text("Filter repos...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = Md3LightTextSecondary)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Md3LightPrimary,
                        unfocusedBorderColor = Md3LightOutlineVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Sort Dropdown
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort repos", tint = Md3LightTextPrimary)
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
                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitHubBlue, modifier = Modifier.size(14.dp))
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
                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitHubBlue, modifier = Modifier.size(14.dp))
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
                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitHubBlue, modifier = Modifier.size(14.dp))
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
                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitHubBlue, modifier = Modifier.size(14.dp))
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
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RepoFilterType.values().forEach { filter ->
                    val isSelected = filterType == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterChange(filter) },
                        label = {
                            Text(
                                text = when (filter) {
                                    RepoFilterType.ALL -> "All ($allCount)"
                                    RepoFilterType.PUBLIC -> "Public ($publicCount)"
                                    RepoFilterType.PRIVATE -> "Private ($privateCount)"
                                    RepoFilterType.FORKS -> "Forks ($forksCount)"
                                },
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Md3LightPrimaryContainer,
                            selectedLabelColor = Md3LightPrimary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Md3LightOutlineVariant)

            // Repositories List
            Box(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Md3LightPrimary, modifier = Modifier.size(28.dp))
                    }
                } else if (repositories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No repos found", style = MaterialTheme.typography.bodySmall, color = Md3LightTextSecondary)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(repositories, key = { it.id }) { repo ->
                            val isSelected = selectedRepo?.id == repo.id
                            val isPinned = pinnedRepoIds.contains(repo.id)
                            val isWorking = workingRepoId == repo.id

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(
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
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                onSelectRepo(repo)
                                                onCloseSidebar()
                                            },
                                            onLongClick = {
                                                selectedRepoForActions = repo
                                            }
                                        )
                                        .testTag("sidebar_repo_item_${repo.name}"),
                                    color = if (isSelected) Md3LightPrimaryContainer.copy(alpha = 0.5f) else Color.Transparent
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (repo.private) Icons.Default.Lock else (if (repo.fork) Icons.Default.ForkRight else Icons.Default.Book),
                                                contentDescription = null,
                                                tint = if (isSelected) Md3LightPrimary else (if (isPinned) GitHubYellow else Md3LightTextSecondary),
                                                modifier = Modifier.size(16.dp)
                                            )

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = repo.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isSelected || isPinned) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) Md3LightPrimary else Md3LightTextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    if (isPinned) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.PushPin,
                                                            contentDescription = "Pinned",
                                                            tint = GitHubYellow,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    if (isWorking) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = GitHubGreen.copy(alpha = 0.15f)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Bolt,
                                                                    contentDescription = null,
                                                                    tint = GitHubGreen,
                                                                    modifier = Modifier.size(10.dp)
                                                                )
                                                                Text(
                                                                    text = "Working Repo",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = GitHubGreen
                                                                )
                                                            }
                                                        }
                                                    }

                                                    if (!repo.language.isNullOrBlank()) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(6.dp)
                                                                    .clip(CircleShape)
                                                                    .background(getLanguageColor(repo.language))
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = repo.language,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Md3LightTextSecondary,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (repo.stargazersCount > 0) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = GitHubYellow,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = "${repo.stargazersCount}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Md3LightTextSecondary,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { selectedRepoForActions = repo },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Repo options",
                                                    tint = Md3LightTextTertiary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = Md3LightOutlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Repository Long Press Context Dialog
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

                    // 1. PIN / UNPIN ACTION
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

                    // 2. SET / UNSET AS WORKING REPO
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
