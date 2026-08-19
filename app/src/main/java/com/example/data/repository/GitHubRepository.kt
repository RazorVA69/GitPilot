package com.example.data.repository

import android.util.Base64
import com.example.data.api.GitHubApiClient
import com.example.data.api.GitHubApiService
import com.example.data.api.GitHubAuthService
import com.example.data.local.AccountEntity
import com.example.data.local.AppDao
import com.example.data.local.FileDraftEntity
import com.example.data.local.SavedRepoEntity
import com.example.data.model.CommitResultResponse
import com.example.data.model.CreateOrUpdateFilePayload
import com.example.data.model.DeleteFilePayload
import com.example.data.model.DeviceCodeRequest
import com.example.data.model.DeviceCodeResponse
import com.example.data.model.ExplorerNode
import com.example.data.model.FileContentResponse
import com.example.data.model.GitHubBranch
import com.example.data.model.GitHubRepository
import com.example.data.model.GitHubUser
import com.example.data.model.GitTreeItem
import com.example.data.model.OAuthTokenRequest
import com.example.data.model.OAuthTokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets

class GitHubRepository(
    private val apiService: GitHubApiService = GitHubApiClient.apiService,
    private val authService: GitHubAuthService = GitHubApiClient.authService,
    private val appDao: AppDao
) {
    val currentAccountFlow: Flow<AccountEntity?> = appDao.getCurrentAccount()
    val allAccountsFlow: Flow<List<AccountEntity>> = appDao.getAllAccounts()
    val savedReposFlow: Flow<List<SavedRepoEntity>> = appDao.getSavedRepos()

    companion object {
        const val DEFAULT_OAUTH_CLIENT_ID = "Iv1.8b22e1189912782b"

        fun buildExplorerTree(items: List<GitTreeItem>): ExplorerNode {
            val root = ExplorerNode(
                name = "",
                path = "",
                isDirectory = true,
                sha = ""
            )

            for (item in items) {
                val segments = item.path.split('/')
                var currentNode = root

                for (i in segments.indices) {
                    val segment = segments[i]
                    val isLast = i == segments.size - 1
                    val currentFullPath = segments.take(i + 1).joinToString("/")

                    var child = currentNode.children.find { it.name == segment }
                    if (child == null) {
                        child = ExplorerNode(
                            name = segment,
                            path = currentFullPath,
                            isDirectory = if (isLast) item.isDirectory else true,
                            sha = if (isLast) item.sha else "",
                            size = if (isLast) item.size else null
                        )
                        currentNode.children.add(child)
                    }
                    currentNode = child
                }
            }

            sortTreeNodes(root)
            return root
        }

        private fun sortTreeNodes(node: ExplorerNode) {
            node.children.sortWith(
                compareBy<ExplorerNode> { !it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
            for (child in node.children) {
                if (child.isDirectory) {
                    sortTreeNodes(child)
                }
            }
        }

        fun findNodeAtDirectory(root: ExplorerNode, directoryPath: String): ExplorerNode? {
            if (directoryPath.isBlank()) return root
            val segments = directoryPath.trim('/').split('/')
            var current = root
            for (segment in segments) {
                current = current.children.find { it.name == segment && it.isDirectory } ?: return null
            }
            return current
        }
    }

    suspend fun requestDeviceCode(clientId: String = DEFAULT_OAUTH_CLIENT_ID): Result<DeviceCodeResponse> = withContext(Dispatchers.IO) {
        try {
            val response = authService.requestDeviceCode(DeviceCodeRequest(clientId = clientId))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Failed to start GitHub login: HTTP ${response.code()}"
                Result.failure(Exception(friendlyErrorMessage(error, null)))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun pollDeviceToken(clientId: String, deviceCode: String): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val response = authService.pollDeviceToken(
                OAuthTokenRequest(
                    clientId = clientId,
                    deviceCode = deviceCode
                )
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
                val rawError = response.errorBody()?.string()
                val code = response.code()
                val msg = when (code) {
                    401 -> "Invalid GitHub Token (401 Unauthorized). Please ensure your token is copied correctly and has 'repo' scope."
                    403 -> "GitHub API Rate Limited or Access Forbidden (403). Ensure token has proper permissions."
                    else -> "Authentication failed: HTTP $code. ${rawError ?: ""}"
                }
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
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
                apiService.getPublicRepositories("octocat")
            }

            if (response.isSuccessful && response.body() != null) {
                val repos = response.body()!!
                Result.success(repos)
            } else {
                val code = response.code()
                val msg = when (code) {
                    401 -> "Session expired or invalid token (401). Please re-enter your PAT token or login again."
                    403 -> "GitHub rate limit exceeded or access denied (403)."
                    else -> "Failed to load repositories: HTTP $code"
                }
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
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
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun getFileTreeRecursive(
        token: String?,
        owner: String,
        repo: String,
        branch: String
    ): Result<Pair<ExplorerNode, List<GitTreeItem>>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getGitTreeRecursive(authHeader, owner, repo, branch, recursive = 1)
            if (response.isSuccessful && response.body() != null) {
                val treeItems = response.body()!!.tree
                val rootNode = buildExplorerTree(treeItems)
                Result.success(Pair(rootNode, treeItems))
            } else {
                Result.failure(Exception("Failed to load tree: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun getFileContent(
        token: String?,
        owner: String,
        repo: String,
        path: String,
        branch: String?
    ): Result<Pair<FileContentResponse, String>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getFileContent(authHeader, owner, repo, path, branch)
            if (response.isSuccessful && response.body() != null) {
                val file = response.body()!!
                val decoded = decodeFileContent(file.content, file.encoding)
                Result.success(Pair(file, decoded))
            } else {
                Result.failure(Exception("Failed to fetch file: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun commitFileChanges(
        token: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        commitMessage: String,
        branch: String,
        fileSha: String?
    ): Result<CommitResultResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(Exception("Authentication token required to commit"))

            val base64Content = Base64.encodeToString(
                content.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )

            val payload = CreateOrUpdateFilePayload(
                message = commitMessage.ifBlank { "Update $path" },
                content = base64Content,
                sha = fileSha,
                branch = branch
            )

            val response = apiService.createOrUpdateFile(authHeader, owner, repo, path, payload)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Commit failed: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun deleteFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        fileSha: String,
        commitMessage: String,
        branch: String
    ): Result<CommitResultResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(Exception("Authentication token required to delete files"))

            val payload = DeleteFilePayload(
                message = commitMessage.ifBlank { "Delete $path" },
                sha = fileSha,
                branch = branch
            )

            val response = apiService.deleteFile(authHeader, owner, repo, path, payload)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Delete failed for $path: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun deleteMultipleFiles(
        token: String,
        owner: String,
        repo: String,
        files: List<Pair<String, String>>,
        branch: String,
        baseMessage: String,
        onProgress: (completed: Int, total: Int, currentFile: String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        var successCount = 0
        for ((index, item) in files.withIndex()) {
            val (path, sha) = item
            onProgress(index, files.size, path)
            val res = deleteFile(
                token = token,
                owner = owner,
                repo = repo,
                path = path,
                fileSha = sha,
                commitMessage = "$baseMessage - $path",
                branch = branch
            )
            if (res.isSuccess) {
                successCount++
            }
        }
        onProgress(files.size, files.size, "Done")
        Result.success(successCount)
    }

    suspend fun saveDraft(repoFullName: String, branch: String, filePath: String, content: String, sha: String?) = withContext(Dispatchers.IO) {
        appDao.saveDraft(
            FileDraftEntity(
                id = "$repoFullName:$branch:$filePath",
                repoFullName = repoFullName,
                branch = branch,
                filePath = filePath,
                draftContent = content,
                originalSha = sha,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }

    suspend fun getDraft(repoFullName: String, branch: String, filePath: String): FileDraftEntity? = withContext(Dispatchers.IO) {
        appDao.getDraft("$repoFullName:$branch:$filePath")
    }

    suspend fun clearDraft(repoFullName: String, branch: String, filePath: String) = withContext(Dispatchers.IO) {
        appDao.deleteDraft("$repoFullName:$branch:$filePath")
    }

    suspend fun saveRecentRepo(repo: GitHubRepository) = withContext(Dispatchers.IO) {
        appDao.insertSavedRepo(
            SavedRepoEntity(
                fullName = repo.fullName,
                owner = repo.owner.login,
                name = repo.name,
                defaultBranch = repo.defaultBranch,
                isPrivate = repo.private,
                isFavorite = false,
                lastOpened = System.currentTimeMillis()
            )
        )
    }

    private fun decodeFileContent(rawContent: String?, encoding: String?): String {
        if (rawContent == null) return ""
        return if (encoding.equals("base64", ignoreCase = true)) {
            try {
                val clean = rawContent.replace("\n", "").replace("\r", "")
                val decodedBytes = Base64.decode(clean, Base64.DEFAULT)
                String(decodedBytes, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                rawContent
            }
        } else {
            rawContent
        }
    }

    private fun friendlyErrorMessage(serverError: String?, throwable: Throwable?): String {
        if (throwable is UnknownHostException || throwable?.message?.contains("Unable to resolve host", ignoreCase = true) == true) {
            return "Connection error: Unable to reach GitHub. Please check your internet connection and retry."
        }
        if (throwable?.message?.contains("timeout", ignoreCase = true) == true) {
            return "Connection timed out. GitHub servers took too long to respond. Please try again."
        }
        if (serverError != null) {
            return serverError
        }
        return throwable?.localizedMessage ?: "An unexpected error occurred. Please try again."
    }
}
