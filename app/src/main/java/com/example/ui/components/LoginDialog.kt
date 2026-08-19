package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.AccountEntity
import com.example.ui.theme.GitHubBlue
import com.example.ui.theme.GitHubDarkBorder
import com.example.ui.theme.GitHubDarkSurface
import com.example.ui.theme.GitHubDarkSurfaceVariant
import com.example.ui.theme.GitHubDarkTextMuted
import com.example.ui.theme.GitHubDarkTextPrimary
import com.example.ui.theme.GitHubDarkTextSecondary
import com.example.ui.theme.GitHubGreenBright
import com.example.ui.theme.GitHubRed
import com.example.ui.theme.GitHubYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginDialog(
    currentAccount: AccountEntity?,
    savedAccounts: List<AccountEntity>,
    isAuthenticating: Boolean,
    authError: String?,
    onDismiss: () -> Unit,
    onLoginWithToken: (String) -> Unit,
    onExplorePublic: (String) -> Unit,
    onSwitchAccount: (AccountEntity) -> Unit,
    onRemoveAccount: (AccountEntity) -> Unit,
    onLogoutAll: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var tokenInput by remember { mutableStateOf("") }
    var publicUsernameInput by remember { mutableStateOf("octocat") }
    var isTokenVisible by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(if (savedAccounts.isNotEmpty()) 0 else 1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GitHubDarkSurface,
        modifier = Modifier.testTag("login_dialog_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = GitHubBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GitHub Authentication",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = GitHubDarkTextPrimary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = GitHubDarkTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = GitHubDarkSurfaceVariant,
                contentColor = GitHubBlue
            ) {
                if (savedAccounts.isNotEmpty()) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Accounts (${savedAccounts.size})", fontSize = 12.sp) }
                    )
                }
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("PAT Token", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Explore Public", fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Error display
            if (authError != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = GitHubRed.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GitHubRed.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = authError,
                        style = MaterialTheme.typography.bodySmall,
                        color = GitHubRed,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            when (selectedTab) {
                0 -> {
                    // Saved Accounts List
                    Text(
                        text = "Switch or Manage Connected Accounts",
                        style = MaterialTheme.typography.labelMedium,
                        color = GitHubDarkTextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedAccounts, key = { it.id }) { acc ->
                            val isCurrent = currentAccount?.id == acc.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSwitchAccount(acc) },
                                color = if (isCurrent) GitHubBlue.copy(alpha = 0.15f) else GitHubDarkSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCurrent) GitHubBlue else GitHubDarkBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (acc.avatarUrl != null) {
                                        AsyncImage(
                                            model = acc.avatarUrl,
                                            contentDescription = "Avatar",
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(GitHubDarkBorder),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = GitHubDarkTextSecondary)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = acc.name ?: acc.username,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = GitHubDarkTextPrimary
                                        )
                                        Text(
                                            text = "@${acc.username}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GitHubDarkTextMuted
                                        )
                                    }

                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = GitHubBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onRemoveAccount(acc) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint = GitHubDarkTextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { selectedTab = 1 }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Another Account")
                        }
                        TextButton(onClick = onLogoutAll) {
                            Text("Logout All", color = GitHubRed)
                        }
                    }
                }

                1 -> {
                    // PAT Token Login View
                    Text(
                        text = "Personal Access Token (PAT)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GitHubDarkTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pat_token_input"),
                        placeholder = { Text("ghp_... or github_pat_...", color = GitHubDarkTextMuted, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = GitHubBlue, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                Icon(
                                    imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility",
                                    tint = GitHubDarkTextSecondary
                                )
                            }
                        },
                        visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GitHubBlue,
                            unfocusedBorderColor = GitHubDarkBorder,
                            focusedContainerColor = GitHubDarkSurfaceVariant,
                            unfocusedContainerColor = GitHubDarkSurfaceVariant,
                            focusedTextColor = GitHubDarkTextPrimary,
                            unfocusedTextColor = GitHubDarkTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Token Permissions Guide Box
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = GitHubDarkSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GitHubDarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = GitHubBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Required Token Scopes",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GitHubBlue
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• repo (Full control of private/public repositories, commits, and trees)\n• read:org (Optional, to list organization repositories)",
                                style = MaterialTheme.typography.bodySmall,
                                color = GitHubDarkTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { onLoginWithToken(tokenInput) },
                        enabled = !isAuthenticating && tokenInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = GitHubGreenBright),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("submit_token_btn")
                    ) {
                        if (isAuthenticating) {
                            CircularProgressIndicator(
                                color = GitHubDarkSurface,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecting to GitHub...", color = GitHubDarkSurface)
                        } else {
                            Text("Connect Account with PAT", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                2 -> {
                    // Explore Public Mode
                    Text(
                        text = "Explore Public GitHub User",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GitHubDarkTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = publicUsernameInput,
                        onValueChange = { publicUsernameInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("public_username_input"),
                        placeholder = { Text("e.g. torvalds, square, octocat", color = GitHubDarkTextMuted, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Public, contentDescription = null, tint = GitHubYellow, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GitHubBlue,
                            unfocusedBorderColor = GitHubDarkBorder,
                            focusedContainerColor = GitHubDarkSurfaceVariant,
                            unfocusedContainerColor = GitHubDarkSurfaceVariant,
                            focusedTextColor = GitHubDarkTextPrimary,
                            unfocusedTextColor = GitHubDarkTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Public mode allows browsing repositories without logging in. (Rate limited to 60 requests/hr by GitHub). To edit and commit, log in with a PAT token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GitHubDarkTextSecondary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { onExplorePublic(publicUsernameInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = GitHubBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("explore_public_btn")
                    ) {
                        Text("Browse Public Repositories", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
