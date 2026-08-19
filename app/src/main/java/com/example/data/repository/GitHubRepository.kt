package com.example.data.repository

import android.util.Base64
import com.example.data.api.GitHubApiClient
import com.example.data.api.GitHubApiService
import com.example.data.local.AccountEntity
import com.example.data.local.AppDao
import com.example.data.local.FileDraftEntity
import com.example.data.local.SavedRepoEntity
import com.example.data.model.CommitResultResponse
import com.example.data.model.CreateOrUpdateFilePayload
import com.example.data.model.DeleteFilePayload
import com.example.data.model.ExplorerNode
import com.example.data.model.FileContentResponse
import com.example.data.model.GitHubBranch
import com.example.data.model.GitHubRepository
import com.example.data.model.GitHubUser
import com.example.data.model.GitTreeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class GitHubRepository(
    private val apiService: GitHubApiService = GitHubApiClient.apiService,
    private val appDao: AppDao
) {
    val currentAccountFlow: Flow<AccountEntity?> = appDao.getCurrentAccount()
    val allAccountsFlow: Flow<List<AccountEntity>> = appDao.getAllAccounts()
    val savedReposFlow: Flow<List<SavedRepoEntity>> = appDao.getSavedRepos()

    suspend fun saveAccount(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(IllegalArgumentException("Token cannot be empty"))

            val response = apiService.getAuthenticatedUser(authHeader)
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                appDao.clearCurrentAccount()
                val account = AccountEntity(
                    username = user.login,
                    token = token.trim(),
                    avatarUrl = user.avatarUrl,
                    name = user.name ?: user.login,
                    isCurrent = true
                )
                appDao.insertAccount(account)
                Result.success(user)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to authenticate: HTTP ${response.code()}"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun switchAccount(id: Long) = withContext(Dispatchers.IO) {
        appDao.clearCurrentAccount()
        appDao.setCurrentAccount(id)
    }

    suspend fun removeAccount(id: Long) = withContext(Dispatchers.IO) {
        appDao.deleteAccount(id)
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        appDao.deleteAllAccounts()
    }

    suspend fun getRepositories(token: String?): Result<List<GitHubRepository>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = if (authHeader != null) {
                apiService.getUserRepositories(authHeader)
            } else {
                // Default popular/sample public showcase if no token
                apiService.getPublicRepositories("octocat")
            }

            if (response.isSuccessful && response.body() != null) {
                val repos = response.body()!!
                Result.success(repos)
            } else {
                Result.failure(Exception("Failed to load repositories: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBranches(token: String?, owner: String, repo: String): Result<List<GitHubBranch>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getBranches(authHeader, owner, repo)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load branches: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGitTree(token: String?, owner: String, repo: String, branchOrSha: String): Result<List<GitTreeItem>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getGitTreeRecursive(authHeader, owner, repo, branchOrSha, recursive = 1)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.tree)
            } else {
                Result.failure(Exception("Failed to fetch repository tree: HTTP ${response.code()} ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFileContent(token: String?, owner: String, repo: String, path: String, ref: String?): Result<Pair<String, FileContentResponse>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getFileContent(authHeader, owner, repo, path, ref)
            if (response.isSuccessful && response.body() != null) {
                val fileContent = response.body()!!
                val decodedText = if (!fileContent.content.isNullOrBlank()) {
                    try {
                        val cleanBase64 = fileContent.content.replace("\n", "").replace("\r", "")
                        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                        String(bytes, StandardCharsets.UTF_8)
                    } catch (e: Exception) {
                        "[Binary content or unsupported encoding]"
                    }
                } else {
                    ""
                }
                Result.success(Pair(decodedText, fileContent))
            } else {
                Result.failure(Exception("Failed to fetch file content: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createOrUpdateFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        sha: String?,
        branch: String,
        message: String
    ): Result<CommitResultResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(IllegalArgumentException("Authentication required to commit"))

            val encodedContent = Base64.encodeToString(content.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            val payload = CreateOrUpdateFilePayload(
                message = message.ifBlank { if (sha != null) "Update $path" else "Create $path" },
                content = encodedContent,
                sha = sha,
                branch = branch
            )

            val response = apiService.createOrUpdateFile(authHeader, owner, repo, path, payload)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val err = response.errorBody()?.string() ?: "Commit failed with HTTP ${response.code()}"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        sha: String,
        branch: String,
        message: String
    ): Result<CommitResultResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(IllegalArgumentException("Authentication required to delete"))

            val payload = DeleteFilePayload(
                message = message.ifBlank { "Delete $path" },
                sha = sha,
                branch = branch
            )

            val response = apiService.deleteFile(authHeader, owner, repo, path, payload)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val err = response.errorBody()?.string() ?: "Delete failed with HTTP ${response.code()}"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMultipleFiles(
        token: String,
        owner: String,
        repo: String,
        files: List<Pair<String, String>>, // list of (path, sha)
        branch: String,
        messagePrefix: String,
        onProgress: (completed: Int, total: Int, currentFile: String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        var deletedCount = 0
        val total = files.size
        for ((index, file) in files.withIndex()) {
            val (path, sha) = file
            onProgress(index, total, path)
            val result = deleteFile(
                token = token,
                owner = owner,
                repo = repo,
                path = path,
                sha = sha,
                branch = branch,
                message = "$messagePrefix: Delete $path"
            )
            if (result.isSuccess) {
                deletedCount++
            } else {
                return@withContext Result.failure(
                    Exception("Failed on '$path': ${result.exceptionOrNull()?.message}")
                )
            }
        }
        onProgress(total, total, "Done")
        Result.success(deletedCount)
    }

    suspend fun saveRecentRepo(repo: SavedRepoEntity) = withContext(Dispatchers.IO) {
        appDao.insertSavedRepo(repo)
    }

    suspend fun saveDraft(draft: FileDraftEntity) = withContext(Dispatchers.IO) {
        appDao.saveDraft(draft)
    }

    suspend fun getDraft(id: String): FileDraftEntity? = withContext(Dispatchers.IO) {
        appDao.getDraft(id)
    }

    suspend fun clearDraft(id: String) = withContext(Dispatchers.IO) {
        appDao.deleteDraft(id)
    }

    companion object {
        /**
         * Ultra-fast tree hierarchy builder from GitHub flat tree items.
         */
        fun buildHierarchyTree(items: List<GitTreeItem>): ExplorerNode {
            val root = ExplorerNode(
                path = "",
                name = "root",
                isDirectory = true,
                sha = "",
                isExpanded = true
            )

            val dirMap = mutableMapOf<String, ExplorerNode>()
            dirMap[""] = root

            // Sort so directories come first or natural path ordering
            val sortedItems = items.sortedBy { it.path }

            for (item in sortedItems) {
                val pathSegments = item.path.split('/')
                var currentPath = ""
                var parentNode = root

                for (i in 0 until pathSegments.size - 1) {
                    val segment = pathSegments[i]
                    currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"

                    var dirNode = dirMap[currentPath]
                    if (dirNode == null) {
                        dirNode = ExplorerNode(
                            path = currentPath,
                            name = segment,
                            isDirectory = true,
                            sha = ""
                        )
                        dirMap[currentPath] = dirNode
                        parentNode.children.add(dirNode)
                    }
                    parentNode = dirNode
                }

                val lastSegment = pathSegments.last()
                val isDir = item.isDirectory
                val node = if (isDir) {
                    dirMap[item.path] ?: ExplorerNode(
                        path = item.path,
                        name = lastSegment,
                        isDirectory = true,
                        sha = item.sha
                    ).also {
                        dirMap[item.path] = it
                        parentNode.children.add(it)
                    }
                } else {
                    ExplorerNode(
                        path = item.path,
                        name = lastSegment,
                        isDirectory = false,
                        sha = item.sha,
                        size = item.size,
                        extension = item.extension
                    ).also {
                        parentNode.children.add(it)
                    }
                }
            }

            // Sort children recursively: directories first, then alphabetically
            fun sortRecursive(node: ExplorerNode) {
                node.children.sortWith(
                    compareBy<ExplorerNode> { !it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
                for (child in node.children) {
                    if (child.isDirectory) {
                        sortRecursive(child)
                    }
                }
            }
            sortRecursive(root)

            return root
        }

        fun findNodeAtDirectory(root: ExplorerNode, directoryPath: String): ExplorerNode? {
            if (directoryPath.isBlank() || directoryPath == "/" || directoryPath == "root") {
                return root
            }
            val segments = directoryPath.trim('/').split('/')
            var current: ExplorerNode? = root
            for (segment in segments) {
                current = current?.children?.firstOrNull { it.isDirectory && it.name == segment }
                if (current == null) return null
            }
            return current
        }
    }
}
