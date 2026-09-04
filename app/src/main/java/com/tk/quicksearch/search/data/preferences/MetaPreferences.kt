package com.tk.quicksearch.search.data.preferences

import android.content.Context
import android.util.Log
import com.tk.quicksearch.tools.aiSearch.MetaModelCatalog

/** Preferences for Meta Model API settings. Uses encrypted storage for the API key. */
class MetaPreferences(
    context: Context,
) : BasePreferences(context) {
    fun getApiKey(): String? {
        val securePrefs =
            encryptedPrefs ?: run {
                Log.e("MetaPreferences", "EncryptedSharedPreferences unavailable; Meta AI API key not loaded")
                return null
            }
        return securePrefs.getString(BasePreferences.KEY_META_API_KEY, null)?.takeIf { it.isNotBlank() }
    }

    fun setApiKey(key: String?) {
        val securePrefs =
            encryptedPrefs ?: run {
                Log.e("MetaPreferences", "EncryptedSharedPreferences unavailable; Meta AI API key not persisted")
                return
            }
        if (key.isNullOrBlank()) {
            securePrefs.edit().remove(BasePreferences.KEY_META_API_KEY).apply()
        } else {
            securePrefs.edit().putString(BasePreferences.KEY_META_API_KEY, key.trim()).apply()
        }
    }

    fun getPersonalContext(): String? =
        encryptedPrefs?.getString(BasePreferences.KEY_META_PERSONAL_CONTEXT, null)?.takeIf { it.isNotBlank() }
            ?: prefs.getString(BasePreferences.KEY_META_PERSONAL_CONTEXT, null)?.takeIf { it.isNotBlank() }

    fun setPersonalContext(value: String?) {
        val trimmed = value?.trim()
        if (trimmed.isNullOrEmpty()) {
            encryptedPrefs?.edit()?.remove(BasePreferences.KEY_META_PERSONAL_CONTEXT)?.apply()
            prefs.edit().remove(BasePreferences.KEY_META_PERSONAL_CONTEXT).apply()
        } else {
            encryptedPrefs?.edit()?.putString(BasePreferences.KEY_META_PERSONAL_CONTEXT, trimmed)?.apply()
                ?: prefs.edit().putString(BasePreferences.KEY_META_PERSONAL_CONTEXT, trimmed).apply()
        }
    }

    fun getModel(): String =
        prefs.getString(BasePreferences.KEY_META_MODEL, null)?.trim().takeUnless { it.isNullOrEmpty() }
            ?: MetaModelCatalog.DEFAULT_MODEL_ID

    fun setModel(modelId: String?) {
        val normalized = modelId?.trim()
        if (normalized.isNullOrEmpty()) {
            prefs.edit().remove(BasePreferences.KEY_META_MODEL).apply()
        } else {
            prefs.edit().putString(BasePreferences.KEY_META_MODEL, normalized).apply()
        }
    }

    fun isGroundingEnabled(): Boolean =
        prefs.getBoolean(BasePreferences.KEY_META_GROUNDING_ENABLED, MetaModelCatalog.DEFAULT_GROUNDING_ENABLED)

    fun setGroundingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(BasePreferences.KEY_META_GROUNDING_ENABLED, enabled).apply()
    }

    fun isThinkingEnabled(): Boolean =
        prefs.getBoolean(BasePreferences.KEY_META_THINKING_ENABLED, false)

    fun setThinkingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(BasePreferences.KEY_META_THINKING_ENABLED, enabled).apply()
    }
}
