package me.masonasons.fastsm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import me.masonasons.fastsm.data.db.dao.AccountDao
import me.masonasons.fastsm.data.db.dao.TimelineDao
import me.masonasons.fastsm.data.db.dao.TimelinePositionDao
import me.masonasons.fastsm.data.db.entity.AccountEntity
import me.masonasons.fastsm.data.db.entity.TimelineEntity
import me.masonasons.fastsm.data.db.entity.TimelinePositionEntity

@Database(
    entities = [AccountEntity::class, TimelineEntity::class, TimelinePositionEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class FastSmDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun timelineDao(): TimelineDao
    abstract fun timelinePositionDao(): TimelinePositionDao

    companion object {
        private const val NAME = "fastsm.db"

        fun create(context: Context): FastSmDatabase =
            Room.databaseBuilder(context, FastSmDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
