package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.AccountEntity
import com.example.ui.theme.GitHubBlue
import com.example.ui.theme.GitHubGreen
import com.example.ui.theme.GitHubPurple
import com.example.ui.theme.GitHubYellow
import com.example.ui.theme.Md3LightBackground
import com.example.ui.theme.Md3LightError
import com.example.ui.theme.Md3LightErrorContainer
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
fun LoginScreen(
    savedAccounts: List<AccountEntity>,
    isAuthenticating: Boolean,
    authError: String?,
    onLoginWithToken: (String) -> Unit,
    onExplorePublic: (String) -> Unit,
    onSwitchAccount: (AccountEntity) -> Unit,
    onRemoveAccount: (AccountEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var tokenInput by remember { mutableStateOf("") }
    var publicUsernameInput by remember { mutableStateOf("") }
    var isTokenVisible by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(if (savedAccounts.isNotEmpty()) 0 else 1) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Md3LightBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Hero Logo Card
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(20.dp),
            color = Md3LightPrimaryContainer,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "GitExplorer Logo",
                    tint = Md3LightPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title & Description
        Text(
            text = "Welcome to GitExplorer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Md3LightTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Fast GitHub file navigation, directory uploads, multi-file editing, and direct branch commits.",
            style = MaterialTheme.typography.bodyMedium,
            color = Md3LightTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Main Login Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_main_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Md3LightSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Md3LightOutlineVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Md3LightSurfaceVariant,
                    contentColor = Md3LightPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    if (savedAccounts.isNotEmpty()) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Saved (${savedAccounts.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        )
                    }
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("PAT Token", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Public User", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Error Message Display
                if (authError != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Md3LightErrorContainer
                    ) {
                        Text(
                            text = authError,
                            style = MaterialTheme.typography.bodySmall,
                            color = Md3LightError,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                when (selectedTab) {
                    0 -> {
                        // Saved Accounts
                        Text(
                            text = "Choose an account to continue",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Md3LightTextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (account in savedAccounts) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSwitchAccount(account) }
                                        .testTag("saved_account_${account.username}"),
                                    color = Md3LightSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Md3LightOutlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (account.avatarUrl != null) {
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

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = account.name ?: account.username,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Md3LightTextPrimary
                                            )
                                            Text(
                                                text = "@${account.username}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Md3LightTextSecondary
                                            )
                                        }

                                        IconButton(
                                            onClick = { onRemoveAccount(account) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = Md3LightTextTertiary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("+ Add New Token Account", color = Md3LightPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    1 -> {
                        // Personal Access Token (PAT)
                        Text(
                            text = "GitHub Personal Access Token (PAT)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Md3LightTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Generate a Classic or Fine-Grained token with 'repo' scope from GitHub Settings -> Developer settings -> Personal access tokens.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Md3LightTextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_pat_input"),
                            placeholder = {
                                Text("ghp_xxxxxxxxxxxxxxxxxxxx or github_pat_...", color = Md3LightTextTertiary, fontSize = 13.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Md3LightPrimary, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                    Icon(
                                        imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility",
                                        tint = Md3LightTextSecondary
                                    )
                                }
                            },
                            visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (tokenInput.isNotBlank()) onLoginWithToken(tokenInput)
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Md3LightPrimary,
                                unfocusedBorderColor = Md3LightOutline,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Md3LightSurfaceVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Permissions checklist banner
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Md3LightSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Md3LightOutlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = GitHubGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Required Permissions", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GitHubGreen)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "✔ repo (Access public/private repositories, commits, and trees)\n✔ Local on-device storage only (Tokens are never sent elsewhere)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Md3LightTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { onLoginWithToken(tokenInput) },
                            enabled = !isAuthenticating && tokenInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("login_submit_btn")
                        ) {
                            if (isAuthenticating) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Validating PAT Token...", color = Color.White, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Log In & Load Repositories", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    2 -> {
                        // Public Mode
                        Text(
                            text = "Explore Any GitHub User Public Repos",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Md3LightTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Browse public repositories without entering credentials. (Read-only mode).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Md3LightTextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = publicUsernameInput,
                            onValueChange = { publicUsernameInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_public_username_input"),
                            placeholder = { Text("e.g. torvalds, square, google, octocat", color = Md3LightTextTertiary, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Public, contentDescription = null, tint = GitHubYellow, modifier = Modifier.size(20.dp))
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                onExplorePublic(publicUsernameInput.ifBlank { "octocat" })
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Md3LightPrimary,
                                unfocusedBorderColor = Md3LightOutline,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Md3LightSurfaceVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick demo buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("octocat", "torvalds", "google", "square").forEach { sample ->
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { publicUsernameInput = sample }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = Md3LightSurfaceVariant
                                ) {
                                    Text(sample, style = MaterialTheme.typography.labelSmall, color = Md3LightPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { onExplorePublic(publicUsernameInput.ifBlank { "octocat" }) },
                            colors = ButtonDefaults.buttonColors(containerColor = Md3LightPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("login_explore_public_btn")
                        ) {
                            Text("Explore Public Repositories", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
