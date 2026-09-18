package com.tk.quicksearch.search.notificationHistory

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_history",
    indices = [
        // Collapses repeated updates of the same notification into one row.
        Index(value = ["notificationKey", "title", "text"], unique = true),
        Index(value = ["postTime"]),
        Index(value = ["packageName"]),
    ],
)
data class NotificationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationKey: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
)

internal fun NotificationHistoryEntity.toModel() =
    NotificationHistoryEntry(notificationKey, packageName, title, text, postTime)

internal fun NotificationHistoryEntry.toEntity() =
    NotificationHistoryEntity(
        notificationKey = key,
        packageName = packageName,
        title = title,
        text = text,
        postTime = postTime,
    )
