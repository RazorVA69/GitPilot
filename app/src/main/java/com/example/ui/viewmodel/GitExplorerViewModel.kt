package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AccountEntity
import com.example.data.local.AppDatabase
import com.example.data.local.SavedRepoEntity
import com.example.data.model.ExplorerNode
import com.example.data.model.FileContentResponse
import com.example.data.model.GitHubBranch
import com.example.data.model.GitHubRepository
import com.example.data.model.GitTreeItem
import com.example.data.repository.GitHubRepository as GitHubRepoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RepoFilterType { ALL, PUBLIC, PRIVATE, FORKS }

data class GitExplorerUiState(
    val currentAccount: AccountEntity? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val isAuthenticating: Boolean = false,
    val authError: String? = null,

    // Repositories
    val repositories: List<GitHubRepository> = emptyList(),
    val filteredRepositories: List<GitHubRepository> = emptyList(),
    val isLoadingRepos: Boolean = false,
    val repoSearchQuery: String = "",
    val repoFilterType: RepoFilterType = RepoFilterType.ALL,
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
    val batchProgress: Triple<Int, Int, String>? = null, // completed, total, currentFile

    // Dialogs & Sheets
    val isSidebarOpen: Boolean = false,
    val showLoginDialog: Boolean = false,
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

    private val _uiState = MutableStateFlow(GitExplorerUiState())
    val uiState: StateFlow<GitExplorerUiState> = _uiState.asStateFlow()

    init {
        // Observe local accounts
        viewModelScope.launch {
            repository.currentAccountFlow.collectLatest { account ->
                _uiState.update { it.copy(currentAccount = account) }
                loadRepositories()
            }
        }
        viewModelScope.launch {
            repository.allAccountsFlow.collectLatest { list ->
                _uiState.update { it.copy(accounts = list) }
            }
        }
    }

    fun loginWithToken(token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            _uiState.update { it.copy(authError = "Please enter a valid Personal Access Token") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, authError = null) }
            val result = repository.saveAccount(cleanToken)
            result.onSuccess { user ->
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        showLoginDialog = false,
                        authError = null,
                        toastOrMessage = "Welcome, ${user.login}!"
                    )
                }
                loadRepositories()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        authError = err.message ?: "Authentication failed. Check your token permissions."
                    )
                }
            }
        }
    }

    fun explorePublicUser(username: String) {
        val cleanUser = username.trim().ifBlank { "octocat" }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRepos = true, showLoginDialog = false) }
            val result = repository.getRepositories(token = null) // fetches public repos
            result.onSuccess { repos ->
                _uiState.update {
                    it.copy(
                        isLoadingRepos = false,
                        repositories = repos,
                        filteredRepositories = repos,
                        toastOrMessage = "Loaded public repositories for $cleanUser"
                    )
                }
                if (repos.isNotEmpty()) {
                    selectRepository(repos.first())
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingRepos = false,
                        errorMessage = "Failed to load public repos: ${err.message}"
                    )
                }
            }
        }
    }

    fun switchAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.switchAccount(account.id)
            _uiState.update { it.copy(showLoginDialog = false) }
        }
    }

    fun removeAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.removeAccount(account.id)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.update {
                it.copy(
                    currentAccount = null,
                    repositories = emptyList(),
                    filteredRepositories = emptyList(),
                    selectedRepo = null,
                    rawTreeItems = emptyList(),
                    rootExplorerNode = null,
                    activeFile = null,
                    activeFileContent = "",
                    toastOrMessage = "Logged out"
                )
            }
        }
    }

    fun loadRepositories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRepos = true) }
            val token = _uiState.value.currentAccount?.token
            val result = repository.getRepositories(token)
            result.onSuccess { repos ->
                _uiState.update { state ->
                    val filtered = filterRepos(repos, state.repoSearchQuery, state.repoFilterType)
                    state.copy(
                        isLoadingRepos = false,
                        repositories = repos,
                        filteredRepositories = filtered
                    )
                }
                // Auto-select first repo if none selected
                if (_uiState.value.selectedRepo == null && repos.isNotEmpty()) {
                    selectRepository(repos.first())
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingRepos = false,
                        errorMessage = "Could not fetch repositories: ${err.message}"
                    )
                }
            }
        }
    }

    fun setRepoSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = filterRepos(state.repositories, query, state.repoFilterType)
            state.copy(repoSearchQuery = query, filteredRepositories = filtered)
        }
    }

    fun setRepoFilterType(filterType: RepoFilterType) {
        _uiState.update { state ->
            val filtered = filterRepos(state.repositories, state.repoSearchQuery, filterType)
            state.copy(repoFilterType = filterType, filteredRepositories = filtered)
        }
    }

    private fun filterRepos(
        all: List<GitHubRepository>,
        query: String,
        filterType: RepoFilterType
    ): List<GitHubRepository> {
        return all.filter { repo ->
            val matchesQuery = query.isBlank() ||
                    repo.name.contains(query, ignoreCase = true) ||
                    (repo.description?.contains(query, ignoreCase = true) == true) ||
                    (repo.language?.contains(query, ignoreCase = true) == true)

            val matchesType = when (filterType) {
                RepoFilterType.ALL -> true
                RepoFilterType.PUBLIC -> !repo.private
                RepoFilterType.PRIVATE -> repo.private
                RepoFilterType.FORKS -> repo.fork
            }
            matchesQuery && matchesType
        }
    }

    fun selectRepository(repo: GitHubRepository) {
        _uiState.update {
            it.copy(
                selectedRepo = repo,
                selectedBranch = repo.defaultBranch,
                currentDirectoryPath = "",
                rawTreeItems = emptyList(),
                rootExplorerNode = null,
                activeFile = null,
                activeFilePath = null,
                activeFileContent = "",
                selectedFilePaths = emptySet(),
                isBatchMode = false,
                fileSearchQuery = "",
                isSidebarOpen = false
            )
        }

        // Save to recent repos
        viewModelScope.launch {
            repository.saveRecentRepo(
                SavedRepoEntity(
                    fullName = repo.fullName,
                    owner = repo.owner?.login ?: repo.fullName.substringBefore('/'),
                    name = repo.name,
                    defaultBranch = repo.defaultBranch,
                    isPrivate = repo.private
                )
            )
        }

        loadBranches(repo)
        loadGitTree(repo, repo.defaultBranch)
    }

    private fun loadBranches(repo: GitHubRepository) {
        val token = _uiState.value.currentAccount?.token
        val owner = repo.owner?.login ?: repo.fullName.substringBefore('/')
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBranches = true) }
            val result = repository.getBranches(token, owner, repo.name)
            result.onSuccess { branches ->
                _uiState.update {
                    it.copy(
                        isLoadingBranches = false,
                        branches = branches
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingBranches = false) }
            }
        }
    }

    fun selectBranch(branchName: String) {
        val repo = _uiState.value.selectedRepo ?: return
        _uiState.update {
            it.copy(
                selectedBranch = branchName,
                showBranchSelector = false,
                currentDirectoryPath = "",
                activeFile = null,
                activeFilePath = null,
                activeFileContent = "",
                selectedFilePaths = emptySet()
            )
        }
        loadGitTree(repo, branchName)
    }

    fun refreshTree() {
        val repo = _uiState.value.selectedRepo ?: return
        loadGitTree(repo, _uiState.value.selectedBranch)
    }

    private fun loadGitTree(repo: GitHubRepository, branch: String) {
        val token = _uiState.value.currentAccount?.token
        val owner = repo.owner?.login ?: repo.fullName.substringBefore('/')
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTree = true, errorMessage = null) }
            val result = repository.getGitTree(token, owner, repo.name, branch)
            result.onSuccess { treeItems ->
                val hierarchy = GitHubRepoRepository.buildHierarchyTree(treeItems)
                _uiState.update {
                    it.copy(
                        isLoadingTree = false,
                        rawTreeItems = treeItems,
                        rootExplorerNode = hierarchy,
                        matchingSearchFiles = filterSearchFiles(treeItems, it.fileSearchQuery)
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingTree = false,
                        errorMessage = "Could not load tree: ${err.message}"
                    )
                }
            }
        }
    }

    fun setFileSearchQuery(query: String) {
        _uiState.update { state ->
            val matching = filterSearchFiles(state.rawTreeItems, query)
            state.copy(
                fileSearchQuery = query,
                matchingSearchFiles = matching
            )
        }
    }

    private fun filterSearchFiles(rawItems: List<GitTreeItem>, query: String): List<GitTreeItem> {
        if (query.isBlank()) return emptyList()
        val clean = query.trim().lowercase()
        return rawItems.filter { !it.isDirectory && it.path.lowercase().contains(clean) }
            .take(50)
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
        navigateToDirectory(parent)
    }

    fun openFile(item: GitTreeItem) {
        val repo = _uiState.value.selectedRepo ?: return
        val token = _uiState.value.currentAccount?.token
        val owner = repo.owner?.login ?: repo.fullName.substringBefore('/')
        val branch = _uiState.value.selectedBranch

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingFile = true,
                    activeFilePath = item.path,
                    activeFileSha = item.sha,
                    errorMessage = null,
                    fileSearchQuery = ""
                )
            }
            val result = repository.getFileContent(token, owner, repo.name, item.path, branch)
            result.onSuccess { (decodedText, fileContent) ->
                _uiState.update {
                    it.copy(
                        isLoadingFile = false,
                        activeFile = fileContent,
                        activeFilePath = fileContent.path,
                        activeFileSha = fileContent.sha,
                        activeFileContent = decodedText,
                        activeFileOriginalContent = decodedText,
                        isFileDirty = false,
                        isMarkdownPreviewMode = fileContent.name.endsWith(".md", ignoreCase = true)
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingFile = false,
                        errorMessage = "Could not load file: ${err.message}"
                    )
                }
            }
        }
    }

    fun closeFile() {
        _uiState.update {
            it.copy(
                activeFile = null,
                activeFilePath = null,
                activeFileSha = null,
                activeFileContent = "",
                activeFileOriginalContent = "",
                isFileDirty = false
            )
        }
    }

    fun updateEditorContent(newContent: String) {
        _uiState.update { state ->
            val isDirty = newContent != state.activeFileOriginalContent
            state.copy(
                activeFileContent = newContent,
                isFileDirty = isDirty
            )
        }
    }

    fun toggleMarkdownPreview() {
        _uiState.update { it.copy(isMarkdownPreviewMode = !it.isMarkdownPreviewMode) }
    }

    fun commitActiveFile(commitMessage: String, targetBranch: String) {
        val repo = _uiState.value.selectedRepo ?: return
        val token = _uiState.value.currentAccount?.token ?: run {
            _uiState.update { it.copy(showLoginDialog = true, errorMessage = "Login with PAT required to commit") }
            return
        }
        val filePath = _uiState.value.activeFilePath ?: return
        val currentContent = _uiState.value.activeFileContent
        val originalSha = _uiState.value.activeFileSha
        val owner = repo.owner?.login ?: repo.fullName.substringBefore('/')

        viewModelScope.launch {
            _uiState.update { it.copy(isCommitting = true, errorMessage = null) }
            val result = repository.createOrUpdateFile(
                token = token,
                owner = owner,
                repo = repo.name,
                path = filePath,
                content = currentContent,
                sha = originalSha,
                branch = targetBranch,
                message = commitMessage.ifBlank { "Update $filePath" }
            )
            result.onSuccess { commitResult ->
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        showCommitDialog = false,
                        isFileDirty = false,
                        activeFileOriginalContent = currentContent,
                        activeFileSha = commitResult.content?.sha ?: originalSha,
                        toastOrMessage = "Successfully committed: ${commitResult.commit?.sha?.take(7) ?: "OK"}"
                    )
                }
                refreshTree()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        errorMessage = "Commit failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun createOrUploadFile(
        targetDirectory: String,
        fileName: String,
        content: String,
        commitMessage: String,
        targetBranch: String
    ) {
        val repo = _uiState.value.selectedRepo ?: return
        val token = _uiState.value.currentAccount?.token ?: run {
            _uiState.update { it.copy(showLoginDialog = true, errorMessage = "Login with PAT required to upload/create files") }
            return
        }
        val cleanDir = targetDirectory.trim().trim('/')
        val cleanName = fileName.trim().trim('/')
        if (cleanName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "File name cannot be empty") }
            return
        }

        val fullPath = if (cleanDir.isEmpty()) cleanName else "$cleanDir/$cleanName"
        val owner = repo.owner?.login ?: repo.fullName.substringBefore('/')

        viewModelScope.launch {
            _uiState.update { it.copy(isCommitting = true, errorMessage = null) }
            val result = repository.createOrUpdateFile(
                token = token,
                owner = owner,
                repo = repo.name,
                path = fullPath,
                content = content,
                sha = null, // new file
                branch = targetBranch,
                message = commitMessage.ifBlank { "Create $fullPath" }
            )
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        showCreateUploadDialog = false,
                        toastOrMessage = "Created $fullPath"
                    )
                }
                refreshTree()
                // Navigate to directory of new file
                if (cleanDir.isNotEmpty()) {
                    navigateToDirectory(cleanDir)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        errorMessage = "Failed to create file: ${err.message}"
                    )
                }
            }
        }
    }

    fun deleteSingleFile(path: String, sha: String, commitMessage: String) {
        val repo = _uiState.value.selectedRepo ?: return
        val token = _uiState.value.currentAccount?.token ?: run {
            _uiState.update { it.copy(showLoginDialog = true, errorMessage = "Login with PAT required to delete files") }
            return
        }
        val owner = repo.owner?.login ?: repo.fullName.substringBefore('/')
        val branch = _uiState.value.selectedBranch

        viewModelScope.launch {
            _uiState.update { it.copy(isCommitting = true, errorMessage = null) }
            val result = repository.deleteFile(
                token = token,
                owner = owner,
                repo = repo.name,
                path = path,
                sha = sha,
                branch = branch,
                message = commitMessage.ifBlank { "Delete $path" }
            )
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        toastOrMessage = "Deleted $path"
                    )
                }
                if (_uiState.value.activeFilePath == path) {
                    closeFile()
                }
                refreshTree()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        errorMessage = "Failed to delete: ${err.message}"
                    )
                }
            }
        }
    }

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
            val updated = state.selectedFilePaths.toMutableSet()
            if (updated.contains(path)) {
                updated.remove(path)
            } else {
                updated.add(path)
            }
            state.copy(selectedFilePaths = updated)
        }
    }

    fun selectAllInCurrentDirectory() {
        val currentDir = _uiState.value.currentDirectoryPath
        val allMatchingBlobs = _uiState.value.rawTreeItems.filter {
            !it.isDirectory && (currentDir.isEmpty() || it.path.startsWith("$currentDir/"))
        }.map { it.path }

        _uiState.update { state ->
            val updated = state.selectedFilePaths.toMutableSet().apply {
                addAll(allMatchingBlobs)
            }
            state.copy(selectedFilePaths = updated)
        }
    }

    fun clearSelectedFiles() {
        _uiState.update { it.copy(selectedFilePaths = emptySet()) }
    }

    fun deleteSelectedFiles(commitMessage: String) {
        val repo = _uiState.value.selectedRepo ?: return
        val token = _uiState.value.currentAccount?.token ?: run {
            _uiState.update { it.copy(showLoginDialog = true, errorMessage = "Login required for batch deletion") }
            return
        }
        val selectedPaths = _uiState.value.selectedFilePaths
        if (selectedPaths.isEmpty()) return

        val filesToDelete = _uiState.value.rawTreeItems
            .filter { selectedPaths.contains(it.path) }
            .map { Pair(it.path, it.sha) }

        val owner = repo.owner?.login ?: repo.fullName.substringBefore('/')
        val branch = _uiState.value.selectedBranch

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBatchDeleting = true,
                    batchProgress = Triple(0, filesToDelete.size, "Starting...")
                )
            }

            val result = repository.deleteMultipleFiles(
                token = token,
                owner = owner,
                repo = repo.name,
                files = filesToDelete,
                branch = branch,
                messagePrefix = commitMessage.ifBlank { "Batch delete ${filesToDelete.size} files" },
                onProgress = { done, total, file ->
                    _uiState.update {
                        it.copy(batchProgress = Triple(done, total, file))
                    }
                }
            )

            result.onSuccess { count ->
                _uiState.update {
                    it.copy(
                        isBatchDeleting = false,
                        showBatchDeleteDialog = false,
                        isBatchMode = false,
                        selectedFilePaths = emptySet(),
                        batchProgress = null,
                        toastOrMessage = "Deleted $count files"
                    )
                }
                refreshTree()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBatchDeleting = false,
                        batchProgress = null,
                        errorMessage = "Batch deletion error: ${err.message}"
                    )
                }
            }
        }
    }

    // Modal Visibility Toggles
    fun setSidebarOpen(isOpen: Boolean) = _uiState.update { it.copy(isSidebarOpen = isOpen) }
    fun setShowLoginDialog(show: Boolean) = _uiState.update { it.copy(showLoginDialog = show) }
    fun setShowCommitDialog(show: Boolean) = _uiState.update { it.copy(showCommitDialog = show) }
    fun setShowCreateUploadDialog(show: Boolean) = _uiState.update { it.copy(showCreateUploadDialog = show) }
    fun setShowBatchDeleteDialog(show: Boolean) = _uiState.update { it.copy(showBatchDeleteDialog = show) }
    fun setShowBranchSelector(show: Boolean) = _uiState.update { it.copy(showBranchSelector = show) }
    fun clearToast() = _uiState.update { it.copy(toastOrMessage = null) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
