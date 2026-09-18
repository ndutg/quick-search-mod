package com.tk.quicksearch.search.data.preferences

import android.content.Context
import android.util.Log
import com.tk.quicksearch.tools.aiSearch.TavilyWebSearchMode

/** Preferences for Tavily web search. Uses encrypted storage for the API key. */
class TavilyPreferences(
    context: Context,
) : BasePreferences(context) {

    fun getApiKey(): String? {
        val securePrefs =
            encryptedPrefs ?: run {
                Log.e("TavilyPreferences", "EncryptedSharedPreferences unavailable; Tavily API key not loaded")
                return null
            }
        return securePrefs.getString(BasePreferences.KEY_TAVILY_API_KEY, null)?.takeIf { it.isNotBlank() }
    }

    fun setApiKey(key: String?) {
        val securePrefs =
            encryptedPrefs ?: run {
                Log.e("TavilyPreferences", "EncryptedSharedPreferences unavailable; Tavily API key not persisted")
                return
            }
        if (key.isNullOrBlank()) {
            securePrefs.edit().remove(BasePreferences.KEY_TAVILY_API_KEY).apply()
            return
        }
        securePrefs.edit().putString(BasePreferences.KEY_TAVILY_API_KEY, key.trim()).apply()
    }

    fun getWebSearchMode(): TavilyWebSearchMode =
        TavilyWebSearchMode.fromStorageValue(
            prefs.getString(BasePreferences.KEY_TAVILY_WEB_SEARCH_MODE, null),
        )

    fun setWebSearchMode(mode: TavilyWebSearchMode) {
        prefs.edit().putString(BasePreferences.KEY_TAVILY_WEB_SEARCH_MODE, mode.storageValue).apply()
    }
}
