package me.masonasons.fastsm.data.db.entity

import androidx.room.Entity

/**
 * Remembered scroll position for a timeline — the status id the user was
 * looking at when the app last persisted their spot. Keyed by
 * (accountId, timelineId) so each account has its own cursor and adding a
 * tab back after closing it reuses the same row.
 *
 * [timelineId] is the [TimelineSpec.id] string (e.g. "home", "list:42",
 * "hashtag:kotlin"). Always-present tabs (Home, Notifications) aren't in
 * the `timelines` table but they do live here if the user has
 * "Remember timeline positions" enabled.
 */
@Entity(
    tableName = "timeline_positions",
    primaryKeys = ["accountId", "timelineId"],
)
data class TimelinePositionEntity(
    val accountId: Long,
    val timelineId: String,
    val statusId: String,
    val updatedAt: Long,
)
