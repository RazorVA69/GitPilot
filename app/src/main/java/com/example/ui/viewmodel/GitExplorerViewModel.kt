package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AccountEntity
import com.example.data.local.AppDatabase
import com.example.data.local.SavedRepoEntity
import com.example.data.model.DeviceCodeResponse
import com.example.data.model.ExplorerNode
import com.example.data.model.FileContentResponse
import com.example.data.model.GitHubBranch
import com.example.data.model.GitHubRepository
import com.example.data.model.GitTreeItem
import com.example.data.repository.GitHubRepository as GitHubRepoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppScreen {
    LOGIN,
    REPO_LIST,
    EXPLORER
}

enum class RepoFilterType { ALL, PUBLIC, PRIVATE, FORKS }

enum class RepoSortOption {
    LAST_ACTIVITY,
    NAME_ASC,
    NAME_DESC,
    STARS_DESC
}

enum class FileTreeSortOption {
    FOLDERS_FIRST,
    FILES_FIRST,
    NAME_ASC,
    NAME_DESC
}

enum class SyncStatus { IDLE, SYNCING, SYNCED, ERROR }

data class GitExplorerUiState(
    val currentScreen: AppScreen = AppScreen.LOGIN,
    val currentAccount: AccountEntity? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val isAuthenticating: Boolean = false,
    val authError: String? = null,

    // GitHub OAuth Device Login Flow
    val deviceCodeState: DeviceCodeResponse? = null,
    val isStartingOAuth: Boolean = false,
    val isPollingOAuth: Boolean = false,
    val oauthError: String? = null,

    // Repositories
    val repositories: List<GitHubRepository> = emptyList(),
    val filteredRepositories: List<GitHubRepository> = emptyList(),
    val isLoadingRepos: Boolean = false,
    val repoSearchQuery: String = "",
    val repoFilterType: RepoFilterType = RepoFilterType.ALL,
    val repoSortOption: RepoSortOption = RepoSortOption.LAST_ACTIVITY,
    val pinnedRepoIds: Set<Long> = emptySet(),
    val workingRepoId: Long? = null,
    val selectedRepo: GitHubRepository? = null,

    // Branches & Trees
    val branches: List<GitHubBranch> = emptyList(),
    val selectedBranch: String = "main",
    val isLoadingBranches: Boolean = false,
    val isLoadingTree: Boolean = false,
    val rawTreeItems: List<GitTreeItem> = emptyList(),
    val rootExplorerNode: ExplorerNode? = null,
    val currentDirectoryPath: String = "", // empty = root
    val fileSearchQuery: String = "",
    val matchingSearchFiles: List<GitTreeItem> = emptyList(),
    val pinnedFolders: Set<String> = emptySet(), // Pinned folders for current repo
    val fileTreeSortOption: FileTreeSortOption = FileTreeSortOption.FOLDERS_FIRST,
    val isFileTreeSortReversed: Boolean = false,

    // Live Sync
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val lastSyncedAt: Long? = null,

    // Active File & Editor
    val activeFile: FileContentResponse? = null,
    val activeFilePath: String? = null,
    val activeFileSha: String? = null,
    val activeFileContent: String = "",
    val activeFileOriginalContent: String = "",
    val isLoadingFile: Boolean = false,
    val isFileDirty: Boolean = false,
    val isMarkdownPreviewMode: Boolean = false,

    // Batch operations
    val isBatchMode: Boolean = false,
    val selectedFilePaths: Set<String> = emptySet(),
    val isBatchDeleting: Boolean = false,
    val batchProgress: Triple<Int, Int, String>? = null,

    // Upload Operations
    val isUploadingFiles: Boolean = false,
    val uploadProgress: Triple<Int, Int, String>? = null,

    // Dialogs & Sheets
    val isLeftDrawerOpen: Boolean = false,
    val showCommitDialog: Boolean = false,
    val showCreateUploadDialog: Boolean = false,
    val showBatchDeleteDialog: Boolean = false,
    val showBranchSelector: Boolean = false,
    val isCommitting: Boolean = false,

    // Feedback
    val toastOrMessage: String? = null,
    val errorMessage: String? = null
)

class GitExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = GitHubRepoRepository(appDao = db.appDao())
    private val prefs: SharedPreferences = application.getSharedPreferences("git_explorer_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(GitExplorerUiState())
    val uiState: StateFlow<GitExplorerUiState> = _uiState.asStateFlow()

    private var oauthPollingJob: Job? = null
    private var autoSyncJob: Job? = null
    private var hasAttemptedAutoOpenWorkingRepo = false

    init {
        // Load initial preferences
        val savedPinnedRepos = prefs.getStringSet("pinned_repos", emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        val savedWorkingRepoId = if (prefs.contains("working_repo_id")) prefs.getLong("working_repo_id", -1L).takeIf { it != -1L } else null
        val savedSort = try {
            RepoSortOption.valueOf(prefs.getString("repo_sort_option", RepoSortOption.LAST_ACTIVITY.name) ?: RepoSortOption.LAST_ACTIVITY.name)
        } catch (e: Exception) {
            RepoSortOption.LAST_ACTIVITY
        }

        _uiState.update {
            it.copy(
                pinnedRepoIds = savedPinnedRepos,
                workingRepoId = savedWorkingRepoId,
                repoSortOption = savedSort
            )
        }

        // Collect current account changes
        viewModelScope.launch {
            repository.currentAccountFlow.collectLatest { account ->
                _uiState.update { it.copy(currentAccount = account) }
                if (account != null) {
                    loadRepositories()
                    _uiState.update {
                        if (it.currentScreen == AppScreen.LOGIN) it.copy(currentScreen = AppScreen.REPO_LIST)
                        else it
                    }
                } else {
                    _uiState.update { it.copy(currentScreen = AppScreen.LOGIN) }
                }
            }
        }

        // Collect all saved accounts
        viewModelScope.launch {
            repository.allAccountsFlow.collectLatest { list ->
                _uiState.update { it.copy(accounts = list) }
            }
        }

        // Background Auto-Sync every 2 minutes for active repository
        startPeriodicAutoSync()
    }

    private fun startPeriodicAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            while (isActive) {
                delay(120_000L) // 2 minutes
                val state = _uiState.value
                if (state.currentScreen == AppScreen.EXPLORER && state.selectedRepo != null && !state.isFileDirty && !state.isLoadingTree) {
                    syncActiveRepository(isSilent = true)
                }
            }
        }
    }

    // ==========================================
    // AUTHENTICATION & LOGIN FLOWS
    // ==========================================

    fun loginWithToken(token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            _uiState.update { it.copy(authError = "Please enter a valid Personal Access Token") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, authError = null) }
            val result = repository.validateAndSaveToken(cleanToken)
            if (result.isSuccess) {
                val account = result.getOrNull()
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        currentAccount = account,
                        currentScreen = AppScreen.REPO_LIST,
                        toastOrMessage = "Welcome, @${account?.username}!"
                    )
                }
                loadRepositories()
            } else {
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        authError = result.exceptionOrNull()?.message ?: "Authentication failed. Check your token permissions."
                    )
                }
            }
        }
    }

    fun startGitHubOAuthLogin(clientId: String = GitHubRepoRepository.DEFAULT_OAUTH_CLIENT_ID) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isStartingOAuth = true,
                    oauthError = null,
                    deviceCodeState = null
                )
            }

            val result = repository.requestDeviceCode(clientId)
            if (result.isSuccess) {
                val codeResp = result.getOrNull()!!
                _uiState.update {
                    it.copy(
                        isStartingOAuth = false,
                        deviceCodeState = codeResp,
                        isPollingOAuth = true
                    )
                }
                beginPollingOAuthToken(codeResp, clientId)
            } else {
                _uiState.update {
                    it.copy(
                        isStartingOAuth = false,
                        oauthError = result.exceptionOrNull()?.message ?: "Failed to initiate GitHub login."
                    )
                }
            }
        }
    }

    private fun beginPollingOAuthToken(codeResp: DeviceCodeResponse, clientId: String = GitHubRepoRepository.DEFAULT_OAUTH_CLIENT_ID) {
        oauthPollingJob?.cancel()
        oauthPollingJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val expiryTime = startTime + (codeResp.expiresIn * 1000L)
            var interval = (codeResp.interval.coerceAtLeast(5)) * 1000L

            while (isActive && System.currentTimeMillis() < expiryTime) {
                delay(interval)
                val pollResult = repository.pollDeviceToken(clientId, codeResp.deviceCode)
                if (pollResult.isSuccess) {
                    val tokenResp = pollResult.getOrNull()
                    val token = tokenResp?.accessToken

                    if (!token.isNullOrBlank()) {
                        val authResult = repository.validateAndSaveToken(token)
                        if (authResult.isSuccess) {
                            val account = authResult.getOrNull()
                            _uiState.update {
                                it.copy(
                                    isPollingOAuth = false,
                                    deviceCodeState = null,
                                    currentAccount = account,
                                    currentScreen = AppScreen.REPO_LIST,
                                    toastOrMessage = "Successfully logged in as @${account?.username}!"
                                )
                            }
                            loadRepositories()
                            return@launch
                        } else {
                            _uiState.update {
                                it.copy(
                                    isPollingOAuth = false,
                                    deviceCodeState = null,
                                    oauthError = "Failed to load account profile with received token."
                                )
                            }
                            return@launch
                        }
                    }

                    when (tokenResp?.error) {
                        "authorization_pending" -> { /* continue polling */ }
                        "slow_down" -> { interval += 5000L }
                        "expired_token" -> {
                            _uiState.update {
                                it.copy(
                                    isPollingOAuth = false,
                                    deviceCodeState = null,
                                    oauthError = "Login session expired. Please start over."
                                )
                            }
                            return@launch
                        }
                        "access_denied" -> {
                            _uiState.update {
                                it.copy(
                                    isPollingOAuth = false,
                                    deviceCodeState = null,
                                    oauthError = "Login was cancelled or denied on GitHub."
                                )
                            }
                            return@launch
                        }
                    }
                }
            }

            if (isActive) {
                _uiState.update {
                    it.copy(
                        isPollingOAuth = false,
                        deviceCodeState = null,
                        oauthError = "Login timed out. Please try again."
                    )
                }
            }
        }
    }

    fun cancelGitHubOAuthLogin() {
        oauthPollingJob?.cancel()
        _uiState.update {
            it.copy(
                isPollingOAuth = false,
                isStartingOAuth = false,
                deviceCodeState = null,
                oauthError = null
            )
        }
    }

    fun explorePublicUser(username: String) {
        val clean = username.trim().trim('@')
        if (clean.isEmpty()) {
            _uiState.update { it.copy(authError = "Please enter a username") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, authError = null) }
            val result = repository.getPublicUserRepositories(clean)
            if (result.isSuccess) {
                val repos = result.getOrNull() ?: emptyList()
                val mockAccount = AccountEntity(
                    username = clean,
                    token = "",
                    name = clean,
                    avatarUrl = "https://github.com/$clean.png",
                    isCurrent = true
                )
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        currentAccount = mockAccount,
                        repositories = repos,
                        currentScreen = AppScreen.REPO_LIST,
                        toastOrMessage = "Viewing public repos of @$clean"
                    )
                }
                applyRepoSorting()
            } else {
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        authError = result.exceptionOrNull()?.message ?: "User not found or network error"
                    )
                }
            }
        }
    }

    fun switchAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.setCurrentAccount(account.username)
            _uiState.update {
                it.copy(
                    currentAccount = account,
                    currentScreen = AppScreen.REPO_LIST,
                    isLeftDrawerOpen = false,
                    toastOrMessage = "Switched to @${account.username}"
                )
            }
            loadRepositories()
        }
    }

    fun switchAccount(id: Long) {
        viewModelScope.launch {
            repository.switchAccount(id)
            val acc = _uiState.value.accounts.find { it.id == id }
            _uiState.update {
                it.copy(
                    currentAccount = acc,
                    currentScreen = AppScreen.REPO_LIST,
                    isLeftDrawerOpen = false,
                    toastOrMessage = "Switched account"
                )
            }
            loadRepositories()
        }
    }

    fun removeAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.deleteAccount(account.username)
            _uiState.update { it.copy(toastOrMessage = "Removed @${account.username}") }
        }
    }

    fun removeAccount(id: Long) {
        viewModelScope.launch {
            repository.removeAccount(id)
            _uiState.update { it.copy(toastOrMessage = "Account removed") }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.LOGIN,
                    currentAccount = null,
                    repositories = emptyList(),
                    filteredRepositories = emptyList(),
                    selectedRepo = null,
                    isLeftDrawerOpen = false,
                    toastOrMessage = "Logged out"
                )
            }
        }
    }

    // ==========================================
    // NAVIGATION
    // ==========================================

    fun navigateToLogin() {
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.LOGIN,
                isLeftDrawerOpen = false
            )
        }
    }

    fun navigateToRepoList() {
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.REPO_LIST,
                isLeftDrawerOpen = false,
                activeFile = null,
                activeFilePath = null
            )
        }
    }

    fun setLeftDrawerOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isLeftDrawerOpen = isOpen) }
    }

    // ==========================================
    // REPOSITORIES & WORKING REPO & PINNING
    // ==========================================

    fun loadRepositories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRepos = true) }
            val token = _uiState.value.currentAccount?.token
            val result = repository.getRepositories(token)
            if (result.isSuccess) {
                val repos = result.getOrNull() ?: emptyList()
                _uiState.update { state ->
                    state.copy(
                        repositories = repos,
                        isLoadingRepos = false
                    )
                }
                applyRepoSorting()

                // Check for Auto-open Working Repo on initial launch
                if (!hasAttemptedAutoOpenWorkingRepo && _uiState.value.workingRepoId != null) {
                    hasAttemptedAutoOpenWorkingRepo = true
                    val workingRepo = repos.find { it.id == _uiState.value.workingRepoId }
                    if (workingRepo != null && _uiState.value.currentScreen == AppScreen.REPO_LIST) {
                        selectRepository(workingRepo)
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingRepos = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to load repositories"
                    )
                }
            }
        }
    }

    fun setRepoSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(repoSearchQuery = query)
        }
        applyRepoSorting()
    }

    fun setRepoFilterType(filterType: RepoFilterType) {
        _uiState.update { state ->
            state.copy(repoFilterType = filterType)
        }
        applyRepoSorting()
    }

    fun setRepoSortOption(sortOption: RepoSortOption) {
        prefs.edit().putString("repo_sort_option", sortOption.name).apply()
        _uiState.update { state ->
            state.copy(repoSortOption = sortOption)
        }
        applyRepoSorting()
    }

    fun togglePinRepo(repoId: Long) {
        val currentPinned = _uiState.value.pinnedRepoIds.toMutableSet()
        val isNowPinned = if (currentPinned.contains(repoId)) {
            currentPinned.remove(repoId)
            false
        } else {
            currentPinned.add(repoId)
            true
        }
        prefs.edit().putStringSet("pinned_repos", currentPinned.map { it.toString() }.toSet()).apply()
        _uiState.update {
            it.copy(
                pinnedRepoIds = currentPinned,
                toastOrMessage = if (isNowPinned) "Repository pinned to top" else "Repository unpinned"
            )
        }
        applyRepoSorting()
    }

    fun setWorkingRepo(repoId: Long?) {
        val currentWorking = _uiState.value.workingRepoId
        val newWorking = if (currentWorking == repoId) null else repoId

        if (newWorking != null) {
            prefs.edit().putLong("working_repo_id", newWorking).apply()
        } else {
            prefs.edit().remove("working_repo_id").apply()
        }

        _uiState.update {
            it.copy(
                workingRepoId = newWorking,
                toastOrMessage = if (newWorking != null) "Set as Working Repository (will open on launch)" else "Removed Working Repository"
            )
        }
        applyRepoSorting()
    }

    private fun applyRepoSorting() {
        _uiState.update { state ->
            val query = state.repoSearchQuery
            val filter = state.repoFilterType
            val sort = state.repoSortOption
            val pinned = state.pinnedRepoIds

            val filtered = state.repositories.filter { repo ->
                val matchesFilter = when (filter) {
                    RepoFilterType.ALL -> true
                    RepoFilterType.PUBLIC -> !repo.private
                    RepoFilterType.PRIVATE -> repo.private
                    RepoFilterType.FORKS -> repo.fork
                }
                val matchesQuery = query.isBlank() ||
                        repo.name.contains(query, ignoreCase = true) ||
                        (repo.description?.contains(query, ignoreCase = true) == true) ||
                        (repo.language?.contains(query, ignoreCase = true) == true)

                matchesFilter && matchesQuery
            }

            // Pinned repos at the top, sorted by criteria, followed by non-pinned repos
            val (pinnedList, unpinnedList) = filtered.partition { pinned.contains(it.id) }

            fun List<GitHubRepository>.sortItems(): List<GitHubRepository> {
                return when (sort) {
                    RepoSortOption.LAST_ACTIVITY -> this.sortedByDescending { it.updatedAt ?: "" }
                    RepoSortOption.NAME_ASC -> this.sortedBy { it.name.lowercase() }
                    RepoSortOption.NAME_DESC -> this.sortedByDescending { it.name.lowercase() }
                    RepoSortOption.STARS_DESC -> this.sortedByDescending { it.stargazersCount }
                }
            }

            state.copy(filteredRepositories = pinnedList.sortItems() + unpinnedList.sortItems())
        }
    }

    fun selectRepository(repo: GitHubRepository) {
        val savedPinnedFolders = prefs.getStringSet("pinned_folders_${repo.id}", emptySet()) ?: emptySet()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedRepo = repo,
                    selectedBranch = repo.defaultBranch,
                    currentDirectoryPath = "",
                    activeFile = null,
                    activeFilePath = null,
                    pinnedFolders = savedPinnedFolders,
                    currentScreen = AppScreen.EXPLORER,
                    isLeftDrawerOpen = false,
                    syncStatus = SyncStatus.SYNCING
                )
            }
            repository.saveRecentRepo(repo)
            loadBranches(repo.owner.login, repo.name)
            syncActiveRepository(isSilent = false)
        }
    }

    // ==========================================
    // FOLDER PINNING INSIDE REPOSITORY
    // ==========================================

    fun togglePinFolder(folderPath: String) {
        val repo = _uiState.value.selectedRepo ?: return
        val currentPinned = _uiState.value.pinnedFolders.toMutableSet()
        val cleanPath = folderPath.trim('/')
        val isPinned = if (currentPinned.contains(cleanPath)) {
            currentPinned.remove(cleanPath)
            false
        } else {
            currentPinned.add(cleanPath)
            true
        }

        prefs.edit().putStringSet("pinned_folders_${repo.id}", currentPinned).apply()
        _uiState.update {
            it.copy(
                pinnedFolders = currentPinned,
                toastOrMessage = if (isPinned) "Pinned folder to Quick Access" else "Unpinned folder"
            )
        }
    }

    fun setFileTreeSortOption(option: FileTreeSortOption) {
        _uiState.update { it.copy(fileTreeSortOption = option) }
    }

    fun toggleFileTreeSortReverse() {
        _uiState.update { it.copy(isFileTreeSortReversed = !it.isFileTreeSortReversed) }
    }

    // ==========================================
    // AUTO SYNC & TREE EXPLORER
    // ==========================================

    fun syncActiveRepository(isSilent: Boolean = false) {
        val repo = _uiState.value.selectedRepo ?: return
        val branch = _uiState.value.selectedBranch

        viewModelScope.launch {
            if (!isSilent) {
                _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING, isLoadingTree = true) }
            } else {
                _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING) }
            }

            val token = _uiState.value.currentAccount?.token
            val result = repository.getRecursiveTree(token, repo.owner.login, repo.name, branch)

            if (result.isSuccess) {
                val rawItems = result.getOrNull() ?: emptyList()
                val treeRoot = buildTreeStructure(rawItems)
                _uiState.update {
                    it.copy(
                        isLoadingTree = false,
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncedAt = System.currentTimeMillis(),
                        rootExplorerNode = treeRoot,
                        rawTreeItems = rawItems,
                        matchingSearchFiles = filterTreeFiles(rawItems, it.fileSearchQuery)
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingTree = false,
                        syncStatus = SyncStatus.ERROR,
                        errorMessage = if (!isSilent) result.exceptionOrNull()?.message ?: "Sync failed" else null
                    )
                }
            }
        }
    }

    private fun loadBranches(owner: String, repo: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBranches = true) }
            val token = _uiState.value.currentAccount?.token
            val result = repository.getBranches(token, owner, repo)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        branches = result.getOrNull() ?: emptyList(),
                        isLoadingBranches = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingBranches = false) }
            }
        }
    }

    fun selectBranch(branch: String) {
        val repo = _uiState.value.selectedRepo ?: return
        _uiState.update {
            it.copy(
                selectedBranch = branch,
                currentDirectoryPath = "",
                activeFile = null,
                activeFilePath = null,
                showBranchSelector = false
            )
        }
        syncActiveRepository(isSilent = false)
    }

    fun refreshTree() {
        syncActiveRepository(isSilent = false)
    }

    fun navigateToDirectory(path: String) {
        _uiState.update {
            it.copy(
                currentDirectoryPath = path.trim('/'),
                fileSearchQuery = ""
            )
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentDirectoryPath
        if (current.isEmpty()) return
        val parent = if (current.contains('/')) current.substringBeforeLast('/') else ""
        _uiState.update { it.copy(currentDirectoryPath = parent) }
    }

    fun setFileSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                fileSearchQuery = query,
                matchingSearchFiles = filterTreeFiles(state.rawTreeItems, query)
            )
        }
    }

    private fun filterTreeFiles(items: List<GitTreeItem>, query: String): List<GitTreeItem> {
        if (query.isBlank()) return emptyList()
        return items.filter {
            !it.isDirectory && (it.path.contains(query, ignoreCase = true) || it.fileName.contains(query, ignoreCase = true))
        }
    }

    // ==========================================
    // FILE VIEWER & EDITOR (FRESH SYNC ON EVERY OPEN)
    // ==========================================

    fun openFile(item: GitTreeItem) {
        val repo = _uiState.value.selectedRepo ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingFile = true,
                    activeFilePath = item.path,
                    activeFileSha = item.sha,
                    activeFileContent = "",
                    activeFileOriginalContent = "",
                    isFileDirty = false,
                    isMarkdownPreviewMode = false
                )
            }

            val token = _uiState.value.currentAccount?.token
            val result = repository.getFileContent(
                token = token,
                owner = repo.owner.login,
                repo = repo.name,
                path = item.path,
                branch = _uiState.value.selectedBranch
            )

            if (result.isSuccess) {
                val (fileResp, decodedContent) = result.getOrNull()!!
                _uiState.update {
                    it.copy(
                        isLoadingFile = false,
                        activeFile = fileResp,
                        activeFileSha = fileResp.sha,
                        activeFileContent = decodedContent,
                        activeFileOriginalContent = decodedContent,
                        isFileDirty = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingFile = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to load latest file content from GitHub"
                    )
                }
            }
        }
    }

    fun updateEditorContent(newContent: String) {
        _uiState.update {
            it.copy(
                activeFileContent = newContent,
                isFileDirty = newContent != it.activeFileOriginalContent
            )
        }
    }

    fun toggleMarkdownPreview() {
        _uiState.update { it.copy(isMarkdownPreviewMode = !it.isMarkdownPreviewMode) }
    }

    fun closeFile() {
        _uiState.update {
            it.copy(
                activeFile = null,
                activeFilePath = null,
                activeFileSha = null,
                activeFileContent = "",
                activeFileOriginalContent = "",
                isFileDirty = false,
                isLoadingFile = false
            )
        }
    }

    fun commitActiveFile(commitMessage: String, branch: String) {
        val repo = _uiState.value.selectedRepo ?: return
        val path = _uiState.value.activeFilePath ?: return
        val sha = _uiState.value.activeFileSha
        val content = _uiState.value.activeFileContent

        if (commitMessage.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Commit message cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCommitting = true) }
            val token = _uiState.value.currentAccount?.token
            val result = repository.commitFile(
                token = token,
                owner = repo.owner.login,
                repo = repo.name,
                path = path,
                content = content,
                message = commitMessage,
                sha = sha,
                branch = branch
            )

            if (result.isSuccess) {
                val commitResp = result.getOrNull()
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        showCommitDialog = false,
                        isFileDirty = false,
                        activeFileOriginalContent = content,
                        activeFileSha = commitResp?.content?.sha ?: it.activeFileSha,
                        toastOrMessage = "Committed successfully to $branch!"
                    )
                }
                syncActiveRepository(isSilent = true)
            } else {
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Commit failed. Verify your branch and permissions."
                    )
                }
            }
        }
    }

    // ==========================================
    // CREATE FILE / UPLOAD BATCH / DELETE
    // ==========================================

    fun createOrUploadFile(
        targetDir: String,
        fileName: String,
        content: String,
        commitMessage: String,
        branch: String
    ) {
        val repo = _uiState.value.selectedRepo ?: return
        val cleanDir = targetDir.trim().trim('/')
        val cleanName = fileName.trim().trim('/')
        val fullPath = if (cleanDir.isEmpty()) cleanName else "$cleanDir/$cleanName"

        viewModelScope.launch {
            _uiState.update { it.copy(isCommitting = true) }
            val token = _uiState.value.currentAccount?.token
            val result = repository.commitFile(
                token = token,
                owner = repo.owner.login,
                repo = repo.name,
                path = fullPath,
                content = content,
                message = commitMessage.ifBlank { "Add $cleanName" },
                sha = null,
                branch = branch
            )

            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        showCreateUploadDialog = false,
                        toastOrMessage = "Created and committed $cleanName!"
                    )
                }
                syncActiveRepository(isSilent = false)
            } else {
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to create file"
                    )
                }
            }
        }
    }

    fun uploadBatchFiles(
        targetDir: String,
        files: List<Pair<String, ByteArray>>,
        commitMessage: String,
        branch: String
    ) {
        val repo = _uiState.value.selectedRepo ?: return
        val cleanDir = targetDir.trim().trim('/')

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploadingFiles = true,
                    uploadProgress = Triple(0, files.size, "Starting batch upload...")
                )
            }

            val token = _uiState.value.currentAccount?.token
            var successCount = 0
            var failedCount = 0

            for ((index, filePair) in files.withIndex()) {
                val (relativePath, bytes) = filePair
                val fullPath = if (cleanDir.isEmpty()) relativePath else "$cleanDir/$relativePath"
                _uiState.update {
                    it.copy(uploadProgress = Triple(index, files.size, "Uploading $relativePath..."))
                }

                val existingSha = _uiState.value.rawTreeItems.find { it.path == fullPath }?.sha
                val result = repository.commitRawFileBytes(
                    token = token,
                    owner = repo.owner.login,
                    repo = repo.name,
                    path = fullPath,
                    bytes = bytes,
                    message = commitMessage.ifBlank { "Upload $relativePath" },
                    sha = existingSha,
                    branch = branch
                )

                if (result.isSuccess) successCount++
                else failedCount++
            }

            _uiState.update {
                it.copy(
                    isUploadingFiles = false,
                    uploadProgress = null,
                    showCreateUploadDialog = false,
                    toastOrMessage = "Uploaded $successCount file(s)" + if (failedCount > 0) " ($failedCount failed)" else ""
                )
            }
            syncActiveRepository(isSilent = false)
        }
    }

    fun deleteSingleFile(path: String, sha: String) {
        val repo = _uiState.value.selectedRepo ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBatchDeleting = true) }
            val token = _uiState.value.currentAccount?.token
            val result = repository.deleteFile(
                token = token,
                owner = repo.owner.login,
                repo = repo.name,
                path = path,
                sha = sha,
                message = "Delete $path",
                branch = _uiState.value.selectedBranch
            )

            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isBatchDeleting = false,
                        toastOrMessage = "Deleted $path"
                    )
                }
                syncActiveRepository(isSilent = false)
            } else {
                _uiState.update {
                    it.copy(
                        isBatchDeleting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to delete file"
                    )
                }
            }
        }
    }

    fun deleteSelectedBatch(commitMessage: String) {
        val repo = _uiState.value.selectedRepo ?: return
        val pathsToDelete = _uiState.value.selectedFilePaths.toList()
        if (pathsToDelete.isEmpty()) return

        val allItemsToDelete = mutableListOf<GitTreeItem>()
        for (selPath in pathsToDelete) {
            val matchingItems = _uiState.value.rawTreeItems.filter {
                !it.isDirectory && (it.path == selPath || it.path.startsWith("$selPath/"))
            }
            allItemsToDelete.addAll(matchingItems)
        }
        val distinctItems = allItemsToDelete.distinctBy { it.path }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBatchDeleting = true,
                    batchProgress = Triple(0, distinctItems.size, "Preparing batch delete...")
                )
            }

            val token = _uiState.value.currentAccount?.token
            var deletedCount = 0

            for ((index, item) in distinctItems.withIndex()) {
                _uiState.update {
                    it.copy(batchProgress = Triple(index, distinctItems.size, "Deleting ${item.fileName}..."))
                }
                val result = repository.deleteFile(
                    token = token,
                    owner = repo.owner.login,
                    repo = repo.name,
                    path = item.path,
                    sha = item.sha,
                    message = commitMessage.ifBlank { "Delete ${item.path}" },
                    branch = _uiState.value.selectedBranch
                )
                if (result.isSuccess) deletedCount++
            }

            _uiState.update {
                it.copy(
                    isBatchDeleting = false,
                    showBatchDeleteDialog = false,
                    isBatchMode = false,
                    selectedFilePaths = emptySet(),
                    batchProgress = null,
                    toastOrMessage = "Deleted $deletedCount item(s)"
                )
            }
            syncActiveRepository(isSilent = false)
        }
    }

    // ==========================================
    // BATCH MODE HELPERS
    // ==========================================

    fun toggleBatchMode() {
        _uiState.update {
            it.copy(
                isBatchMode = !it.isBatchMode,
                selectedFilePaths = emptySet()
            )
        }
    }

    fun toggleSelectFile(path: String) {
        _uiState.update { state ->
            val set = state.selectedFilePaths.toMutableSet()
            if (set.contains(path)) set.remove(path)
            else set.add(path)
            state.copy(selectedFilePaths = set)
        }
    }

    fun toggleSelectFolder(folderPath: String) {
        val clean = folderPath.trim('/')
        _uiState.update { state ->
            val set = state.selectedFilePaths.toMutableSet()
            if (set.contains(clean)) set.remove(clean)
            else set.add(clean)
            state.copy(selectedFilePaths = set)
        }
    }

    fun selectAllInCurrentDirectory() {
        val curDir = _uiState.value.currentDirectoryPath
        val itemsInDir = _uiState.value.rawTreeItems.filter { item ->
            val itemParent = if (item.path.contains('/')) item.path.substringBeforeLast('/') else ""
            itemParent == curDir
        }
        val paths = itemsInDir.map { it.path }.toSet()
        _uiState.update { it.copy(selectedFilePaths = paths) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedFilePaths = emptySet()) }
    }

    fun clearSelectedFiles() {
        clearSelection()
    }

    fun deleteSelectedFiles(commitMessage: String = "") {
        deleteSelectedBatch(commitMessage)
    }

    // ==========================================
    // DIALOG CONTROLLERS
    // ==========================================

    fun setShowCommitDialog(show: Boolean) {
        _uiState.update { it.copy(showCommitDialog = show) }
    }

    fun setShowCreateUploadDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateUploadDialog = show) }
    }

    fun setShowBatchDeleteDialog(show: Boolean) {
        _uiState.update { it.copy(showBatchDeleteDialog = show) }
    }

    fun setShowBranchSelector(show: Boolean) {
        _uiState.update { it.copy(showBranchSelector = show) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastOrMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ==========================================
    // INTERNAL TREE BUILDER
    // ==========================================

    private fun buildTreeStructure(items: List<GitTreeItem>): ExplorerNode {
        val root = ExplorerNode(
            path = "",
            name = "root",
            isDirectory = true,
            sha = ""
        )

        val nodeMap = mutableMapOf<String, ExplorerNode>()
        nodeMap[""] = root

        val sortedItems = items.sortedBy { it.path }
        for (item in sortedItems) {
            val parts = item.path.split('/')
            var currentPath = ""

            for (i in parts.indices) {
                val part = parts[i]
                val parentPath = currentPath
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"

                val isLast = i == parts.lastIndex
                val isDir = if (isLast) item.isDirectory else true

                if (!nodeMap.containsKey(currentPath)) {
                    val newNode = ExplorerNode(
                        path = currentPath,
                        name = part,
                        isDirectory = isDir,
                        size = if (!isDir) item.size else 0L,
                        sha = if (isLast) item.sha else "",
                        extension = if (!isDir) item.extension else ""
                    )
                    nodeMap[currentPath] = newNode
                    val parentNode = nodeMap[parentPath]
                    parentNode?.children?.add(newNode)
                }
            }
        }

        return root
    }
}
