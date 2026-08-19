package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.ui.screens.getLanguageColor
import com.example.ui.theme.GitHubBlue
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
import com.example.ui.viewmodel.RepoFilterType

@Composable
fun RepoSidebarDrawer(
    account: AccountEntity?,
    repositories: List<GitHubRepository>,
    selectedRepo: GitHubRepository?,
    isLoading: Boolean,
    searchQuery: String,
    filterType: RepoFilterType,
    onSearchChange: (String) -> Unit,
    onFilterChange: (RepoFilterType) -> Unit,
    onSelectRepo: (GitHubRepository) -> Unit,
    onNavigateToAllRepos: () -> Unit,
    onRefreshRepos: () -> Unit,
    onOpenLogin: () -> Unit,
    onCloseSidebar: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .testTag("repo_left_sidebar_drawer"),
        color = Md3LightSurface,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // Header: Account Info & Actions
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                            Text("All Repos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = onOpenLogin,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Switch Account", tint = Md3LightPrimary)
                        }

                        if (account != null) {
                            IconButton(
                                onClick = onLogout,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Md3LightError)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Md3LightOutlineVariant)

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("sidebar_repo_search_input"),
                placeholder = { Text("Filter repos...", fontSize = 12.sp, color = Md3LightTextTertiary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Md3LightPrimary, modifier = Modifier.size(16.dp))
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Md3LightPrimary,
                    unfocusedBorderColor = Md3LightOutline,
                    focusedContainerColor = Md3LightSurfaceVariant,
                    unfocusedContainerColor = Md3LightSurfaceVariant
                )
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    RepoFilterType.ALL to "All",
                    RepoFilterType.PUBLIC to "Public",
                    RepoFilterType.PRIVATE to "Private",
                    RepoFilterType.FORKS to "Forks"
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = filterType == type,
                        onClick = { onFilterChange(type) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Md3LightPrimaryContainer,
                            selectedLabelColor = Md3LightPrimary
                        )
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
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(repositories, key = { it.id }) { repo ->
                            val isSelected = selectedRepo?.id == repo.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectRepo(repo)
                                        onCloseSidebar()
                                    }
                                    .testTag("sidebar_repo_item_${repo.name}"),
                                color = if (isSelected) Md3LightPrimaryContainer else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (repo.private) Icons.Default.Lock else Icons.Default.Book,
                                        contentDescription = null,
                                        tint = if (isSelected) Md3LightPrimary else (if (repo.private) GitHubYellow else Md3LightTextSecondary),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = repo.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Md3LightPrimary else Md3LightTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
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
                                }
                            }
                            HorizontalDivider(color = Md3LightOutlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}
