package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.BatchActionsModal
import com.example.ui.components.BranchSelectorSheet
import com.example.ui.components.CodeEditorView
import com.example.ui.components.CommitDialog
import com.example.ui.components.CreateOrUploadModal
import com.example.ui.components.FileTreeExplorer
import com.example.ui.components.LoginDialog
import com.example.ui.components.RepoSidebarDrawer
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.GitExplorerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                GitExplorerApp()
            }
        }
    }
}

@Composable
fun GitExplorerApp(
    viewModel: GitExplorerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle toast/error notifications
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

    // Back handling: close active file first, then close directory up, then close sidebar
    BackHandler(enabled = uiState.isSidebarOpen || uiState.activeFile != null || uiState.currentDirectoryPath.isNotEmpty()) {
        when {
            uiState.isSidebarOpen -> viewModel.setSidebarOpen(false)
            uiState.activeFile != null -> viewModel.closeFile()
            uiState.currentDirectoryPath.isNotEmpty() -> viewModel.navigateUp()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Content: either Code Editor or File Explorer
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
                    onContentChange = viewModel::updateEditorContent,
                    onToggleMarkdownPreview = viewModel::toggleMarkdownPreview,
                    onOpenCommitDialog = { viewModel.setShowCommitDialog(true) },
                    onClose = viewModel::closeFile
                )
            } else {
                FileTreeExplorer(
                    repo = uiState.selectedRepo,
                    selectedBranch = uiState.selectedBranch,
                    currentPath = uiState.currentDirectoryPath,
                    rootNode = uiState.rootExplorerNode,
                    rawTreeItems = uiState.rawTreeItems,
                    isLoadingTree = uiState.isLoadingTree,
                    searchQuery = uiState.fileSearchQuery,
                    matchingSearchFiles = uiState.matchingSearchFiles,
                    isBatchMode = uiState.isBatchMode,
                    selectedFilePaths = uiState.selectedFilePaths,
                    onBranchClick = { viewModel.setShowBranchSelector(true) },
                    onNavigateToDir = viewModel::navigateToDirectory,
                    onNavigateUp = viewModel::navigateUp,
                    onOpenFile = viewModel::openFile,
                    onSearchChange = viewModel::setFileSearchQuery,
                    onRefresh = viewModel::refreshTree,
                    onOpenNewFileDialog = { viewModel.setShowCreateUploadDialog(true) },
                    onToggleBatchMode = viewModel::toggleBatchMode,
                    onToggleSelectFile = viewModel::toggleSelectFile,
                    onSelectAllInDir = viewModel::selectAllInCurrentDirectory,
                    onClearSelection = viewModel::clearSelectedFiles,
                    onOpenBatchDeleteModal = { viewModel.setShowBatchDeleteDialog(true) },
                    onDeleteSingleFile = { path, sha ->
                        viewModel.deleteSingleFile(path, sha, "Delete $path")
                    },
                    onToggleSidebar = { viewModel.setSidebarOpen(!uiState.isSidebarOpen) }
                )
            }

            // Right Sidebar Drawer Overlay
            AnimatedVisibility(
                visible = uiState.isSidebarOpen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { viewModel.setSidebarOpen(false) }
                )
            }

            // Right Sidebar Drawer Sliding Pane
            AnimatedVisibility(
                visible = uiState.isSidebarOpen,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                RepoSidebarDrawer(
                    account = uiState.currentAccount,
                    repositories = uiState.filteredRepositories,
                    selectedRepo = uiState.selectedRepo,
                    isLoading = uiState.isLoadingRepos,
                    searchQuery = uiState.repoSearchQuery,
                    filterType = uiState.repoFilterType,
                    onSearchChange = viewModel::setRepoSearchQuery,
                    onFilterChange = viewModel::setRepoFilterType,
                    onSelectRepo = viewModel::selectRepository,
                    onRefreshRepos = viewModel::loadRepositories,
                    onOpenLogin = { viewModel.setShowLoginDialog(true) },
                    onCloseSidebar = { viewModel.setSidebarOpen(false) }
                )
            }
        }
    }

    // Modal Dialogs
    if (uiState.showLoginDialog) {
        LoginDialog(
            currentAccount = uiState.currentAccount,
            savedAccounts = uiState.accounts,
            isAuthenticating = uiState.isAuthenticating,
            authError = uiState.authError,
            onDismiss = { viewModel.setShowLoginDialog(false) },
            onLoginWithToken = viewModel::loginWithToken,
            onExplorePublic = viewModel::explorePublicUser,
            onSwitchAccount = viewModel::switchAccount,
            onRemoveAccount = viewModel::removeAccount,
            onLogoutAll = viewModel::logout
        )
    }

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
        CreateOrUploadModal(
            initialDirectory = uiState.currentDirectoryPath,
            currentBranch = uiState.selectedBranch,
            isCommitting = uiState.isCommitting,
            onDismiss = { viewModel.setShowCreateUploadDialog(false) },
            onCreateOrUpload = { targetDir, name, content, msg, branch ->
                viewModel.createOrUploadFile(targetDir, name, content, msg, branch)
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
}
