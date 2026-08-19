package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import coil.compose.AsyncImage
import com.example.data.local.AccountEntity
import com.example.data.model.GitHubRepository
import com.example.ui.components.FileIcons
import com.example.ui.theme.GitHubBlue
import com.example.ui.theme.GitHubGreen
import com.example.ui.theme.GitHubOrange
import com.example.ui.theme.GitHubPurple
import com.example.ui.theme.GitHubYellow
import com.example.ui.theme.Md3LightBackground
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(
    account: AccountEntity?,
    repositories: List<GitHubRepository>,
    isLoading: Boolean,
    searchQuery: String,
    filterType: RepoFilterType,
    onSearchChange: (String) -> Unit,
    onFilterChange: (RepoFilterType) -> Unit,
    onSelectRepo: (GitHubRepository) -> Unit,
    onRefresh: () -> Unit,
    onOpenLeftDrawer: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearchVisible by remember { mutableStateOf(false) }

    val publicCount = remember(repositories) { repositories.count { !it.private } }
    val privateCount = remember(repositories) { repositories.count { it.private } }
    val forksCount = remember(repositories) { repositories.count { it.fork } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Repositories",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Md3LightTextPrimary
                        )
                        if (account != null) {
                            Text(
                                text = "@${account.username} • ${repositories.size} repos",
                                style = MaterialTheme.typography.labelSmall,
                                color = Md3LightTextSecondary
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
                            tint = Md3LightPrimary
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
                            tint = if (isSearchVisible || searchQuery.isNotEmpty()) Md3LightPrimary else Md3LightTextSecondary
                        )
                    }

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("repo_list_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Md3LightTextSecondary
                        )
                    }

                    if (account?.avatarUrl != null) {
                        AsyncImage(
                            model = account.avatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onOpenLeftDrawer)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Md3LightSurface,
                    titleContentColor = Md3LightTextPrimary
                )
            )
        },
        containerColor = Md3LightBackground,
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
                    placeholder = { Text("Find a repository by name or language...", color = Md3LightTextTertiary, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Md3LightPrimary, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Md3LightTextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Md3LightPrimary,
                        unfocusedBorderColor = Md3LightOutline,
                        focusedContainerColor = Md3LightSurface,
                        unfocusedContainerColor = Md3LightSurface
                    )
                )
            }

            // Filter Chips Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == RepoFilterType.ALL,
                    onClick = { onFilterChange(RepoFilterType.ALL) },
                    label = { Text("All (${repositories.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Md3LightPrimaryContainer,
                        selectedLabelColor = Md3LightPrimary
                    )
                )
                FilterChip(
                    selected = filterType == RepoFilterType.PUBLIC,
                    onClick = { onFilterChange(RepoFilterType.PUBLIC) },
                    label = { Text("Public ($publicCount)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Md3LightPrimaryContainer,
                        selectedLabelColor = Md3LightPrimary
                    )
                )
                FilterChip(
                    selected = filterType == RepoFilterType.PRIVATE,
                    onClick = { onFilterChange(RepoFilterType.PRIVATE) },
                    label = { Text("Private ($privateCount)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Md3LightPrimaryContainer,
                        selectedLabelColor = Md3LightPrimary
                    )
                )
                FilterChip(
                    selected = filterType == RepoFilterType.FORKS,
                    onClick = { onFilterChange(RepoFilterType.FORKS) },
                    label = { Text("Forks ($forksCount)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Md3LightPrimaryContainer,
                        selectedLabelColor = Md3LightPrimary
                    )
                )
            }

            HorizontalDivider(color = Md3LightOutlineVariant, thickness = 1.dp)

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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(repositories, key = { it.id }) { repo ->
                        RepositoryCard(
                            repo = repo,
                            onClick = { onSelectRepo(repo) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RepositoryCard(
    repo: GitHubRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("repo_card_${repo.name}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Md3LightSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Md3LightOutlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        tint = if (repo.private) GitHubYellow else Md3LightPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Md3LightPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (repo.private) GitHubYellow.copy(alpha = 0.12f) else Md3LightSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (repo.private) GitHubYellow.copy(alpha = 0.3f) else Md3LightOutlineVariant
                    )
                ) {
                    Text(
                        text = if (repo.private) "Private" else "Public",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (repo.private) GitHubYellow else Md3LightTextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 11.sp
                    )
                }
            }

            // Description
            if (!repo.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Md3LightTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(getLanguageColor(repo.language))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = repo.language,
                            style = MaterialTheme.typography.labelSmall,
                            color = Md3LightTextSecondary
                        )
                    }
                }

                // Stars
                if (repo.stargazersCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Stars",
                            tint = GitHubYellow,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${repo.stargazersCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Md3LightTextSecondary
                        )
                    }
                }

                // Forks
                if (repo.forksCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ForkRight,
                            contentDescription = "Forks",
                            tint = Md3LightTextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${repo.forksCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Md3LightTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Default branch
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Md3LightSurfaceVariant
                ) {
                    Text(
                        text = repo.defaultBranch,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Md3LightPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

fun getLanguageColor(language: String): Color {
    return when (language.lowercase()) {
        "kotlin" -> Color(0xFFA97BFF)
        "java" -> Color(0xFFB07219)
        "python" -> Color(0xFF3572A5)
        "javascript" -> Color(0xFFF1E05A)
        "typescript" -> Color(0xFF3178C6)
        "c++", "cpp" -> Color(0xFFF34B7D)
        "c" -> Color(0xFF555555)
        "c#" -> Color(0xFF178600)
        "go" -> Color(0xFF00ADD8)
        "rust" -> Color(0xFFDEA584)
        "swift" -> Color(0xFFF05138)
        "html" -> Color(0xFFE34C26)
        "css" -> Color(0xFF563D7C)
        "ruby" -> Color(0xFF701516)
        "php" -> Color(0xFF4F5D95)
        "dart" -> Color(0xFF00B4AB)
        else -> Md3LightPrimary
    }
}
