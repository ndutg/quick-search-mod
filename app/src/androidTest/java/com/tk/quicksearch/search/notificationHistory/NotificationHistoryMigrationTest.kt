package com.tk.quicksearch.search.notificationHistory

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationHistoryMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            NotificationHistoryDatabase::class.java,
        )

    @Test
    fun migrate1To2KeepsRowsAndCountsThemAsCandidates() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO notification_history (notificationKey, packageName, title, text, postTime) " +
                    "VALUES ('key', 'com.bank', 'Bank', 'Rs 500 debited', 1000)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, NotificationHistoryDatabase.MIGRATION_1_2)

        migrated.query("SELECT notificationKey, appNotificationCandidate FROM notification_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("key", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(1, cursor.count)
        }
    }

    private companion object {
        const val TEST_DB = "notification-history-migration-test"
    }
}
