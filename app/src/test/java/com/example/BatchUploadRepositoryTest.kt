package com.example

import com.example.data.api.GitHubApiService
import com.example.data.api.GitHubAuthService
import com.example.data.local.AccountEntity
import com.example.data.local.AppDao
import com.example.data.local.CachedFileBlobEntity
import com.example.data.local.FileDraftEntity
import com.example.data.local.SavedRepoEntity
import com.example.data.model.BranchCommit
import com.example.data.model.CommitDetail
import com.example.data.model.CommitInfo
import com.example.data.model.CommitResultResponse
import com.example.data.model.CompareResponse
import com.example.data.model.CreateBlobPayload
import com.example.data.model.CreateBlobResponse
import com.example.data.model.CreateCommitPayload
import com.example.data.model.CreateCommitResponse
import com.example.data.model.CreateOrUpdateFilePayload
import com.example.data.model.CreateRefPayload
import com.example.data.model.CreateTreeItemPayload
import com.example.data.model.CreateTreePayload
import com.example.data.model.CreateTreeResponse
import com.example.data.model.DeleteFilePayload
import com.example.data.model.DeviceCodeRequest
import com.example.data.model.DeviceCodeResponse
import com.example.data.model.FileContentResponse
import com.example.data.model.GitHubBranch
import com.example.data.model.GitHubCommitItem
import com.example.data.model.GitHubIssue
import com.example.data.model.GitHubMergeBranchResponse
import com.example.data.model.GitHubMergeResponse
import com.example.data.model.GitHubPullRequest
import com.example.data.model.GitHubRelease
import com.example.data.model.GitHubTagItem
import com.example.data.model.GitHubUser
import com.example.data.model.GitHubWorkflowRunsResponse
import com.example.data.model.GitRefResponse
import com.example.data.model.GitTreeResponse
import com.example.data.model.MergeBranchesPayload
import com.example.data.model.MergePullRequestPayload
import com.example.data.model.MergeUpstreamPayload
import com.example.data.model.MergeUpstreamResponse
import com.example.data.model.OAuthTokenRequest
import com.example.data.model.OAuthTokenResponse
import com.example.data.model.UpdateRefPayload
import com.example.data.repository.GitHubRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatchUploadRepositoryTest {

    @Test
    fun `moshi CreateTreeItemPayload omits null sha for inline content and null content for sha`() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(CreateTreeItemPayload::class.java)

        val inlineJson = adapter.toJson(
            CreateTreeItemPayload(path = "src/Main.kt", content = "fun main() {}")
        )
        assertTrue(inlineJson.contains("\"content\":\"fun main() {}\""))
        assertFalse(inlineJson.contains("\"sha\""))

        val blobJson = adapter.toJson(
            CreateTreeItemPayload(path = "assets/icon.png", sha = "abc123")
        )
        assertTrue(blobJson.contains("\"sha\":\"abc123\""))
        assertFalse(blobJson.contains("\"content\""))
    }

    @Test
    fun `sanitizeBatchFiles strips git metadata normalizes paths deduplicates and removes folder collisions`() {
        val input = listOf(
            ".git/HEAD" to "ref: refs/heads/main".toByteArray(),
            "app\\.git\\objects\\aa\\bb" to byteArrayOf(1, 2, 3),
            ".git" to "file".toByteArray(),
            ".github/workflows/build.yml" to "name: CI".toByteArray(),
            ".gitignore" to "build/\n".toByteArray(),
            "app/src/.gitkeep" to ByteArray(0),
            "./app\\build.gradle.kts" to "old".toByteArray(),
            "app/build.gradle.kts" to "new".toByteArray(),
            "app" to "collides-with-app-dir".toByteArray()
        )

        val sanitized = GitHubRepository.sanitizeBatchFiles(input)
        val paths = sanitized.map { it.first }

        assertEquals(
            listOf(
                ".github/workflows/build.yml",
                ".gitignore",
                "app/src/.gitkeep",
                "app/build.gradle.kts"
            ),
            paths
        )
        assertEquals("new", String(sanitized.last().second))
    }

    @Test
    fun `tryDecodeStrictUtf8 accepts valid utf8 and rejects binary empty or oversized payloads`() {
        val text = "package com.example\nval greeting = \"नमस्ते 🚀\"\n"
        assertEquals(text, GitHubRepository.tryDecodeStrictUtf8(text.toByteArray(Charsets.UTF_8)))

        assertNull(GitHubRepository.tryDecodeStrictUtf8(ByteArray(0)))
        assertNull(GitHubRepository.tryDecodeStrictUtf8(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00, 0x0A)))
        assertNull(GitHubRepository.tryDecodeStrictUtf8(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte())))

        val oversized = ByteArray(GitHubRepository.MAX_INLINE_TREE_FILE_BYTES + 1) { 'a'.code.toByte() }
        assertNull(GitHubRepository.tryDecodeStrictUtf8(oversized))
    }

    @Test
    fun `chunkTreeItems splits large batches while keeping single commit structure`() {
        val items = (1..650).map { i ->
            CreateTreeItemPayload(path = "src/File$i.kt", content = "val x = $i")
        }
        val chunks = GitHubRepository.chunkTreeItems(items)
        assertEquals(3, chunks.size)
        assertEquals(650, chunks.sumOf { it.size })
    }

    @Test
    fun `commitMultipleFilesBatch bundles text files inline and uploads unique binary blobs in parallel for 1 commit`() = runTest {
        val fakeApi = FakeGitHubApiService(emptyRepo = false)
        val repo = GitHubRepository(apiService = fakeApi, authService = FakeGitHubAuthService(), appDao = FakeAppDao())

        val binaryA = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x00, 0x01)
        val files = listOf(
            "settings.gradle.kts" to "rootProject.name = \"App\"".toByteArray(),
            "gradle.properties" to "org.gradle.jvmargs=-Xmx2g".toByteArray(),
            "app/build.gradle.kts" to "plugins {}".toByteArray(),
            ".gitignore" to "build/\n".toByteArray(),
            "app/proguard-rules.pro" to "-keep class *".toByteArray(),
            "app/src/main/res/drawable/icon1.png" to binaryA,
            "app/src/main/res/drawable/icon2.png" to binaryA, // identical binary should deduplicate
            "app/src/main/res/raw/empty.bin" to ByteArray(0)
        )

        val progressUpdates = CopyOnWriteArrayList<Triple<Int, Int, String>>()
        val result = repo.commitMultipleFilesBatch(
            token = "ghp_testtoken",
            owner = "BlazeFTL",
            repo = "Better-Gemini",
            branch = "main",
            message = "Upload Android template",
            files = files,
            onProgress = { cur, total, msg -> progressUpdates.add(Triple(cur, total, msg)) }
        )

        assertTrue("Expected batch commit success, got: ${result.exceptionOrNull()}", result.isSuccess)

        // Only 2 unique non-inline blobs (binaryA + empty.bin); icon1 and icon2 deduplicated; 5 text files went inline
        assertEquals(2, fakeApi.createBlobCount.get())
        assertEquals(1, fakeApi.createTreeCount.get())
        assertEquals(1, fakeApi.createCommitCount.get())
        assertEquals(1, fakeApi.updateRefCount.get())
        assertEquals(0, fakeApi.createOrUpdateFileCount.get())

        val treePayload = fakeApi.lastTreePayload!!
        assertEquals("base_tree_123", treePayload.baseTree)
        assertEquals(8, treePayload.tree.size)
        assertEquals(5, treePayload.tree.count { it.content != null && it.sha == null })
        assertEquals(3, treePayload.tree.count { it.sha != null && it.content == null })

        val commitPayload = fakeApi.lastCommitPayload!!
        assertEquals("Upload Android template", commitPayload.message)
        assertEquals(listOf("head_commit_123"), commitPayload.parents)

        val refPayload = fakeApi.lastUpdateRefPayload!!
        assertFalse(refPayload.force)
        assertTrue(progressUpdates.isNotEmpty())
    }

    @Test
    fun `commitMultipleFilesBatch on empty repo produces 1 final root commit with all files`() = runTest {
        val fakeApi = FakeGitHubApiService(emptyRepo = true)
        val repo = GitHubRepository(apiService = fakeApi, authService = FakeGitHubAuthService(), appDao = FakeAppDao())

        val files = listOf(
            "README.md" to "# Hello".toByteArray(),
            "src/Main.kt" to "fun main() {}".toByteArray(),
            "src/Helper.kt" to "object Helper".toByteArray()
        )

        val result = repo.commitMultipleFilesBatch(
            token = "ghp_testtoken",
            owner = "BlazeFTL",
            repo = "EmptyRepo",
            branch = "main",
            message = "Initial import",
            files = files
        )

        assertTrue("Expected empty-repo batch commit success, got: ${result.exceptionOrNull()}", result.isSuccess)
        // 1 Contents API seed to unlock Git Data API + 1 orphan root commit with baseTree=null replacing it
        assertEquals(1, fakeApi.createOrUpdateFileCount.get())
        assertEquals(1, fakeApi.createCommitCount.get())
        assertEquals(1, fakeApi.updateRefCount.get())

        val treePayload = fakeApi.lastTreePayload!!
        assertNull(treePayload.baseTree)
        assertEquals(3, treePayload.tree.size)

        val commitPayload = fakeApi.lastCommitPayload!!
        assertEquals(emptyList<String>(), commitPayload.parents)
        assertEquals("Initial import", commitPayload.message)
        assertTrue(fakeApi.lastUpdateRefPayload!!.force)
    }

    private class FakeGitHubApiService(private var emptyRepo: Boolean) : GitHubApiService {
        val createBlobCount = AtomicInteger(0)
        val createTreeCount = AtomicInteger(0)
        val createCommitCount = AtomicInteger(0)
        val updateRefCount = AtomicInteger(0)
        val createOrUpdateFileCount = AtomicInteger(0)

        var lastTreePayload: CreateTreePayload? = null
        var lastCommitPayload: CreateCommitPayload? = null
        var lastUpdateRefPayload: UpdateRefPayload? = null

        override suspend fun getCommitDetail(
            authHeader: String?,
            owner: String,
            repo: String,
            ref: String
        ): Response<GitHubCommitItem> {
            if (emptyRepo) {
                return Response.error(
                    409,
                    "{\"message\":\"Git Repository is empty.\"}".toResponseBody("application/json".toMediaType())
                )
            }
            val commitSha = if (ref == "seed_commit_1") "seed_commit_1" else "head_commit_123"
            val treeSha = if (ref == "seed_commit_1") "seed_tree_1" else "base_tree_123"
            return Response.success(
                GitHubCommitItem(
                    sha = commitSha,
                    commit = CommitInfo(
                        message = "prev",
                        tree = BranchCommit(sha = treeSha)
                    )
                )
            )
        }

        override suspend fun createOrUpdateFile(
            authHeader: String,
            owner: String,
            repo: String,
            path: String,
            payload: CreateOrUpdateFilePayload
        ): Response<CommitResultResponse> {
            createOrUpdateFileCount.incrementAndGet()
            emptyRepo = false
            return Response.success(
                CommitResultResponse(
                    commit = CommitDetail(sha = "seed_commit_1", message = payload.message)
                )
            )
        }

        override suspend fun createBlob(
            authHeader: String,
            owner: String,
            repo: String,
            payload: CreateBlobPayload
        ): Response<CreateBlobResponse> {
            val idx = createBlobCount.incrementAndGet()
            return Response.success(CreateBlobResponse(sha = "blob_sha_$idx"))
        }

        override suspend fun createTree(
            authHeader: String,
            owner: String,
            repo: String,
            payload: CreateTreePayload
        ): Response<CreateTreeResponse> {
            val idx = createTreeCount.incrementAndGet()
            lastTreePayload = payload
            return Response.success(CreateTreeResponse(sha = "tree_sha_$idx"))
        }

        override suspend fun createCommit(
            authHeader: String,
            owner: String,
            repo: String,
            payload: CreateCommitPayload
        ): Response<CreateCommitResponse> {
            val idx = createCommitCount.incrementAndGet()
            lastCommitPayload = payload
            return Response.success(CreateCommitResponse(sha = "commit_sha_$idx"))
        }

        override suspend fun updateBranchRef(
            authHeader: String,
            owner: String,
            repo: String,
            branch: String,
            payload: UpdateRefPayload
        ): Response<ResponseBody> {
            updateRefCount.incrementAndGet()
            lastUpdateRefPayload = payload
            return Response.success("{}".toResponseBody("application/json".toMediaType()))
        }

        // Remaining unused interface stubs for compilation
        override suspend fun getAuthenticatedUser(authHeader: String): Response<GitHubUser> = TODO()
        override suspend fun getUserRepositories(authHeader: String, perPage: Int, sort: String, direction: String, affiliation: String): Response<List<com.example.data.model.GitHubRepository>> = TODO()
        override suspend fun getPublicRepositories(username: String, perPage: Int, sort: String): Response<List<com.example.data.model.GitHubRepository>> = TODO()
        override suspend fun getBranches(authHeader: String?, owner: String, repo: String, perPage: Int): Response<List<GitHubBranch>> = Response.success(emptyList())
        override suspend fun getGitTreeRecursive(authHeader: String?, owner: String, repo: String, treeSha: String, recursive: Int): Response<GitTreeResponse> = TODO()
        override suspend fun getFileContent(authHeader: String?, owner: String, repo: String, path: String, ref: String?): Response<FileContentResponse> = TODO()
        override suspend fun getRawFile(authHeader: String?, owner: String, repo: String, branch: String, path: String): Response<ResponseBody> = TODO()
        override suspend fun deleteFile(authHeader: String, owner: String, repo: String, path: String, payload: DeleteFilePayload): Response<CommitResultResponse> = TODO()
        override suspend fun getCommits(authHeader: String?, owner: String, repo: String, sha: String?, path: String?, perPage: Int): Response<List<GitHubCommitItem>> = TODO()
        override suspend fun createRef(authHeader: String, owner: String, repo: String, payload: CreateRefPayload): Response<Any> = TODO()
        override suspend fun getBranchRef(authHeader: String, owner: String, repo: String, branch: String): Response<GitRefResponse> = TODO()
        override suspend fun deleteBranchRef(authHeader: String, owner: String, repo: String, branch: String): Response<ResponseBody> = TODO()
        override suspend fun getTags(authHeader: String?, owner: String, repo: String, perPage: Int): Response<List<GitHubTagItem>> = TODO()
        override suspend fun compareBranches(authHeader: String?, owner: String, repo: String, basehead: String): Response<CompareResponse> = TODO()
        override suspend fun getPullRequests(authHeader: String?, owner: String, repo: String, state: String, perPage: Int): Response<List<GitHubPullRequest>> = TODO()
        override suspend fun getPullRequest(authHeader: String?, owner: String, repo: String, pullNumber: Int): Response<GitHubPullRequest> = TODO()
        override suspend fun mergePullRequest(authHeader: String, owner: String, repo: String, pullNumber: Int, payload: MergePullRequestPayload): Response<GitHubMergeResponse> = TODO()
        override suspend fun mergeUpstream(authHeader: String, owner: String, repo: String, payload: MergeUpstreamPayload): Response<MergeUpstreamResponse> = TODO()
        override suspend fun mergeBranches(authHeader: String, owner: String, repo: String, payload: MergeBranchesPayload): Response<GitHubMergeBranchResponse> = TODO()
        override suspend fun getIssues(authHeader: String?, owner: String, repo: String, state: String, perPage: Int): Response<List<GitHubIssue>> = TODO()
        override suspend fun getReleases(authHeader: String?, owner: String, repo: String, perPage: Int): Response<List<GitHubRelease>> = TODO()
        override suspend fun getReleaseByTag(authHeader: String?, owner: String, repo: String, tag: String): Response<GitHubRelease> = TODO()
        override suspend fun deleteRelease(authHeader: String?, owner: String, repo: String, releaseId: Long): Response<ResponseBody> = TODO()
        override suspend fun deleteTagRef(authHeader: String?, owner: String, repo: String, tag: String): Response<ResponseBody> = TODO()
        override suspend fun getWorkflowRuns(authHeader: String?, owner: String, repo: String, perPage: Int): Response<GitHubWorkflowRunsResponse> = TODO()
    }

    private class FakeGitHubAuthService : GitHubAuthService {
        override suspend fun requestDeviceCode(payload: DeviceCodeRequest): Response<DeviceCodeResponse> = TODO()
        override suspend fun pollDeviceToken(payload: OAuthTokenRequest): Response<OAuthTokenResponse> = TODO()
    }

    private class FakeAppDao : AppDao {
        override fun getAllAccounts(): Flow<List<AccountEntity>> = flowOf(emptyList())
        override fun getCurrentAccount(): Flow<AccountEntity?> = flowOf(null)
        override suspend fun getCurrentAccountSync(): AccountEntity? = null
        override suspend fun insertAccount(account: AccountEntity): Long = 1L
        override suspend fun clearCurrentAccount() {}
        override suspend fun setCurrentAccount(id: Long) {}
        override suspend fun setCurrentAccountByUsername(username: String) {}
        override suspend fun deleteAccount(id: Long) {}
        override suspend fun deleteAccountByUsername(username: String) {}
        override suspend fun deleteAllAccounts() {}
        override fun getSavedRepos(): Flow<List<SavedRepoEntity>> = flowOf(emptyList())
        override suspend fun insertSavedRepo(repo: SavedRepoEntity) {}
        override suspend fun setRepoFavorite(fullName: String, isFavorite: Boolean) {}
        override suspend fun deleteSavedRepo(fullName: String) {}
        override suspend fun getDraft(id: String): FileDraftEntity? = null
        override suspend fun saveDraft(draft: FileDraftEntity) {}
        override suspend fun deleteDraft(id: String) {}
        override suspend fun getCachedFilesForRepo(repoFullName: String, branch: String): List<CachedFileBlobEntity> = emptyList()
        override suspend fun getCachedFile(id: String): CachedFileBlobEntity? = null
        override suspend fun getCachedFilesUnderPath(repoFullName: String, branch: String, pathPrefix: String): List<CachedFileBlobEntity> = emptyList()
        override suspend fun insertCachedFiles(files: List<CachedFileBlobEntity>) {}
        override suspend fun insertCachedFile(file: CachedFileBlobEntity) {}
        override suspend fun deleteCachedRepoFiles(repoFullName: String) {}
    }
}
