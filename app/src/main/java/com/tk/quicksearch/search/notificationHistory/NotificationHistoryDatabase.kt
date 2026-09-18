package com.tk.quicksearch.search.notificationHistory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NotificationHistoryEntity::class], version = 1, exportSchema = true)
abstract class NotificationHistoryDatabase : RoomDatabase() {
    abstract fun notificationHistoryDao(): NotificationHistoryDao

    companion object {
        const val FILE_NAME = "notification_history.db"

        @Volatile private var instance: NotificationHistoryDatabase? = null

        fun get(context: Context): NotificationHistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotificationHistoryDatabase::class.java,
                    FILE_NAME,
                ).build().also { instance = it }
            }
    }
}
