package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUser(
    val id: Long,
    val login: String,
    val name: String?,
    @Json(name = "avatar_url") val avatarUrl: String?,
    val bio: String?,
    @Json(name = "public_repos") val publicRepos: Int = 0,
    @Json(name = "total_private_repos") val totalPrivateRepos: Int = 0,
    @Json(name = "followers") val followers: Int = 0,
    @Json(name = "following") val following: Int = 0,
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubRepository(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val private: Boolean = false,
    val description: String? = null,
    val fork: Boolean = false,
    val language: String? = null,
    @Json(name = "stargazers_count") val stargazersCount: Int = 0,
    @Json(name = "forks_count") val forksCount: Int = 0,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "updated_at") val updatedAt: String? = null,
    val owner: RepoOwner? = null,
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class RepoOwner(
    val id: Long = 0,
    val login: String,
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
