package me.masonasons.fastsm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.masonasons.fastsm.data.db.entity.TimelinePositionEntity

@Dao
interface TimelinePositionDao {

    @Query("SELECT statusId FROM timeline_positions WHERE accountId = :accountId AND timelineId = :timelineId")
    suspend fun getStatusId(accountId: Long, timelineId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TimelinePositionEntity)

    @Query("DELETE FROM timeline_positions WHERE accountId = :accountId AND timelineId = :timelineId")
    suspend fun delete(accountId: Long, timelineId: String)

    @Query("DELETE FROM timeline_positions WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: Long)
}
