package com.tk.quicksearch.search.data.preferences

import com.tk.quicksearch.search.data.preferences.LegacySecretsMigration.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySecretsMigrationTest {
    private val backing = InMemorySharedPreferences()
    private val target = SecurePreferences(backing, FakeSecretCipher(), logError = {})
    private val legacy =
        InMemorySharedPreferences().apply {
            values["openai_api_key"] = "sk-openai"
            values["gemini_personal_context"] = "I live in Toronto"
            values["custom_llm_providers"] = """[{"id":"x","apiKey":"k"}]"""
        }
    private var legacyDeleted = false

    private fun migrate(
        legacyExists: Boolean = true,
        openLegacy: () -> InMemorySharedPreferences? = { legacy },
    ) = LegacySecretsMigration.run(
        flagStore = backing,
        target = target,
        legacyExists = legacyExists,
        openLegacy = openLegacy,
        deleteLegacy = { legacyDeleted = true },
    )

    private fun migrated() = backing.getBoolean(LegacySecretsMigration.MIGRATED_FLAG_KEY, false)

    @Test
    fun copiesEveryValueThenDeletesLegacyStore() {
        assertEquals(Outcome.MIGRATED, migrate())

        assertEquals(legacy.values, target.all)
        assertTrue(migrated())
        assertTrue(legacyDeleted)
    }

    @Test
    fun runsOnlyOnce() {
        migrate()
        legacyDeleted = false

        assertEquals(Outcome.ALREADY_DONE, migrate(openLegacy = { error("must not reopen") }))
        assertFalse(legacyDeleted)
    }

    @Test
    fun freshInstallJustMarksDone() {
        assertEquals(Outcome.NO_LEGACY_DATA, migrate(legacyExists = false, openLegacy = { error("must not open") }))

        assertTrue(migrated())
        assertFalse(legacyDeleted)
    }

    @Test
    fun legacyStoreThatCannotOpenIsKeptForRetry() {
        assertEquals(Outcome.RETRY_LATER, migrate(openLegacy = { throw SecurityException("keystore") }))
        assertEquals(Outcome.RETRY_LATER, migrate(openLegacy = { null }))

        assertFalse(migrated())
        assertFalse(legacyDeleted)
    }

    @Test
    fun failedWriteKeepsLegacyStoreAndRetriesLater() {
        backing.failCommits = true
        assertEquals(Outcome.RETRY_LATER, migrate())
        assertFalse(migrated())
        assertFalse(legacyDeleted)

        backing.failCommits = false
        assertEquals(Outcome.MIGRATED, migrate())
        assertEquals(legacy.values, target.all)
    }

    @Test
    fun keyEnteredAfterAFailedAttemptIsNotOverwritten() {
        target.edit().putString("openai_api_key", "sk-newer").apply()

        assertEquals(Outcome.MIGRATED, migrate())

        assertEquals("sk-newer", target.getString("openai_api_key", null))
        assertEquals("I live in Toronto", target.getString("gemini_personal_context", null))
    }

    @Test
    fun emptyLegacyStoreIsStillCleanedUp() {
        legacy.values.clear()

        assertEquals(Outcome.MIGRATED, migrate())
        assertTrue(legacyDeleted)
    }
}
