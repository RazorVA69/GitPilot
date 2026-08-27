package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AccountSwitcherModal
import com.example.ui.components.BatchActionsModal
import com.example.ui.components.BranchSelectorSheet
import com.example.ui.components.CodeEditorView
import com.example.ui.components.CommitDialog
import com.example.ui.components.CreateOrUploadModal
import com.example.ui.components.FileTreeExplorer
import com.example.ui.components.GitHubTerminalModal
import com.example.ui.components.RepoSidebarDrawer
import com.example.ui.components.SearchAcrossFilesView
import com.example.ui.components.SettingsModal
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.RepoListScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.GitExplorerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GitExplorerApp()
        }
    }
}

@Composable
fun GitExplorerApp(
    viewModel: GitExplorerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    MyApplicationTheme(
        themeId = uiState.selectedThemeId,
        isBgTintEnabled = uiState.isThemeBgTintEnabled
    ) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Request Storage Permission for Android 10 and below, or All Files Access for Android 11+
    val legacyStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Legacy permissions handled
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        } else {
            legacyStorageLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    // Feedback Notifications
    LaunchedEffect(uiState.toastOrMessage) {
        uiState.toastOrMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearToast()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(
                message = err,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    // Predictive Back Handling
    BackHandler(
        enabled = uiState.showSettingsDialog ||
                uiState.showAccountSwitcherDialog ||
                uiState.showTerminal ||
                uiState.showCreateUploadDialog ||
                uiState.showBatchDeleteDialog ||
                uiState.showBranchSelector ||
                uiState.isLeftDrawerOpen ||
                uiState.activeFile != null ||
                uiState.activeFilePath != null ||
                uiState.showSearchAcrossFiles ||
                uiState.isBatchMode ||
                uiState.selectedFilePaths.isNotEmpty() ||
                (uiState.currentScreen == AppScreen.EXPLORER)
    ) {
        when {
            uiState.showSettingsDialog -> viewModel.setShowSettings(false)
            uiState.showAccountSwitcherDialog -> viewModel.setShowAccountSwitcher(false)
            uiState.showTerminal -> viewModel.closeTerminal()
            uiState.showCreateUploadDialog -> viewModel.setShowCreateUploadDialog(false)
            uiState.showBatchDeleteDialog -> viewModel.setShowBatchDeleteDialog(false)
            uiState.showBranchSelector -> viewModel.setShowBranchSelector(false)
            uiState.isLeftDrawerOpen -> viewModel.setLeftDrawerOpen(false)
            uiState.activeFile != null || uiState.activeFilePath != null -> viewModel.closeFile()
            uiState.showSearchAcrossFiles -> viewModel.closeSearchAcrossFiles()
            uiState.isBatchMode || uiState.selectedFilePaths.isNotEmpty() -> viewModel.clearSelectionAndBatchMode()
            uiState.currentDirectoryPath.isNotEmpty() -> viewModel.navigateUp()
            uiState.currentScreen == AppScreen.EXPLORER -> viewModel.navigateToRepoList()
        }
    }

    // Root Scaffold with zero-inset padding so top bars align seamlessly with zero top gap
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(uiState.isLeftDrawerOpen, uiState.currentScreen) {
                    if (uiState.currentScreen == AppScreen.REPO_LIST) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            if (!uiState.isLeftDrawerOpen) {
                                // Swipe from left edge (within 120dp/px) towards right opens drawer
                                if (change.position.x < 240f && dragAmount > 15f) {
                                    viewModel.setLeftDrawerOpen(true)
                                }
                            } else {
                                // Swiping left closes drawer
                                if (dragAmount < -15f) {
                                    viewModel.setLeftDrawerOpen(false)
                                }
                            }
                        }
                    }
                }
        ) {
            // Main Screen Routing
            when (uiState.currentScreen) {
                AppScreen.LOGIN -> {
                    LoginScreen(
                        savedAccounts = uiState.accounts,
                        isAuthenticating = uiState.isAuthenticating,
                        authError = uiState.authError,
                        deviceCodeState = uiState.deviceCodeState,
                        isStartingOAuth = uiState.isStartingOAuth,
                        isPollingOAuth = uiState.isPollingOAuth,
                        oauthError = uiState.oauthError,
                        onLoginWithToken = viewModel::loginWithToken,
                        onStartGitHubOAuth = viewModel::startGitHubOAuthLogin,
                        onCancelOAuth = viewModel::cancelGitHubOAuthLogin,
                        onExplorePublic = viewModel::explorePublicUser,
                        onSwitchAccount = viewModel::switchAccount,
                        onRemoveAccount = viewModel::removeAccount
                    )
                }

                AppScreen.REPO_LIST -> {
                    RepoListScreen(
                        account = uiState.currentAccount,
                        repositories = uiState.filteredRepositories,
                        allRepositories = uiState.repositories,
                        isLoading = uiState.isLoadingRepos,
                        searchQuery = uiState.repoSearchQuery,
                        filterType = uiState.repoFilterType,
                        sortOption = uiState.repoSortOption,
                        pinnedRepoIds = uiState.pinnedRepoIds,
                        workingRepoId = uiState.workingRepoId,
                        onSearchChange = viewModel::setRepoSearchQuery,
                        onFilterChange = viewModel::setRepoFilterType,
                        onSortChange = viewModel::setRepoSortOption,
                        onTogglePinRepo = viewModel::togglePinRepo,
                        onSetWorkingRepo = viewModel::setWorkingRepo,
                        onSelectRepo = viewModel::selectRepository,
                        onRefresh = viewModel::loadRepositories,
                        onOpenLeftDrawer = { viewModel.setLeftDrawerOpen(true) },
                        onOpenSettings = { viewModel.setShowSettings(true) },
                        onOpenAccountSwitcher = { viewModel.setShowAccountSwitcher(true) },
                        onLogout = viewModel::logout
                    )
                }

                AppScreen.EXPLORER -> {
                    if (uiState.activeFile != null || uiState.activeFilePath != null) {
                        CodeEditorView(
                            file = uiState.activeFile,
                            filePath = uiState.activeFilePath ?: "",
                            content = uiState.activeFileContent,
                            originalContent = uiState.activeFileOriginalContent,
                            isLoading = uiState.isLoadingFile,
                            isDirty = uiState.isFileDirty,
                            isMarkdownPreviewMode = uiState.isMarkdownPreviewMode,
                            selectedBranch = uiState.selectedBranch,
                            initialLine = uiState.initialEditorLine,
                            openTabs = uiState.openEditorTabs,
                            pinnedFiles = uiState.pinnedFiles,
                            allTreeItems = uiState.rawTreeItems,
                            onContentChange = viewModel::updateEditorContent,
                            onToggleMarkdownPreview = viewModel::toggleMarkdownPreview,
                            onOpenCommitDialog = { viewModel.setShowCommitDialog(true) },
                            onClose = viewModel::closeFile,
                            onSelectTab = viewModel::switchEditorTab,
                            onCloseTab = viewModel::closeEditorTab,
                            onTogglePinTab = viewModel::togglePinEditorTab,
                            onTogglePinFile = viewModel::togglePinFile,
                            onOpenFileFromFolder = viewModel::openFile
                        )
                    } else if (uiState.showSearchAcrossFiles) {
                        SearchAcrossFilesView(
                            repoName = uiState.selectedRepo?.name ?: "",
                            branch = uiState.selectedBranch,
                            searchQuery = uiState.searchAcrossFilesQuery,
                            selectedPath = uiState.searchAcrossFilesPath,
                            pinnedFolders = uiState.pinnedFolders,
                            allTreeItems = uiState.rawTreeItems,
                            isSearching = uiState.isSearchingAcrossFiles,
                            progress = uiState.searchAcrossFilesProgress,
                            results = uiState.searchAcrossFilesResults,
                            onSearchQueryChange = viewModel::setSearchAcrossFilesQuery,
                            onPathSelected = viewModel::setSearchAcrossFilesPath,
                            onSelectMatch = { path, line ->
                                viewModel.openFileAtLine(path, line)
                            },
                            onRefreshSearch = viewModel::refreshSearchAcrossFiles,
                            onClose = viewModel::closeSearchAcrossFiles
                        )
                    } else {
                        FileTreeExplorer(
                            repo = uiState.selectedRepo,
                            selectedBranch = uiState.selectedBranch,
                            currentPath = uiState.currentDirectoryPath,
                            rootNode = uiState.rootExplorerNode,
                            rawTreeItems = uiState.rawTreeItems,
                            isLoadingTree = uiState.isLoadingTree,
                            syncStatus = uiState.syncStatus,
                            lastSyncedAt = uiState.lastSyncedAt,
                            searchQuery = uiState.fileSearchQuery,
                            matchingSearchFiles = uiState.matchingSearchFiles,
                            isBatchMode = uiState.isBatchMode,
                            selectedFilePaths = uiState.selectedFilePaths,
                            pinnedFolders = uiState.pinnedFolders,
                            fileTreeSortOption = uiState.fileTreeSortOption,
                            isFileTreeSortReversed = uiState.isFileTreeSortReversed,
                            clipboard = uiState.clipboard,
                            isPasting = uiState.isPasting,
                            onBranchClick = { viewModel.setShowBranchSelector(true) },
                            onNavigateToDir = viewModel::navigateToDirectory,
                            onNavigateUp = viewModel::navigateUp,
                            onNavigateToReposList = viewModel::navigateToRepoList,
                            onOpenFile = viewModel::openFile,
                            onSearchChange = viewModel::setFileSearchQuery,
                            onRefresh = viewModel::refreshTree,
                            onOpenNewFileDialog = { viewModel.setShowCreateUploadDialog(true) },
                            onToggleBatchMode = viewModel::toggleBatchMode,
                            onToggleSelectFile = viewModel::toggleSelectFile,
                            onToggleSelectFolder = viewModel::toggleSelectFolder,
                            onSelectAllInDir = viewModel::selectAllInCurrentDirectory,
                            onClearSelection = viewModel::clearSelectedFiles,
                            onOpenBatchDeleteModal = { viewModel.setShowBatchDeleteDialog(true) },
                            onDeleteSingleFile = { path, sha, isDir ->
                                viewModel.deleteSingleFile(path = path, sha = sha, isDirectory = isDir)
                            },
                            onCutItem = { path, isDir, sha -> viewModel.cutItem(path, isDir, sha) },
                            onCopyItem = { path, isDir, sha -> viewModel.copyItem(path, isDir, sha) },
                            onRenameItem = { path, newName, isDir, sha -> viewModel.renameItem(path, newName, isDir, sha) },
                            onCutSelection = viewModel::cutSelection,
                            onCopySelection = viewModel::copySelection,
                            onClearClipboard = viewModel::clearClipboard,
                            onPasteClipboard = viewModel::pasteClipboard,
                            onTogglePinFolder = viewModel::togglePinFolder,
                            onFileTreeSortChange = viewModel::setFileTreeSortOption,
                            onToggleFileTreeSortReverse = viewModel::toggleFileTreeSortReverse,
                            onToggleLeftDrawer = { viewModel.setLeftDrawerOpen(!uiState.isLeftDrawerOpen) },
                            onOpenSearchAcrossFiles = { viewModel.openSearchAcrossFiles(uiState.currentDirectoryPath) },
                            onOpenTerminal = { viewModel.openTerminal() },
                            onOpenSettings = { viewModel.setShowSettings(true) }
                        )
                    }
                }
            }

            // Left Sidebar Drawer Scrim (Click to dismiss)
            AnimatedVisibility(
                visible = uiState.isLeftDrawerOpen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { viewModel.setLeftDrawerOpen(false) }
                )
            }

            // Left Sidebar Drawer Panel (Positioned on the Left Side)
            AnimatedVisibility(
                visible = uiState.isLeftDrawerOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                RepoSidebarDrawer(
                    account = uiState.currentAccount,
                    repositories = uiState.filteredRepositories,
                    allRepositories = uiState.repositories,
                    selectedRepo = uiState.selectedRepo,
                    isLoading = uiState.isLoadingRepos,
                    searchQuery = uiState.repoSearchQuery,
                    filterType = uiState.repoFilterType,
                    sortOption = uiState.repoSortOption,
                    pinnedRepoIds = uiState.pinnedRepoIds,
                    workingRepoId = uiState.workingRepoId,
                    onSearchChange = viewModel::setRepoSearchQuery,
                    onFilterChange = viewModel::setRepoFilterType,
                    onSortChange = viewModel::setRepoSortOption,
                    onTogglePinRepo = viewModel::togglePinRepo,
                    onSetWorkingRepo = viewModel::setWorkingRepo,
                    onSelectRepo = viewModel::selectRepository,
                    onNavigateToAllRepos = viewModel::navigateToRepoList,
                    onRefreshRepos = viewModel::loadRepositories,
                    onOpenLogin = viewModel::navigateToLogin,
                    onCloseSidebar = { viewModel.setLeftDrawerOpen(false) },
                    onLogout = viewModel::logout
                )
            }
        }
    }

    // Modal Sheets and Dialogs
    if (uiState.showBranchSelector) {
        BranchSelectorSheet(
            branches = uiState.branches,
            selectedBranch = uiState.selectedBranch,
            isLoading = uiState.isLoadingBranches,
            onDismiss = { viewModel.setShowBranchSelector(false) },
            onSelectBranch = viewModel::selectBranch
        )
    }

    if (uiState.showCommitDialog && uiState.activeFilePath != null) {
        CommitDialog(
            filePath = uiState.activeFilePath ?: "",
            currentBranch = uiState.selectedBranch,
            isCommitting = uiState.isCommitting,
            onDismiss = { viewModel.setShowCommitDialog(false) },
            onConfirmCommit = { msg, branch ->
                viewModel.commitActiveFile(msg, branch)
            }
        )
    }

    if (uiState.showCreateUploadDialog) {
        val existingDirs = remember(uiState.rawTreeItems) {
            uiState.rawTreeItems.filter { it.isDirectory }.map { it.path }
        }

        CreateOrUploadModal(
            initialDirectory = uiState.currentDirectoryPath,
            currentBranch = uiState.selectedBranch,
            existingDirectories = existingDirs,
            isCommitting = uiState.isCommitting,
            isUploading = uiState.isUploadingFiles,
            uploadProgress = uiState.uploadProgress,
            onDismiss = { viewModel.setShowCreateUploadDialog(false) },
            onCreateOrUpload = { targetDir, name, content, msg, branch ->
                viewModel.createOrUploadFile(targetDir, name, content, msg, branch)
            },
            onUploadBatch = { targetDir, files, msg, branch ->
                viewModel.uploadBatchFiles(targetDir, files, msg, branch)
            }
        )
    }

    if (uiState.showBatchDeleteDialog) {
        BatchActionsModal(
            selectedFiles = uiState.selectedFilePaths.toList(),
            isDeleting = uiState.isBatchDeleting,
            progress = uiState.batchProgress,
            onDismiss = { viewModel.setShowBatchDeleteDialog(false) },
            onConfirmDelete = { msg ->
                viewModel.deleteSelectedFiles(msg)
            }
        )
    }

    if (uiState.showTerminal) {
        GitHubTerminalModal(
            repo = uiState.selectedRepo,
            selectedBranch = uiState.selectedBranch,
            currentPath = uiState.terminalWorkingDir,
            terminalLines = uiState.terminalLines,
            isExecuting = uiState.isTerminalExecuting,
            onDismiss = viewModel::closeTerminal,
            onExecuteCommand = viewModel::executeTerminalCommand,
            onClearTerminal = viewModel::clearTerminal
        )
    }

    if (uiState.showSettingsDialog) {
        SettingsModal(
            selectedThemeId = uiState.selectedThemeId,
            isBgTintEnabled = uiState.isThemeBgTintEnabled,
            onSelectTheme = viewModel::setTheme,
            onToggleBgTint = viewModel::setThemeBgTintEnabled,
            onDismiss = { viewModel.setShowSettings(false) }
        )
    }

    if (uiState.showAccountSwitcherDialog) {
        AccountSwitcherModal(
            currentAccount = uiState.currentAccount,
            savedAccounts = uiState.accounts,
            isAuthenticating = uiState.isAuthenticating,
            authError = uiState.authError,
            onSwitchAccount = viewModel::switchAccount,
            onRemoveAccount = viewModel::removeAccount,
            onAddNewAccountWithToken = viewModel::addNewAccountWithToken,
            onStartOAuthFlow = {
                viewModel.setShowAccountSwitcher(false)
                viewModel.startGitHubOAuthLogin()
            },
            onExplorePublicUser = { user ->
                viewModel.setShowAccountSwitcher(false)
                viewModel.explorePublicUser(user)
            },
            onDismiss = { viewModel.setShowAccountSwitcher(false) }
        )
    }
    }
}
