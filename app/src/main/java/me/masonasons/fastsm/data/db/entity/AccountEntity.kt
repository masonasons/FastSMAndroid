package me.masonasons.fastsm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["platform", "userId"], unique = true)],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String,
    val instance: String,
    val userId: String,
    val acct: String,
    val displayName: String,
    val avatar: String?,
    val clientId: String?,
    val clientSecret: String?,
    val createdAt: Long,
)
