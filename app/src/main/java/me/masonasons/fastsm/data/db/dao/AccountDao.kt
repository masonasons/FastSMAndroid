package me.masonasons.fastsm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.masonasons.fastsm.data.db.entity.AccountEntity

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity): Long

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
