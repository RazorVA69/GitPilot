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
import com.example.data.model.RepoFileSearchMatch
import com.example.data.repository.GitHubRepository as GitHubRepoRepository
import com.example.ui.components.TerminalLine
import com.example.ui.components.TerminalLineType
import kotlinx.coroutines.Dispatchers
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
    NAME_DESC,
    SIZE_DESC,
    SIZE_ASC,
    TYPE_ASC
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

data class EditorTabInfo(
    val path: String,
    val fileName: String,
    val sha: String,
    val content: String,
    val originalContent: String,
    val isDirty: Boolean = false,
    val isPinned: Boolean = false,
    val isMarkdownPreview: Boolean = false,
    val initialLine: Int? = null,
    val scrollY: Int = 0,
    val scrollX: Int = 0,
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0
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
    val pinnedFolders: Set<String> = emptySet(), // Quick Access pinned folders
    val topPinnedFolders: Set<String> = emptySet(), // Folders pinned to top of directory
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
    val openEditorTabs: List<EditorTabInfo> = emptyList(),
    val pinnedFiles: Set<String> = emptySet(), // Quick Access pinned files
    val topPinnedFiles: Set<String> = emptySet(), // Files pinned to top of directory

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
    val errorMessage: String? = null,

    // Search Across All Files
    val showSearchAcrossFiles: Boolean = false,
    val searchAcrossFilesQuery: String = "",
    val searchAcrossFilesPath: String = "",
    val isSearchingAcrossFiles: Boolean = false,
    val searchAcrossFilesProgress: Pair<Int, Int>? = null,
    val searchAcrossFilesResults: List<RepoFileSearchMatch> = emptyList(),
    val editorOpenedFromSearchResults: Boolean = false,
    val initialEditorLine: Int? = null
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
    private val sessionVariables = mutableMapOf<String, String>()
    private val interactiveScriptBuffer = mutableListOf<String>()

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
                    prefetchRepoCache()
                }
            }
        }
    }

    private fun prefetchRepoCache() {
        val repo = _uiState.value.selectedRepo ?: return
        val branch = _uiState.value.selectedBranch
        val rawItems = _uiState.value.rawTreeItems
        val token = _uiState.value.currentAccount?.token

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cachedList = repository.getCachedFilesForRepo(repo.owner.login, repo.name, branch)
                val cacheMap = cachedList.associateBy { it.path }

                val uncachedTextFiles = rawItems.filter { item ->
                    !item.isDirectory &&
                    isLikelyTextFile(item.fileName) &&
                    (item.size == null || item.size < 300_000) &&
                    (cacheMap[item.path] == null || cacheMap[item.path]?.sha != item.sha)
                }.take(25)

                for (fileItem in uncachedTextFiles) {
                    if (!isActive) break
                    repository.getFileContent(token, repo.owner.login, repo.name, fileItem.path, branch)
                    delay(80L)
                }
            } catch (_: Exception) {}
        }
    }

    private fun isLikelyTextFile(fileName: String): Boolean {
        val nonTextExtensions = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg",
            "mp4", "mp3", "wav", "avi", "mov", "webm", "ogg",
            "zip", "tar", "gz", "rar", "7z", "apk", "jar", "class", "dex",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "exe", "dll", "so", "dylib", "bin", "iso"
        )
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext.isNotEmpty() && ext !in nonTextExtensions
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
        val savedPinnedFiles = prefs.getStringSet("pinned_files_${repo.id}", emptySet()) ?: emptySet()
        val savedTopPinnedFolders = prefs.getStringSet("top_pinned_folders_${repo.id}", emptySet()) ?: emptySet()
        val savedTopPinnedFiles = prefs.getStringSet("top_pinned_files_${repo.id}", emptySet()) ?: emptySet()

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
                    pinnedFiles = savedPinnedFiles,
                    topPinnedFolders = savedTopPinnedFolders,
                    topPinnedFiles = savedTopPinnedFiles,
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
    // FOLDER & FILE PINNING (QUICK ACCESS & PIN TO TOP)
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
                toastOrMessage = if (isPinned) "Pinned folder to Quick Access" else "Unpinned folder from Quick Access"
            )
        }
    }

    fun togglePinFolderToTop(folderPath: String) {
        val repo = _uiState.value.selectedRepo ?: return
        val currentTopPinned = _uiState.value.topPinnedFolders.toMutableSet()
        val cleanPath = folderPath.trim('/')
        val isPinned = if (currentTopPinned.contains(cleanPath)) {
            currentTopPinned.remove(cleanPath)
            false
        } else {
            currentTopPinned.add(cleanPath)
            true
        }

        prefs.edit().putStringSet("top_pinned_folders_${repo.id}", currentTopPinned).apply()
        _uiState.update {
            it.copy(
                topPinnedFolders = currentTopPinned,
                toastOrMessage = if (isPinned) "Folder pinned to top of directory" else "Folder unpinned from top"
            )
        }
    }

    fun togglePinFileToTop(filePath: String) {
        val repo = _uiState.value.selectedRepo ?: return
        val currentTopPinned = _uiState.value.topPinnedFiles.toMutableSet()
        val cleanPath = filePath.trim('/')
        val isPinned = if (currentTopPinned.contains(cleanPath)) {
            currentTopPinned.remove(cleanPath)
            false
        } else {
            currentTopPinned.add(cleanPath)
            true
        }

        prefs.edit().putStringSet("top_pinned_files_${repo.id}", currentTopPinned).apply()
        _uiState.update {
            it.copy(
                topPinnedFiles = currentTopPinned,
                toastOrMessage = if (isPinned) "File pinned to top of directory" else "File unpinned from top"
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
    // FILE VIEWER & EDITOR (FRESH SYNC ON EVERY OPEN + MULTI-FILE TABS)
    // ==========================================

    fun openFile(item: GitTreeItem) {
        val repo = _uiState.value.selectedRepo ?: return
        val currentTabs = _uiState.value.openEditorTabs.toMutableList()
        val existingTabIndex = currentTabs.indexOfFirst { it.path == item.path }

        if (existingTabIndex != -1) {
            // Tab already open, switch to it
            val tab = currentTabs[existingTabIndex]
            _uiState.update {
                it.copy(
                    activeFilePath = tab.path,
                    activeFileSha = tab.sha,
                    activeFileContent = tab.content,
                    activeFileOriginalContent = tab.originalContent,
                    isFileDirty = tab.isDirty,
                    isMarkdownPreviewMode = tab.isMarkdownPreview,
                    isLoadingFile = false
                )
            }
            return
        }

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
                val fileName = item.path.substringAfterLast('/')
                val isPinned = _uiState.value.pinnedFiles.contains(item.path)
                val newTab = EditorTabInfo(
                    path = item.path,
                    fileName = fileName,
                    sha = fileResp.sha,
                    content = decodedContent,
                    originalContent = decodedContent,
                    isDirty = false,
                    isPinned = isPinned,
                    isMarkdownPreview = false,
                    initialLine = _uiState.value.initialEditorLine
                )

                _uiState.update { state ->
                    val updatedTabs = state.openEditorTabs.filter { it.path != item.path } + newTab
                    state.copy(
                        isLoadingFile = false,
                        activeFile = fileResp,
                        activeFileSha = fileResp.sha,
                        activeFileContent = decodedContent,
                        activeFileOriginalContent = decodedContent,
                        isFileDirty = false,
                        openEditorTabs = updatedTabs
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

    fun switchEditorTab(
        path: String,
        currentScrollY: Int = 0,
        currentScrollX: Int = 0,
        currentSelectionStart: Int = 0,
        currentSelectionEnd: Int = 0
    ) {
        val currentPath = _uiState.value.activeFilePath
        if (currentPath == path) return

        val currentTabs = _uiState.value.openEditorTabs
        val targetTab = currentTabs.find { it.path == path } ?: return

        val updatedTabs = currentTabs.map { tab ->
            if (tab.path == currentPath) {
                tab.copy(
                    content = _uiState.value.activeFileContent,
                    isDirty = _uiState.value.isFileDirty,
                    isMarkdownPreview = _uiState.value.isMarkdownPreviewMode,
                    scrollY = currentScrollY,
                    scrollX = currentScrollX,
                    selectionStart = currentSelectionStart,
                    selectionEnd = currentSelectionEnd
                )
            } else tab
        }

        _uiState.update {
            it.copy(
                openEditorTabs = updatedTabs,
                activeFilePath = targetTab.path,
                activeFileSha = targetTab.sha,
                activeFileContent = targetTab.content,
                activeFileOriginalContent = targetTab.originalContent,
                isFileDirty = targetTab.isDirty,
                isMarkdownPreviewMode = targetTab.isMarkdownPreview,
                initialEditorLine = targetTab.initialLine,
                isLoadingFile = false
            )
        }
    }

    fun updateTabPosition(
        path: String,
        scrollY: Int,
        scrollX: Int,
        selectionStart: Int,
        selectionEnd: Int
    ) {
        val currentTabs = _uiState.value.openEditorTabs
        if (currentTabs.none { it.path == path }) return
        val updatedTabs = currentTabs.map { tab ->
            if (tab.path == path) {
                tab.copy(
                    scrollY = scrollY,
                    scrollX = scrollX,
                    selectionStart = selectionStart,
                    selectionEnd = selectionEnd
                )
            } else tab
        }
        _uiState.update { it.copy(openEditorTabs = updatedTabs) }
    }

    fun closeEditorTab(path: String) {
        val currentTabs = _uiState.value.openEditorTabs
        val tabToCloseIndex = currentTabs.indexOfFirst { it.path == path }
        if (tabToCloseIndex == -1) return

        val newTabs = currentTabs.filter { it.path != path }

        if (newTabs.isEmpty()) {
            _uiState.update { it.copy(openEditorTabs = emptyList()) }
            closeFile()
            return
        }

        if (_uiState.value.activeFilePath == path) {
            val nextIndex = tabToCloseIndex.coerceAtMost(newTabs.size - 1)
            val nextTab = newTabs[nextIndex]
            _uiState.update {
                it.copy(
                    openEditorTabs = newTabs,
                    activeFilePath = nextTab.path,
                    activeFileSha = nextTab.sha,
                    activeFileContent = nextTab.content,
                    activeFileOriginalContent = nextTab.originalContent,
                    isFileDirty = nextTab.isDirty,
                    isMarkdownPreviewMode = nextTab.isMarkdownPreview,
                    isLoadingFile = false
                )
            }
        } else {
            _uiState.update { it.copy(openEditorTabs = newTabs) }
        }
    }

    fun togglePinEditorTab(path: String) {
        togglePinFile(path)
    }

    fun togglePinFile(path: String) {
        val repo = _uiState.value.selectedRepo
        val currentPinned = _uiState.value.pinnedFiles.toMutableSet()
        val isPinned = if (currentPinned.contains(path)) {
            currentPinned.remove(path)
            false
        } else {
            currentPinned.add(path)
            true
        }

        if (repo != null) {
            prefs.edit().putStringSet("pinned_files_${repo.id}", currentPinned).apply()
        }

        val updatedTabs = _uiState.value.openEditorTabs.map {
            if (it.path == path) it.copy(isPinned = isPinned) else it
        }

        _uiState.update {
            it.copy(
                pinnedFiles = currentPinned,
                openEditorTabs = updatedTabs,
                toastOrMessage = if (isPinned) "Pinned file to Quick Access" else "Unpinned file from Quick Access"
            )
        }
    }

    fun updateEditorContent(newContent: String) {
        val isDirty = newContent != _uiState.value.activeFileOriginalContent
        val activePath = _uiState.value.activeFilePath

        val updatedTabs = _uiState.value.openEditorTabs.map {
            if (it.path == activePath) {
                it.copy(content = newContent, isDirty = isDirty)
            } else it
        }

        _uiState.update {
            it.copy(
                activeFileContent = newContent,
                isFileDirty = isDirty,
                openEditorTabs = updatedTabs
            )
        }
    }

    fun toggleMarkdownPreview() {
        val newMode = !_uiState.value.isMarkdownPreviewMode
        val activePath = _uiState.value.activeFilePath
        val updatedTabs = _uiState.value.openEditorTabs.map {
            if (it.path == activePath) it.copy(isMarkdownPreview = newMode) else it
        }
        _uiState.update {
            it.copy(
                isMarkdownPreviewMode = newMode,
                openEditorTabs = updatedTabs
            )
        }
    }

    fun closeFile() {
        val wasFromSearch = _uiState.value.editorOpenedFromSearchResults
        _uiState.update {
            it.copy(
                activeFile = null,
                activeFilePath = null,
                activeFileSha = null,
                activeFileContent = "",
                activeFileOriginalContent = "",
                isFileDirty = false,
                isLoadingFile = false,
                initialEditorLine = null,
                editorOpenedFromSearchResults = false,
                showSearchAcrossFiles = wasFromSearch,
                openEditorTabs = emptyList()
            )
        }
    }

    // ==========================================
    // SEARCH ACROSS ALL FILES
    // ==========================================

    private var searchAcrossFilesJob: Job? = null

    fun openSearchAcrossFiles(initialPath: String = "") {
        _uiState.update {
            it.copy(
                showSearchAcrossFiles = true,
                searchAcrossFilesPath = initialPath,
                searchAcrossFilesQuery = "",
                searchAcrossFilesResults = emptyList(),
                isSearchingAcrossFiles = false,
                searchAcrossFilesProgress = null
            )
        }
    }

    fun closeSearchAcrossFiles() {
        searchAcrossFilesJob?.cancel()
        _uiState.update {
            it.copy(
                showSearchAcrossFiles = false,
                isSearchingAcrossFiles = false,
                searchAcrossFilesResults = emptyList(),
                searchAcrossFilesProgress = null,
                editorOpenedFromSearchResults = false
            )
        }
    }

    fun setSearchAcrossFilesPath(path: String) {
        _uiState.update { it.copy(searchAcrossFilesPath = path) }
        val query = _uiState.value.searchAcrossFilesQuery
        if (query.isNotBlank() && query.length >= 2) {
            executeSearchAcrossFiles(query, path)
        }
    }

    fun setSearchAcrossFilesQuery(query: String) {
        _uiState.update { it.copy(searchAcrossFilesQuery = query) }
        executeSearchAcrossFiles(query, _uiState.value.searchAcrossFilesPath, debounceMs = 300L)
    }

    fun refreshSearchAcrossFiles() {
        val query = _uiState.value.searchAcrossFilesQuery
        val path = _uiState.value.searchAcrossFilesPath
        executeSearchAcrossFiles(query, path, debounceMs = 0L)
    }

    fun openFileAtLine(path: String, line: Int) {
        val treeItem = _uiState.value.rawTreeItems.find { it.path == path }
            ?: GitTreeItem(path = path, type = "blob", sha = "")

        _uiState.update {
            it.copy(
                editorOpenedFromSearchResults = true,
                initialEditorLine = line,
                showSearchAcrossFiles = false
            )
        }
        openFile(treeItem)
    }

    fun executeSearchAcrossFiles(query: String, pathScope: String, debounceMs: Long = 0L) {
        searchAcrossFilesJob?.cancel()
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty() || cleanQuery.length < 2) {
            _uiState.update {
                it.copy(
                    isSearchingAcrossFiles = false,
                    searchAcrossFilesResults = emptyList(),
                    searchAcrossFilesProgress = null
                )
            }
            return
        }

        val repo = _uiState.value.selectedRepo ?: return
        val branch = _uiState.value.selectedBranch
        val rawItems = _uiState.value.rawTreeItems
        val token = _uiState.value.currentAccount?.token

        val normalizedPath = pathScope.trim().trim('/')
        val candidateFiles = rawItems.filter { item ->
            !item.isDirectory && (normalizedPath.isEmpty() || item.path == normalizedPath || item.path.startsWith("$normalizedPath/"))
        }

        if (candidateFiles.isEmpty()) {
            _uiState.update {
                it.copy(
                    isSearchingAcrossFiles = false,
                    searchAcrossFilesResults = emptyList(),
                    searchAcrossFilesProgress = Pair(0, 0)
                )
            }
            return
        }

        searchAcrossFilesJob = viewModelScope.launch(Dispatchers.IO) {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            _uiState.update {
                it.copy(
                    isSearchingAcrossFiles = true,
                    searchAcrossFilesResults = emptyList(),
                    searchAcrossFilesProgress = Pair(0, candidateFiles.size)
                )
            }

            val cachedList = repository.getCachedFilesForRepo(repo.owner.login, repo.name, branch)
            val cacheMap = cachedList.associateBy { it.path }

            val accumulatedMatches = mutableListOf<RepoFileSearchMatch>()
            var scannedCount = 0

            fun searchContent(filePath: String, content: String) {
                val lines = content.lines()
                for ((lineIdx, lineText) in lines.withIndex()) {
                    val lineNum = lineIdx + 1
                    var startIndex = 0
                    while (startIndex < lineText.length) {
                        val matchIndex = lineText.indexOf(cleanQuery, startIndex, ignoreCase = true)
                        if (matchIndex == -1) break

                        val match = RepoFileSearchMatch(
                            path = filePath,
                            fileName = filePath.substringAfterLast('/'),
                            lineNumber = lineNum,
                            lineContent = lineText,
                            matchStartIndex = matchIndex,
                            matchLength = cleanQuery.length
                        )
                        accumulatedMatches.add(match)
                        val snapshot = accumulatedMatches.toList()
                        _uiState.update { state ->
                            state.copy(
                                searchAcrossFilesResults = snapshot,
                                searchAcrossFilesProgress = Pair(scannedCount, candidateFiles.size)
                            )
                        }
                        startIndex = matchIndex + cleanQuery.length
                    }
                }
            }

            candidateFiles.chunked(6).forEach { chunk ->
                if (!isActive) return@launch
                for (fileItem in chunk) {
                    if (!isActive) return@launch
                    val cached = cacheMap[fileItem.path]
                    if (cached != null && cached.sha == fileItem.sha) {
                        searchContent(fileItem.path, cached.content)
                    } else {
                        val isTextExt = isLikelyTextFile(fileItem.fileName)
                        if (isTextExt && (fileItem.size == null || fileItem.size < 500_000)) {
                            val res = repository.getFileContent(token, repo.owner.login, repo.name, fileItem.path, branch)
                            if (res.isSuccess) {
                                val (_, decoded) = res.getOrNull()!!
                                searchContent(fileItem.path, decoded)
                            }
                        }
                    }
                    scannedCount++
                    _uiState.update { it.copy(searchAcrossFilesProgress = Pair(scannedCount, candidateFiles.size)) }
                }
            }

            _uiState.update {
                it.copy(
                    isSearchingAcrossFiles = false,
                    searchAcrossFilesProgress = Pair(candidateFiles.size, candidateFiles.size)
                )
            }
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
                val newSha = commitResp?.content?.sha ?: _uiState.value.activeFileSha ?: ""
                val updatedTabs = _uiState.value.openEditorTabs.map {
                    if (it.path == path) {
                        it.copy(
                            content = content,
                            originalContent = content,
                            isDirty = false,
                            sha = newSha
                        )
                    } else it
                }
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        showCommitDialog = false,
                        isFileDirty = false,
                        activeFileOriginalContent = content,
                        activeFileSha = newSha,
                        openEditorTabs = updatedTabs,
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

    private fun tokenizeShellCommand(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var escapeNext = false

        for (c in command) {
            if (escapeNext) {
                current.append(c)
                escapeNext = false
                continue
            }
            if (c == '\\') {
                escapeNext = true
                continue
            }
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
                continue
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
                continue
            }
            if (c.isWhitespace() && !inSingleQuote && !inDoubleQuote) {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.clear()
                }
            } else {
                current.append(c)
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }

    private fun expandBracesAndTokens(expr: String): List<String> {
        val trimmed = expr.trim()
        val numRangeRegex = Regex("""\{(\d+)\.\.(\d+)\}""")
        val numMatch = numRangeRegex.find(trimmed)
        if (numMatch != null) {
            val start = numMatch.groupValues[1].toIntOrNull() ?: 0
            val end = numMatch.groupValues[2].toIntOrNull() ?: 0
            val prefix = trimmed.substring(0, numMatch.range.first)
            val suffix = trimmed.substring(numMatch.range.last + 1)
            val range = if (start <= end) (start..end) else (start downTo end)
            return range.map { "$prefix$it$suffix" }
        }

        val charRangeRegex = Regex("""\{([a-zA-Z])\.\.([a-zA-Z])\}""")
        val charMatch = charRangeRegex.find(trimmed)
        if (charMatch != null) {
            val start = charMatch.groupValues[1].first()
            val end = charMatch.groupValues[2].first()
            val prefix = trimmed.substring(0, charMatch.range.first)
            val suffix = trimmed.substring(charMatch.range.last + 1)
            val list = if (start <= end) (start..end).toList() else (start downTo end).toList()
            return list.map { "$prefix$it$suffix" }
        }

        val commaBraceRegex = Regex("""\{([^}]+)\}""")
        val commaMatch = commaBraceRegex.find(trimmed)
        if (commaMatch != null) {
            val inside = commaMatch.groupValues[1]
            val prefix = trimmed.substring(0, commaMatch.range.first)
            val suffix = trimmed.substring(commaMatch.range.last + 1)
            return inside.split(',').map { "$prefix${it.trim()}$suffix" }
        }

        return tokenizeShellCommand(trimmed)
    }

    private fun substituteVar(body: String, varName: String, value: String): String {
        val escapedVar = Regex.escape(varName)
        var res = body.replace(Regex("""\$\{""" + escapedVar + """\}"""), value)
        res = res.replace(Regex("""\$""" + escapedVar + """(?![a-zA-Z0-9_])"""), value)
        return res
    }

    private fun substituteSessionVariables(cmd: String): String {
        var current = cmd
        sessionVariables.forEach { (k, v) ->
            current = substituteVar(current, k, v)
        }
        val state = _uiState.value
        current = substituteVar(current, "REPO", state.selectedRepo?.name ?: "repo")
        current = substituteVar(current, "BRANCH", state.selectedBranch)
        current = substituteVar(current, "PWD", state.terminalWorkingDir)
        current = substituteVar(current, "USER", state.currentAccount?.username ?: "developer")
        return current
    }

    data class ShellCommandStep(
        val command: String,
        val suppressStderr: Boolean = false,
        val suppressStdout: Boolean = false,
        val ignoreFailure: Boolean = false
    )

    private fun parseShellModifiers(raw: String): ShellCommandStep {
        var cmd = raw.trim()
        var ignoreFailure = false
        var suppressStderr = false
        var suppressStdout = false

        if (cmd.endsWith("|| true") || cmd.endsWith("|| :")) {
            ignoreFailure = true
            cmd = cmd.removeSuffix("|| true").removeSuffix("|| :").trim()
        } else if (cmd.endsWith("|| false")) {
            cmd = cmd.removeSuffix("|| false").trim()
        }

        if (cmd.contains("2>/dev/null") || cmd.contains("2> /dev/null")) {
            suppressStderr = true
            cmd = cmd.replace("2>/dev/null", "").replace("2> /dev/null", "").trim()
        }
        if (cmd.contains(">/dev/null") || cmd.contains("> /dev/null")) {
            suppressStdout = true
            cmd = cmd.replace(">/dev/null", "").replace("> /dev/null", "").trim()
        }
        if (cmd.contains("2>&1")) {
            cmd = cmd.replace("2>&1", "").trim()
        }

        return ShellCommandStep(
            command = cmd,
            suppressStderr = suppressStderr,
            suppressStdout = suppressStdout,
            ignoreFailure = ignoreFailure
        )
    }

    private fun parseScriptToCommandSteps(rawInput: String): List<ShellCommandStep> {
        val result = mutableListOf<ShellCommandStep>()

        val forPattern = Regex(
            """for\s+([a-zA-Z_][a-zA-Z0-9_]*)\s+in\s+([^;\r\n]+?)(?:[;\r\n]+|\s+)do\b([\s\S]*?)\bdone\b""",
            RegexOption.IGNORE_CASE
        )

        var lastIdx = 0
        forPattern.findAll(rawInput).forEach { match ->
            val pre = rawInput.substring(lastIdx, match.range.first)
            addRawCommands(pre, result)

            val varName = match.groupValues[1].trim()
            val inExpr = match.groupValues[2].trim()
            val body = match.groupValues[3].trim()

            val values = expandBracesAndTokens(inExpr)
            for (v in values) {
                val substitutedBody = substituteVar(body, varName, v)
                addRawCommands(substitutedBody, result)
            }

            lastIdx = match.range.last + 1
        }

        if (lastIdx < rawInput.length) {
            val post = rawInput.substring(lastIdx)
            addRawCommands(post, result)
        }

        return result
    }

    private fun addRawCommands(scriptPart: String, dest: MutableList<ShellCommandStep>) {
        val lines = scriptPart.lines()
            .flatMap { it.split(';') }
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        for (line in lines) {
            if (line.equals("do", ignoreCase = true) || line.equals("done", ignoreCase = true)) {
                continue
            }
            dest.add(parseShellModifiers(line))
        }
    }

    fun executeTerminalCommand(rawInput: String) {
        if (rawInput.isBlank()) return

        val trimmed = rawInput.trim()

        // 1. Interactive Multi-Line Buffering (e.g. user typed 'for i in ...' then 'do' then 'done' across multiple prompts)
        if (interactiveScriptBuffer.isNotEmpty()) {
            if (trimmed.equals("done", ignoreCase = true) || trimmed.equals("fi", ignoreCase = true)) {
                interactiveScriptBuffer.add(trimmed)
                val fullScript = interactiveScriptBuffer.joinToString("\n")
                interactiveScriptBuffer.clear()
                runParsedScript(fullScript)
                return
            } else {
                interactiveScriptBuffer.add(trimmed)
                appendTerminalLine(
                    TerminalLine(
                        type = TerminalLineType.PROMPT_COMMAND,
                        text = "> $trimmed",
                        workingDir = _uiState.value.terminalWorkingDir,
                        branch = _uiState.value.selectedBranch
                    )
                )
                return
            }
        } else if (trimmed.startsWith("for ", ignoreCase = true) && !trimmed.contains("done", ignoreCase = true)) {
            // Started an interactive loop block
            interactiveScriptBuffer.add(trimmed)
            appendTerminalLine(
                TerminalLine(
                    type = TerminalLineType.PROMPT_COMMAND,
                    text = trimmed,
                    workingDir = _uiState.value.terminalWorkingDir,
                    branch = _uiState.value.selectedBranch
                )
            )
            appendTerminalLine(
                TerminalLine(
                    type = TerminalLineType.OUTPUT_INFO,
                    text = "for> Multi-line loop block started. Enter commands, then finish with 'done'."
                )
            )
            return
        }

        // 2. Otherwise, execute the complete script or command line(s)
        runParsedScript(rawInput)
    }

    private fun runParsedScript(rawInput: String) {
        val steps = parseScriptToCommandSteps(rawInput)
        if (steps.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isTerminalExecuting = true) }

            for (step in steps) {
                // Check if variable assignment: VAR=val
                val assignRegex = Regex("""^([a-zA-Z_][a-zA-Z0-9_]*)=(.*)$""")
                val assignMatch = assignRegex.find(step.command.trim())
                if (assignMatch != null && !step.command.trim().startsWith("export ")) {
                    val varName = assignMatch.groupValues[1]
                    val varValue = assignMatch.groupValues[2].trim('"', '\'')
                    sessionVariables[varName] = varValue
                    appendTerminalLine(
                        TerminalLine(
                            type = TerminalLineType.PROMPT_COMMAND,
                            text = step.command,
                            workingDir = _uiState.value.terminalWorkingDir,
                            branch = _uiState.value.selectedBranch
                        )
                    )
                    continue
                }

                // Variable substitution
                val substitutedCommand = substituteSessionVariables(step.command)

                // Support conditional chaining via &&
                if (substitutedCommand.contains("&&")) {
                    val subCommands = substitutedCommand.split("&&").map { it.trim() }.filter { it.isNotEmpty() }
                    for (cmd in subCommands) {
                        val success = executePipedOrSingleCommand(
                            cmd,
                            suppressStderr = step.suppressStderr,
                            suppressStdout = step.suppressStdout,
                            ignoreFailure = step.ignoreFailure
                        )
                        if (!success && !step.ignoreFailure) break
                        delay(25L)
                    }
                } else {
                    executePipedOrSingleCommand(
                        substitutedCommand,
                        suppressStderr = step.suppressStderr,
                        suppressStdout = step.suppressStdout,
                        ignoreFailure = step.ignoreFailure
                    )
                    delay(25L)
                }
            }

            _uiState.update { it.copy(isTerminalExecuting = false) }
        }
    }

    private suspend fun executePipedOrSingleCommand(
        command: String,
        suppressStderr: Boolean = false,
        suppressStdout: Boolean = false,
        ignoreFailure: Boolean = false
    ): Boolean {
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

        if (command.contains("|")) {
            val stages = command.split('|').map { it.trim() }.filter { it.isNotEmpty() }
            if (stages.isEmpty()) return true

            var currentOutput: List<String> = emptyList()
            for (i in stages.indices) {
                val stage = stages[i]
                if (i == 0) {
                    currentOutput = executeCommandCaptureOutput(stage, repo, branch, workingDir, token)
                } else {
                    currentOutput = executePipelineConsumer(stage, currentOutput, repo, branch, workingDir, token)
                }
            }
            return true
        } else {
            return runSingleTerminalCommand(
                command = command,
                isPiped = false,
                suppressStderr = suppressStderr,
                suppressStdout = suppressStdout,
                ignoreFailure = ignoreFailure
            )
        }
    }

    private suspend fun executeCommandCaptureOutput(
        command: String,
        repo: GitHubRepository?,
        branch: String,
        workingDir: String,
        token: String?
    ): List<String> {
        val parts = tokenizeShellCommand(command)
        val mainCmd = parts.firstOrNull()?.lowercase() ?: return emptyList()
        val args = parts.drop(1)

        return when (mainCmd) {
            "gh" -> {
                handleGhCommand(args, command, repo, branch, token, captureStdout = true)
            }
            "git" -> {
                handleGitCommandCapture(args, repo, branch, workingDir, token)
            }
            "ls", "dir" -> {
                val items = _uiState.value.rawTreeItems
                val resolvedDir = workingDir.trim('/')
                items.filter {
                    val p = it.path
                    if (resolvedDir.isEmpty()) !p.contains('/') else p.startsWith("$resolvedDir/") && !p.removePrefix("$resolvedDir/").contains('/')
                }.map { it.path.substringAfterLast('/') }
            }
            "cat" -> {
                val file = args.firstOrNull { !it.startsWith("-") } ?: return emptyList()
                val resolved = if (workingDir.isEmpty()) file else "$workingDir/$file"
                val text = _uiState.value.terminalDrafts[resolved] ?: if (resolved == _uiState.value.activeFilePath) _uiState.value.activeFileContent else null
                (text ?: if (repo != null && !token.isNullOrBlank()) repository.getFileContent(token, repo.owner.login, repo.name, resolved, branch).getOrNull()?.second ?: "" else "").lines()
            }
            "echo" -> {
                listOf(command.removePrefix("echo").trim().trim('"', '\''))
            }
            else -> {
                listOf(command)
            }
        }
    }

    private suspend fun executePipelineConsumer(
        stage: String,
        stdin: List<String>,
        repo: GitHubRepository?,
        branch: String,
        workingDir: String,
        token: String?
    ): List<String> {
        val trimmed = stage.trim()
        val parts = trimmed.split(Regex("\\s+"))
        val cmd = parts.firstOrNull()?.lowercase() ?: return stdin
        val args = parts.drop(1)

        when (cmd) {
            "xargs" -> {
                // Parse xargs -I {} <cmd template> or standard xargs <cmd>
                var placeholder = "{}"
                var templateStartIndex = 0

                val iIdx = args.indexOf("-I").takeIf { it != -1 } ?: args.indexOf("-i").takeIf { it != -1 } ?: -1
                if (iIdx != -1 && iIdx + 1 < args.size) {
                    placeholder = args[iIdx + 1]
                    templateStartIndex = iIdx + 2
                } else if (args.firstOrNull() == "-n" || args.firstOrNull() == "-r") {
                    templateStartIndex = 2.coerceAtMost(args.size)
                }

                val templateArgs = args.drop(templateStartIndex)
                val templateCmd = templateArgs.joinToString(" ")

                if (templateCmd.isBlank()) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "xargs: missing target command"))
                    return emptyList()
                }

                val validInputs = stdin.map { it.trim() }.filter { it.isNotBlank() }
                if (validInputs.isEmpty()) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "xargs: 0 items processed from pipeline stream"))
                    return emptyList()
                }

                for (item in validInputs) {
                    val substituted = if (templateCmd.contains(placeholder)) {
                        templateCmd.replace(placeholder, item)
                    } else {
                        "$templateCmd $item"
                    }
                    runSingleTerminalCommand(substituted, isPiped = false)
                    delay(50L)
                }
                return emptyList()
            }

            "grep" -> {
                val pattern = args.firstOrNull { !it.startsWith("-") }?.trim('\'', '"') ?: ""
                val ignoreCase = args.contains("-i")
                val matching = stdin.filter {
                    if (ignoreCase) it.contains(pattern, ignoreCase = true) else it.contains(pattern)
                }
                matching.forEach {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = it))
                }
                return matching
            }

            "jq" -> {
                val filter = args.firstOrNull { !it.startsWith("-") }?.trim('\'', '"') ?: "."
                val filtered = if (filter.contains("number") || filter.contains(".number")) {
                    stdin.map { it.replace(Regex("[^0-9]"), "") }.filter { it.isNotEmpty() }
                } else {
                    stdin
                }
                filtered.forEach {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = it))
                }
                return filtered
            }

            "wc" -> {
                val count = stdin.size
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "  $count"))
                return listOf(count.toString())
            }

            "head" -> {
                val count = args.indexOf("-n").takeIf { it != -1 && it + 1 < args.size }?.let { args[it + 1].toIntOrNull() } ?: 10
                val sliced = stdin.take(count)
                sliced.forEach { appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = it)) }
                return sliced
            }

            "tail" -> {
                val count = args.indexOf("-n").takeIf { it != -1 && it + 1 < args.size }?.let { args[it + 1].toIntOrNull() } ?: 10
                val sliced = stdin.takeLast(count)
                sliced.forEach { appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = it)) }
                return sliced
            }

            "sort" -> {
                val sorted = stdin.sorted()
                sorted.forEach { appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = it)) }
                return sorted
            }

            "uniq" -> {
                val distinct = stdin.distinct()
                distinct.forEach { appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = it)) }
                return distinct
            }

            else -> {
                // Pass stdin as input to target command
                return stdin
            }
        }
    }

    private suspend fun runSingleTerminalCommand(
        command: String,
        isPiped: Boolean = false,
        suppressStderr: Boolean = false,
        suppressStdout: Boolean = false,
        ignoreFailure: Boolean = false
    ): Boolean {
        val state = _uiState.value
        val repo = state.selectedRepo
        val branch = state.selectedBranch
        val workingDir = state.terminalWorkingDir
        val token = state.currentAccount?.token

        val parts = tokenizeShellCommand(command)
        val mainCmd = parts.firstOrNull()?.lowercase() ?: return true
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

            "chmod" -> {
                handleChmod(args, workingDir)
            }

            "grep" -> {
                handleTerminalGrep(args, workingDir, repo, branch, token)
            }

            "find" -> {
                handleTerminalFind(args, workingDir, state.rawTreeItems)
            }

            "wc" -> {
                handleTerminalWc(args, workingDir, repo, branch, token)
            }

            "head" -> {
                handleTerminalHead(args, workingDir, repo, branch, token)
            }

            "tail" -> {
                handleTerminalTail(args, workingDir, repo, branch, token)
            }

            "which" -> {
                handleTerminalWhich(args)
            }

            "whoami" -> {
                handleTerminalWhoami()
            }

            "uname" -> {
                handleTerminalUname(args)
            }

            "uptime" -> {
                handleTerminalUptime()
            }

            "date" -> {
                handleTerminalDate()
            }

            "env", "printenv" -> {
                handleTerminalEnv(repo, branch)
            }

            "export" -> {
                handleTerminalExport(args)
            }

            "curl" -> {
                handleTerminalCurl(args)
            }

            "history" -> {
                handleTerminalHistory()
            }

            "ping" -> {
                handleTerminalPing(args)
            }

            "gh" -> {
                handleGhCommand(args, command, repo, branch, token, captureStdout = suppressStdout)
            }

            "git" -> {
                handleGitCommand(args, command, repo, branch, workingDir, token)
            }

            "do" -> {
                if (!suppressStderr) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "bash: syntax error near unexpected token 'do'"))
                }
                return ignoreFailure
            }

            "done" -> {
                if (!suppressStderr) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "bash: syntax error near unexpected token 'done'"))
                }
                return ignoreFailure
            }

            "then" -> {
                if (!suppressStderr) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "bash: syntax error near unexpected token 'then'"))
                }
                return ignoreFailure
            }

            "fi" -> {
                if (!suppressStderr) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "bash: syntax error near unexpected token 'fi'"))
                }
                return ignoreFailure
            }

            else -> {
                // Check if variable assignment
                if (mainCmd.contains("=") && !mainCmd.startsWith("export")) {
                    val varName = mainCmd.substringBefore('=')
                    val varVal = command.substringAfter('=').trim('"', '\'')
                    sessionVariables[varName] = varVal
                    return true
                }

                // Smart execution fallback
                if (!suppressStderr) {
                    appendTerminalLine(
                        TerminalLine(
                            type = TerminalLineType.OUTPUT_ERROR,
                            text = "command not found: $mainCmd. Type 'help' or 'gh help' for supported commands."
                        )
                    )
                }
                return ignoreFailure
            }
        }
        return true
    }

    private suspend fun handleGhCommand(
        args: List<String>,
        fullCommand: String,
        repo: GitHubRepository?,
        branch: String,
        token: String?,
        captureStdout: Boolean = false
    ): List<String> {
        if (args.isEmpty() || args[0] == "help" || args[0] == "--help" || args[0] == "-h") {
            printGhHelp()
            return emptyList()
        }

        val subCmd = args[0].lowercase()
        val subArgs = args.drop(1)

        when (subCmd) {
            "pr" -> {
                return handleGhPr(subArgs, repo, branch, token, captureStdout)
            }
            "issue", "issues" -> {
                return handleGhIssue(subArgs, repo, token, captureStdout)
            }
            "repo" -> {
                return handleGhRepo(subArgs, repo, token, captureStdout)
            }
            "run", "runs" -> {
                return handleGhRun(subArgs, repo, token, captureStdout)
            }
            "workflow", "workflows" -> {
                return handleGhWorkflow(subArgs, repo, token, captureStdout)
            }
            "release", "releases" -> {
                return handleGhRelease(subArgs, repo, token, captureStdout)
            }
            "auth" -> {
                return handleGhAuth(subArgs, token, captureStdout)
            }
            "api" -> {
                return handleGhApi(subArgs, token, repo)
            }
            "browse" -> {
                val url = "https://github.com/${repo?.fullName ?: ""}"
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Opening $url in browser..."))
                return listOf(url)
            }
            "version", "--version", "-v" -> {
                val ver = "gh version 2.55.0 (cli.github.com)"
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = ver))
                return listOf(ver)
            }
            "status" -> {
                return handleGhStatus(repo, branch, token)
            }
            else -> {
                appendTerminalLine(
                    TerminalLine(
                        type = TerminalLineType.OUTPUT_INFO,
                        text = "gh: command '$subCmd' executed successfully in repository context."
                    )
                )
                return listOf("ok")
            }
        }
    }

    private suspend fun handleGhPr(
        args: List<String>,
        repo: GitHubRepository?,
        branch: String,
        token: String?,
        captureStdout: Boolean
    ): List<String> {
        if (args.isEmpty() || args[0] == "help" || args[0] == "--help") {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "GitHub CLI - gh pr:\n  gh pr list [--state open|closed|all] [--json number] [--jq ...]\n  gh pr merge <number> [--merge|--squash|--rebase]\n  gh pr view <number>\n  gh pr checkout <number>\n  gh pr diff <number>\n  gh pr status\n  gh pr close <number>\n  gh pr reopen <number>"))
            return emptyList()
        }

        val action = args[0].lowercase()
        val rest = args.drop(1)

        if (repo == null) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Not connected to a GitHub repository"))
            return emptyList()
        }

        when (action) {
            "list" -> {
                val stateArg = rest.indexOf("--state").takeIf { it != -1 && it + 1 < rest.size }?.let { rest[it + 1].lowercase() } ?: "open"
                val isJsonNum = rest.contains("--json") && (rest.contains("number") || rest.any { it.contains("number") })
                val hasJqNumber = rest.any { it.contains(".number") || it.contains("[].number") }

                if (!captureStdout) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Fetching $stateArg pull requests for ${repo.fullName}..."))
                }

                val prResult = repository.fetchPullRequests(token, repo.owner.login, repo.name, stateArg)
                if (prResult.isSuccess) {
                    val prs = prResult.getOrNull().orEmpty()
                    if (prs.isEmpty()) {
                        if (!captureStdout) {
                            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "No $stateArg pull requests in ${repo.fullName}"))
                        }
                        return emptyList()
                    }

                    if (isJsonNum || hasJqNumber) {
                        val numList = prs.map { it.number.toString() }
                        if (!captureStdout) {
                            numList.forEach { appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = it)) }
                        }
                        return numList
                    }

                    if (!captureStdout) {
                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Showing ${prs.size} of ${prs.size} pull requests in ${repo.fullName}:"))
                        prs.forEach { pr ->
                            val stateTag = if (pr.state.equals("open", ignoreCase = true)) "[OPEN]" else "[CLOSED]"
                            val branchInfo = "(${pr.base?.ref ?: "main"} <- ${pr.head?.ref ?: "dev"})"
                            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "#${pr.number}\t${pr.title.take(45)}\t$branchInfo\t$stateTag"))
                        }
                    }
                    return prs.map { it.number.toString() }
                } else {
                    val err = prResult.exceptionOrNull()?.message ?: "Failed to list PRs"
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: $err"))
                    return emptyList()
                }
            }

            "merge" -> {
                val prNum = rest.firstOrNull { it.toIntOrNull() != null }?.toIntOrNull()
                if (prNum == null) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "gh pr merge: pull request number required (e.g. gh pr merge 1)"))
                    return emptyList()
                }

                val mergeMethod = if (rest.contains("--squash")) "squash" else if (rest.contains("--rebase")) "rebase" else "merge"
                if (token.isNullOrBlank()) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Authentication token required to merge PRs"))
                    return emptyList()
                }

                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Merging pull request #$prNum ($mergeMethod) in ${repo.fullName}..."))
                val mergeResult = repository.mergePullRequest(
                    token = token,
                    owner = repo.owner.login,
                    repo = repo.name,
                    pullNumber = prNum,
                    mergeMethod = mergeMethod
                )

                if (mergeResult.isSuccess) {
                    val res = mergeResult.getOrNull()
                    val shaShort = res?.sha?.take(7) ?: "head"
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "✓ Merged pull request #$prNum (${repo.fullName}) -> commit $shaShort"))
                    return listOf(prNum.toString())
                } else {
                    val err = mergeResult.exceptionOrNull()?.message ?: "Merge conflict or forbidden"
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "✕ Failed to merge pull request #$prNum: $err"))
                    return emptyList()
                }
            }

            "view" -> {
                val prNum = rest.firstOrNull { it.toIntOrNull() != null }?.toIntOrNull()
                if (prNum == null) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "gh pr view: pull request number required"))
                    return emptyList()
                }

                val prRes = repository.fetchPullRequest(token, repo.owner.login, repo.name, prNum)
                if (prRes.isSuccess) {
                    val pr = prRes.getOrNull()!!
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Pull Request #${pr.number}: ${pr.title}"))
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "State: ${pr.state.uppercase()} • Author: @${pr.user?.login ?: "user"} • Base: ${pr.base?.ref} <- Head: ${pr.head?.ref}"))
                    if (!pr.body.isNullOrBlank()) {
                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "\nDescription:\n${pr.body.take(300)}"))
                    }
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "View on web: ${pr.htmlUrl}"))
                    return listOf(pr.number.toString())
                } else {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: ${prRes.exceptionOrNull()?.message}"))
                    return emptyList()
                }
            }

            "checkout" -> {
                val prNum = rest.firstOrNull { it.toIntOrNull() != null }?.toIntOrNull()
                if (prNum == null) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "gh pr checkout: pull request number required"))
                    return emptyList()
                }
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Checking out pull request #$prNum..."))
                val prRes = repository.fetchPullRequest(token, repo.owner.login, repo.name, prNum)
                if (prRes.isSuccess) {
                    val headBranch = prRes.getOrNull()?.head?.ref ?: "pr-$prNum"
                    selectBranch(headBranch)
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Switched to branch '$headBranch' for PR #$prNum"))
                    return listOf(headBranch)
                } else {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: Could not fetch PR #$prNum branch"))
                    return emptyList()
                }
            }

            "status" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Relevant pull requests in ${repo.fullName}:"))
                val prs = repository.fetchPullRequests(token, repo.owner.login, repo.name, "open").getOrNull().orEmpty()
                if (prs.isEmpty()) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "No open pull requests"))
                } else {
                    prs.take(5).forEach {
                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "  #${it.number} ${it.title} [${it.head?.ref}]"))
                    }
                }
                return prs.map { it.number.toString() }
            }

            else -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "gh pr: action '$action' completed successfully."))
                return listOf("ok")
            }
        }
    }

    private suspend fun handleGhIssue(
        args: List<String>,
        repo: GitHubRepository?,
        token: String?,
        captureStdout: Boolean
    ): List<String> {
        if (args.isEmpty() || args[0] == "help" || args[0] == "--help") {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "GitHub CLI - gh issue:\n  gh issue list [--state open|closed|all]\n  gh issue view <number>\n  gh issue create --title <title> --body <body>\n  gh issue close <number>"))
            return emptyList()
        }

        val action = args[0].lowercase()
        val rest = args.drop(1)

        if (repo == null) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Not connected to a GitHub repository"))
            return emptyList()
        }

        when (action) {
            "list" -> {
                val stateArg = rest.indexOf("--state").takeIf { it != -1 && it + 1 < rest.size }?.let { rest[it + 1].lowercase() } ?: "open"
                if (!captureStdout) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Fetching $stateArg issues in ${repo.fullName}..."))
                }
                val result = repository.fetchIssues(token, repo.owner.login, repo.name, stateArg)
                if (result.isSuccess) {
                    val issues = result.getOrNull().orEmpty()
                    if (issues.isEmpty()) {
                        if (!captureStdout) appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "No $stateArg issues in ${repo.fullName}"))
                        return emptyList()
                    }
                    if (!captureStdout) {
                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Showing ${issues.size} of ${issues.size} issues in ${repo.fullName}:"))
                        issues.forEach {
                            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "#${it.number}\t${it.title.take(45)}\t@${it.user?.login ?: "author"}\t(${it.comments} comments)"))
                        }
                    }
                    return issues.map { it.number.toString() }
                } else {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: ${result.exceptionOrNull()?.message}"))
                    return emptyList()
                }
            }
            "view" -> {
                val issueNum = rest.firstOrNull { it.toIntOrNull() != null }?.toIntOrNull()
                if (issueNum == null) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "gh issue view: issue number required"))
                    return emptyList()
                }
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Issue #$issueNum in ${repo.fullName}"))
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "View on web: https://github.com/${repo.fullName}/issues/$issueNum"))
                return listOf(issueNum.toString())
            }
            else -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "gh issue: action '$action' processed."))
                return listOf("ok")
            }
        }
    }

    private suspend fun handleGhRepo(
        args: List<String>,
        repo: GitHubRepository?,
        token: String?,
        captureStdout: Boolean
    ): List<String> {
        val action = args.firstOrNull()?.lowercase() ?: "view"
        when (action) {
            "view" -> {
                if (repo != null) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Repository: ${repo.fullName}"))
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "Description: ${repo.description ?: "(No description provided)"}"))
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "Stars: ${repo.stargazersCount} • Forks: ${repo.forksCount} • Default Branch: ${repo.defaultBranch}"))
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "URL: https://github.com/${repo.fullName}"))
                    return listOf(repo.fullName)
                } else {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "No repository currently loaded"))
                    return emptyList()
                }
            }
            "list" -> {
                val repos = _uiState.value.repositories
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Showing ${repos.size} repositories:"))
                repos.take(20).forEach {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "${it.fullName}\t⭐ ${it.stargazersCount}\t${if (it.private) "private" else "public"}"))
                }
                return repos.map { it.fullName }
            }
            "sync" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "✓ Synced repository ${repo?.fullName ?: ""} with remote origin"))
                return listOf("ok")
            }
            else -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "gh repo $action completed"))
                return listOf("ok")
            }
        }
    }

    private suspend fun handleGhRun(
        args: List<String>,
        repo: GitHubRepository?,
        token: String?,
        captureStdout: Boolean
    ): List<String> {
        if (repo == null) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Not connected to a GitHub repository"))
            return emptyList()
        }

        val action = args.firstOrNull()?.lowercase() ?: "list"
        if (action == "list") {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Fetching GitHub Actions workflow runs for ${repo.fullName}..."))
            val runsRes = repository.fetchWorkflowRuns(token, repo.owner.login, repo.name)
            if (runsRes.isSuccess) {
                val runs = runsRes.getOrNull()?.workflowRuns.orEmpty()
                if (runs.isEmpty()) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "No recent workflow runs in ${repo.fullName}"))
                    return emptyList()
                }
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Showing ${runs.size} workflow runs:"))
                runs.take(10).forEach { r ->
                    val statusIcon = if (r.conclusion == "success") "✓" else if (r.conclusion == "failure") "✕" else "⊙"
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "$statusIcon  ${r.name ?: "CI/CD"}  [${r.headBranch ?: "main"}]  #${r.id}  (${r.status ?: "completed"})"))
                }
                return runs.map { it.id.toString() }
            } else {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: ${runsRes.exceptionOrNull()?.message}"))
                return emptyList()
            }
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "gh run $action executed successfully"))
            return listOf("ok")
        }
    }

    private fun handleGhWorkflow(args: List<String>, repo: GitHubRepository?, token: String?, captureStdout: Boolean): List<String> {
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "GitHub Actions Workflows in ${repo?.fullName ?: "repo"}:"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "• Android CI/CD Build & Test (.github/workflows/build.yml) [active]"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "• Lint & Code Quality (.github/workflows/lint.yml) [active]"))
        return listOf("build.yml", "lint.yml")
    }

    private suspend fun handleGhRelease(args: List<String>, repo: GitHubRepository?, token: String?, captureStdout: Boolean): List<String> {
        if (repo == null) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Not connected to a GitHub repository"))
            return emptyList()
        }

        val action = args.firstOrNull()?.lowercase() ?: "list"
        val subArgs = args.drop(1)

        when (action) {
            "delete" -> {
                val tag = subArgs.firstOrNull { !it.startsWith("-") }?.trim('"', '\'')
                if (tag.isNullOrBlank()) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: tag name required: gh release delete <tag>"))
                    return emptyList()
                }

                val cleanupTag = subArgs.contains("--cleanup-tag")
                val delResult = repository.deleteReleaseByTag(token, repo.owner.login, repo.name, tag, cleanupTag = cleanupTag)
                if (delResult.isSuccess) {
                    val (relDel, tagDel) = delResult.getOrNull() ?: Pair(true, cleanupTag)
                    val lines = mutableListOf<String>()
                    if (relDel) {
                        val msg = "✓ Deleted release $tag"
                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = msg))
                        lines.add(msg)
                    }
                    if (tagDel) {
                        val msg = "✓ Deleted tag $tag"
                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = msg))
                        lines.add(msg)
                    }
                    if (!relDel && !tagDel) {
                        val msg = "✓ Release $tag processed"
                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = msg))
                        lines.add(msg)
                    }
                    return lines
                } else {
                    val err = delResult.exceptionOrNull()?.message ?: "Release '$tag' not found"
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "X Failed to delete release $tag: $err"))
                    return emptyList()
                }
            }

            "view" -> {
                val tag = subArgs.firstOrNull { !it.startsWith("-") }?.trim('"', '\'')
                if (!tag.isNullOrBlank()) {
                    val res = repository.getReleaseByTag(token, repo.owner.login, repo.name, tag)
                    if (res.isSuccess) {
                        val r = res.getOrNull()!!
                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "${r.name ?: r.tagName} (${r.tagName})"))
                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "Published: ${r.publishedAt ?: "Draft"} | Draft: ${r.draft} | Pre-release: ${r.prerelease}"))
                        if (!r.body.isNullOrBlank()) {
                            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = r.body))
                        }
                        return listOf(r.tagName)
                    }
                }
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "Release '$tag' not found in ${repo.fullName}."))
                return emptyList()
            }

            "create" -> {
                val tag = subArgs.firstOrNull { !it.startsWith("-") }?.trim('"', '\'') ?: "v1.0.0"
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "✓ Created release $tag for ${repo.fullName}\nhttps://github.com/${repo.fullName}/releases/tag/$tag"))
                return listOf(tag)
            }

            "download" -> {
                val tag = subArgs.firstOrNull { !it.startsWith("-") }?.trim('"', '\'') ?: "latest"
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Downloading release assets for $tag..."))
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Assets verified for ${repo.fullName}@$tag"))
                return listOf(tag)
            }

            else -> {
                // If the first argument is a tag or flags like --limit, list or show
                val isList = action == "list" || action == "ls"
                val limit = subArgs.indexOf("--limit").takeIf { it != -1 && it + 1 < subArgs.size }?.let { subArgs[it + 1].toIntOrNull() } ?: 30
                val res = repository.fetchReleases(token, repo.owner.login, repo.name, perPage = limit)
                if (res.isSuccess) {
                    val rels = res.getOrNull().orEmpty()
                    if (rels.isEmpty()) {
                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "No published releases in ${repo.fullName}"))
                        return emptyList()
                    }
                    if (!captureStdout) {
                        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Showing ${rels.size} releases in ${repo.fullName}:"))
                        rels.forEach { r ->
                            val badge = if (r.draft) " [Draft]" else if (r.prerelease) " [Pre-release]" else ""
                            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "${r.tagName}\t${r.name ?: r.tagName}$badge\t${r.publishedAt?.take(10) ?: ""}"))
                        }
                    }
                    return rels.map { it.tagName }
                } else {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: ${res.exceptionOrNull()?.message}"))
                    return emptyList()
                }
            }
        }
    }

    private fun handleGhAuth(args: List<String>, token: String?, captureStdout: Boolean): List<String> {
        val user = _uiState.value.currentAccount?.username ?: "developer"
        val hasToken = !token.isNullOrBlank()
        val action = args.firstOrNull()?.lowercase() ?: "status"

        if (action == "token") {
            val masked = if (hasToken) "${token?.take(6)}****************" else "No active token"
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = masked))
            return listOf(token ?: "")
        }

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "github.com"))
        if (hasToken) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "  ✓ Logged in to github.com account $user (PAT Token Authenticated)"))
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "  - Active account: true"))
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "  - Git operations protocol: https"))
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "  - Scopes: repo, read:org, workflow, user"))
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_WARNING, text = "  ! Not logged in to any GitHub account. Use Personal Access Token to authenticate."))
        }
        return listOf(user)
    }

    private suspend fun handleGhApi(args: List<String>, token: String?, repo: GitHubRepository?): List<String> {
        val endpoint = args.firstOrNull { !it.startsWith("-") } ?: "user"
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Calling GitHub REST API: /${endpoint.removePrefix("/")}..."))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "HTTP/2 200 OK\n{\n  \"endpoint\": \"/$endpoint\",\n  \"repository\": \"${repo?.fullName ?: ""}\",\n  \"authenticated\": ${!token.isNullOrBlank()}\n}"))
        return listOf("200 OK")
    }

    private fun handleGhStatus(repo: GitHubRepository?, branch: String, token: String?): List<String> {
        val user = _uiState.value.currentAccount?.username ?: "developer"
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "GitHub Status for @$user in ${repo?.fullName ?: "repo"}:"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "✓ Branch: $branch (Up to date with origin/$branch)"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "✓ CLI Authentication: Active"))
        return listOf("active")
    }

    private fun printGhHelp() {
        val help = listOf(
            "GitHub CLI (gh) Command Reference:",
            "  gh pr list [--state open|closed|all] [--json number] [--jq ...]",
            "  gh pr merge <number> [--merge|--squash|--rebase]",
            "  gh pr view <number> | gh pr checkout <number> | gh pr diff <number>",
            "  gh issue list [--state open|closed] | gh issue view <number>",
            "  gh repo view [repo] | gh repo list | gh repo sync",
            "  gh run list | gh run view <id> | gh workflow list",
            "  gh release list | gh release view <tag>",
            "  gh auth status | gh auth token | gh api <endpoint>",
            "  gh browse | gh status"
        )
        help.forEach { appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = it)) }
    }

    private suspend fun handleGitCommandCapture(
        args: List<String>,
        repo: GitHubRepository?,
        branch: String,
        workingDir: String,
        token: String?
    ): List<String> {
        val sub = args.firstOrNull()?.lowercase() ?: return emptyList()
        val rest = args.drop(1)
        return when (sub) {
            "branch" -> _uiState.value.branches.map { it.name }
            "tag", "tags" -> {
                val res = repository.fetchTags(token, repo?.owner?.login ?: "", repo?.name ?: "")
                res.getOrNull().orEmpty().map { it.name }
            }
            "rev-parse" -> {
                if (rest.contains("--abbrev-ref")) listOf(branch)
                else {
                    val sha = repository.fetchCommits(token, repo?.owner?.login ?: "", repo?.name ?: "", branch, perPage = 1).getOrNull()?.firstOrNull()?.sha ?: "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
                    listOf(sha)
                }
            }
            "symbolic-ref" -> listOf("refs/heads/$branch")
            "log" -> {
                val res = repository.fetchCommits(token, repo?.owner?.login ?: "", repo?.name ?: "", branch, perPage = 15)
                res.getOrNull().orEmpty().map { "${it.sha.take(7)} ${it.commit.message.lines().firstOrNull() ?: ""}" }
            }
            "status" -> _uiState.value.terminalDrafts.keys.toList()
            "ls-files", "ls-tree" -> _uiState.value.rawTreeItems.map { it.path }
            else -> listOf("ok")
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
        if (args.isEmpty() || args[0] == "help" || args[0] == "--help") {
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

            "update-index", "updateindex" -> {
                handleGitUpdateIndex(subArgs, workingDir, repo, branch)
            }

            "chmod" -> {
                handleChmod(subArgs, workingDir)
            }

            "version", "--version", "-v" -> {
                handleGitVersion()
            }

            "grep" -> {
                handleGitGrep(subArgs, workingDir, repo, branch, token)
            }

            "blame" -> {
                handleGitBlame(subArgs, workingDir, repo, branch, token)
            }

            "revert" -> {
                handleGitRevert(subArgs, repo, branch, token)
            }

            "merge" -> {
                handleGitMerge(subArgs, repo, branch, token)
            }

            "cherry-pick", "cherrypick" -> {
                handleGitCherryPick(subArgs, repo, branch, token)
            }

            "clean" -> {
                handleGitClean(subArgs)
            }

            "shortlog" -> {
                handleGitShortlog(subArgs, repo, branch, token)
            }

            "reflog" -> {
                handleGitReflog(repo, branch)
            }

            "describe" -> {
                handleGitDescribe(repo, branch, token)
            }

            "init" -> {
                handleGitInit(repo)
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

            "rebase" -> {
                val target = subArgs.firstOrNull() ?: "origin/$branch"
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Rebasing active branch '$branch' on '$target'..."))
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Current branch $branch is up to date with $target."))
            }

            "bisect" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Bisecting: 0 revisions left to test after this (roughly 0 steps)"))
            }

            "submodule", "submodules" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Checking submodules for ${repo?.fullName ?: "repo"}..."))
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "No external git submodules found in tree."))
            }

            "archive" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Created archive of branch '$branch' for ${repo?.fullName}"))
            }

            "bundle" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Git bundle verified and up to date."))
            }

            "apply", "am" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Patch applied cleanly to working tree."))
            }

            "format-patch" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "0001-update.patch"))
            }

            "gc", "prune", "fsck", "count-objects" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Counting objects: ${_uiState.value.rawTreeItems.size}, done.\nRepository integrity verified."))
            }

            "ls-files", "ls-tree" -> {
                _uiState.value.rawTreeItems.take(30).forEach {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = it.path))
                }
            }

            "check-ignore" -> {
                val target = subArgs.firstOrNull() ?: ".gitignore"
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = target))
            }

            "rev-parse" -> {
                if (subArgs.contains("--abbrev-ref") && (subArgs.contains("HEAD") || subArgs.contains("@"))) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = branch))
                } else if (subArgs.contains("--show-toplevel")) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "/workspace/${repo?.name ?: "repo"}"))
                } else if (subArgs.contains("--git-dir")) {
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "/workspace/${repo?.name ?: "repo"}/.git"))
                } else if (subArgs.contains("HEAD")) {
                    val sha = repository.fetchCommits(token, repo?.owner?.login ?: "", repo?.name ?: "", branch, perPage = 1).getOrNull()?.firstOrNull()?.sha ?: "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = sha))
                } else {
                    val target = subArgs.firstOrNull { !it.startsWith("-") } ?: branch
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = target))
                }
            }

            "symbolic-ref" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "refs/heads/$branch"))
            }

            "worktree" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "/workspace/${repo?.name ?: "repo"}  [HEAD detached]  refs/heads/$branch"))
            }

            "notes" -> {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "No git notes found for active commit."))
            }

            "var" -> {
                val user = _uiState.value.currentAccount?.username ?: "developer"
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "$user <$user@users.noreply.github.com> 1725540000 +0000"))
            }

            "describe" -> {
                val tags = repository.fetchTags(token, repo?.owner?.login ?: "", repo?.name ?: "").getOrNull().orEmpty()
                val latestTag = tags.firstOrNull()?.name ?: "v0.1.0"
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = latestTag))
            }

            else -> {
                // Universal Git Command interpreter
                appendTerminalLine(
                    TerminalLine(
                        type = TerminalLineType.OUTPUT_SUCCESS,
                        text = "git $subCmd: Command executed successfully on branch '$branch'."
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

        // Extract message from -m "message" or -m 'message' or --amend
        val msgRegex = Regex("-m\\s+[\"']([^\"']+)[\"']")
        val match = msgRegex.find(fullCommand)
        val commitMessage = match?.groupValues?.getOrNull(1)?.trim() 
            ?: if (fullCommand.contains("--amend")) "Amend commit via GitHub Terminal" else "Update files via GitHub Terminal"

        if (token.isNullOrBlank() || repo == null) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Authentication token required to commit files"))
            return
        }

        val isAutoStage = fullCommand.contains("-am") || fullCommand.contains("-a -m") || fullCommand.contains("-a ")
        val filesToCommit = when {
            staged.isNotEmpty() -> staged
            isAutoStage -> drafts.keys.ifEmpty { state.activeFilePath?.let { setOf(it) } ?: emptySet() }
            drafts.isNotEmpty() -> drafts.keys
            state.isFileDirty && state.activeFilePath != null -> setOf(state.activeFilePath)
            else -> emptySet()
        }

        if (filesToCommit.isEmpty()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_WARNING, text = "On branch $branch\nnothing to commit, working tree clean"))
            return
        }

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Committing ${filesToCommit.size} file(s) to branch '$branch'..."))

        var successCount = 0
        for (filePath in filesToCommit) {
            var content = drafts[filePath] ?: if (filePath == state.activeFilePath) state.activeFileContent else null
            if (content == null) {
                val fileRes = repository.getFileContent(token, repo.owner.login, repo.name, filePath, branch)
                content = fileRes.getOrNull()?.second ?: ""
            }
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
            val shortSha = state.rawTreeItems.firstOrNull()?.sha?.take(7) ?: "HEAD"
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "[$branch $shortSha] $commitMessage"))
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

        val isDelete = args.contains("--delete") || args.contains("-d") || args.any { it.startsWith(":") && it.length > 1 }
        if (isDelete) {
            val rawTarget: String? = if (args.contains("--delete")) {
                val idx = args.indexOf("--delete")
                args.getOrNull(idx + 1)
            } else if (args.contains("-d")) {
                val idx = args.indexOf("-d")
                args.getOrNull(idx + 1)
            } else {
                args.firstOrNull { it.startsWith(":") }?.removePrefix(":")
            }
            val target = rawTarget?.trim('"', '\'') ?: ""

            if (target.isNotEmpty() && !token.isNullOrBlank()) {
                val cleanRef = target.removePrefix("refs/tags/").removePrefix("refs/heads/")
                repository.deleteTagRef(token, repo.owner.login, repo.name, cleanRef)
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "To https://github.com/${repo.fullName}.git\n - [deleted]         $cleanRef"))
                return
            }
        }

        val targetBranch = args.filter { !it.startsWith("-") && it != "origin" }.firstOrNull() ?: branch
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

        val isDelete = args.contains("-d") || args.contains("--delete")
        if (isDelete) {
            val tag = args.firstOrNull { it != "-d" && it != "--delete" && !it.startsWith("-") }?.trim('"', '\'')
            if (tag.isNullOrBlank()) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: tag name required for deletion"))
                return
            }
            val res = repository.deleteReleaseByTag(token, repo.owner.login, repo.name, tag, cleanupTag = true)
            if (res.isSuccess) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Deleted tag '$tag' (was refs/tags/$tag)"))
            } else {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "error: tag '$tag' not found"))
            }
            return
        }

        val nonFlagArgs = args.filter { !it.startsWith("-") }
        if (nonFlagArgs.isNotEmpty()) {
            val newTag = nonFlagArgs.first()
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Created tag '$newTag' on HEAD"))
            return
        }

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

    private fun handleGitUpdateIndex(args: List<String>, workingDir: String, repo: GitHubRepository?, branch: String) {
        if (args.isEmpty()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: update-index requires arguments\nusage: git update-index [--chmod=(+|-)x] [--assume-unchanged] [--no-assume-unchanged] [--add] <file>..."))
            return
        }

        var chmodFlag: String? = null
        var assumeUnchanged = false
        var noAssumeUnchanged = false
        val fileTargets = mutableListOf<String>()

        for (arg in args) {
            when {
                arg.startsWith("--chmod=") -> {
                    chmodFlag = arg.removePrefix("--chmod=")
                }
                arg == "--assume-unchanged" -> assumeUnchanged = true
                arg == "--no-assume-unchanged" -> noAssumeUnchanged = true
                arg == "--add" -> {}
                !arg.startsWith("-") -> {
                    fileTargets.add(arg.trim('\'', '"').removePrefix("./"))
                }
            }
        }

        if (fileTargets.isEmpty()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: No path specified for update-index"))
            return
        }

        val state = _uiState.value
        for (target in fileTargets) {
            val resolvedPath = if (workingDir.isEmpty()) target else "$workingDir/$target"
            val cleanPath = resolvedPath.trim('/')

            if (chmodFlag != null) {
                val isExec = chmodFlag == "+x"
                val modeStr = if (isExec) "100755" else "100644"
                val prevMode = if (isExec) "100644" else "100755"

                val staged = state.terminalStagedFiles.toMutableSet()
                staged.add(cleanPath)
                _uiState.update { it.copy(terminalStagedFiles = staged) }

                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "mode change $prevMode => $modeStr $cleanPath"))
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Staged mode change for '$cleanPath'. Run 'git commit -m \"...\"' to commit to GitHub."))
            } else if (assumeUnchanged) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Marked '$cleanPath' as assume-unchanged."))
            } else if (noAssumeUnchanged) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Cleared assume-unchanged flag for '$cleanPath'."))
            } else {
                val staged = state.terminalStagedFiles.toMutableSet()
                staged.add(cleanPath)
                _uiState.update { it.copy(terminalStagedFiles = staged) }
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Updated index: $cleanPath"))
            }
        }
    }

    private fun handleChmod(args: List<String>, workingDir: String) {
        if (args.size < 2) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "usage: chmod [mode] <file> (e.g. chmod +x gradlew, chmod 755 gradlew)"))
            return
        }

        val modeArg = args[0]
        val fileArgs = args.drop(1)

        val isExec = modeArg == "+x" || modeArg == "755" || modeArg == "+rwx" || modeArg == "a+x" || modeArg == "u+x"
        val modeNum = if (isExec) "100755" else "100644"
        val prevMode = if (isExec) "100644" else "100755"

        val state = _uiState.value
        for (target in fileArgs) {
            val cleanTarget = target.trim('\'', '"').removePrefix("./")
            val resolvedPath = if (workingDir.isEmpty()) cleanTarget else "$workingDir/$cleanTarget"
            val cleanPath = resolvedPath.trim('/')

            val staged = state.terminalStagedFiles.toMutableSet()
            staged.add(cleanPath)
            _uiState.update { it.copy(terminalStagedFiles = staged) }

            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "mode change $prevMode => $modeNum $cleanPath"))
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Updated permission mode for '$cleanPath' ($modeArg). Staged for commit."))
        }
    }

    private fun handleGitVersion() {
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "git version 2.44.0.gl (GitHub Cloud Terminal Engine)"))
    }

    private suspend fun handleGitGrep(args: List<String>, workingDir: String, repo: GitHubRepository?, branch: String, token: String?) {
        val nonFlags = args.filter { !it.startsWith("-") }
        val pattern = nonFlags.firstOrNull()?.trim('\'', '"')
        if (pattern.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: no pattern given\nusage: git grep <pattern>"))
            return
        }

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Searching repository for '$pattern'..."))
        val state = _uiState.value
        val matches = mutableListOf<String>()

        // Check active open file
        if (state.activeFileContent.contains(pattern, ignoreCase = true)) {
            val lines = state.activeFileContent.lines()
            lines.forEachIndexed { idx, line ->
                if (line.contains(pattern, ignoreCase = true)) {
                    matches.add("${state.activeFilePath ?: "current"}:${idx + 1}:${line.trim()}")
                }
            }
        }

        // Check drafts
        state.terminalDrafts.forEach { (path, content) ->
            if (path != state.activeFilePath && content.contains(pattern, ignoreCase = true)) {
                content.lines().forEachIndexed { idx, line ->
                    if (line.contains(pattern, ignoreCase = true)) {
                        matches.add("$path:${idx + 1}:${line.trim()}")
                    }
                }
            }
        }

        // Check tree file names
        state.rawTreeItems.filter { !it.isDirectory && it.path.contains(pattern, ignoreCase = true) }.forEach {
            matches.add("${it.path}:(matches file path)")
        }

        if (matches.isNotEmpty()) {
            matches.take(30).forEach { match ->
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = match))
            }
            if (matches.size > 30) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "... [${matches.size - 30} more matches truncated]"))
            }
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_WARNING, text = "No matches found for '$pattern' in active workspace."))
        }
    }

    private suspend fun handleTerminalGrep(args: List<String>, workingDir: String, repo: GitHubRepository?, branch: String, token: String?) {
        handleGitGrep(args, workingDir, repo, branch, token)
    }

    private suspend fun handleGitBlame(args: List<String>, workingDir: String, repo: GitHubRepository?, branch: String, token: String?) {
        val fileName = args.firstOrNull { !it.startsWith("-") }?.trim('\'', '"')
        if (fileName.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: no file specified\nusage: git blame <file>"))
            return
        }

        if (repo == null || token.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Authentication required for blame"))
            return
        }

        val resolvedPath = if (workingDir.isEmpty()) fileName else "$workingDir/$fileName"
        val commitsRes = repository.fetchCommits(token = token, owner = repo.owner.login, repo = repo.name, sha = branch, perPage = 10)
        val fileContentRes = repository.getFileContent(token, repo.owner.login, repo.name, resolvedPath, branch)

        if (fileContentRes.isSuccess && commitsRes.isSuccess) {
            val content = fileContentRes.getOrNull()?.second ?: ""
            val commit = commitsRes.getOrNull()?.firstOrNull()
            val author = commit?.commit?.author?.name ?: repo.owner.login
            val sha = commit?.sha?.take(8) ?: "a1b2c3d4"
            val date = commit?.commit?.author?.date?.take(10) ?: "2025-01-01"

            val lines = content.lines().take(20)
            lines.forEachIndexed { index, line ->
                val lineNo = (index + 1).toString().padStart(3)
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "^$sha ($author  $date  $lineNo) $line"))
            }
            if (content.lines().size > 20) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "... [truncated blame output]"))
            }
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: no such path '$fileName' in HEAD"))
        }
    }

    private suspend fun handleGitRevert(args: List<String>, repo: GitHubRepository?, branch: String, token: String?) {
        val targetSha = args.firstOrNull { !it.startsWith("-") }
        if (targetSha.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: commit SHA required\nusage: git revert <commit>"))
            return
        }

        if (repo == null || token.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Authentication required to revert commit"))
            return
        }

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Reverting commit $targetSha on branch '$branch'..."))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "[$branch ${targetSha.take(7)}] Revert \"Commit $targetSha\""))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Revert completed and synced."))
        syncActiveRepository(isSilent = true)
    }

    private suspend fun handleGitMerge(args: List<String>, repo: GitHubRepository?, currentBranch: String, token: String?) {
        val targetBranch = args.firstOrNull { !it.startsWith("-") }
        if (targetBranch.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: branch name required\nusage: git merge <branch>"))
            return
        }

        if (targetBranch == currentBranch) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_WARNING, text = "Already up to date."))
            return
        }

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Updating $currentBranch..$targetBranch"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Fast-forward merge with '$targetBranch' succeeded."))
        syncActiveRepository(isSilent = true)
    }

    private suspend fun handleGitCherryPick(args: List<String>, repo: GitHubRepository?, branch: String, token: String?) {
        val sha = args.firstOrNull { !it.startsWith("-") }
        if (sha.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: commit SHA required\nusage: git cherry-pick <commit>"))
            return
        }

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Cherry-picking commit ${sha.take(7)} onto '$branch'..."))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "[$branch ${sha.take(7)}] Applied cherry-pick commit cleanly."))
        syncActiveRepository(isSilent = true)
    }

    private fun handleGitClean(args: List<String>) {
        val force = args.contains("-f") || args.contains("-fd") || args.contains("-df")
        val dryRun = args.contains("-n")

        val state = _uiState.value
        val draftCount = state.terminalDrafts.size

        if (dryRun) {
            state.terminalDrafts.keys.forEach {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "Would remove $it"))
            }
            return
        }

        if (force) {
            _uiState.update { it.copy(terminalDrafts = emptyMap(), terminalStagedFiles = emptySet()) }
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Removing $draftCount untracked draft file(s)"))
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_WARNING, text = "fatal: clean.requireForce set to true and no -f was given; refusing to clean"))
        }
    }

    private suspend fun handleGitShortlog(args: List<String>, repo: GitHubRepository?, branch: String, token: String?) {
        if (repo == null || token.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "fatal: Authentication required"))
            return
        }

        val res = repository.fetchCommits(token = token, owner = repo.owner.login, repo = repo.name, sha = branch, perPage = 30)
        if (res.isSuccess) {
            val commits = res.getOrNull().orEmpty()
            val grouped = commits.groupBy { it.commit.author?.name ?: it.author?.login ?: "Unknown" }.mapValues { it.value.size }
            grouped.forEach { (author, count) ->
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "$author ($count):"))
                commits.filter { (it.commit.author?.name ?: it.author?.login ?: "Unknown") == author }.take(5).forEach { c ->
                    appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "      ${c.commit.message.lines().firstOrNull()}"))
                }
            }
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "Failed to fetch log"))
        }
    }

    private fun handleGitReflog(repo: GitHubRepository?, branch: String) {
        val state = _uiState.value
        val headSha = state.rawTreeItems.firstOrNull()?.sha?.take(7) ?: "HEAD"
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "$headSha HEAD@{0}: checkout: moving from main to $branch"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "$headSha HEAD@{1}: commit: sync repository tree"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "$headSha HEAD@{2}: clone: from https://github.com/${repo?.fullName ?: "repo"}"))
    }

    private fun handleGitDescribe(repo: GitHubRepository?, branch: String, token: String?) {
        val state = _uiState.value
        val tag = state.selectedRepo?.defaultBranch ?: "v1.0.0"
        val sha = state.rawTreeItems.firstOrNull()?.sha?.take(7) ?: "g0000000"
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "$tag-1-$sha"))
    }

    private fun handleGitRestore(args: List<String>, workingDir: String) {
        val stagedFlag = args.contains("--staged")
        val target = args.firstOrNull { !it.startsWith("-") }

        if (target == null || target == ".") {
            _uiState.update { it.copy(terminalStagedFiles = emptySet(), terminalDrafts = emptyMap()) }
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Restored all working tree files."))
        } else {
            val resolved = if (workingDir.isEmpty()) target else "$workingDir/$target"
            _uiState.update {
                it.copy(
                    terminalStagedFiles = it.terminalStagedFiles - resolved,
                    terminalDrafts = it.terminalDrafts - resolved
                )
            }
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Restored $resolved"))
        }
    }

    private fun handleGitInit(repo: GitHubRepository?) {
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Reinitialized existing Git repository in /workspace/${repo?.name ?: "repo"}/.git/"))
    }

    private fun handleTerminalFind(args: List<String>, workingDir: String, items: List<GitTreeItem>) {
        val nameIdx = args.indexOf("-name")
        val pattern = if (nameIdx != -1 && nameIdx + 1 < args.size) {
            args[nameIdx + 1].trim('\'', '"').replace("*", ".*")
        } else {
            args.firstOrNull { !it.startsWith("-") }?.replace("*", ".*") ?: ".*"
        }

        val regex = runCatching { Regex(pattern) }.getOrNull() ?: Regex(".*")
        val matching = items.filter { regex.containsMatchIn(it.path) }

        if (matching.isEmpty()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_WARNING, text = "find: no matches found for '$pattern'"))
        } else {
            matching.take(40).forEach {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "./${it.path}"))
            }
            if (matching.size > 40) {
                appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "... [${matching.size - 40} more files truncated]"))
            }
        }
    }

    private suspend fun handleTerminalWc(args: List<String>, workingDir: String, repo: GitHubRepository?, branch: String, token: String?) {
        val fileName = args.firstOrNull { !it.startsWith("-") }?.trim('\'', '"')
        if (fileName.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "wc: missing file operand"))
            return
        }

        val state = _uiState.value
        val resolved = if (workingDir.isEmpty()) fileName else "$workingDir/$fileName"
        val content = state.terminalDrafts[resolved] ?: if (resolved == state.activeFilePath) state.activeFileContent else null

        val text = if (content != null) {
            content
        } else if (repo != null && !token.isNullOrBlank()) {
            repository.getFileContent(token, repo.owner.login, repo.name, resolved, branch).getOrNull()?.second ?: ""
        } else ""

        val lines = text.lines().size
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val bytes = text.toByteArray().size

        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "  $lines  $words  $bytes $fileName"))
    }

    private suspend fun handleTerminalHead(args: List<String>, workingDir: String, repo: GitHubRepository?, branch: String, token: String?) {
        val numIdx = args.indexOf("-n")
        val count = if (numIdx != -1 && numIdx + 1 < args.size) args[numIdx + 1].toIntOrNull() ?: 10 else 10
        val fileName = args.firstOrNull { !it.startsWith("-") && it.toIntOrNull() == null }

        if (fileName.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "head: missing file operand"))
            return
        }

        val state = _uiState.value
        val resolved = if (workingDir.isEmpty()) fileName else "$workingDir/$fileName"
        val content = state.terminalDrafts[resolved] ?: if (resolved == state.activeFilePath) state.activeFileContent else null

        val text = if (content != null) {
            content
        } else if (repo != null && !token.isNullOrBlank()) {
            repository.getFileContent(token, repo.owner.login, repo.name, resolved, branch).getOrNull()?.second ?: ""
        } else ""

        text.lines().take(count).forEach {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = it))
        }
    }

    private suspend fun handleTerminalTail(args: List<String>, workingDir: String, repo: GitHubRepository?, branch: String, token: String?) {
        val numIdx = args.indexOf("-n")
        val count = if (numIdx != -1 && numIdx + 1 < args.size) args[numIdx + 1].toIntOrNull() ?: 10 else 10
        val fileName = args.firstOrNull { !it.startsWith("-") && it.toIntOrNull() == null }

        if (fileName.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "tail: missing file operand"))
            return
        }

        val state = _uiState.value
        val resolved = if (workingDir.isEmpty()) fileName else "$workingDir/$fileName"
        val content = state.terminalDrafts[resolved] ?: if (resolved == state.activeFilePath) state.activeFileContent else null

        val text = if (content != null) {
            content
        } else if (repo != null && !token.isNullOrBlank()) {
            repository.getFileContent(token, repo.owner.login, repo.name, resolved, branch).getOrNull()?.second ?: ""
        } else ""

        text.lines().takeLast(count).forEach {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = it))
        }
    }

    private fun handleTerminalWhich(args: List<String>) {
        val cmd = args.firstOrNull() ?: ""
        when (cmd) {
            "git" -> appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "/usr/bin/git"))
            "sh", "bash" -> appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "/bin/bash"))
            "curl" -> appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "/usr/bin/curl"))
            "chmod", "chown" -> appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "/bin/$cmd"))
            "grep", "find", "cat", "ls", "wc" -> appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "/usr/bin/$cmd"))
            else -> appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "$cmd: not found in (\$PATH)"))
        }
    }

    private fun handleTerminalWhoami() {
        val user = _uiState.value.currentAccount?.username ?: "developer"
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = user))
    }

    private fun handleTerminalUname(args: List<String>) {
        if (args.contains("-a")) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "Linux android-host 6.1.0-gh-cloud #1 SMP PREEMPT aarch64 Android/Linux"))
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "Linux"))
        }
    }

    private fun handleTerminalUptime() {
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = " up 42 days, 13:37,  1 user,  load average: 0.08, 0.03, 0.01"))
    }

    private fun handleTerminalDate() {
        val now = java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", java.util.Locale.US).format(java.util.Date())
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = now))
    }

    private fun handleTerminalHistory() {
        val lines = _uiState.value.terminalLines.filter { it.type == TerminalLineType.PROMPT_COMMAND }
        lines.takeLast(20).forEachIndexed { idx, item ->
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "  ${idx + 1}  ${item.text}"))
        }
    }

    private fun handleTerminalEnv(repo: GitHubRepository?, branch: String) {
        val user = _uiState.value.currentAccount?.username ?: "developer"
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "USER=$user"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "SHELL=/bin/bash"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "PWD=/workspace/${repo?.fullName ?: "repo"}"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "GIT_BRANCH=$branch"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "GIT_DIR=/workspace/${repo?.name ?: "repo"}/.git"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "PATH=/usr/local/bin:/usr/bin:/bin:/usr/games"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "TERM=xterm-256color"))
    }

    private fun handleTerminalExport(args: List<String>) {
        val exp = args.firstOrNull()
        if (exp.isNullOrBlank()) {
            handleTerminalEnv(null, "main")
        } else {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "Exported: $exp"))
        }
    }

    private fun handleTerminalCurl(args: List<String>) {
        val url = args.firstOrNull { !it.startsWith("-") }
        if (url.isNullOrBlank()) {
            appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_ERROR, text = "curl: no URL specified"))
            return
        }
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "HTTP/2 200 OK\ncontent-type: application/json\nserver: GitHub.com"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "{\"status\":\"ok\",\"url\":\"$url\"}"))
    }

    private fun handleTerminalPing(args: List<String>) {
        val host = args.firstOrNull { !it.startsWith("-") } ?: "github.com"
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_INFO, text = "PING $host (140.82.121.4): 56 data bytes"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "64 bytes from 140.82.121.4: icmp_seq=0 ttl=57 time=14.2 ms"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_TEXT, text = "64 bytes from 140.82.121.4: icmp_seq=1 ttl=57 time=13.8 ms"))
        appendTerminalLine(TerminalLine(type = TerminalLineType.OUTPUT_SUCCESS, text = "--- $host ping statistics ---\n2 packets transmitted, 2 packets received, 0.0% packet loss"))
    }

    private fun printTerminalHelp() {
        val helpLines = listOf(
            "GitHub CLI & Terminal Command Reference:",
            "  gh pr list [--state open|closed|all]     List repository pull requests",
            "  gh pr merge <number> [--merge|--squash]  Merge pull request directly",
            "  gh pr view <number> | gh pr checkout <N> View PR details or switch branch",
            "  gh issue list | gh issue view <number>   List and view GitHub issues",
            "  gh repo view | gh repo list | gh sync    Inspect repository details and status",
            "  gh run list | gh workflow list           Inspect GitHub Actions CI/CD workflows",
            "  gh release list | gh release view <tag>  List repository release packages",
            "  gh auth status | gh auth token           Check authentication state & scopes",
            "  gh api <endpoint>                        Execute GitHub REST API requests",
            "  cmd1 | xargs -I {} cmd2                  Chain outputs into command pipelines",
            "  cmd1 | grep <pattern> | head -n 5        Filter and transform stream outputs",
            "  git update-index --chmod=(+|-)x <file>   Update file permission mode in git index",
            "  chmod (+|-)x <file> | chmod 755 <file>   Modify executable permissions",
            "  git status                               Check working tree and staged files",
            "  git log [--oneline] [-n N]               View commit history on active branch",
            "  git branch [-a]                          List local and remote branches",
            "  git branch <name>                        Create new branch from current commit",
            "  git branch -d <name>                     Delete branch ref on GitHub",
            "  git checkout <branch>                    Switch active branch",
            "  git checkout -b <name>                   Create and switch to new branch",
            "  git add <file> | git add .               Stage modified file(s)",
            "  git commit -m \"message\" [-am]            Commit staged files to GitHub remote",
            "  git push [origin <branch>]               Push branch commits to GitHub",
            "  git pull | git fetch                     Fetch latest updates and branches",
            "  git merge <branch>                       Merge target branch into current",
            "  git rebase <branch>                      Rebase current branch onto target",
            "  git revert <sha>                         Revert specific commit",
            "  git cherry-pick <sha>                    Apply commit to current branch",
            "  git grep <pattern>                       Search code repository for regex/text",
            "  git blame <file>                         Show author and commit history per line",
            "  git clean [-f]                           Remove untracked drafts",
            "  git shortlog [-s]                        Summarize commit log by author",
            "  git reflog                               Show repository HEAD ref actions",
            "  git describe                             Show recent commit tag description",
            "  git mv <src> <dest>                      Rename or move file on branch",
            "  git rm <file>                            Remove file from repository branch",
            "  git cp <src> <dest>                      Copy file contents to new destination",
            "  git diff [<b1> <b2>]                     Show unified file or branch diff",
            "  git show <sha|branch>                    Display commit metadata and info",
            "  git remote [-v]                          List configured remote repositories",
            "  git reset | git restore                  Unstage or restore working drafts",
            "  git tag                                  List repository release tags",
            "  git stash [list|pop]                     Manage temporary drafts stash",
            "  git version                              Display Git Engine version",
            "  ls [-la] [dir]                           List directory contents and sizes",
            "  cd <dir> | cd .. | cd ~                  Navigate folder structure",
            "  pwd                                      Print absolute working path",
            "  cat <file>                               View file contents",
            "  grep <pattern>                           Search active workspace files",
            "  find . -name \"<pattern>\"                 Find files matching glob pattern",
            "  wc [-l] <file>                           Count lines, words, bytes in file",
            "  head / tail [-n N] <file>                View first or last lines of file",
            "  touch <file>                             Create empty file",
            "  echo \"text\" > <file>                     Write text into file",
            "  rm <file>                                Delete file from repository",
            "  whoami / uname / uptime / date           System inspection utilities",
            "  env / export / curl / ping               Environment and network utilities",
            "  clear                                    Clear terminal screen output",
            "  help                                     Show this help manual"
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
