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
import com.example.ui.components.TerminalLine
import com.example.ui.components.TerminalLineType
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

data class ClipboardItem(
    val path: String,
    val isDirectory: Boolean,
    val sha: String = "",
    val name: String = path.substringAfterLast('/')
)

data class ClipboardState(
    val items: List<ClipboardItem>,
    val isCut: Boolean, // true for cut/move, false for copy
    val sourceRepoOwner: String,
    val sourceRepoName: String,
    val sourceBranch: String
)

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

    // Clipboard (Cut / Copy / Paste)
    val clipboard: ClipboardState? = null,
    val isPasting: Boolean = false,
    val pasteProgress: Triple<Int, Int, String>? = null,

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
    val showSettingsDialog: Boolean = false,
    val showAccountSwitcherDialog: Boolean = false,
    val isCommitting: Boolean = false,

    // Theme & Appearance Preferences
    val selectedThemeId: String = "EMERALD",
    val isThemeBgTintEnabled: Boolean = false,

    // GitHub Terminal
    val showTerminal: Boolean = false,
    val terminalWorkingDir: String = "",
    val terminalLines: List<TerminalLine> = emptyList(),
    val isTerminalExecuting: Boolean = false,
    val terminalStagedFiles: Set<String> = emptySet(),
    val terminalDrafts: Map<String, String> = emptyMap(),

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
        val savedThemeId = prefs.getString("selected_theme_id", "EMERALD") ?: "EMERALD"
        val savedBgTint = prefs.getBoolean("theme_bg_tint_enabled", false)

        _uiState.update {
            it.copy(
                pinnedRepoIds = savedPinnedRepos,
                workingRepoId = savedWorkingRepoId,
                repoSortOption = savedSort,
                selectedThemeId = savedThemeId,
                isThemeBgTintEnabled = savedBgTint
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

    fun addNewAccountWithToken(token: String) {
        viewModelScope.launch {
            val cleanToken = token.trim()
            if (cleanToken.isBlank()) {
                _uiState.update { it.copy(authError = "Token cannot be empty") }
                return@launch
            }
            _uiState.update { it.copy(isAuthenticating = true, authError = null) }
            val result = repository.validateAndSaveToken(cleanToken)
            result.onSuccess { account ->
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        authError = null,
                        currentAccount = account,
                        currentScreen = AppScreen.REPO_LIST,
                        showAccountSwitcherDialog = false,
                        toastOrMessage = "Connected as @${account.username}"
                    )
                }
                loadRepositories()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        authError = error.message ?: "Failed to validate token"
                    )
                }
            }
        }
    }

    fun setShowSettings(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun setShowAccountSwitcher(show: Boolean) {
        _uiState.update { it.copy(showAccountSwitcherDialog = show, authError = null) }
    }

    fun setTheme(themeId: String) {
        prefs.edit().putString("selected_theme_id", themeId).apply()
        _uiState.update { it.copy(selectedThemeId = themeId) }
    }

    fun setThemeBgTintEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("theme_bg_tint_enabled", enabled).apply()
        _uiState.update { it.copy(isThemeBgTintEnabled = enabled) }
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
                activeFilePath = null,
                isBatchMode = false,
                selectedFilePaths = emptySet()
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
                    rawTreeItems = emptyList(),
                    rootExplorerNode = null,
                    matchingSearchFiles = emptyList(),
                    isLoadingTree = true,
                    isBatchMode = false,
                    selectedFilePaths = emptySet(),
                    pinnedFolders = savedPinnedFolders,
                    currentScreen = AppScreen.EXPLORER,
                    isLeftDrawerOpen = false,
                    syncStatus = SyncStatus.SYNCING,
                    terminalLines = emptyList(),
                    terminalDrafts = emptyMap(),
                    terminalStagedFiles = emptySet()
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
                rawTreeItems = emptyList(),
                rootExplorerNode = null,
                matchingSearchFiles = emptyList(),
                isLoadingTree = true,
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
                fileSearchQuery = "",
                isBatchMode = false,
                selectedFilePaths = emptySet()
            )
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentDirectoryPath
        if (current.isEmpty()) return
        val parent = if (current.contains('/')) current.substringBeforeLast('/') else ""
        _uiState.update {
            it.copy(
                currentDirectoryPath = parent,
                isBatchMode = false,
                selectedFilePaths = emptySet()
            )
        }
    }

    fun navigateHome() {
        _uiState.update {
            it.copy(
                currentDirectoryPath = "",
                activeFile = null,
                activeFilePath = null,
                isBatchMode = false,
                selectedFilePaths = emptySet()
            )
        }
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

    fun deleteSingleFile(path: String, sha: String = "", isDirectory: Boolean = false) {
        val repo = _uiState.value.selectedRepo ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBatchDeleting = true) }
            val token = _uiState.value.currentAccount?.token
            val rawTree = _uiState.value.rawTreeItems
            val cleanPath = path.trim().trim('/')
            val displayName = cleanPath.substringAfterLast('/')

            val isFolder = isDirectory || rawTree.none { it.path == cleanPath && !it.isDirectory } || rawTree.any { it.path.startsWith("$cleanPath/") }

            if (isFolder) {
                // Delete all files inside the directory
                val folderPrefix = "$cleanPath/"
                val filesInFolder = rawTree.filter { !it.isDirectory && (it.path == cleanPath || it.path.startsWith(folderPrefix)) }

                if (filesInFolder.isNotEmpty()) {
                    _uiState.update {
                        it.copy(batchProgress = Triple(0, filesInFolder.size, "Deleting folder $displayName..."))
                    }
                    var deletedCount = 0
                    var failedCount = 0
                    var lastError: String? = null

                    for ((index, item) in filesInFolder.withIndex()) {
                        _uiState.update {
                            it.copy(batchProgress = Triple(index, filesInFolder.size, "Deleting ${item.fileName}..."))
                        }
                        val result = repository.deleteFile(
                            token = token,
                            owner = repo.owner.login,
                            repo = repo.name,
                            path = item.path,
                            sha = item.sha,
                            message = "Delete ${item.path}",
                            branch = _uiState.value.selectedBranch
                        )
                        if (result.isSuccess) {
                            deletedCount++
                        } else {
                            failedCount++
                            lastError = result.exceptionOrNull()?.message
                        }
                    }

                    _uiState.update {
                        it.copy(
                            isBatchDeleting = false,
                            batchProgress = null,
                            toastOrMessage = if (failedCount == 0) "Deleted folder $displayName" else "Deleted $deletedCount file(s) in $displayName ($failedCount failed)",
                            errorMessage = if (deletedCount == 0 && failedCount > 0) "Failed to delete folder: $lastError" else null
                        )
                    }
                    syncActiveRepository(isSilent = false)
                } else {
                    // Try deleting placeholder .gitkeep if exists or directly
                    val gitkeepRes = repository.deleteFile(
                        token = token,
                        owner = repo.owner.login,
                        repo = repo.name,
                        path = "$cleanPath/.gitkeep",
                        sha = "",
                        message = "Delete folder $displayName",
                        branch = _uiState.value.selectedBranch
                    )
                    if (gitkeepRes.isSuccess) {
                        _uiState.update {
                            it.copy(
                                isBatchDeleting = false,
                                batchProgress = null,
                                toastOrMessage = "Deleted folder $displayName"
                            )
                        }
                        syncActiveRepository(isSilent = false)
                    } else {
                        _uiState.update {
                            it.copy(
                                isBatchDeleting = false,
                                batchProgress = null,
                                errorMessage = "Failed to delete folder $displayName: ${gitkeepRes.exceptionOrNull()?.message ?: "Folder is empty or already removed"}"
                            )
                        }
                    }
                }
            } else {
                val fileSha = if (sha.isNotBlank()) sha else (rawTree.find { it.path == cleanPath }?.sha ?: "")
                val result = repository.deleteFile(
                    token = token,
                    owner = repo.owner.login,
                    repo = repo.name,
                    path = cleanPath,
                    sha = fileSha,
                    message = "Delete $cleanPath",
                    branch = _uiState.value.selectedBranch
                )

                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isBatchDeleting = false,
                            batchProgress = null,
                            toastOrMessage = "Deleted $displayName"
                        )
                    }
                    syncActiveRepository(isSilent = false)
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: ""
                    // If GitHub returned 422 "is not a file", attempt folder deletion fallback
                    if (errorMsg.contains("is not a file", ignoreCase = true) || errorMsg.contains("422")) {
                        val folderPrefix = "$cleanPath/"
                        val filesInFolder = rawTree.filter { !it.isDirectory && it.path.startsWith(folderPrefix) }
                        if (filesInFolder.isNotEmpty()) {
                            var delCount = 0
                            for (item in filesInFolder) {
                                val r = repository.deleteFile(token, repo.owner.login, repo.name, item.path, item.sha, "Delete ${item.path}", _uiState.value.selectedBranch)
                                if (r.isSuccess) delCount++
                            }
                            _uiState.update {
                                it.copy(
                                    isBatchDeleting = false,
                                    batchProgress = null,
                                    toastOrMessage = "Deleted folder $displayName ($delCount items)"
                                )
                            }
                            syncActiveRepository(isSilent = false)
                            return@launch
                        } else {
                            val gitkeepRes = repository.deleteFile(token, repo.owner.login, repo.name, "$cleanPath/.gitkeep", "", "Delete folder $displayName", _uiState.value.selectedBranch)
                            if (gitkeepRes.isSuccess) {
                                _uiState.update {
                                    it.copy(
                                        isBatchDeleting = false,
                                        batchProgress = null,
                                        toastOrMessage = "Deleted folder $displayName"
                                    )
                                }
                                syncActiveRepository(isSilent = false)
                                return@launch
                            }
                        }
                    }

                    _uiState.update {
                        it.copy(
                            isBatchDeleting = false,
                            batchProgress = null,
                            errorMessage = result.exceptionOrNull()?.message ?: "Failed to delete file"
                        )
                    }
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

    fun clearSelectionAndBatchMode() {
        _uiState.update {
            it.copy(
                isBatchMode = false,
                selectedFilePaths = emptySet()
            )
        }
    }

    fun setBatchMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isBatchMode = enabled,
                selectedFilePaths = if (!enabled) emptySet() else it.selectedFilePaths
            )
        }
    }

    fun clearSelectedFiles() {
        clearSelection()
    }

    fun deleteSelectedFiles(commitMessage: String = "") {
        deleteSelectedBatch(commitMessage)
    }

    // ==========================================
    // CLIPBOARD (CUT / COPY / PASTE)
    // ==========================================

    fun cutItem(path: String, isDirectory: Boolean, sha: String = "") {
        val repo = _uiState.value.selectedRepo ?: return
        val item = ClipboardItem(path = path, isDirectory = isDirectory, sha = sha)
        _uiState.update {
            it.copy(
                clipboard = ClipboardState(
                    items = listOf(item),
                    isCut = true,
                    sourceRepoOwner = repo.owner.login,
                    sourceRepoName = repo.name,
                    sourceBranch = it.selectedBranch
                ),
                toastOrMessage = "Cut \"${item.name}\""
            )
        }
    }

    fun copyItem(path: String, isDirectory: Boolean, sha: String = "") {
        val repo = _uiState.value.selectedRepo ?: return
        val item = ClipboardItem(path = path, isDirectory = isDirectory, sha = sha)
        _uiState.update {
            it.copy(
                clipboard = ClipboardState(
                    items = listOf(item),
                    isCut = false,
                    sourceRepoOwner = repo.owner.login,
                    sourceRepoName = repo.name,
                    sourceBranch = it.selectedBranch
                ),
                toastOrMessage = "Copied \"${item.name}\""
            )
        }
    }

    fun cutSelection() {
        val repo = _uiState.value.selectedRepo ?: return
        val selected = _uiState.value.selectedFilePaths
        if (selected.isEmpty()) return

        val items = selected.map { path ->
            val isDir = _uiState.value.rawTreeItems.none { it.path == path } ||
                    _uiState.value.rawTreeItems.any { it.path.startsWith("$path/") }
            val sha = _uiState.value.rawTreeItems.find { it.path == path }?.sha ?: ""
            ClipboardItem(path = path, isDirectory = isDir, sha = sha)
        }

        _uiState.update {
            it.copy(
                clipboard = ClipboardState(
                    items = items,
                    isCut = true,
                    sourceRepoOwner = repo.owner.login,
                    sourceRepoName = repo.name,
                    sourceBranch = it.selectedBranch
                ),
                isBatchMode = false,
                selectedFilePaths = emptySet(),
                toastOrMessage = "Cut ${items.size} item(s)"
            )
        }
    }

    fun copySelection() {
        val repo = _uiState.value.selectedRepo ?: return
        val selected = _uiState.value.selectedFilePaths
        if (selected.isEmpty()) return

        val items = selected.map { path ->
            val isDir = _uiState.value.rawTreeItems.none { it.path == path } ||
                    _uiState.value.rawTreeItems.any { it.path.startsWith("$path/") }
            val sha = _uiState.value.rawTreeItems.find { it.path == path }?.sha ?: ""
            ClipboardItem(path = path, isDirectory = isDir, sha = sha)
        }

        _uiState.update {
            it.copy(
                clipboard = ClipboardState(
                    items = items,
                    isCut = false,
                    sourceRepoOwner = repo.owner.login,
                    sourceRepoName = repo.name,
                    sourceBranch = it.selectedBranch
                ),
                isBatchMode = false,
                selectedFilePaths = emptySet(),
                toastOrMessage = "Copied ${items.size} item(s)"
            )
        }
    }

    fun clearClipboard() {
        _uiState.update { it.copy(clipboard = null) }
    }

    fun renameItem(oldPath: String, newName: String, isDirectory: Boolean, sha: String = "") {
        val repo = _uiState.value.selectedRepo ?: return
        val branch = _uiState.value.selectedBranch
        val cleanNewName = newName.trim().trim('/')
        if (cleanNewName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Name cannot be empty") }
            return
        }

        val parentDir = if (oldPath.contains('/')) oldPath.substringBeforeLast('/') else ""
        val newPath = if (parentDir.isEmpty()) cleanNewName else "$parentDir/$cleanNewName"

        if (oldPath == newPath) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPasting = true,
                    pasteProgress = Triple(0, 1, "Renaming to $cleanNewName...")
                )
            }

            val token = _uiState.value.currentAccount?.token
            val rawTree = _uiState.value.rawTreeItems

            if (isDirectory) {
                val folderPrefix = "$oldPath/"
                val dirFiles = rawTree.filter { !it.isDirectory && it.path.startsWith(folderPrefix) }
                var successCount = 0
                var failCount = 0

                for ((idx, df) in dirFiles.withIndex()) {
                    val relativeSubPath = df.path.removePrefix(folderPrefix)
                    val destFilePath = "$newPath/$relativeSubPath"
                    _uiState.update {
                        it.copy(pasteProgress = Triple(idx, dirFiles.size, "Moving ${df.fileName}..."))
                    }

                    val contentResult = repository.getFileContent(token, repo.owner.login, repo.name, df.path, branch)
                    if (contentResult.isSuccess) {
                        val (_, decoded) = contentResult.getOrNull()!!
                        val destSha = rawTree.find { it.path == destFilePath }?.sha
                        val commitRes = repository.commitFile(
                            token = token,
                            owner = repo.owner.login,
                            repo = repo.name,
                            path = destFilePath,
                            content = decoded,
                            message = "Rename $oldPath to $newPath ($relativeSubPath)",
                            sha = destSha,
                            branch = branch
                        )
                        if (commitRes.isSuccess) {
                            repository.deleteFile(
                                token = token,
                                owner = repo.owner.login,
                                repo = repo.name,
                                path = df.path,
                                sha = df.sha,
                                message = "Remove old $oldPath after rename",
                                branch = branch
                            )
                            successCount++
                        } else {
                            failCount++
                        }
                    } else {
                        failCount++
                    }
                }

                _uiState.update {
                    it.copy(
                        isPasting = false,
                        pasteProgress = null,
                        toastOrMessage = "Renamed folder to $cleanNewName" + if (failCount > 0) " ($failCount failed)" else ""
                    )
                }
            } else {
                val contentResult = repository.getFileContent(token, repo.owner.login, repo.name, oldPath, branch)
                if (contentResult.isSuccess) {
                    val (_, decoded) = contentResult.getOrNull()!!
                    val fileSha = if (sha.isNotBlank()) sha else (rawTree.find { it.path == oldPath }?.sha ?: "")
                    val destSha = rawTree.find { it.path == newPath }?.sha

                    val commitRes = repository.commitFile(
                        token = token,
                        owner = repo.owner.login,
                        repo = repo.name,
                        path = newPath,
                        content = decoded,
                        message = "Rename $oldPath to $newPath",
                        sha = destSha,
                        branch = branch
                    )

                    if (commitRes.isSuccess) {
                        repository.deleteFile(
                            token = token,
                            owner = repo.owner.login,
                            repo = repo.name,
                            path = oldPath,
                            sha = fileSha,
                            message = "Delete old $oldPath after rename",
                            branch = branch
                        )
                        _uiState.update {
                            it.copy(
                                isPasting = false,
                                pasteProgress = null,
                                toastOrMessage = "Renamed to $cleanNewName"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isPasting = false,
                                pasteProgress = null,
                                errorMessage = "Failed to rename: ${commitRes.exceptionOrNull()?.message}"
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isPasting = false,
                            pasteProgress = null,
                            errorMessage = "Failed to fetch file content for rename"
                        )
                    }
                }
            }

            syncActiveRepository(isSilent = false)
        }
    }

    fun pasteClipboard(targetDirectory: String) {
        val repo = _uiState.value.selectedRepo ?: return
        val clipboard = _uiState.value.clipboard ?: return
        if (clipboard.items.isEmpty()) return

        val cleanTarget = targetDirectory.trim().trim('/')
        val branch = _uiState.value.selectedBranch

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPasting = true,
                    pasteProgress = Triple(0, clipboard.items.size, "Preparing to ${if (clipboard.isCut) "move" else "copy"}...")
                )
            }

            val token = _uiState.value.currentAccount?.token
            var successCount = 0
            var failCount = 0

            val rawTree = _uiState.value.rawTreeItems
            val filesToProcess = mutableListOf<Pair<GitTreeItem, String>>() // Pair<srcItem, destFullPath>

            for (clipItem in clipboard.items) {
                if (clipItem.isDirectory) {
                    val folderPrefix = "${clipItem.path}/"
                    val dirFiles = rawTree.filter { !it.isDirectory && it.path.startsWith(folderPrefix) }
                    for (df in dirFiles) {
                        val relativeSubPath = df.path.removePrefix(clipItem.path).trimStart('/')
                        val destPath = if (cleanTarget.isEmpty()) "${clipItem.name}/$relativeSubPath" else "$cleanTarget/${clipItem.name}/$relativeSubPath"
                        filesToProcess.add(Pair(df, destPath))
                    }
                } else {
                    val singleFile = rawTree.find { it.path == clipItem.path }
                        ?: GitTreeItem(path = clipItem.path, type = "blob", sha = clipItem.sha)
                    val destPath = if (cleanTarget.isEmpty()) clipItem.name else "$cleanTarget/${clipItem.name}"
                    filesToProcess.add(Pair(singleFile, destPath))
                }
            }

            val distinctFiles = filesToProcess.distinctBy { it.second }

            for ((idx, filePair) in distinctFiles.withIndex()) {
                val (srcItem, destPath) = filePair
                _uiState.update {
                    it.copy(pasteProgress = Triple(idx, distinctFiles.size, "${if (clipboard.isCut) "Moving" else "Copying"} ${srcItem.fileName}..."))
                }

                val contentResult = repository.getFileContent(token, clipboard.sourceRepoOwner, clipboard.sourceRepoName, srcItem.path, clipboard.sourceBranch)
                if (contentResult.isSuccess) {
                    val (_, decodedContent) = contentResult.getOrNull()!!
                    val existingDestSha = rawTree.find { it.path == destPath }?.sha

                    val commitResult = repository.commitFile(
                        token = token,
                        owner = repo.owner.login,
                        repo = repo.name,
                        path = destPath,
                        content = decodedContent,
                        message = "${if (clipboard.isCut) "Move" else "Copy"} ${srcItem.path} to $destPath",
                        sha = existingDestSha,
                        branch = branch
                    )

                    if (commitResult.isSuccess) {
                        successCount++
                        if (clipboard.isCut && srcItem.path != destPath) {
                            repository.deleteFile(
                                token = token,
                                owner = clipboard.sourceRepoOwner,
                                repo = clipboard.sourceRepoName,
                                path = srcItem.path,
                                sha = srcItem.sha,
                                message = "Delete ${srcItem.path} after move",
                                branch = clipboard.sourceBranch
                            )
                        }
                    } else {
                        failCount++
                    }
                } else {
                    failCount++
                }
            }

            _uiState.update {
                it.copy(
                    isPasting = false,
                    pasteProgress = null,
                    clipboard = if (clipboard.isCut) null else clipboard,
                    toastOrMessage = "${if (clipboard.isCut) "Moved" else "Copied"} $successCount item(s)" + if (failCount > 0) " ($failCount failed)" else ""
                )
            }
            syncActiveRepository(isSilent = false)
        }
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
    // GITHUB TERMINAL ENGINE & CLI EXECUTION
    // ==========================================

    fun openTerminal(workingDir: String? = null) {
        val state = _uiState.value
        val repo = state.selectedRepo
        val branch = state.selectedBranch
        val path = workingDir ?: state.currentDirectoryPath

        val repoFullName = repo?.fullName ?: (repo?.name ?: "repository")
        val initialLines = if (state.terminalLines.isEmpty()) {
            listOf(
                TerminalLine(
                    type = TerminalLineType.OUTPUT_INFO,
                    text = "GitHub Terminal CLI [Version 2.4.0-compose]"
                ),
                TerminalLine(
                    type = TerminalLineType.OUTPUT_SUCCESS,
                    text = "Connected to @$repoFullName (Branch: $branch)"
                ),
                TerminalLine(
                    type = TerminalLineType.OUTPUT_TEXT,
                    text = "Workspace: /workspace/${repo?.name ?: "repo"}${if (path.isNotEmpty()) "/$path" else ""}"
                ),
                TerminalLine(
                    type = TerminalLineType.OUTPUT_TEXT,
                    text = "Type 'help' or 'git help' for a list of supported commands. Multi-line pastes are supported."
                ),
                TerminalLine(
                    type = TerminalLineType.OUTPUT_DIVIDER,
                    text = ""
                )
            )
        } else {
            state.terminalLines
        }

        _uiState.update {
            it.copy(
                showTerminal = true,
                terminalWorkingDir = path,
                terminalLines = initialLines
            )
        }
    }

    fun closeTerminal() {
        _uiState.update { it.copy(showTerminal = false) }
    }

    fun clearTerminal() {
        _uiState.update {
            it.copy(
                terminalLines = listOf(
                    TerminalLine(
                        type = TerminalLineType.OUTPUT_INFO,
                        text = "Terminal cleared."
                    )
                )
            )
        }
    }

    fun executeTerminalCommand(rawInput: String) {
        if (rawInput.isBlank()) return

        // Handle single or multi-line commands (split by lines or semicolons)
        val commandsToRun = rawInput.lines()
            .flatMap { it.split(';') }
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        if (commandsToRun.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isTerminalExecuting = true) }

            for (cmd in commandsToRun) {
                runSingleTerminalCommand(cmd)
                delay(80L) // Small organic delay for natural CLI visual feedback
            }

            _uiState.update { it.copy(isTerminalExecuting = false) }
        }
    }

    private suspend fun runSingleTerminalCommand(command: String) {
        val state = _uiState.value
        val repo = state.selectedRepo
        val branch = state.selectedBranch
        val workingDir = state.terminalWorkingDir
        val token = state.currentAccount?.token

        // Append Prompt Command line
        appendTerminalLine(
            TerminalLine(
                type = TerminalLineType.PROMPT_COMMAND,
                text = command,
                workingDir = workingDir,
                branch = branch
            )
        )

        val parts = command.trim().split(Regex("\\s+"))
        val mainCmd = parts.firstOrNull()?.lowercase() ?: return
        val args = parts.drop(1)

        when (mainCmd) {
            "clear", "cls" -> {
                clearTerminal()
            }

            "help", "man" -> {
                printTerminalHelp()
            }

            "pwd" -> {
                val fullPath = if (workingDir.isEmpty()) "/workspace/${repo?.fullName ?: "repo"}" else "/workspace/${repo?.fullName ?: "repo"}/$workingDir"
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = fullPath))
            }

            "ls", "dir" -> {
                handleTerminalLs(args, workingDir, state.rawTreeItems)
            }

            "cd" -> {
                handleTerminalCd(args, workingDir, state.rawTreeItems)
            }

            "cat", "head", "tail" -> {
                handleTerminalCat(args, workingDir, repo, branch, token)
            }

            "touch" -> {
                handleTerminalTouch(args, workingDir, repo, branch)
            }

            "mkdir" -> {
                val dirName = args.firstOrNull()
                if (dirName.isNullOrBlank()) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "mkdir: missing operand"))
                } else {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Directory created: $dirName"))
                }
            }

            "echo" -> {
                handleTerminalEcho(command, workingDir, repo, branch)
            }

            "rm" -> {
                handleTerminalRm(args, workingDir, repo, branch, token)
            }

            "mv" -> {
                handleGitMv(args, workingDir, repo, branch, token)
            }

            "cp" -> {
                handleGitCp(args, workingDir, repo, branch, token)
            }

            "git" -> {
                handleGitCommand(args, command, repo, branch, workingDir, token)
            }

            else -> {
                appendTerminalLine(
                    TerminalLine(
                        type = TerminalLineType.OUTPUT_ERROR,
                        text = "command not found: $mainCmd. Type 'help' for available git and shell commands."
                    )
                )
            }
        }
    }

    private suspend fun handleGitCommand(
        args: List<String>,
        fullCommand: String,
        repo: GitHubRepository?,
        branch: String,
        workingDir: String,
        token: String?
    ) {
        if (args.isEmpty() || args[0] == "help") {
            printTerminalHelp()
            return
        }

        val subCmd = args[0].lowercase()
        val subArgs = args.drop(1)

        when (subCmd) {
            "status" -> {
                handleGitStatus(repo, branch)
            }

            "log" -> {
                handleGitLog(subArgs, repo, branch, token)
            }

            "branch" -> {
                handleGitBranch(subArgs, repo, branch, token)
            }

            "checkout", "switch" -> {
                handleGitCheckout(subArgs, repo, branch, token)
            }

            "add" -> {
                handleGitAdd(subArgs, workingDir)
            }

            "commit" -> {
                handleGitCommit(fullCommand, repo, branch, token)
            }

            "push" -> {
                handleGitPush(subArgs, repo, branch, token)
            }

            "pull", "fetch" -> {
                handleGitPull(repo, branch)
            }

            "diff" -> {
                handleGitDiff(subArgs, repo, branch, token)
            }

            "show" -> {
                handleGitShow(subArgs, repo, branch, token)
            }

            "remote" -> {
                val fullName = repo?.fullName ?: "origin/repo"
                if (subArgs.contains("-v") || subArgs.contains("--verbose")) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "origin\thttps://github.com/$fullName.git (fetch)"))
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "origin\thttps://github.com/$fullName.git (push)"))
                } else {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "origin"))
                }
            }

            "reset", "restore" -> {
                handleGitReset(subArgs, workingDir)
            }

            "tag", "tags" -> {
                handleGitTag(subArgs, repo, token)
            }

            "stash" -> {
                handleGitStash(subArgs)
            }

            "clone" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Cloning into '${subArgs.firstOrNull() ?: "repo"}'..."))
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Repository already loaded and ready in workspace."))
            }

            "config" -> {
                val state = _uiState.value
                val user = state.currentAccount?.username ?: "git-user"
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "user.name=$user"))
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "user.email=$user@users.noreply.github.com"))
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "init.defaultBranch=main"))
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "remote.origin.url=https://github.com/${repo?.fullName ?: ""}.git"))
            }

            "mv", "rename" -> {
                handleGitMv(subArgs, workingDir, repo, branch, token)
            }

            "rm" -> {
                handleTerminalRm(subArgs, workingDir, repo, branch, token)
            }

            "cp" -> {
                handleGitCp(subArgs, workingDir, repo, branch, token)
            }

            else -> {
                appendTerminalLine(
                    TerminalLine(
                        type = TerminalLineType.OUTPUT_ERROR,
                        text = "git: '$subCmd' is not a git command. See 'git help'."
                    )
                )
            }
        }
    }

    private fun handleGitStatus(repo: GitHubRepository?, branch: String) {
        val state = _uiState.value
        val drafts = state.terminalDrafts
        val staged = state.terminalStagedFiles

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "On branch $branch"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "Your branch is up to date with 'origin/$branch'."))

        if (staged.isNotEmpty()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "\nChanges to be committed:"))
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "  (use \"git restore --staged <file>...\" to unstage)"))
            staged.forEach { file ->
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "\tmodified:   $file"))
            }
        }

        val unstagedDrafts = drafts.keys.filter { !staged.contains(it) }
        if (unstagedDrafts.isNotEmpty()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_WARNING, text = "\nChanges not staged for commit:"))
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "  (use \"git add <file>...\" to update what will be committed)"))
            unstagedDrafts.forEach { file ->
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "\tmodified:   $file"))
            }
        }

        if (staged.isEmpty() && unstagedDrafts.isEmpty()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "nothing to commit, working tree clean"))
        }
    }

    private suspend fun handleGitLog(args: List<String>, repo: GitHubRepository?, branch: String, token: String?) {
        if (repo == null) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: not a git repository"))
            return
        }

        val limit = args.indexOf("-n").takeIf { it != -1 && it + 1 < args.size }?.let { args[it + 1].toIntOrNull() } ?: 10
        val isOneLine = args.contains("--oneline")

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Fetching commit log for branch '$branch'..."))

        val result = repository.fetchCommits(
            token = token,
            owner = repo.owner.login,
            repo = repo.name,
            sha = branch,
            perPage = limit
        )

        if (result.isSuccess) {
            val commits = result.getOrNull().orEmpty()
            if (commits.isEmpty()) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_WARNING, text = "No commits found on branch $branch."))
            } else {
                commits.forEach { commit ->
                    val shortSha = commit.sha.take(7)
                    if (isOneLine) {
                        val msg = commit.commit.message.lines().firstOrNull() ?: ""
                        appendTerminalLine(
                            TerminalLine(
                                type = TerminalLineType.OUTPUT_WARNING,
                                text = "$shortSha $msg"
                            )
                        )
                    } else {
                        val author = commit.commit.author?.name ?: commit.author?.login ?: "Unknown"
                        val date = commit.commit.author?.date ?: ""
                        val msg = commit.commit.message.trim()

                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_WARNING, text = "commit ${commit.sha} (HEAD -> $branch)"))
                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "Author: $author"))
                        if (date.isNotEmpty()) {
                            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "Date:   $date"))
                        }
                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "    $msg\n"))
                    }
                }
            }
        } else {
            appendTerminalLine(
                TerminalLine(
                    type = TerminalLineType.OUTPUT_ERROR,
                    text = "fatal: ${result.exceptionOrNull()?.message ?: "Unable to fetch commit log"}"
                )
            )
        }
    }

    private suspend fun handleGitBranch(args: List<String>, repo: GitHubRepository?, currentBranch: String, token: String?) {
        val state = _uiState.value
        val branches = state.branches

        if (args.isEmpty() || args.contains("-a") || args.contains("-r")) {
            branches.forEach { b ->
                val isCurrent = b.name == currentBranch
                if (isCurrent) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "* ${b.name}"))
                } else {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "  ${b.name}"))
                }
            }
            return
        }

        // Branch deletion: git branch -d <name> or -D
        if (args[0] == "-d" || args[0] == "-D") {
            val targetBranch = args.getOrNull(1)
            if (targetBranch.isNullOrBlank()) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: branch name required for deletion"))
                return
            }
            if (targetBranch == currentBranch) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: Cannot delete currently checked out branch '$targetBranch'"))
                return
            }
            if (token.isNullOrBlank() || repo == null) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Authentication required to delete remote branch"))
                return
            }

            val delResult = repository.deleteBranch(token, repo.owner.login, repo.name, targetBranch)
            if (delResult.isSuccess) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Deleted branch $targetBranch (was remote)."))
                loadBranches(repo.owner.login, repo.name)
            } else {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: ${delResult.exceptionOrNull()?.message}"))
            }
            return
        }

        // Create new branch: git branch <new_name>
        val newBranchName = args[0]
        if (token.isNullOrBlank() || repo == null) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Authentication token required to create branches"))
            return
        }

        val baseSha = branches.find { it.name == currentBranch }?.commit?.sha
            ?: branches.firstOrNull()?.commit?.sha
            ?: ""

        if (baseSha.isEmpty()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Cannot find base commit SHA for '$currentBranch'"))
            return
        }

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Creating branch '$newBranchName' from $currentBranch ($baseSha)..."))
        val createResult = repository.createBranch(token, repo.owner.login, repo.name, newBranchName, baseSha)
        if (createResult.isSuccess) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Branch '$newBranchName' created successfully."))
            loadBranches(repo.owner.login, repo.name)
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: ${createResult.exceptionOrNull()?.message}"))
        }
    }

    private suspend fun handleGitCheckout(args: List<String>, repo: GitHubRepository?, currentBranch: String, token: String?) {
        if (args.isEmpty()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: you must specify a branch to checkout"))
            return
        }

        // git checkout -b <new_branch>
        if (args[0] == "-b" || args[0] == "-c") {
            val newBranch = args.getOrNull(1)
            if (newBranch.isNullOrBlank()) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: branch name required"))
                return
            }
            if (repo == null || token.isNullOrBlank()) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Authentication required to create branch"))
                return
            }

            val baseSha = _uiState.value.branches.find { it.name == currentBranch }?.commit?.sha ?: ""
            val createResult = repository.createBranch(token, repo.owner.login, repo.name, newBranch, baseSha)
            if (createResult.isSuccess) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Switched to a new branch '$newBranch'"))
                selectBranch(newBranch)
            } else {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: ${createResult.exceptionOrNull()?.message}"))
            }
            return
        }

        val targetBranch = args[0]
        val exists = _uiState.value.branches.any { it.name == targetBranch }
        if (exists) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Switched to branch '$targetBranch'"))
            selectBranch(targetBranch)
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: pathspec '$targetBranch' did not match any file(s) known to git"))
        }
    }

    private fun handleGitAdd(args: List<String>, workingDir: String) {
        val state = _uiState.value
        val drafts = state.terminalDrafts

        if (args.isEmpty()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "Nothing specified, nothing added."))
            return
        }

        val target = args[0]
        val currentStaged = state.terminalStagedFiles.toMutableSet()

        if (target == "." || target == "-A" || target == "--all") {
            currentStaged.addAll(drafts.keys)
            state.activeFilePath?.let { currentStaged.add(it) }
            _uiState.update { it.copy(terminalStagedFiles = currentStaged) }
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Staged ${currentStaged.size} modified file(s) for commit."))
        } else {
            val resolvedPath = if (workingDir.isEmpty()) target else "$workingDir/$target"
            currentStaged.add(resolvedPath)
            _uiState.update { it.copy(terminalStagedFiles = currentStaged) }
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Staged: $resolvedPath"))
        }
    }

    private suspend fun handleGitCommit(fullCommand: String, repo: GitHubRepository?, branch: String, token: String?) {
        val state = _uiState.value
        val staged = state.terminalStagedFiles
        val drafts = state.terminalDrafts

        // Extract message from -m "message" or -m 'message'
        val msgRegex = Regex("-m\\s+[\"']([^\"']+)[\"']")
        val match = msgRegex.find(fullCommand)
        val commitMessage = match?.groupValues?.getOrNull(1)?.trim() ?: "Update files via GitHub Terminal"

        if (token.isNullOrBlank() || repo == null) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Authentication token required to commit files"))
            return
        }

        val filesToCommit = if (staged.isNotEmpty()) staged else drafts.keys
        if (filesToCommit.isEmpty()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_WARNING, text = "On branch $branch\nnothing to commit, working tree clean"))
            return
        }

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Committing ${filesToCommit.size} file(s) to branch '$branch'..."))

        var successCount = 0
        for (filePath in filesToCommit) {
            val content = drafts[filePath] ?: if (filePath == state.activeFilePath) state.activeFileContent else ""
            val sha = state.rawTreeItems.find { it.path == filePath }?.sha

            val res = repository.commitFile(
                token = token,
                owner = repo.owner.login,
                repo = repo.name,
                path = filePath,
                content = content,
                message = commitMessage,
                sha = sha,
                branch = branch
            )

            if (res.isSuccess) {
                successCount++
            }
        }

        _uiState.update {
            it.copy(
                terminalStagedFiles = emptySet(),
                terminalDrafts = it.terminalDrafts.filterKeys { k -> !filesToCommit.contains(k) }
            )
        }

        if (successCount > 0) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "[$branch] $commitMessage"))
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = " $successCount file(s) changed, committed to remote GitHub branch."))
            syncActiveRepository(isSilent = true)
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: failed to commit file(s)"))
        }
    }

    private suspend fun handleGitPush(args: List<String>, repo: GitHubRepository?, branch: String, token: String?) {
        if (repo == null) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Not in a git repository"))
            return
        }

        val targetBranch = args.getOrNull(1) ?: branch
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Writing objects: 100% (3/3), done."))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "To https://github.com/${repo.fullName}.git"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "   main..$targetBranch  $targetBranch -> $targetBranch"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Branch '$targetBranch' set up to track remote branch '$targetBranch' from 'origin'."))
        syncActiveRepository(isSilent = true)
    }

    private suspend fun handleGitPull(repo: GitHubRepository?, branch: String) {
        if (repo == null) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Not in a git repository"))
            return
        }

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "remote: Enumerating objects..."))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "remote: Counting objects: 100%, done."))
        syncActiveRepository(isSilent = true)
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Already up to date with 'origin/$branch'."))
    }

    private suspend fun handleGitDiff(args: List<String>, repo: GitHubRepository?, currentBranch: String, token: String?) {
        val state = _uiState.value
        val drafts = state.terminalDrafts

        // If comparing 2 branches: git diff branch1 branch2
        if (args.size >= 2 && repo != null) {
            val base = args[0]
            val head = args[1]
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_DIFF_HEADER, text = "diff --git a/$base b/$head"))

            val comp = repository.compareBranches(token, repo.owner.login, repo.name, base, head)
            if (comp.isSuccess) {
                val data = comp.getOrNull()
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Total commits: ${data?.totalCommits}, Files changed: ${data?.files?.size ?: 0}"))
                data?.files?.forEach { f ->
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_DIFF_HEADER, text = "+++ b/${f.filename} (${f.status})"))
                    f.patch?.lines()?.forEach { pline ->
                        if (pline.startsWith("+")) appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_DIFF_ADD, text = pline))
                        else if (pline.startsWith("-")) appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_DIFF_DEL, text = pline))
                        else appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = pline))
                    }
                }
            } else {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: ${comp.exceptionOrNull()?.message}"))
            }
            return
        }

        // Local drafts diff
        if (drafts.isEmpty() && !state.isFileDirty) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "No local diffs found."))
            return
        }

        drafts.forEach { (path, content) ->
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_DIFF_HEADER, text = "diff --git a/$path b/$path"))
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_DIFF_HEADER, text = "--- a/$path"))
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_DIFF_HEADER, text = "+++ b/$path"))
            content.lines().take(20).forEach { line ->
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_DIFF_ADD, text = "+$line"))
            }
        }
    }

    private suspend fun handleGitShow(args: List<String>, repo: GitHubRepository?, branch: String, token: String?) {
        val target = args.firstOrNull() ?: branch
        if (repo == null) return

        val commitRes = repository.fetchCommitDetail(token, repo.owner.login, repo.name, target)
        if (commitRes.isSuccess) {
            val commit = commitRes.getOrNull()
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_WARNING, text = "commit ${commit?.sha}"))
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "Author: ${commit?.commit?.author?.name}"))
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "Date:   ${commit?.commit?.author?.date}"))
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "\n    ${commit?.commit?.message}"))
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: ambiguous argument '$target': unknown revision"))
        }
    }

    private fun handleGitReset(args: List<String>, workingDir: String) {
        _uiState.update { it.copy(terminalStagedFiles = emptySet()) }
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Unstaged all changes."))
    }

    private suspend fun handleGitTag(args: List<String>, repo: GitHubRepository?, token: String?) {
        if (repo == null) return
        val res = repository.fetchTags(token, repo.owner.login, repo.name)
        if (res.isSuccess) {
            val tags = res.getOrNull().orEmpty()
            if (tags.isEmpty()) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "No tags found."))
            } else {
                tags.forEach { tag ->
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = tag.name))
                }
            }
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: ${res.exceptionOrNull()?.message}"))
        }
    }

    private fun handleGitStash(args: List<String>) {
        val sub = args.firstOrNull() ?: "push"
        when (sub) {
            "list" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "stash@{0}: WIP on ${_uiState.value.selectedBranch}: Auto stash"))
            }
            "pop", "apply" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Dropped refs/stash@{0}"))
            }
            else -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Saved working directory and index state WIP on ${_uiState.value.selectedBranch}"))
            }
        }
    }

    // Shell File Utilities

    private fun handleTerminalLs(args: List<String>, workingDir: String, items: List<GitTreeItem>) {
        val showAll = args.contains("-a") || args.contains("-la") || args.contains("-al")
        val showLong = args.contains("-l") || args.contains("-la") || args.contains("-al")

        val targetDir = args.firstOrNull { !it.startsWith("-") } ?: workingDir

        val directChildren = items.filter { item ->
            if (targetDir.isEmpty()) {
                !item.path.contains('/')
            } else {
                item.path.startsWith("$targetDir/") && !item.path.removePrefix("$targetDir/").contains('/')
            }
        }

        if (directChildren.isEmpty()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "(empty directory)"))
            return
        }

        if (showLong) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "total ${directChildren.size}"))
            directChildren.forEach { item ->
                val perm = if (item.isDirectory) "drwxr-xr-x" else "-rw-r--r--"
                val size = (item.size ?: 0L).toString().padStart(6)
                val type = if (item.isDirectory) TerminalLineType.OUTPUT_INFO else TerminalLineType.OUTPUT_TEXT
                val name = item.fileName + if (item.isDirectory) "/" else ""
                appendTerminalLine(TerminalLine(type = type, text = "$perm  1 user  group  $size  $name"))
            }
        } else {
            val formattedNames = directChildren.joinToString("  ") { item ->
                if (item.isDirectory) "${item.fileName}/" else item.fileName
            }
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = formattedNames))
        }
    }

    private fun handleTerminalCd(args: List<String>, currentDir: String, items: List<GitTreeItem>) {
        val target = args.firstOrNull() ?: ""

        if (target.isEmpty() || target == "~" || target == "/") {
            _uiState.update { it.copy(terminalWorkingDir = "") }
            return
        }

        if (target == "..") {
            val parent = if (currentDir.contains('/')) currentDir.substringBeforeLast('/') else ""
            _uiState.update { it.copy(terminalWorkingDir = parent) }
            return
        }

        val newPath = if (currentDir.isEmpty()) target else "$currentDir/$target"
        val exists = items.any { it.isDirectory && (it.path == newPath || it.path.startsWith("$newPath/")) }

        if (exists) {
            _uiState.update { it.copy(terminalWorkingDir = newPath) }
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "cd: $target: No such file or directory"))
        }
    }

    private suspend fun handleTerminalCat(args: List<String>, workingDir: String, repo: GitHubRepository?, branch: String, token: String?) {
        val fileName = args.firstOrNull()
        if (fileName.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "cat: missing file operand"))
            return
        }
        if (repo == null) return

        val filePath = if (workingDir.isEmpty()) fileName else "$workingDir/$fileName"
        val contentRes = repository.getFileContent(token, repo.owner.login, repo.name, filePath, branch)

        if (contentRes.isSuccess) {
            val text = contentRes.getOrNull()?.second ?: ""
            text.lines().take(100).forEach { line ->
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = line))
            }
            if (text.lines().size > 100) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "... [truncated output: ${text.lines().size} lines]"))
            }
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "cat: $fileName: No such file or directory"))
        }
    }

    private fun handleTerminalTouch(args: List<String>, workingDir: String, repo: GitHubRepository?, branch: String) {
        val fileName = args.firstOrNull()
        if (fileName.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "touch: missing file operand"))
            return
        }
        val filePath = if (workingDir.isEmpty()) fileName else "$workingDir/$fileName"
        _uiState.update {
            it.copy(terminalDrafts = it.terminalDrafts + (filePath to ""))
        }
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Created empty file: $filePath"))
    }

    private fun handleTerminalEcho(fullCommand: String, workingDir: String, repo: GitHubRepository?, branch: String) {
        if (fullCommand.contains(">")) {
            val parts = fullCommand.split(">")
            val rawText = parts[0].removePrefix("echo").trim().removeSurrounding("\"").removeSurrounding("'")
            val targetFile = parts[1].trim()
            val filePath = if (workingDir.isEmpty()) targetFile else "$workingDir/$targetFile"
            _uiState.update {
                it.copy(terminalDrafts = it.terminalDrafts + (filePath to rawText))
            }
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Written to $filePath"))
        } else {
            val text = fullCommand.removePrefix("echo").trim().removeSurrounding("\"").removeSurrounding("'")
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = text))
        }
    }

    private suspend fun handleTerminalRm(args: List<String>, workingDir: String, repo: GitHubRepository?, branch: String, token: String?) {
        val nonFlags = args.filter { !it.startsWith("-") }
        val fileName = nonFlags.firstOrNull()?.trim('\'', '"')
        if (fileName.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "rm: missing operand"))
            return
        }
        if (repo == null || token.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Authentication required to delete remote files"))
            return
        }

        val rawPath = if (workingDir.isEmpty()) fileName else "$workingDir/$fileName"
        val cleanPath = rawPath.trim('/')
        val rawTree = _uiState.value.rawTreeItems
        val dirFiles = rawTree.filter { !it.isDirectory && (it.path == cleanPath || it.path.startsWith("$cleanPath/")) }

        if (dirFiles.isNotEmpty()) {
            var delCount = 0
            for (f in dirFiles) {
                val res = repository.deleteFile(token, repo.owner.login, repo.name, f.path, f.sha, "Remove ${f.path} via Terminal", branch)
                if (res.isSuccess) delCount++
            }
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Removed: $cleanPath ($delCount file(s) deleted)"))
            syncActiveRepository(isSilent = true)
        } else {
            val fileItem = rawTree.find { it.path == cleanPath }
            val sha = fileItem?.sha ?: ""
            val res = repository.deleteFile(token, repo.owner.login, repo.name, cleanPath, sha, "Delete $cleanPath via Terminal", branch)
            if (res.isSuccess) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Removed: $cleanPath"))
                syncActiveRepository(isSilent = true)
            } else {
                val gitkeepRes = repository.deleteFile(token, repo.owner.login, repo.name, "$cleanPath/.gitkeep", "", "Delete $cleanPath via Terminal", branch)
                if (gitkeepRes.isSuccess) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Removed folder: $cleanPath"))
                    syncActiveRepository(isSilent = true)
                } else {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "rm: cannot remove '$fileName': ${res.exceptionOrNull()?.message}"))
                }
            }
        }
    }

    private suspend fun handleGitMv(args: List<String>, workingDir: String, repo: GitHubRepository?, branch: String, token: String?) {
        val nonFlags = args.filter { !it.startsWith("-") }
        if (nonFlags.size < 2) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: destination operand missing\nusage: git mv <source> <destination>"))
            return
        }

        val srcRaw = nonFlags[0].trim('\'', '"')
        val destRaw = nonFlags[1].trim('\'', '"')

        val srcPath = if (workingDir.isEmpty() || srcRaw.startsWith("/")) srcRaw.removePrefix("/") else "$workingDir/$srcRaw"
        val destPath = if (workingDir.isEmpty() || destRaw.startsWith("/")) destRaw.removePrefix("/") else "$workingDir/$destRaw"

        val state = _uiState.value
        val drafts = state.terminalDrafts
        val rawTree = state.rawTreeItems

        val inDrafts = drafts.containsKey(srcPath)
        val inTree = rawTree.find { it.path == srcPath }

        if (!inDrafts && inTree == null) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: bad source, source='$srcRaw', destination='$destRaw' (No such file or directory)"))
            return
        }

        if (inDrafts) {
            val content = drafts[srcPath] ?: ""
            _uiState.update {
                it.copy(
                    terminalDrafts = (it.terminalDrafts - srcPath) + (destPath to content),
                    terminalStagedFiles = (it.terminalStagedFiles - srcPath) + destPath
                )
            }
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Renamed '$srcRaw' -> '$destRaw' (staged in drafts)"))
            return
        }

        if (token.isNullOrBlank() || repo == null) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Authentication token required to rename remote files"))
            return
        }

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Renaming '$srcRaw' to '$destRaw' on branch '$branch'..."))

        val contentRes = repository.getFileContent(token, repo.owner.login, repo.name, srcPath, branch)
        if (contentRes.isSuccess) {
            val (_, decoded) = contentRes.getOrNull()!!
            val destSha = rawTree.find { it.path == destPath }?.sha

            val commitRes = repository.commitFile(
                token = token,
                owner = repo.owner.login,
                repo = repo.name,
                path = destPath,
                content = decoded,
                message = "Rename $srcPath to $destPath via git mv",
                sha = destSha,
                branch = branch
            )

            if (commitRes.isSuccess) {
                repository.deleteFile(
                    token = token,
                    owner = repo.owner.login,
                    repo = repo.name,
                    path = srcPath,
                    sha = inTree?.sha ?: "",
                    message = "Remove old $srcPath after rename",
                    branch = branch
                )
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Renamed '$srcRaw' to '$destRaw' successfully."))
                syncActiveRepository(isSilent = true)
            } else {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: failed to commit '$destRaw': ${commitRes.exceptionOrNull()?.message}"))
            }
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: could not read '$srcRaw' on branch '$branch'"))
        }
    }

    private suspend fun handleGitCp(args: List<String>, workingDir: String, repo: GitHubRepository?, branch: String, token: String?) {
        val nonFlags = args.filter { !it.startsWith("-") }
        if (nonFlags.size < 2) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "cp: missing destination operand\nusage: cp <source> <destination>"))
            return
        }
        val srcRaw = nonFlags[0].trim('\'', '"')
        val destRaw = nonFlags[1].trim('\'', '"')

        val srcPath = if (workingDir.isEmpty() || srcRaw.startsWith("/")) srcRaw.removePrefix("/") else "$workingDir/$srcRaw"
        val destPath = if (workingDir.isEmpty() || destRaw.startsWith("/")) destRaw.removePrefix("/") else "$workingDir/$destRaw"

        val state = _uiState.value
        val drafts = state.terminalDrafts
        val rawTree = state.rawTreeItems

        if (drafts.containsKey(srcPath)) {
            val content = drafts[srcPath] ?: ""
            _uiState.update {
                it.copy(terminalDrafts = it.terminalDrafts + (destPath to content))
            }
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Copied '$srcRaw' -> '$destRaw' (draft)"))
            return
        }

        if (token.isNullOrBlank() || repo == null) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Authentication token required to copy files"))
            return
        }

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Copying '$srcRaw' to '$destRaw'..."))
        val contentRes = repository.getFileContent(token, repo.owner.login, repo.name, srcPath, branch)
        if (contentRes.isSuccess) {
            val (_, decoded) = contentRes.getOrNull()!!
            val destSha = rawTree.find { it.path == destPath }?.sha
            val commitRes = repository.commitFile(
                token = token,
                owner = repo.owner.login,
                repo = repo.name,
                path = destPath,
                content = decoded,
                message = "Copy $srcPath to $destPath",
                sha = destSha,
                branch = branch
            )
            if (commitRes.isSuccess) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Copied '$srcRaw' to '$destRaw' successfully."))
                syncActiveRepository(isSilent = true)
            } else {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: failed to commit copied file: ${commitRes.exceptionOrNull()?.message}"))
            }
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "cp: cannot stat '$srcRaw': No such file or directory"))
        }
    }

    private fun printTerminalHelp() {
        val helpLines = listOf(
            "GitHub Terminal Command Reference:",
            "  git status                   Check working tree and staged files",
            "  git log [--oneline] [-n N]   View commit history on active branch",
            "  git branch [-a]              List local and remote branches",
            "  git branch <name>            Create new branch from current commit",
            "  git branch -d <name>         Delete branch ref on GitHub",
            "  git checkout <branch>        Switch active branch",
            "  git checkout -b <name>       Create and switch to new branch",
            "  git add <file> | git add .   Stage modified file(s)",
            "  git commit -m \"message\"      Commit staged files to GitHub remote",
            "  git push [origin <branch>]   Push branch commits to GitHub",
            "  git pull | git fetch         Fetch latest updates and branches",
            "  git mv <src> <dest>          Rename or move file on branch",
            "  git rm <file>                Remove file from repository branch",
            "  git cp <src> <dest>          Copy file contents to new destination",
            "  git diff [<b1> <b2>]         Show unified file or branch diff",
            "  git show <sha|branch>        Display commit metadata and info",
            "  git remote [-v]              List configured remote repositories",
            "  git reset | git restore      Unstage or restore working drafts",
            "  git tag                      List repository release tags",
            "  git stash [list|pop]         Manage temporary drafts stash",
            "  ls [-la] [dir]               List directory contents and sizes",
            "  cd <dir> | cd .. | cd ~      Navigate folder structure",
            "  pwd                          Print absolute working path",
            "  cat <file>                   View file contents",
            "  mv <src> <dest>              Move or rename file",
            "  cp <src> <dest>              Copy file",
            "  touch <file>                 Create empty file",
            "  echo \"text\" > <file>         Write text into file",
            "  rm <file>                    Delete file from repository",
            "  clear                        Clear terminal screen output",
            "  help                         Show this help manual"
        )
        helpLines.forEach { line ->
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = line))
        }
    }

    private fun appendTerminalLine(line: TerminalLine) {
        _uiState.update {
            it.copy(terminalLines = it.terminalLines + line)
        }
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
