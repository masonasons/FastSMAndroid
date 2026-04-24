package me.masonasons.fastsm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.masonasons.fastsm.data.db.entity.TimelineEntity

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timelines WHERE accountId = :accountId ORDER BY position ASC, rowId ASC")
    fun observeByAccount(accountId: Long): Flow<List<TimelineEntity>>

    @Query("SELECT * FROM timelines WHERE accountId = :accountId ORDER BY position ASC, rowId ASC")
    suspend fun getByAccount(accountId: Long): List<TimelineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TimelineEntity): Long

    @Query("DELETE FROM timelines WHERE rowId = :rowId")
    suspend fun delete(rowId: Long)

    @Query("DELETE FROM timelines WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: Long)
}
