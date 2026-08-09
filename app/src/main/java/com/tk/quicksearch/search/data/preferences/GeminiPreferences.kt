package com.tk.quicksearch.search.data.preferences

import android.content.Context
import android.util.Log
import com.tk.quicksearch.tools.aiSearch.GeminiModelCatalog

/**
 * Preferences for Gemini API-related settings such as API key and personal context.
 * Uses encrypted storage for sensitive data.
 */
class GeminiPreferences(
    context: Context,
) : BasePreferences(context) {
    // ============================================================================
    // Gemini API Preferences
    // ============================================================================

    fun getGeminiApiKey(): String? {
        val securePrefs =
            encryptedPrefs ?: run {
                Log.e("GeminiPreferences", "EncryptedSharedPreferences unavailable; Gemini API key not loaded")
                return null
            }

        return securePrefs.getString(BasePreferences.KEY_GEMINI_API_KEY, null)
    }

    fun setGeminiApiKey(key: String?) {
        val securePrefs =
            encryptedPrefs ?: run {
                Log.e("GeminiPreferences", "EncryptedSharedPreferences unavailable; Gemini API key not persisted")
                return
            }

        if (key.isNullOrBlank()) {
            securePrefs.edit().remove(BasePreferences.KEY_GEMINI_API_KEY).apply()
            return
        }

        val normalizedKey = key.trim()
        securePrefs.edit().putString(BasePreferences.KEY_GEMINI_API_KEY, normalizedKey).apply()
    }

    fun getPersonalContext(): String? {
        // Prefer encrypted storage when available
        val securePrefs = encryptedPrefs
        val encryptedValue = securePrefs?.getString(BasePreferences.KEY_GEMINI_PERSONAL_CONTEXT, null)
        if (!encryptedValue.isNullOrBlank()) return encryptedValue

        return prefs.getString(BasePreferences.KEY_GEMINI_PERSONAL_CONTEXT, null)
    }

    fun setPersonalContext(context: String?) {
        val trimmed = context?.trim()
        val securePrefs = encryptedPrefs

        if (trimmed.isNullOrEmpty()) {
            securePrefs?.edit()?.remove(BasePreferences.KEY_GEMINI_PERSONAL_CONTEXT)?.apply()
            prefs.edit().remove(BasePreferences.KEY_GEMINI_PERSONAL_CONTEXT).apply()
            return
        }

        if (securePrefs != null) {
            securePrefs.edit().putString(BasePreferences.KEY_GEMINI_PERSONAL_CONTEXT, trimmed).apply()
            // Keep plain storage clean if we can encrypt
            prefs.edit().remove(BasePreferences.KEY_GEMINI_PERSONAL_CONTEXT).apply()
        } else {
            prefs.edit().putString(BasePreferences.KEY_GEMINI_PERSONAL_CONTEXT, trimmed).apply()
        }
    }

    fun getGeminiModel(): String {
        val model = prefs.getString(BasePreferences.KEY_GEMINI_MODEL, null)?.trim()
        return model.takeUnless { it.isNullOrEmpty() } ?: GeminiModelCatalog.DEFAULT_MODEL_ID
    }

    fun setGeminiModel(modelId: String?) {
        val normalized = modelId?.trim()
        if (normalized.isNullOrEmpty()) {
            prefs.edit().remove(BasePreferences.KEY_GEMINI_MODEL).apply()
            return
        }
        prefs.edit().putString(BasePreferences.KEY_GEMINI_MODEL, normalized).apply()
    }

    fun isGeminiGroundingEnabled(): Boolean =
        prefs.getBoolean(
            BasePreferences.KEY_GEMINI_GROUNDING_ENABLED,
            GeminiModelCatalog.DEFAULT_GROUNDING_ENABLED,
        )

    fun setGeminiGroundingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(BasePreferences.KEY_GEMINI_GROUNDING_ENABLED, enabled).apply()
    }

    fun isThinkingEnabled(): Boolean =
        prefs.getBoolean(BasePreferences.KEY_GEMINI_THINKING_ENABLED, false)

    fun setThinkingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(BasePreferences.KEY_GEMINI_THINKING_ENABLED, enabled).apply()
    }
}
