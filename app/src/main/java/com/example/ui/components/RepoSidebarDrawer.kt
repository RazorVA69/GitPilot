package com.example.ui.components

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.ui.screens.getLanguageColor
import com.example.ui.theme.GitAccent
import com.example.ui.theme.GitAccentSoft
import com.example.ui.theme.GitBg
import com.example.ui.theme.GitBorder
import com.example.ui.theme.GitBorderStrong
import com.example.ui.theme.GitSurface
import com.example.ui.theme.GitSurface2
import com.example.ui.theme.GitText1
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3
import com.example.ui.theme.GitYellow
import com.example.ui.theme.Md3LightError
import com.example.ui.viewmodel.RepoFilterType
import com.example.ui.viewmodel.RepoSortOption
import kotlinx.coroutines.launch

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
        color = GitSurface,
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
                color = GitSurface2
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
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = GitAccent
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = account?.username?.take(1)?.uppercase() ?: "P",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = account?.name ?: (account?.username ?: "Public Mode"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GitText1,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (account != null) "@${account.username}" else "Not logged in",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GitText2
                                )
                            }
                        }

                        IconButton(
                            onClick = onCloseSidebar,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("close_left_sidebar_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Sidebar", tint = GitText2, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons (All Repos / Switch Account / Logout)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onNavigateToAllRepos()
                                    onCloseSidebar()
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = GitSurface,
                            border = BorderStroke(1.dp, GitBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Dashboard, contentDescription = null, tint = GitText1, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("All Repos", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GitText1, maxLines = 1)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenLogin),
                            shape = RoundedCornerShape(8.dp),
                            color = GitSurface,
                            border = BorderStroke(1.dp, GitBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "Add Account", tint = GitText1, modifier = Modifier.size(16.dp))
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onLogout),
                            shape = RoundedCornerShape(8.dp),
                            color = GitSurface,
                            border = BorderStroke(1.dp, GitBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Md3LightError, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = GitBorder, thickness = 1.dp)

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
                    placeholder = { Text("Filter repos...", fontSize = 12.sp, color = GitText3) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = GitText2)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GitAccent,
                        unfocusedBorderColor = GitBorder,
                        focusedContainerColor = GitSurface2,
                        unfocusedContainerColor = GitSurface2
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Sort Dropdown
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort repos", tint = GitText2, modifier = Modifier.size(18.dp))
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
            }

            // Filter Pills Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf(
                    Triple(RepoFilterType.ALL, "All", allCount),
                    Triple(RepoFilterType.PUBLIC, "Public", publicCount),
                    Triple(RepoFilterType.PRIVATE, "Private", privateCount),
                    Triple(RepoFilterType.FORKS, "Forks", forksCount)
                )

                filters.forEach { (filter, label, count) ->
                    val isSelected = filterType == filter
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onFilterChange(filter) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) GitSurface2 else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, GitBorderStrong) else BorderStroke(1.dp, GitBorder)
                    ) {
                        Text(
                            text = "$label ($count)",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) GitText1 else GitText2,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = GitBorder, thickness = 1.dp)

            // Repositories List
            Box(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GitAccent, modifier = Modifier.size(28.dp))
                    }
                } else if (repositories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No repos found", style = MaterialTheme.typography.bodySmall, color = GitText2)
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
                                    color = if (isSelected) GitAccentSoft else Color.Transparent
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (repo.private) Icons.Default.Lock else (if (repo.fork) Icons.Default.ForkRight else Icons.Default.Book),
                                                contentDescription = null,
                                                tint = if (isSelected) GitAccent else (if (isPinned) GitYellow else GitText2),
                                                modifier = Modifier.size(16.dp)
                                            )

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = repo.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isSelected || isPinned) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) GitAccent else GitText1,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    if (isPinned) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.PushPin,
                                                            contentDescription = "Pinned",
                                                            tint = GitYellow,
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
                                                            color = GitAccentSoft
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Bolt,
                                                                    contentDescription = null,
                                                                    tint = GitAccent,
                                                                    modifier = Modifier.size(10.dp)
                                                                )
                                                                Text(
                                                                    text = "Working Repo",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = GitAccent
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
                                                                color = GitText2,
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
                                                        tint = GitYellow,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = "${repo.stargazersCount}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = GitText2,
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
                                                    tint = GitText3,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = GitBorder, thickness = 1.dp)
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
                        tint = GitAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(repo.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GitText1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select quick action for this repository:", fontSize = 13.sp, color = GitText2)

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
                        color = GitSurface2,
                        border = BorderStroke(1.dp, GitBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = if (isPinned) GitYellow else GitText1,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isPinned) "Unpin from Top" else "Pin to Top",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = GitText1
                                )
                                Text(
                                    text = if (isPinned) "Remove priority pin" else "Keep at the top of your sidebar & repo list",
                                    fontSize = 11.sp,
                                    color = GitText2
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
                        color = if (isWorking) GitAccentSoft else GitSurface2,
                        border = BorderStroke(1.dp, if (isWorking) GitAccent.copy(alpha = 0.4f) else GitBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (isWorking) GitAccent else GitText1,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isWorking) "Remove Working Repository" else "Set as Working Repository",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isWorking) GitAccent else GitText1
                                )
                                Text(
                                    text = "Auto-opens automatically whenever you launch the app",
                                    fontSize = 11.sp,
                                    color = GitText2
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedRepoForActions = null }) {
                    Text("Close", color = GitAccent)
                }
            }
        )
    }
}
