package com.tk.quicksearch.search.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Moves secrets from the deprecated androidx.security-crypto store into [SecurePreferences].
 * The old store is deleted only after every value has been written and read back, so an
 * interrupted or failed run leaves it intact and the next open tries again.
 */
internal object LegacySecretsMigration {
    const val MIGRATED_FLAG_KEY = SecurePreferences.INTERNAL_KEY_PREFIX + "legacy_secrets_migrated"

    enum class Outcome { ALREADY_DONE, NO_LEGACY_DATA, MIGRATED, RETRY_LATER }

    fun run(
        flagStore: SharedPreferences,
        target: SharedPreferences,
        legacyExists: Boolean,
        openLegacy: () -> SharedPreferences?,
        deleteLegacy: () -> Unit,
    ): Outcome {
        if (flagStore.getBoolean(MIGRATED_FLAG_KEY, false)) return Outcome.ALREADY_DONE
        if (!legacyExists) {
            flagStore.edit().putBoolean(MIGRATED_FLAG_KEY, true).apply()
            return Outcome.NO_LEGACY_DATA
        }

        val legacy = runCatching(openLegacy).getOrNull() ?: return Outcome.RETRY_LATER
        val legacyValues = runCatching { legacy.all }.getOrNull() ?: return Outcome.RETRY_LATER
        // A value saved in the new store since an earlier failed attempt is newer; keep it.
        val toCopy =
            legacyValues
                .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
                .filterNot { (key, _) -> target.contains(key) }

        if (toCopy.isNotEmpty()) {
            val editor = target.edit()
            toCopy.forEach { (key, value) -> editor.putString(key, value) }
            if (!editor.commit()) return Outcome.RETRY_LATER
            if (toCopy.any { (key, value) -> target.getString(key, null) != value }) {
                return Outcome.RETRY_LATER
            }
        }

        if (!flagStore.edit().putBoolean(MIGRATED_FLAG_KEY, true).commit()) return Outcome.RETRY_LATER
        deleteLegacy()
        return Outcome.MIGRATED
    }
}

/** Read-only access to the old store; kept only so existing installs can be migrated. */
// TODO: Remove this migration, LegacyEncryptedPreferences and the security-crypto dependency once most
// users have updated past the release that moved secrets to the Keystore. Users who skip that release
// will need to re-enter their API keys.
@Suppress("DEPRECATION")
internal object LegacyEncryptedPreferences {
    // security-crypto's default master key alias, used by MasterKey.Builder.
    private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"

    fun exists(context: Context): Boolean =
        File(context.applicationInfo.dataDir, "shared_prefs/${BasePreferences.ENCRYPTED_PREFS_NAME}.xml").exists()

    fun open(context: Context): SharedPreferences {
        val masterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        return EncryptedSharedPreferences.create(
            context,
            BasePreferences.ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun delete(context: Context) {
        context.deleteSharedPreferences(BasePreferences.ENCRYPTED_PREFS_NAME)
        KeystoreSecretCipher.deleteKeystoreEntry(MASTER_KEY_ALIAS)
    }
}
