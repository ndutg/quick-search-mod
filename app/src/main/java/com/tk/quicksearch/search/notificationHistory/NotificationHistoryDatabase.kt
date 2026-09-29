package com.tk.quicksearch.search.notificationHistory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NotificationHistoryEntity::class], version = 2, exportSchema = true)
abstract class NotificationHistoryDatabase : RoomDatabase() {
    abstract fun notificationHistoryDao(): NotificationHistoryDao

    companion object {
        const val FILE_NAME = "notification_history.db"

        internal val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE notification_history ADD COLUMN appNotificationCandidate INTEGER NOT NULL DEFAULT 1",
                    )
                }
            }

        @Volatile private var instance: NotificationHistoryDatabase? = null

        fun get(context: Context): NotificationHistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotificationHistoryDatabase::class.java,
                    FILE_NAME,
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
