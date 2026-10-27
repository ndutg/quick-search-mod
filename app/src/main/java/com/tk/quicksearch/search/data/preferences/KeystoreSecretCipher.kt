package com.tk.quicksearch.search.data.preferences

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts single string values; [associatedData] binds a ciphertext to the key it is stored under. */
internal interface SecretCipher {
    fun encrypt(
        plainText: String,
        associatedData: String,
    ): String

    /** Returns null when [encoded] cannot be decrypted (corrupt, tampered, or the key is gone). */
    fun decrypt(
        encoded: String,
        associatedData: String,
    ): String?
}

/**
 * AES-256-GCM with a non-exportable key held in the Android Keystore. Each value is stored as
 * Base64(IV + ciphertext + tag).
 */
internal class KeystoreSecretCipher private constructor(
    private val key: SecretKey,
) : SecretCipher {
    override fun encrypt(
        plainText: String,
        associatedData: String,
    ): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    override fun decrypt(
        encoded: String,
        associatedData: String,
    ): String? =
        runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > IV_SIZE_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, bytes, 0, IV_SIZE_BYTES))
            cipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(bytes, IV_SIZE_BYTES, bytes.size - IV_SIZE_BYTES), Charsets.UTF_8)
        }.getOrNull()

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "quicksearch_secure_preferences_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val IV_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128

        /** Loads the Keystore key, creating it on first use. Throws if the Keystore is unusable. */
        fun getOrCreate(): KeystoreSecretCipher {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            val key =
                existingKey
                    ?: KeyGenerator
                        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                        .apply {
                            init(
                                KeyGenParameterSpec
                                    .Builder(
                                        KEY_ALIAS,
                                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                    .setKeySize(KEY_SIZE_BITS)
                                    .build(),
                            )
                        }.generateKey()
            return KeystoreSecretCipher(key)
        }

        /** Removes a Keystore entry; used to drop the old security-crypto master key after migration. */
        fun deleteKeystoreEntry(alias: String) {
            runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias)
            }
        }
    }
}
