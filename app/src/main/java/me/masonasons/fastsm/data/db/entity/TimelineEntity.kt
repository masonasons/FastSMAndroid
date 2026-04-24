package me.masonasons.fastsm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-added timeline tab on a specific account. Home and Notifications
 * are implicit and never stored — only closable timelines live here.
 *
 * [kind] drives how to interpret the other columns:
 *  - "local", "federated" → no extra fields
 *  - "remote-instance" → [instance], [localOnly]
 *  - "remote-user" → [instance], [acct]
 *  - "user-posts" → [userId], [label]
 */
@Entity(
    tableName = "timelines",
    indices = [Index(value = ["accountId"])],
)
data class TimelineEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val accountId: Long,
    val kind: String,
    val instance: String? = null,
    val acct: String? = null,
    val userId: String? = null,
    val label: String? = null,
    val localOnly: Boolean? = null,
    val position: Int,
    val createdAt: Long,
)
