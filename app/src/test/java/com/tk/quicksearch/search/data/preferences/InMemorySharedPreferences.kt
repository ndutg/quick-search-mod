package com.tk.quicksearch.search.data.preferences

import android.content.SharedPreferences

/** Map-backed [SharedPreferences] for JVM tests. Set [failCommits] to simulate a failed disk write. */
class InMemorySharedPreferences : SharedPreferences {
    val values = linkedMapOf<String, Any?>()
    var failCommits = false

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? = values[key] as? MutableSet<String> ?: defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = values[key] as? Int ?: defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = values[key] as? Long ?: defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = values[key] as? Float ?: defValue

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private var clear = false

        private fun put(
            key: String?,
            value: Any?,
        ): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putString(
            key: String?,
            value: String?,
        ) = put(key, value)

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ) = put(key, values)

        override fun putInt(
            key: String?,
            value: Int,
        ) = put(key, value)

        override fun putLong(
            key: String?,
            value: Long,
        ) = put(key, value)

        override fun putFloat(
            key: String?,
            value: Float,
        ) = put(key, value)

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ) = put(key, value)

        override fun remove(key: String?) = put(key, null)

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            if (failCommits) return false
            apply()
            return true
        }

        override fun apply() {
            if (clear) values.clear()
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }
    }
}
