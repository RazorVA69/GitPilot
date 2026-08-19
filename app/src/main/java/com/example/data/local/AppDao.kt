package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    @Query("SELECT * FROM accounts ORDER BY isCurrent DESC, addedAt DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isCurrent = 1 LIMIT 1")
    fun getCurrentAccount(): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentAccountSync(): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Query("UPDATE accounts SET isCurrent = 0")
    suspend fun clearCurrentAccount()

    @Query("UPDATE accounts SET isCurrent = 1 WHERE id = :id")
    suspend fun setCurrentAccount(id: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    // Saved Repos
    @Query("SELECT * FROM saved_repos ORDER BY lastOpened DESC")
    fun getSavedRepos(): Flow<List<SavedRepoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedRepo(repo: SavedRepoEntity)

    @Query("UPDATE saved_repos SET isFavorite = :isFavorite WHERE fullName = :fullName")
    suspend fun setRepoFavorite(fullName: String, isFavorite: Boolean)

    @Query("DELETE FROM saved_repos WHERE fullName = :fullName")
    suspend fun deleteSavedRepo(fullName: String)

    // Drafts
    @Query("SELECT * FROM file_drafts WHERE id = :id")
    suspend fun getDraft(id: String): FileDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: FileDraftEntity)

    @Query("DELETE FROM file_drafts WHERE id = :id")
    suspend fun deleteDraft(id: String)
}
