package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val token: String,
    val avatarUrl: String? = null,
    val name: String? = null,
    val isCurrent: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_repos")
data class SavedRepoEntity(
    @PrimaryKey val fullName: String,
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val isFavorite: Boolean = false,
    val lastOpened: Long = System.currentTimeMillis()
)

@Entity(tableName = "file_drafts")
data class FileDraftEntity(
    @PrimaryKey val id: String, // format: "owner/repo:branch:path"
    val repoFullName: String,
    val branch: String,
    val filePath: String,
    val draftContent: String,
    val originalSha: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_file_blobs")
data class CachedFileBlobEntity(
    @PrimaryKey val id: String, // format: "owner/repo:branch:path"
    val repoFullName: String,
    val branch: String,
    val path: String,
    val sha: String,
    val content: String,
    val size: Long = 0L,
    val lastSyncedAt: Long = System.currentTimeMillis()
)
