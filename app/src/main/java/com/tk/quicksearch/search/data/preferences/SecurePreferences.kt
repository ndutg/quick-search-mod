package com.tk.quicksearch.search.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * String-only [SharedPreferences] whose values are encrypted with [cipher] before they reach
 * [backing]. Key names stay readable; they only identify which provider a value belongs to.
 * Non-string reads and writes are unsupported because nothing sensitive uses them.
 */
internal class SecurePreferences(
    private val backing: SharedPreferences,
    private val cipher: SecretCipher,
    private val logError: (String) -> Unit = { Log.e(TAG, it) },
) : SharedPreferences {
    override fun getString(
        key: String?,
        defValue: String?,
    ): String? {
        if (key == null) return defValue
        val encoded = backing.getString(key, null) ?: return defValue
        decryptedCache[cacheKey(key, encoded)]?.let { return it }
        val decrypted = cipher.decrypt(encoded, key)
        if (decrypted == null) {
            logError("Could not decrypt stored value for $key")
            return defValue
        }
        decryptedCache[cacheKey(key, encoded)] = decrypted
        return decrypted
    }

    override fun getAll(): Map<String, *> =
        backing.all.keys
            .filterNot { it.startsWith(INTERNAL_KEY_PREFIX) }
            .mapNotNull { key -> getString(key, null)?.let { key to it } }
            .toMap()

    override fun contains(key: String?): Boolean =
        key != null && !key.startsWith(INTERNAL_KEY_PREFIX) && backing.contains(key)

    override fun edit(): SharedPreferences.Editor = Editor(backing.edit())

    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? = unsupported()

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = unsupported()

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = unsupported()

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = unsupported()

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = unsupported()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) =
        unsupported()

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) =
        unsupported()

    private inner class Editor(
        private val backingEditor: SharedPreferences.Editor,
    ) : SharedPreferences.Editor {
        private var encryptionFailed = false

        override fun putString(
            key: String?,
            value: String?,
        ): SharedPreferences.Editor {
            if (key == null) return this
            if (value == null) {
                backingEditor.remove(key)
                return this
            }
            val encoded = runCatching { cipher.encrypt(value, key) }.getOrNull()
            if (encoded == null) {
                logError("Could not encrypt value for $key; it will not be saved")
                encryptionFailed = true
                return this
            }
            decryptedCache[cacheKey(key, encoded)] = value
            backingEditor.putString(key, encoded)
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            backingEditor.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            backingEditor.clear()
            return this
        }

        override fun commit(): Boolean = backingEditor.commit() && !encryptionFailed

        override fun apply() = backingEditor.apply()

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = unsupported()

        override fun putInt(
            key: String?,
            value: Int,
        ): SharedPreferences.Editor = unsupported()

        override fun putLong(
            key: String?,
            value: Long,
        ): SharedPreferences.Editor = unsupported()

        override fun putFloat(
            key: String?,
            value: Float,
        ): SharedPreferences.Editor = unsupported()

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ): SharedPreferences.Editor = unsupported()
    }

    companion object {
        private const val TAG = "SecurePreferences"

        /** Keys with this prefix hold the store's own bookkeeping and are hidden from callers. */
        const val INTERNAL_KEY_PREFIX = "__qs_"

        // Keyed by preference key plus ciphertext, so a value rewritten through any instance is never
        // served stale and a ciphertext copied under another key still has to pass decryption.
        // Avoids a Keystore round trip on every read of the same key.
        private val decryptedCache = ConcurrentHashMap<String, String>()

        private fun cacheKey(
            key: String,
            encoded: String,
        ) = "$key\u0000$encoded"

        private fun unsupported(): Nothing = throw UnsupportedOperationException("SecurePreferences stores strings only")
    }
}

/** Process-wide entry point for the encrypted store; migrates the old security-crypto store on first open. */
internal object SecurePreferencesStore {
    private const val TAG = "SecurePreferences"

    @Volatile
    private var instance: SharedPreferences? = null

    /** Returns null when the Keystore is unusable; callers then neither read nor persist secrets. */
    fun open(context: Context): SharedPreferences? {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val appContext = context.applicationContext
            val opened =
                runCatching {
                    val backing =
                        appContext.getSharedPreferences(
                            BasePreferences.SECURE_PREFS_NAME,
                            Context.MODE_PRIVATE,
                        )
                    val store = SecurePreferences(backing, KeystoreSecretCipher.getOrCreate())
                    val outcome =
                        LegacySecretsMigration.run(
                            flagStore = backing,
                            target = store,
                            legacyExists = LegacyEncryptedPreferences.exists(appContext),
                            openLegacy = { LegacyEncryptedPreferences.open(appContext) },
                            deleteLegacy = { LegacyEncryptedPreferences.delete(appContext) },
                        )
                    if (outcome == LegacySecretsMigration.Outcome.RETRY_LATER) {
                        Log.w(TAG, "Legacy encrypted preferences not migrated yet; will retry")
                    }
                    store
                }.onFailure { Log.e(TAG, "Failed to open secure preferences", it) }
                    .getOrNull()
            instance = opened
            return opened
        }
    }
}
