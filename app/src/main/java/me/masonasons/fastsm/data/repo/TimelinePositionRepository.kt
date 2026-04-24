package me.masonasons.fastsm.data.repo

import me.masonasons.fastsm.data.db.dao.TimelinePositionDao
import me.masonasons.fastsm.data.db.entity.TimelinePositionEntity

/**
 * Thin wrapper over [TimelinePositionDao] — keeps ViewModels off the DAO
 * directly and gives us a single spot to hang behavior like TTL or
 * per-platform filtering if/when that becomes needed.
 */
class TimelinePositionRepository(private val dao: TimelinePositionDao) {

    suspend fun get(accountId: Long, timelineId: String): String? =
        dao.getStatusId(accountId, timelineId)

    suspend fun save(accountId: Long, timelineId: String, statusId: String) {
        dao.upsert(
            TimelinePositionEntity(
                accountId = accountId,
                timelineId = timelineId,
                statusId = statusId,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun clear(accountId: Long, timelineId: String) = dao.delete(accountId, timelineId)

    suspend fun clearForAccount(accountId: Long) = dao.deleteByAccount(accountId)
}
