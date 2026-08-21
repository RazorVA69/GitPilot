package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.AccountEntity
import com.example.data.model.DeviceCodeResponse
import com.example.data.repository.GitHubRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    savedAccounts: List<AccountEntity>,
    isAuthenticating: Boolean,
    authError: String?,
    deviceCodeState: DeviceCodeResponse? = null,
    isStartingOAuth: Boolean = false,
    isPollingOAuth: Boolean = false,
    oauthError: String? = null,
    onLoginWithToken: (String) -> Unit,
    onStartGitHubOAuth: (clientId: String) -> Unit = {},
    onCancelOAuth: () -> Unit = {},
    onExplorePublic: (String) -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onRemoveAccount: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })

    var tokenInput by remember { mutableStateOf("") }
    var isTokenVisible by remember { mutableStateOf(false) }
    var publicUsernameInput by remember { mutableStateOf("") }
    var customClientId by remember { mutableStateOf(GitHubRepository.DEFAULT_OAUTH_CLIENT_ID) }
    var showClientIdConfig by remember { mutableStateOf(false) }

    val quickTokensUrl = "https://github.com/settings/tokens/new?scopes=repo,read:org,user,workflow&description=GitExplorer"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Md3LightBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // App Brand Header Icon (Emerald Key Squircle)
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = GitAccentSoft,
                border = BorderStroke(1.dp, GitAccent.copy(alpha = 0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = "GitHub Auth",
                        tint = GitAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = GitText1,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Sign in to browse, edit, and commit across your repositories.",
                style = MaterialTheme.typography.bodyMedium,
                color = GitText2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Auth Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_main_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GitSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                border = BorderStroke(1.dp, GitBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Segmented Control (HTML-style)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = GitSurface2
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val tabs = listOf("GitHub", "Token", "Public")
                            tabs.forEachIndexed { index, title ->
                                val isSelected = pagerState.currentPage == index
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(
                                                    page = index,
                                                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                                                )
                                            }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) GitSurface else Color.Transparent,
                                    border = if (isSelected) BorderStroke(1.dp, GitBorderStrong) else null,
                                    shadowElevation = if (isSelected) 1.dp else 0.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = title,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) GitText1 else GitText2
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Error Notification Banner
                    val activeError = authError ?: oauthError
                    if (!activeError.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Md3LightErrorContainer,
                            border = BorderStroke(1.dp, Md3LightError.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Md3LightError,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = activeError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Md3LightError,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (deviceCodeState == null) {
                                        Text(
                                            text = "OAuth Authorization",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = GitText1
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Authenticate securely via GitHub's official authorization. Grants access to your repositories and organizations without manual token creation.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GitText2
                                        )

                                        Spacer(modifier = Modifier.height(20.dp))

                                        // Dark / High-Contrast GitHub Login Button
                                        Button(
                                            onClick = { onStartGitHubOAuth(customClientId) },
                                            enabled = !isStartingOAuth && !isAuthenticating,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = GitText1,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("github_oauth_login_btn")
                                        ) {
                                            if (isStartingOAuth) {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Connecting to GitHub...", color = Color.White, fontWeight = FontWeight.Bold)
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Security,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Sign in with GitHub",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Advanced OAuth App Client ID Toggle
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            TextButton(onClick = { showClientIdConfig = !showClientIdConfig }) {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = GitText3
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (showClientIdConfig) "Hide custom client" else "Use a custom OAuth client",
                                                    fontSize = 12.sp,
                                                    color = GitText3
                                                )
                                            }
                                        }

                                        AnimatedVisibility(visible = showClientIdConfig) {
                                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                                OutlinedTextField(
                                                    value = customClientId,
                                                    onValueChange = { customClientId = it },
                                                    label = { Text("OAuth App Client ID", fontSize = 11.sp) },
                                                    placeholder = { Text("e.g. Iv1.8b22e1189912782b") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = GitAccent,
                                                        unfocusedBorderColor = GitBorderStrong
                                                    )
                                                )
                                            }
                                        }
                                    } else {
                                        // DEVICE FLOW ACTIVE AUTHORIZATION VIEW
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            color = GitSurface2,
                                            border = BorderStroke(1.dp, GitBorder)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "Authorize on GitHub",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = GitText1
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Enter this one-time code on the GitHub authorization page:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = GitText2,
                                                    textAlign = TextAlign.Center
                                                )

                                                Spacer(modifier = Modifier.height(14.dp))

                                                // Display Code Box with Copy
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color.White,
                                                    border = BorderStroke(1.5.dp, GitAccent),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            clipboardManager.setText(AnnotatedString(deviceCodeState.userCode))
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = deviceCodeState.userCode,
                                                            style = MaterialTheme.typography.headlineMedium,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = GitAccent,
                                                            letterSpacing = 3.sp
                                                        )
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.ContentCopy,
                                                            contentDescription = "Copy code",
                                                            tint = GitAccent,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))

                                                // Action to Open Browser
                                                Button(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(deviceCodeState.userCode))
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deviceCodeState.verificationUri))
                                                        context.startActivity(intent)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = GitAccent),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Open GitHub & Authorize", fontWeight = FontWeight.Bold)
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))

                                                if (isPollingOAuth) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            color = GitAccent,
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Waiting for authorization in browser...",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = GitText2
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                TextButton(onClick = onCancelOAuth) {
                                                    Text("Cancel", color = GitText2, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            1 -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Personal Access Token",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = GitText1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Paste a personal access token (classic or fine-grained) with 'repo' scope.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GitText2
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Quick 1-Click Generate Button on GitHub
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(quickTokensUrl))
                                                context.startActivity(intent)
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = GitSurface2,
                                        border = BorderStroke(1.dp, GitBorderStrong)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp), tint = GitText1)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Generate on GitHub ↗", fontSize = 12.sp, color = GitText1, fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = tokenInput,
                                        onValueChange = { tokenInput = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("login_pat_token_input"),
                                        placeholder = { Text("ghp_… or github_pat_…", color = GitText3, fontSize = 13.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                                Icon(
                                                    imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = "Toggle Visibility",
                                                    tint = GitText3,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { onLoginWithToken(tokenInput) }),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GitAccent,
                                            unfocusedBorderColor = GitBorderStrong,
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = GitSurface2
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = { onLoginWithToken(tokenInput) },
                                        enabled = !isAuthenticating && tokenInput.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = GitAccent),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("login_pat_submit_btn")
                                    ) {
                                        if (isAuthenticating) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Validating...", color = Color.White, fontWeight = FontWeight.Bold)
                                        } else {
                                            Text("Load repositories →", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }

                            2 -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Public Explorer",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = GitText1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Explore any public GitHub user's repositories without logging in. Read-only mode.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GitText2
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Quick presets chips
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("torvalds", "google", "android", "kotlin", "facebook").forEach { name ->
                                            Surface(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable { publicUsernameInput = name },
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (publicUsernameInput == name) GitAccentSoft else GitSurface2,
                                                border = if (publicUsernameInput == name) BorderStroke(1.dp, GitAccent) else BorderStroke(1.dp, GitBorder)
                                            ) {
                                                Text(
                                                    text = "@$name",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (publicUsernameInput == name) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (publicUsernameInput == name) GitAccentDeep else GitText2,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = publicUsernameInput,
                                        onValueChange = { publicUsernameInput = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("login_public_user_input"),
                                        placeholder = { Text("e.g. BlazeFTL", color = GitText3, fontSize = 13.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GitAccent,
                                            unfocusedBorderColor = GitBorderStrong,
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = GitSurface2
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = { onExplorePublic(publicUsernameInput) },
                                        enabled = !isAuthenticating && publicUsernameInput.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = GitAccent),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("login_public_submit_btn")
                                    ) {
                                        if (isAuthenticating) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Loading...", color = Color.White)
                                        } else {
                                            Text("Browse repositories →", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Saved Accounts Switcher List
            if (savedAccounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GitSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    border = BorderStroke(1.dp, GitBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Saved Accounts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = GitText1
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        for (acc in savedAccounts) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSwitchAccount(acc.id) }
                                    .padding(vertical = 8.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (acc.avatarUrl != null) {
                                        AsyncImage(
                                            model = acc.avatarUrl,
                                            contentDescription = "Avatar",
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = GitAccent
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = acc.username.take(1).uppercase(),
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
                                            text = (acc.name?.takeIf { it.isNotBlank() }) ?: acc.username,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = GitText1,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "@${acc.username}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GitText2
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onRemoveAccount(acc.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove account",
                                        tint = Md3LightError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = GitBorder, thickness = 1.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
