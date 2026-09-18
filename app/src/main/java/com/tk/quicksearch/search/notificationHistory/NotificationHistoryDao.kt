package com.tk.quicksearch.search.notificationHistory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {
    @Query("SELECT * FROM notification_history ORDER BY postTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NotificationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrIgnore(entry: NotificationHistoryEntity): Long

    @Query(
        "UPDATE notification_history SET postTime = :postTime " +
            "WHERE notificationKey = :notificationKey AND title = :title AND text = :text " +
            "AND postTime < :postTime",
    )
    fun bumpPostTime(
        notificationKey: String,
        title: String,
        text: String,
        postTime: Long,
    )

    @Query(
        "DELETE FROM notification_history WHERE id NOT IN " +
            "(SELECT id FROM notification_history ORDER BY postTime DESC LIMIT :maxEntries)",
    )
    fun trimTo(maxEntries: Int)

    @Query("DELETE FROM notification_history WHERE packageName = :packageName")
    fun deleteByPackage(packageName: String)

    @Query("DELETE FROM notification_history")
    fun deleteAll()

    /** Inserts new rows, refreshes the time of already-seen ones, and enforces the size cap. */
    @Transaction
    fun record(
        entries: List<NotificationHistoryEntity>,
        maxEntries: Int,
    ) {
        entries.forEach { entry ->
            if (insertOrIgnore(entry) == -1L) {
                bumpPostTime(entry.notificationKey, entry.title, entry.text, entry.postTime)
            }
        }
        trimTo(maxEntries)
    }
}
