package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUser(
    val id: Long = 0,
    val login: String = "",
    val name: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    @Json(name = "public_repos") val publicRepos: Int = 0,
    @Json(name = "total_private_repos") val totalPrivateRepos: Int = 0,
    @Json(name = "followers") val followers: Int = 0,
    @Json(name = "following") val following: Int = 0,
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubRepository(
    val id: Long = 0,
    val name: String = "",
    @Json(name = "full_name") val fullName: String = "",
    val private: Boolean = false,
    val description: String? = null,
    val fork: Boolean = false,
    val language: String? = null,
    @Json(name = "stargazers_count") val stargazersCount: Int = 0,
    @Json(name = "forks_count") val forksCount: Int = 0,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "updated_at") val updatedAt: String? = null,
    val owner: RepoOwner = RepoOwner(0, "user"),
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class RepoOwner(
    val id: Long = 0,
    val login: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubBranch(
    val name: String,
    val commit: BranchCommit? = null,
    val `protected`: Boolean = false
)

@JsonClass(generateAdapter = true)
data class BranchCommit(
    val sha: String,
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class GitTreeResponse(
    val sha: String,
    val url: String,
    val tree: List<GitTreeItem>,
    val truncated: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GitTreeItem(
    val path: String,
    val mode: String? = null,
    val type: String, // "blob" (file) or "tree" (directory)
    val sha: String,
    val size: Long? = null,
    val url: String? = null
) {
    val isDirectory: Boolean get() = type == "tree"
    val isFile: Boolean get() = type == "blob"
    val fileName: String get() = path.substringAfterLast('/')
    val directoryPath: String get() = if (path.contains('/')) path.substringBeforeLast('/') else ""
    val extension: String get() = if (fileName.contains('.')) fileName.substringAfterLast('.').lowercase() else ""
}

@JsonClass(generateAdapter = true)
data class FileContentResponse(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0,
    val type: String,
    val content: String? = null, // Base64 encoded
    val encoding: String? = null,
    @Json(name = "download_url") val downloadUrl: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateOrUpdateFilePayload(
    val message: String,
    val content: String, // Base64 encoded content
    val sha: String? = null, // Required if updating existing file
    val branch: String? = null,
    val committer: CommitterInfo? = null
)

@JsonClass(generateAdapter = true)
data class DeleteFilePayload(
    val message: String,
    val sha: String,
    val branch: String? = null,
    val committer: CommitterInfo? = null
)

@JsonClass(generateAdapter = true)
data class CommitterInfo(
    val name: String,
    val email: String
)

@JsonClass(generateAdapter = true)
data class CommitResultResponse(
    val content: FileContentResponse? = null,
    val commit: CommitDetail? = null
)

@JsonClass(generateAdapter = true)
data class CommitDetail(
    val sha: String,
    val message: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubCommitItem(
    val sha: String = "",
    @Json(name = "node_id") val nodeId: String? = null,
    val commit: CommitInfo = CommitInfo(),
    @Json(name = "html_url") val htmlUrl: String? = null,
    val author: RepoOwner? = null,
    val committer: RepoOwner? = null,
    val parents: List<BranchCommit>? = null
)

@JsonClass(generateAdapter = true)
data class CommitInfo(
    val message: String = "",
    val author: GitAuthorInfo? = null,
    val committer: GitAuthorInfo? = null,
    @Json(name = "comment_count") val commentCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class GitAuthorInfo(
    val name: String = "",
    val email: String = "",
    val date: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateRefPayload(
    val ref: String,
    val sha: String
)

@JsonClass(generateAdapter = true)
data class GitHubTagItem(
    val name: String = "",
    val commit: BranchCommit? = null,
    @Json(name = "zipball_url") val zipballUrl: String? = null,
    @Json(name = "tarball_url") val tarballUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class CompareResponse(
    val status: String? = null,
    @Json(name = "ahead_by") val aheadBy: Int = 0,
    @Json(name = "behind_by") val behindBy: Int = 0,
    @Json(name = "total_commits") val totalCommits: Int = 0,
    val commits: List<GitHubCommitItem> = emptyList(),
    val files: List<CompareFile>? = null
)

@JsonClass(generateAdapter = true)
data class CompareFile(
    val filename: String = "",
    val status: String = "",
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    val patch: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubPullRequest(
    val id: Long = 0,
    val number: Int = 0,
    val state: String = "open",
    val title: String = "",
    val body: String? = null,
    val user: GitHubUser? = null,
    @Json(name = "html_url") val htmlUrl: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "merged_at") val mergedAt: String? = null,
    @Json(name = "draft") val isDraft: Boolean = false,
    val head: PullRequestBranchRef? = null,
    val base: PullRequestBranchRef? = null
)

@JsonClass(generateAdapter = true)
data class PullRequestBranchRef(
    val label: String? = null,
    val ref: String = "",
    val sha: String = ""
)

@JsonClass(generateAdapter = true)
data class MergePullRequestPayload(
    @Json(name = "commit_title") val commitTitle: String? = null,
    @Json(name = "commit_message") val commitMessage: String? = null,
    val sha: String? = null,
    @Json(name = "merge_method") val mergeMethod: String = "merge" // merge, squash, rebase
)

@JsonClass(generateAdapter = true)
data class GitHubMergeResponse(
    val sha: String? = null,
    val merged: Boolean = false,
    val message: String = ""
)

@JsonClass(generateAdapter = true)
data class GitHubIssue(
    val id: Long = 0,
    val number: Int = 0,
    val state: String = "open",
    val title: String = "",
    val body: String? = null,
    val user: GitHubUser? = null,
    val comments: Int = 0,
    @Json(name = "html_url") val htmlUrl: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    val id: Long = 0,
    @Json(name = "tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflow(
    val id: Long = 0,
    val name: String = "",
    val path: String = "",
    val state: String = "active"
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowRunsResponse(
    @Json(name = "total_count") val totalCount: Int = 0,
    @Json(name = "workflow_runs") val workflowRuns: List<GitHubWorkflowRun> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowRun(
    val id: Long = 0,
    val name: String? = null,
    @Json(name = "head_branch") val headBranch: String? = null,
    @Json(name = "head_sha") val headSha: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null
)

// In-Memory Hierarchical File Node for Ultra-Fast Explorer Navigation
data class ExplorerNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sha: String,
    val size: Long? = null,
    val extension: String = "",
    val children: MutableList<ExplorerNode> = mutableListOf(),
    var isExpanded: Boolean = false
) {
    val fileCount: Int get() = if (isDirectory) children.sumOf { if (it.isDirectory) it.fileCount else 1 } else 0
    val totalSize: Long get() = if (isDirectory) children.sumOf { it.totalSize } else (size ?: 0L)
}

// Search Across All Files Result Models
data class RepoFileSearchMatch(
    val path: String,
    val fileName: String,
    val lineNumber: Int,
    val lineContent: String,
    val matchStartIndex: Int,
    val matchLength: Int
)

data class RepoFileGroupedMatches(
    val path: String,
    val fileName: String,
    val matches: List<RepoFileSearchMatch>
)
