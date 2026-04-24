package me.masonasons.fastsm.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: adds the `timelines` table (user-configurable pager tabs).
 * Additive only — no account data touched.
 *
 * SQL taken verbatim from `app/schemas/.../2.json` so a runtime schema-diff
 * check can't reject this migration.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `timelines` (" +
                "`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`instance` TEXT, " +
                "`acct` TEXT, " +
                "`userId` TEXT, " +
                "`label` TEXT, " +
                "`localOnly` INTEGER, " +
                "`position` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_timelines_accountId` ON `timelines` (`accountId`)"
        )
    }
}

/**
 * v2 → v3: adds `timeline_positions` — per-timeline remembered scroll
 * position. Independent of the server-side home marker sync; used when the
 * user enables "Remember timeline positions" so every tab restores its
 * last-viewed post across app restarts.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `timeline_positions` (" +
                "`accountId` INTEGER NOT NULL, " +
                "`timelineId` TEXT NOT NULL, " +
                "`statusId` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`accountId`, `timelineId`))"
        )
    }
}

internal val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
