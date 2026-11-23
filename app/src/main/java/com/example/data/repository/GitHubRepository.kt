package com.example.data.repository

import android.util.Base64
import com.example.data.api.GitHubApiClient
import com.example.data.api.GitHubApiService
import com.example.data.api.GitHubAuthService
import com.example.data.local.AccountEntity
import com.example.data.local.AppDao
import com.example.data.local.CachedFileBlobEntity
import com.example.data.local.FileDraftEntity
import com.example.data.local.SavedRepoEntity
import com.example.data.model.CommitResultResponse
import com.example.data.model.CreateBlobPayload
import com.example.data.model.CreateCommitPayload
import com.example.data.model.CreateOrUpdateFilePayload
import com.example.data.model.CreateTreeItemPayload
import com.example.data.model.CreateTreePayload
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
import com.example.data.model.UpdateRefPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

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

        internal const val MAX_INLINE_TREE_FILE_BYTES = 128 * 1024
        internal const val MAX_TREE_ITEMS_PER_CHUNK = 300
        internal const val MAX_TREE_INLINE_CHARS_PER_CHUNK = 1_000_000
        internal const val BLOB_UPLOAD_CONCURRENCY = 8

        internal fun encodeBranchPath(branch: String): String {
            val clean = branch.trim().removePrefix("refs/heads/").trim('/')
            if (clean.isEmpty()) return "main"
            return clean.split('/')
                .filter { it.isNotEmpty() }
                .joinToString("/") { segment ->
                    URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
                }
        }

        internal fun sanitizeBatchFiles(files: List<Pair<String, ByteArray>>): List<Pair<String, ByteArray>> {
            val deduped = LinkedHashMap<String, ByteArray>(files.size)
            for ((rawPath, bytes) in files) {
                val segments = rawPath
                    .replace('\\', '/')
                    .split('/')
                    .filter { it.isNotBlank() && it != "." && it != ".." }
                if (segments.isEmpty()) continue
                // GitHub strictly rejects any tree containing a ".git" path segment (HTTP 422)
                if (segments.any { it == ".git" }) continue
                val normalized = segments.joinToString("/")
                deduped[normalized] = bytes
            }

            if (deduped.size <= 1) return deduped.entries.map { it.key to it.value }

            // Collect all directory prefixes implied by file paths so a file never collides with a directory of the same name
            val directoryPrefixes = HashSet<String>(deduped.size * 2)
            for (path in deduped.keys) {
                var slash = path.indexOf('/')
                while (slash != -1) {
                    directoryPrefixes.add(path.substring(0, slash))
                    slash = path.indexOf('/', slash + 1)
                }
            }

            return deduped.entries
                .filter { !directoryPrefixes.contains(it.key) }
                .map { it.key to it.value }
        }

        internal fun tryDecodeStrictUtf8(bytes: ByteArray): String? {
            if (bytes.isEmpty() || bytes.size > MAX_INLINE_TREE_FILE_BYTES) return null
            for (b in bytes) {
                if (b == 0.toByte()) return null
            }
            return try {
                val decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()
                // Verify bijective round-trip so bytes match 100% identically
                if (decoded.toByteArray(StandardCharsets.UTF_8).contentEquals(bytes)) decoded else null
            } catch (_: Exception) {
                null
            }
        }

        internal fun gitBlobDigestKey(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-1")
            val header = "blob ${bytes.size}\u0000".toByteArray(StandardCharsets.US_ASCII)
            md.update(header)
            md.update(bytes)
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        internal fun chunkTreeItems(items: List<CreateTreeItemPayload>): List<List<CreateTreeItemPayload>> {
            if (items.isEmpty()) return emptyList()
            val chunks = ArrayList<List<CreateTreeItemPayload>>()
            var current = ArrayList<CreateTreeItemPayload>()
            var currentChars = 0

            for (item in items) {
                val itemChars = item.path.length + (item.content?.length ?: 48) + 64
                val wouldExceedItems = current.size >= MAX_TREE_ITEMS_PER_CHUNK
                val wouldExceedBytes = current.isNotEmpty() && (currentChars + itemChars > MAX_TREE_INLINE_CHARS_PER_CHUNK)
                if (wouldExceedItems || wouldExceedBytes) {
                    chunks.add(current)
                    current = ArrayList()
                    currentChars = 0
                }
                current.add(item)
                currentChars += itemChars
            }
            if (current.isNotEmpty()) {
                chunks.add(current)
            }
            return chunks
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

    suspend fun validateAndSaveToken(token: String): Result<AccountEntity> = withContext(Dispatchers.IO) {
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
                val id = appDao.insertAccount(account)
                Result.success(account.copy(id = id))
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

    suspend fun setCurrentAccount(username: String) = withContext(Dispatchers.IO) {
        appDao.clearCurrentAccount()
        appDao.setCurrentAccountByUsername(username)
    }

    suspend fun removeAccount(id: Long) = withContext(Dispatchers.IO) {
        appDao.deleteAccount(id)
    }

    suspend fun deleteAccount(username: String) = withContext(Dispatchers.IO) {
        appDao.deleteAccountByUsername(username)
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

    suspend fun getPublicUserRepositories(username: String): Result<List<GitHubRepository>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPublicRepositories(username)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val code = response.code()
                val msg = when (code) {
                    404 -> "User @$username not found on GitHub."
                    403 -> "GitHub API Rate limit exceeded. Try adding a PAT token."
                    else -> "Failed to fetch repositories for @$username (HTTP $code)"
                }
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun getRecursiveTree(
        token: String?,
        owner: String,
        repo: String,
        branch: String
    ): Result<List<GitTreeItem>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getGitTreeRecursive(authHeader, owner, repo, branch, recursive = 1)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.tree)
            } else {
                Result.failure(Exception("Failed to load tree: HTTP ${response.code()}"))
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
                if (branch != null) {
                    appDao.insertCachedFile(
                        CachedFileBlobEntity(
                            id = "$owner/$repo:$branch:$path",
                            repoFullName = "$owner/$repo",
                            branch = branch,
                            path = path,
                            sha = file.sha,
                            content = decoded,
                            size = file.size,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }
                Result.success(Pair(file, decoded))
            } else {
                Result.failure(Exception("Failed to fetch file: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun getCachedFilesForRepo(owner: String, repo: String, branch: String): List<CachedFileBlobEntity> = withContext(Dispatchers.IO) {
        appDao.getCachedFilesForRepo("$owner/$repo", branch)
    }

    suspend fun getCachedFile(owner: String, repo: String, branch: String, path: String): CachedFileBlobEntity? = withContext(Dispatchers.IO) {
        appDao.getCachedFile("$owner/$repo:$branch:$path")
    }

    suspend fun cacheFileBlob(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        sha: String,
        content: String,
        size: Long = 0L
    ) = withContext(Dispatchers.IO) {
        appDao.insertCachedFile(
            CachedFileBlobEntity(
                id = "$owner/$repo:$branch:$path",
                repoFullName = "$owner/$repo",
                branch = branch,
                path = path,
                sha = sha,
                content = content,
                size = size,
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun commitFile(
        token: String?,
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        sha: String?,
        branch: String
    ): Result<CommitResultResponse> {
        if (token.isNullOrBlank()) return Result.failure(Exception("Authentication token required to commit"))
        return commitFileChanges(
            token = token,
            owner = owner,
            repo = repo,
            path = path,
            content = content,
            commitMessage = message,
            branch = branch,
            fileSha = sha
        )
    }

    suspend fun commitRawFileBytes(
        token: String?,
        owner: String,
        repo: String,
        path: String,
        bytes: ByteArray,
        message: String,
        sha: String?,
        branch: String
    ): Result<CommitResultResponse> {
        if (token.isNullOrBlank()) return Result.failure(Exception("Authentication token required to upload"))
        return commitBinaryFile(
            token = token,
            owner = owner,
            repo = repo,
            path = path,
            bytes = bytes,
            commitMessage = message,
            branch = branch,
            fileSha = sha
        )
    }

    suspend fun commitFilesAtomic(
        token: String?,
        owner: String,
        repo: String,
        branch: String,
        message: String,
        files: List<Pair<String, ByteArray>>,
        onProgress: ((current: Int, total: Int, fileName: String) -> Unit)? = null
    ): Result<String> = commitMultipleFilesBatch(token, owner, repo, branch, message, files, onProgress)

    suspend fun commitMultipleFilesBatch(
        token: String?,
        owner: String,
        repo: String,
        branch: String,
        message: String,
        files: List<Pair<String, ByteArray>>,
        onProgress: ((current: Int, total: Int, fileName: String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (token.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Authentication token required to upload"))
            }
            val sanitizedFiles = sanitizeBatchFiles(files)
            if (sanitizedFiles.isEmpty()) {
                return@withContext Result.failure(Exception("No valid files to commit (filtered empty or .git metadata paths)"))
            }

            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(Exception("Invalid authentication token"))

            val cleanBranch = branch.trim().removePrefix("refs/heads/").trim('/').ifEmpty { "main" }
            val encodedBranch = encodeBranchPath(cleanBranch)
            val commitMsg = message.ifBlank { "Upload ${sanitizedFiles.size} file(s)" }
            val totalCount = sanitizedFiles.size

            // 1. Single fast-path for 1 file (avoids extra round-trips on empty OR normal repos)
            if (totalCount == 1) {
                val (singlePath, singleBytes) = sanitizedFiles[0]
                val displayName = singlePath.substringAfterLast('/')
                onProgress?.invoke(1, 1, "Committing $displayName...")
                val existingSha = runCatching {
                    val fResp = apiService.getFileContent(authHeader, owner, repo, singlePath, cleanBranch)
                    if (fResp.isSuccessful) fResp.body()?.sha else null
                }.getOrNull()
                val res = commitBinaryFile(
                    token = token,
                    owner = owner,
                    repo = repo,
                    path = singlePath,
                    bytes = singleBytes,
                    commitMessage = commitMsg,
                    branch = cleanBranch,
                    fileSha = existingSha
                )
                return@withContext res.map { it.commit?.sha ?: it.content?.sha ?: "HEAD" }
            }

            // 2. Partition sanitized files into fast inline UTF-8 tree entries vs parallel binary/large blobs
            val inlineTextEntries = mutableMapOf<String, String>()
            val blobEntries = mutableListOf<Triple<String, ByteArray, String>>() // path, bytes, digestKey
            for ((path, bytes) in sanitizedFiles) {
                val inlineUtf8 = tryDecodeStrictUtf8(bytes)
                if (inlineUtf8 != null) {
                    inlineTextEntries[path] = inlineUtf8
                } else {
                    blobEntries.add(Triple(path, bytes, gitBlobDigestKey(bytes)))
                }
            }

            // 3. Resolve branch head commit & base tree (detecting empty repository HTTP 409)
            onProgress?.invoke(0, totalCount, "Connecting to branch '$cleanBranch'...")
            var branchState = resolveBranchHead(authHeader, owner, repo, cleanBranch, encodedBranch)
            var isEmptyRepoSeeded = false
            var seedCommitSha = ""
            var seedTreeSha = ""
            var seedFilePath = ""

            if (branchState == null) {
                // Repository might be completely empty (0 commits) — bootstrap with 1 seed commit so Git Data API unlocks,
                // then replace with a clean single orphan root commit containing all files!
                val (firstPath, firstBytes) = sanitizedFiles[0]
                onProgress?.invoke(0, totalCount, "Initializing empty repository...")
                val initRes = commitBinaryFile(
                    token = token,
                    owner = owner,
                    repo = repo,
                    path = firstPath,
                    bytes = firstBytes,
                    commitMessage = commitMsg,
                    branch = cleanBranch,
                    fileSha = null
                )
                if (initRes.isFailure) {
                    return@withContext Result.failure(
                        initRes.exceptionOrNull() ?: Exception("Failed to initialize branch '$cleanBranch'")
                    )
                }
                val initCommit = initRes.getOrNull()?.commit?.sha ?: ""
                val refreshed = resolveBranchHead(authHeader, owner, repo, cleanBranch, encodedBranch)
                    ?: return@withContext Result.failure(Exception("Branch '$cleanBranch' could not be resolved after init"))
                isEmptyRepoSeeded = true
                seedCommitSha = initCommit.ifBlank { refreshed.first }
                seedTreeSha = refreshed.second ?: ""
                seedFilePath = firstPath
                branchState = Pair(seedCommitSha, null) // Null base_tree creates 1 clean root commit with ALL files
            }

            // 4. Upload all binary/non-UTF-8/large blobs IN PARALLEL (deduplicated by content SHA-1)
            val completedCounter = AtomicInteger(inlineTextEntries.size)
            if (inlineTextEntries.isNotEmpty() && blobEntries.isNotEmpty()) {
                onProgress?.invoke(
                    completedCounter.get().coerceAtMost(totalCount),
                    totalCount,
                    "Uploading ${blobEntries.size} asset(s) in parallel (${inlineTextEntries.size} text files bundled)..."
                )
            }

            val uploadedBlobShas = if (blobEntries.isNotEmpty()) {
                uploadBlobsConcurrently(
                    authHeader = authHeader,
                    owner = owner,
                    repo = repo,
                    blobEntries = blobEntries,
                    onBlobUploaded = { fileName ->
                        val done = completedCounter.incrementAndGet().coerceAtMost(totalCount)
                        onProgress?.invoke(done, totalCount, "Uploaded $fileName ($done/$totalCount)")
                    }
                )
            } else {
                emptyMap()
            }

            // 5. Assemble ordered CreateTreeItemPayload list preserving original path order
            val treeItems = sanitizedFiles.map { (path, _) ->
                val inlineText = inlineTextEntries[path]
                if (inlineText != null) {
                    CreateTreeItemPayload(
                        path = path,
                        mode = "100644",
                        type = "blob",
                        sha = null,
                        content = inlineText
                    )
                } else {
                    val blobSha = uploadedBlobShas[path]
                        ?: throw IllegalStateException("Missing uploaded blob SHA for $path")
                    CreateTreeItemPayload(
                        path = path,
                        mode = "100644",
                        type = "blob",
                        sha = blobSha,
                        content = null
                    )
                }
            }

            onProgress?.invoke(totalCount, totalCount, "Creating single commit for $totalCount file(s)...")

            // 6. Create tree(s), create 1 commit, and update branch ref (with fast-forward race retry)
            var currentParentSha = branchState.first
            var currentBaseTreeSha = branchState.second

            for (attempt in 0..2) {
                val finalTreeSha = createChainedTrees(
                    authHeader = authHeader,
                    owner = owner,
                    repo = repo,
                    baseTreeSha = if (isEmptyRepoSeeded) null else currentBaseTreeSha,
                    treeItems = treeItems
                )

                val parents = if (isEmptyRepoSeeded) emptyList() else listOf(currentParentSha)
                val commitResp = executeWithRetry("createCommit") {
                    apiService.createCommit(
                        authHeader = authHeader,
                        owner = owner,
                        repo = repo,
                        payload = CreateCommitPayload(
                            message = commitMsg,
                            tree = finalTreeSha,
                            parents = parents
                        )
                    )
                }

                if (!commitResp.isSuccessful || commitResp.body() == null) {
                    val err = commitResp.errorBody()?.string() ?: commitResp.message()
                    return@withContext Result.failure(
                        Exception("Failed to create single commit (${commitResp.code()}): $err")
                    )
                }

                val newCommitSha = commitResp.body()!!.sha

                val refResp = apiService.updateBranchRef(
                    authHeader = authHeader,
                    owner = owner,
                    repo = repo,
                    branch = encodedBranch,
                    payload = UpdateRefPayload(sha = newCommitSha, force = isEmptyRepoSeeded)
                )

                if (refResp.isSuccessful) {
                    return@withContext Result.success(newCommitSha)
                }

                val refErrCode = refResp.code()
                val refErrBody = refResp.errorBody()?.string() ?: refResp.message()

                // If empty repo ruleset rejected force=true orphan commit, fall back cleanly to 1 tree on top of seedCommitSha
                if (isEmptyRepoSeeded && (refErrCode == 403 || refErrCode == 422)) {
                    val remainingItems = treeItems.filter { it.path != seedFilePath }
                    if (remainingItems.isEmpty()) {
                        return@withContext Result.success(seedCommitSha)
                    }
                    val chainedTreeSha = createChainedTrees(
                        authHeader = authHeader,
                        owner = owner,
                        repo = repo,
                        baseTreeSha = seedTreeSha.ifEmpty { null },
                        treeItems = remainingItems
                    )
                    val appendCommitResp = apiService.createCommit(
                        authHeader = authHeader,
                        owner = owner,
                        repo = repo,
                        payload = CreateCommitPayload(
                            message = commitMsg,
                            tree = chainedTreeSha,
                            parents = listOf(seedCommitSha)
                        )
                    )
                    if (appendCommitResp.isSuccessful && appendCommitResp.body() != null) {
                        val appendSha = appendCommitResp.body()!!.sha
                        val appendRef = apiService.updateBranchRef(
                            authHeader = authHeader,
                            owner = owner,
                            repo = repo,
                            branch = encodedBranch,
                            payload = UpdateRefPayload(sha = appendSha, force = false)
                        )
                        if (appendRef.isSuccessful) {
                            return@withContext Result.success(appendSha)
                        }
                    }
                    return@withContext Result.failure(
                        Exception("Failed to update branch '$cleanBranch' ($refErrCode): $refErrBody")
                    )
                }

                // If another push landed on branch during upload, re-fetch head & re-link (reusing already uploaded tree/blobs)
                if (refErrCode == 422 && attempt < 2 && !isEmptyRepoSeeded) {
                    val latest = resolveBranchHead(authHeader, owner, repo, cleanBranch, encodedBranch)
                    if (latest != null) {
                        currentParentSha = latest.first
                        currentBaseTreeSha = latest.second
                        continue
                    }
                }

                return@withContext Result.failure(
                    Exception("Failed to update branch reference ($refErrCode): $refErrBody")
                )
            }

            Result.failure(Exception("Failed to update branch reference after concurrent updates"))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    private suspend fun resolveBranchHead(
        authHeader: String,
        owner: String,
        repo: String,
        cleanBranch: String,
        encodedBranch: String
    ): Pair<String, String?>? {
        // Fast path: 1 HTTP call via /commits/{branch}
        val commitResp = apiService.getCommitDetail(authHeader, owner, repo, cleanBranch)
        if (commitResp.isSuccessful && commitResp.body() != null) {
            val c = commitResp.body()!!
            if (c.sha.isNotBlank()) {
                return Pair(c.sha, c.commit.tree?.sha)
            }
        }
        // Empty repo returns HTTP 409 here
        if (commitResp.code() == 409) {
            return null
        }

        // Secondary path: /git/ref/heads/{branch} (reliable for branch names containing special characters / slashes)
        val refResp = apiService.getBranchRef(authHeader, owner, repo, encodedBranch)
        if (refResp.isSuccessful && refResp.body()?.refObject != null) {
            val headSha = refResp.body()!!.refObject!!.sha
            if (headSha.isNotBlank()) {
                val headCommitResp = apiService.getCommitDetail(authHeader, owner, repo, headSha)
                val baseTreeSha = if (headCommitResp.isSuccessful) headCommitResp.body()?.commit?.tree?.sha else null
                return Pair(headSha, baseTreeSha)
            }
        }
        if (refResp.code() == 409) {
            return null
        }

        // Check if repository has zero branches (another indicator of an empty repository returning 404)
        if (commitResp.code() == 404 || refResp.code() == 404) {
            val branchesResp = apiService.getBranches(authHeader, owner, repo)
            if (branchesResp.isSuccessful && (branchesResp.body()?.isEmpty() == true)) {
                return null
            }
        }

        val errMsg = commitResp.errorBody()?.string() ?: commitResp.message()
        throw IllegalStateException("Unable to resolve branch '$cleanBranch' (${commitResp.code()}): $errMsg")
    }

    private suspend fun uploadBlobsConcurrently(
        authHeader: String,
        owner: String,
        repo: String,
        blobEntries: List<Triple<String, ByteArray, String>>,
        onBlobUploaded: (fileName: String) -> Unit
    ): Map<String, String> = coroutineScope {
        // Deduplicate identical payloads so repeated files only upload once
        val uniqueByDigest = LinkedHashMap<String, Pair<String, ByteArray>>()
        for ((path, bytes, digest) in blobEntries) {
            if (!uniqueByDigest.containsKey(digest)) {
                uniqueByDigest[digest] = Pair(path, bytes)
            }
        }

        val semaphore = Semaphore(BLOB_UPLOAD_CONCURRENCY)
        val digestToSha = java.util.concurrent.ConcurrentHashMap<String, String>(uniqueByDigest.size)

        uniqueByDigest.entries.map { (digest, samplePair) ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val (samplePath, bytes) = samplePair
                    val displayName = samplePath.substringAfterLast('/')
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val blobResp = executeWithRetry("createBlob($displayName)") {
                        apiService.createBlob(
                            authHeader = authHeader,
                            owner = owner,
                            repo = repo,
                            payload = CreateBlobPayload(content = b64, encoding = "base64")
                        )
                    }
                    if (!blobResp.isSuccessful || blobResp.body() == null) {
                        val errBody = blobResp.errorBody()?.string() ?: blobResp.message()
                        throw IllegalStateException("Failed to upload $displayName (${blobResp.code()}): $errBody")
                    }
                    digestToSha[digest] = blobResp.body()!!.sha
                    onBlobUploaded(displayName)
                }
            }
        }.awaitAll()

        val result = HashMap<String, String>(blobEntries.size)
        for ((path, _, digest) in blobEntries) {
            result[path] = digestToSha[digest]
                ?: throw IllegalStateException("Missing blob SHA for $path")
        }
        result
    }

    private suspend fun createChainedTrees(
        authHeader: String,
        owner: String,
        repo: String,
        baseTreeSha: String?,
        treeItems: List<CreateTreeItemPayload>
    ): String {
        val chunks = chunkTreeItems(treeItems)
        var currentBase = baseTreeSha
        for (chunk in chunks) {
            val treeResp = executeWithRetry("createTree(${chunk.size})") {
                apiService.createTree(
                    authHeader = authHeader,
                    owner = owner,
                    repo = repo,
                    payload = CreateTreePayload(
                        baseTree = currentBase,
                        tree = chunk
                    )
                )
            }
            if (!treeResp.isSuccessful || treeResp.body() == null) {
                val errBody = treeResp.errorBody()?.string() ?: treeResp.message()
                throw IllegalStateException("Failed to create git tree (${treeResp.code()}): $errBody")
            }
            currentBase = treeResp.body()!!.sha
        }
        return currentBase ?: throw IllegalStateException("Git tree creation returned empty SHA")
    }

    private suspend fun <T> executeWithRetry(
        label: String,
        maxAttempts: Int = 3,
        block: suspend () -> Response<T>
    ): Response<T> {
        var lastException: Exception? = null
        for (attempt in 0 until maxAttempts) {
            try {
                val resp = block()
                val code = resp.code()
                val shouldRetry = (code == 429 || code == 500 || code == 502 || code == 503 || code == 504 ||
                    (code == 403 && resp.headers()["Retry-After"] != null))
                if (!shouldRetry || attempt == maxAttempts - 1) {
                    return resp
                }
                val retryAfterSec = resp.headers()["Retry-After"]?.toLongOrNull()
                val backoffMs = if (retryAfterSec != null) {
                    (retryAfterSec * 1000L).coerceIn(250L, 10_000L)
                } else {
                    (350L shl attempt)
                }
                delay(backoffMs)
            } catch (e: IOException) {
                lastException = e
                if (attempt == maxAttempts - 1) throw e
                delay(350L shl attempt)
            }
        }
        throw lastException ?: IllegalStateException("Request $label failed after $maxAttempts attempts")
    }

    suspend fun getCommitDetail(
        token: String?,
        owner: String,
        repo: String,
        ref: String
    ): Result<com.example.data.model.GitHubCommitItem> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getCommitDetail(authHeader, owner, repo, ref)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to get commit detail: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun getFullGitTree(
        token: String?,
        owner: String,
        repo: String,
        ref: String
    ): Result<com.example.data.model.GitTreeResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getGitTreeRecursive(authHeader, owner, repo, ref, recursive = 1)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to get git tree: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun createMergeCommit(
        token: String?,
        owner: String,
        repo: String,
        branch: String,
        message: String,
        parentShas: List<String>,
        baseTreeSha: String,
        overrideFiles: List<Pair<String, ByteArray>> = emptyList(),
        additionalTreeItems: List<com.example.data.model.CreateTreeItemPayload> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (token.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Authentication token required to commit"))
            }
            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(Exception("Invalid authentication token"))

            val treeItems = mutableListOf<com.example.data.model.CreateTreeItemPayload>()
            treeItems.addAll(additionalTreeItems)

            for (filePair in overrideFiles) {
                val (filePath, fileBytes) = filePair
                val b64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
                val blobResp = apiService.createBlob(
                    authHeader = authHeader,
                    owner = owner,
                    repo = repo,
                    payload = com.example.data.model.CreateBlobPayload(content = b64, encoding = "base64")
                )
                if (!blobResp.isSuccessful || blobResp.body() == null) {
                    val err = blobResp.errorBody()?.string() ?: "HTTP ${blobResp.code()}"
                    return@withContext Result.failure(Exception("Failed to create blob for $filePath: $err"))
                }
                treeItems.add(
                    com.example.data.model.CreateTreeItemPayload(
                        path = filePath.trimStart('/'),
                        mode = "100644",
                        type = "blob",
                        sha = blobResp.body()!!.sha
                    )
                )
            }

            // Create tree with base_tree set to baseTreeSha
            val treeResp = apiService.createTree(
                authHeader = authHeader,
                owner = owner,
                repo = repo,
                payload = com.example.data.model.CreateTreePayload(
                    baseTree = baseTreeSha,
                    tree = treeItems
                )
            )
            if (!treeResp.isSuccessful || treeResp.body() == null) {
                val err = treeResp.errorBody()?.string() ?: "HTTP ${treeResp.code()}"
                return@withContext Result.failure(Exception("Failed to create tree: $err"))
            }
            val newTreeSha = treeResp.body()!!.sha

            // Create commit with parentShas
            val commitResp = apiService.createCommit(
                authHeader = authHeader,
                owner = owner,
                repo = repo,
                payload = com.example.data.model.CreateCommitPayload(
                    message = message.ifBlank { "Merge branch into $branch" },
                    tree = newTreeSha,
                    parents = parentShas
                )
            )
            if (!commitResp.isSuccessful || commitResp.body() == null) {
                val err = commitResp.errorBody()?.string() ?: "HTTP ${commitResp.code()}"
                return@withContext Result.failure(Exception("Failed to create commit: $err"))
            }
            val newCommitSha = commitResp.body()!!.sha

            // Update branch reference
            val refResp = apiService.updateBranchRef(
                authHeader = authHeader,
                owner = owner,
                repo = repo,
                branch = branch,
                payload = com.example.data.model.UpdateRefPayload(sha = newCommitSha, force = false)
            )
            if (!refResp.isSuccessful) {
                val forceRefResp = apiService.updateBranchRef(
                    authHeader = authHeader,
                    owner = owner,
                    repo = repo,
                    branch = branch,
                    payload = com.example.data.model.UpdateRefPayload(sha = newCommitSha, force = true)
                )
                if (!forceRefResp.isSuccessful) {
                    val err = forceRefResp.errorBody()?.string() ?: "HTTP ${forceRefResp.code()}"
                    return@withContext Result.failure(Exception("Failed to update branch ref: $err"))
                }
            }

            Result.success(newCommitSha)
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

    suspend fun commitBinaryFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        bytes: ByteArray,
        commitMessage: String,
        branch: String,
        fileSha: String? = null
    ): Result<CommitResultResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(Exception("Authentication token required to commit"))

            val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val payload = CreateOrUpdateFilePayload(
                message = commitMessage.ifBlank { "Upload $path" },
                content = base64Content,
                sha = fileSha,
                branch = branch
            )

            val response = apiService.createOrUpdateFile(authHeader, owner, repo, path, payload)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Upload failed: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun uploadMultipleFiles(
        token: String,
        owner: String,
        repo: String,
        files: List<Pair<String, ByteArray>>,
        branch: String,
        baseMessage: String,
        onProgress: (completed: Int, total: Int, currentFile: String) -> Unit
    ): Result<Int> {
        val batchRes = commitMultipleFilesBatch(
            token = token,
            owner = owner,
            repo = repo,
            branch = branch,
            message = baseMessage.ifBlank { "Upload ${files.size} file(s)" },
            files = files,
            onProgress = onProgress
        )
        return batchRes.map {
            onProgress(files.size, files.size, "Done")
            files.size
        }
    }

    suspend fun deleteFile(
        token: String?,
        owner: String,
        repo: String,
        path: String,
        sha: String,
        message: String = "",
        branch: String
    ): Result<CommitResultResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(Exception("Authentication token required to delete files"))

            val payload = DeleteFilePayload(
                message = message.ifBlank { "Delete $path" },
                sha = sha,
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
                sha = sha,
                message = "$baseMessage - $path",
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

    suspend fun fetchCommits(
        token: String?,
        owner: String,
        repo: String,
        sha: String? = null,
        path: String? = null,
        perPage: Int = 30
    ): Result<List<com.example.data.model.GitHubCommitItem>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getCommits(authHeader, owner, repo, sha, path, perPage)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to fetch commits: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun fetchCommitDetail(
        token: String?,
        owner: String,
        repo: String,
        ref: String
    ): Result<com.example.data.model.GitHubCommitItem> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getCommitDetail(authHeader, owner, repo, ref)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to fetch commit detail: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun createBranch(
        token: String,
        owner: String,
        repo: String,
        newBranchName: String,
        baseSha: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(Exception("Authentication token required to create branches"))
            val formattedRef = if (newBranchName.startsWith("refs/heads/")) newBranchName else "refs/heads/$newBranchName"
            val payload = com.example.data.model.CreateRefPayload(ref = formattedRef, sha = baseSha)
            val response = apiService.createRef(authHeader, owner, repo, payload)
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Branch creation failed: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun deleteBranch(
        token: String,
        owner: String,
        repo: String,
        branchName: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(Exception("Authentication token required to delete branches"))
            val cleanBranch = branchName.removePrefix("refs/heads/")
            val response = apiService.deleteBranchRef(authHeader, owner, repo, cleanBranch)
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Branch deletion failed: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun fetchTags(
        token: String?,
        owner: String,
        repo: String
    ): Result<List<com.example.data.model.GitHubTagItem>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getTags(authHeader, owner, repo)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to fetch tags: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun compareBranches(
        token: String?,
        owner: String,
        repo: String,
        base: String,
        head: String
    ): Result<com.example.data.model.CompareResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.compareBranches(authHeader, owner, repo, "$base...$head")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Compare failed: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun fetchPullRequests(
        token: String?,
        owner: String,
        repo: String,
        state: String = "open",
        perPage: Int = 50
    ): Result<List<com.example.data.model.GitHubPullRequest>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getPullRequests(authHeader, owner, repo, state, perPage)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to fetch pull requests: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun fetchPullRequest(
        token: String?,
        owner: String,
        repo: String,
        pullNumber: Int
    ): Result<com.example.data.model.GitHubPullRequest> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getPullRequest(authHeader, owner, repo, pullNumber)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to fetch PR #$pullNumber: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun mergePullRequest(
        token: String,
        owner: String,
        repo: String,
        pullNumber: Int,
        commitTitle: String? = null,
        commitMessage: String? = null,
        mergeMethod: String = "merge"
    ): Result<com.example.data.model.GitHubMergeResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(Exception("Authentication token required to merge pull request"))
            val payload = com.example.data.model.MergePullRequestPayload(
                commitTitle = commitTitle,
                commitMessage = commitMessage,
                mergeMethod = mergeMethod
            )
            val response = apiService.mergePullRequest(authHeader, owner, repo, pullNumber, payload)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to merge PR #$pullNumber: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun mergeUpstream(
        token: String?,
        owner: String,
        repo: String,
        branch: String
    ): Result<com.example.data.model.MergeUpstreamResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(Exception("Authentication token required to sync fork with upstream"))
            val response = apiService.mergeUpstream(authHeader, owner, repo, com.example.data.model.MergeUpstreamPayload(branch))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun mergeBranches(
        token: String?,
        owner: String,
        repo: String,
        base: String,
        head: String,
        commitMessage: String? = null
    ): Result<com.example.data.model.GitHubMergeBranchResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
                ?: return@withContext Result.failure(Exception("Authentication token required to merge branches"))
            val response = apiService.mergeBranches(
                authHeader,
                owner,
                repo,
                com.example.data.model.MergeBranchesPayload(base, head, commitMessage)
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun fetchIssues(
        token: String?,
        owner: String,
        repo: String,
        state: String = "open",
        perPage: Int = 50
    ): Result<List<com.example.data.model.GitHubIssue>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getIssues(authHeader, owner, repo, state, perPage)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to fetch issues: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun fetchReleases(
        token: String?,
        owner: String,
        repo: String,
        perPage: Int = 30
    ): Result<List<com.example.data.model.GitHubRelease>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getReleases(authHeader, owner, repo, perPage)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to fetch releases: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun getReleaseByTag(
        token: String?,
        owner: String,
        repo: String,
        tag: String
    ): Result<com.example.data.model.GitHubRelease> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getReleaseByTag(authHeader, owner, repo, tag)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Release not found for tag '$tag': $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun deleteRelease(
        token: String?,
        owner: String,
        repo: String,
        releaseId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.deleteRelease(authHeader, owner, repo, releaseId)
            if (response.isSuccessful || response.code() == 204) {
                Result.success(Unit)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to delete release: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun deleteTagRef(
        token: String?,
        owner: String,
        repo: String,
        tag: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val cleanTag = tag.removePrefix("tags/").removePrefix("refs/tags/")
            val response = apiService.deleteTagRef(authHeader, owner, repo, cleanTag)
            if (response.isSuccessful || response.code() == 204) {
                Result.success(Unit)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to delete tag '$cleanTag': $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun deleteReleaseByTag(
        token: String?,
        owner: String,
        repo: String,
        tag: String,
        cleanupTag: Boolean = false
    ): Result<Pair<Boolean, Boolean>> = withContext(Dispatchers.IO) {
        try {
            var releaseDeleted = false
            var tagDeleted = false

            // 1. Try to find release by tag directly
            val relResult = getReleaseByTag(token, owner, repo, tag)
            val releaseId = if (relResult.isSuccess) {
                relResult.getOrNull()?.id
            } else {
                // Fallback: search in releases list
                val listResult = fetchReleases(token, owner, repo, perPage = 100)
                listResult.getOrNull()?.firstOrNull { it.tagName == tag || it.tagName == tag.removePrefix("v") || "v${it.tagName}" == tag }?.id
            }

            if (releaseId != null && releaseId > 0) {
                val delRel = deleteRelease(token, owner, repo, releaseId)
                if (delRel.isSuccess) {
                    releaseDeleted = true
                }
            }

            // 2. If cleanupTag is requested or if release was deleted with cleanup tag
            if (cleanupTag) {
                val delTag = deleteTagRef(token, owner, repo, tag)
                if (delTag.isSuccess) {
                    tagDeleted = true
                }
            }

            if (releaseDeleted || tagDeleted) {
                Result.success(Pair(releaseDeleted, tagDeleted))
            } else if (releaseId == null && !cleanupTag) {
                Result.failure(Exception("Release '$tag' not found in $owner/$repo"))
            } else {
                Result.failure(Exception("Could not delete release or tag for '$tag'"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
    }

    suspend fun fetchWorkflowRuns(
        token: String?,
        owner: String,
        repo: String,
        perPage: Int = 30
    ): Result<com.example.data.model.GitHubWorkflowRunsResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiClient.formatAuthHeader(token)
            val response = apiService.getWorkflowRuns(authHeader, owner, repo, perPage)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to fetch workflow runs: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyErrorMessage(null, e)))
        }
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
