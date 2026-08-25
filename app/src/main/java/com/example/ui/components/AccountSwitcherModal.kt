package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.AccountEntity
import com.example.ui.theme.GitBorder
import com.example.ui.theme.GitBorderStrong
import com.example.ui.theme.GitButtonPrimary
import com.example.ui.theme.GitSurface
import com.example.ui.theme.GitSurface2
import com.example.ui.theme.GitText1
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3
import com.example.ui.theme.GitTheme
import com.example.ui.theme.Md3LightError

@Composable
fun AccountSwitcherModal(
    currentAccount: AccountEntity?,
    savedAccounts: List<AccountEntity>,
    isAuthenticating: Boolean,
    authError: String?,
    onSwitchAccount: (AccountEntity) -> Unit,
    onRemoveAccount: (AccountEntity) -> Unit,
    onAddNewAccountWithToken: (String) -> Unit,
    onStartOAuthFlow: () -> Unit,
    onExplorePublicUser: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTheme = GitTheme.current
    BackHandler(onBack = onDismiss)

    var isAddingAccount by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var isTokenVisible by remember { mutableStateOf(false) }
    var publicUsernameInput by remember { mutableStateOf("") }
    var showPublicExplorerSection by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<AccountEntity?>(null) }

    val quickTokensUrl = "https://github.com/settings/tokens/new?scopes=repo,read:org,user,workflow&description=GitPilot"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .clickable(onClick = onDismiss)
            .testTag("account_switcher_modal_overlay")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 18.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(16.dp),
            color = GitSurface,
            border = BorderStroke(1.dp, GitBorderStrong),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = currentTheme.soft,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SupervisorAccount,
                                    contentDescription = null,
                                    tint = currentTheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "GitHub Accounts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GitText1,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "${savedAccounts.size} account${if (savedAccounts.size != 1) "s" else ""} connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = GitText2,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("account_switcher_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = GitText2,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp), color = GitBorder)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // List of Connected Accounts
                    item {
                        Text(
                            text = "CONNECTED ACCOUNTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = GitText3,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(savedAccounts, key = { it.id }) { acc ->
                        val isCurrent = currentAccount?.id == acc.id || (currentAccount?.username == acc.username && currentAccount?.token == acc.token)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (!isCurrent) {
                                        onSwitchAccount(acc)
                                        onDismiss()
                                    }
                                }
                                .testTag("account_item_${acc.username}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) currentTheme.soft else GitSurface2,
                            border = BorderStroke(
                                if (isCurrent) 1.5.dp else 1.dp,
                                if (isCurrent) currentTheme.primary else GitBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Avatar
                                    Surface(
                                        modifier = Modifier.size(38.dp),
                                        shape = CircleShape,
                                        border = BorderStroke(1.dp, currentTheme.primary.copy(alpha = 0.3f)),
                                        color = currentTheme.soft
                                    ) {
                                        if (acc.avatarUrl != null) {
                                            AsyncImage(
                                                model = acc.avatarUrl,
                                                contentDescription = "Avatar",
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = acc.username.take(1).uppercase(),
                                                    color = currentTheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "@${acc.username}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = GitText1,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isCurrent) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = currentTheme.primary,
                                                ) {
                                                    Text(
                                                        text = "Active",
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = acc.name ?: if (acc.token.isEmpty()) "Public Viewer Mode" else "Authenticated Token",
                                            fontSize = 11.sp,
                                            color = GitText2,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active Account",
                                            tint = currentTheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        IconButton(
                                            onClick = { accountToDelete = acc },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Remove Account",
                                                tint = GitText3,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ADD NEW ACCOUNT BUTTON / EXPANDABLE SECTION
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (!isAddingAccount) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { isAddingAccount = true }
                                    .testTag("add_new_account_btn"),
                                shape = RoundedCornerShape(12.dp),
                                color = GitSurface,
                                border = BorderStroke(1.dp, currentTheme.primary)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = currentTheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Add New Account",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = currentTheme.primary
                                    )
                                }
                            }
                        } else {
                            // Expanded Add Account Form
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = GitSurface2,
                                border = BorderStroke(1.dp, GitBorderStrong)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Add GitHub Account",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = GitText1
                                        )
                                        IconButton(
                                            onClick = {
                                                isAddingAccount = false
                                                tokenInput = ""
                                                publicUsernameInput = ""
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel",
                                                tint = GitText2,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Personal Access Token Input
                                    OutlinedTextField(
                                        value = tokenInput,
                                        onValueChange = { tokenInput = it },
                                        label = { Text("Personal Access Token", fontSize = 12.sp) },
                                        placeholder = { Text("ghp_... or github_pat_...", fontSize = 12.sp, color = GitText3) },
                                        visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (tokenInput.isNotBlank() && !isAuthenticating) {
                                                    onAddNewAccountWithToken(tokenInput)
                                                }
                                            }
                                        ),
                                        trailingIcon = {
                                            IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                                Icon(
                                                    imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = "Toggle visibility",
                                                    tint = GitText2,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription = null,
                                                tint = currentTheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("add_account_token_input"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = currentTheme.primary,
                                            unfocusedBorderColor = GitBorderStrong,
                                            focusedContainerColor = GitSurface,
                                            unfocusedContainerColor = GitSurface
                                        )
                                    )

                                    if (authError != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = authError,
                                            color = Md3LightError,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Helper Link: Generate Token on GitHub
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(quickTokensUrl))
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {}
                                            }
                                            .padding(vertical = 4.dp, horizontal = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = null,
                                            tint = currentTheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Generate Personal Access Token on GitHub",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = currentTheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Submit Button
                                    Button(
                                        onClick = {
                                            if (tokenInput.isNotBlank() && !isAuthenticating) {
                                                onAddNewAccountWithToken(tokenInput)
                                            }
                                        },
                                        enabled = tokenInput.isNotBlank() && !isAuthenticating,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("add_account_submit_btn"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GitButtonPrimary,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        if (isAuthenticating) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Validating Account...", fontSize = 13.sp)
                                        } else {
                                            Text("Connect & Switch", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Alternative: OAuth Login
                                    OutlinedButton(
                                        onClick = {
                                            onDismiss()
                                            onStartOAuthFlow()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, GitBorderStrong)
                                    ) {
                                        GitHubMarkIcon(
                                            modifier = Modifier.size(16.dp),
                                            tint = GitText1
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Sign In via GitHub OAuth Flow",
                                            fontSize = 12.sp,
                                            color = GitText1,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirm Delete Account Dialog
    accountToDelete?.let { acc ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Remove Account", fontWeight = FontWeight.Bold, color = GitText1) },
            text = { Text("Are you sure you want to remove @${acc.username} from GitPilot?", color = GitText2) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveAccount(acc)
                        accountToDelete = null
                    }
                ) {
                    Text("Remove", color = Md3LightError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel", color = GitText2)
                }
            },
            containerColor = GitSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }
}
