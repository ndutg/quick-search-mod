package com.tk.quicksearch.search.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reversible stand-in for the Keystore cipher; fails like GCM when the associated data differs. */
class FakeSecretCipher : SecretCipher {
    var failEncrypt = false

    override fun encrypt(
        plainText: String,
        associatedData: String,
    ): String {
        check(!failEncrypt)
        return "enc[$associatedData]:${plainText.reversed()}"
    }

    override fun decrypt(
        encoded: String,
        associatedData: String,
    ): String? {
        val prefix = "enc[$associatedData]:"
        return if (encoded.startsWith(prefix)) encoded.removePrefix(prefix).reversed() else null
    }
}

class SecurePreferencesTest {
    private val backing = InMemorySharedPreferences()
    private val cipher = FakeSecretCipher()
    private val store = SecurePreferences(backing, cipher, logError = {})

    @Test
    fun storesOnlyCiphertextAndReadsBackPlainText() {
        store.edit().putString("openai_api_key", "sk-secret").apply()

        assertEquals("sk-secret", store.getString("openai_api_key", null))
        assertFalse(backing.values.values.any { it == "sk-secret" })
    }

    @Test
    fun ciphertextMovedToAnotherKeyIsRejected() {
        store.edit().putString("openai_api_key", "sk-secret").apply()
        backing.values["groq_api_key"] = backing.values["openai_api_key"]

        assertNull(store.getString("groq_api_key", null))
    }

    @Test
    fun undecryptableValueFallsBackToDefault() {
        backing.values["openai_api_key"] = "garbage"

        assertEquals("fallback", store.getString("openai_api_key", "fallback"))
    }

    @Test
    fun putNullAndRemoveDeleteTheValue() {
        store.edit().putString("a", "1").putString("b", "2").apply()
        store.edit().putString("a", null).remove("b").apply()

        assertFalse(store.contains("a"))
        assertFalse(store.contains("b"))
    }

    @Test
    fun commitReportsFailedEncryptionAndSavesNothing() {
        cipher.failEncrypt = true

        assertFalse(store.edit().putString("a", "1").commit())
        assertFalse(backing.contains("a"))
    }

    @Test
    fun internalKeysAreHiddenFromCallers() {
        backing.values[LegacySecretsMigration.MIGRATED_FLAG_KEY] = true
        store.edit().putString("a", "1").apply()

        assertEquals(mapOf("a" to "1"), store.all)
        assertFalse(store.contains(LegacySecretsMigration.MIGRATED_FLAG_KEY))
        assertTrue(store.contains("a"))
    }
}
