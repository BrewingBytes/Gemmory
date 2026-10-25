package com.brewingbytes.gemmory.ads

import android.content.SharedPreferences

/** Map-backed SharedPreferences for JVM tests; only what PictureUnlocks uses is meaningful. */
class FakePrefs : SharedPreferences {
    val values = mutableMapOf<String, Any?>()

    /** A full disk: `commit()` writes nothing and returns false. */
    var commitFails = false

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?) = (values[key] as? Set<String>)?.toSet() ?: defValues

    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue

    override fun contains(key: String) = key in values

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()

        override fun putString(key: String, value: String?) = apply { pending[key] = value }

        override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values?.toSet() }

        override fun putInt(key: String, value: Int) = apply { pending[key] = value }

        override fun putLong(key: String, value: Long) = apply { pending[key] = value }

        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }

        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }

        override fun remove(key: String) = apply { pending[key] = null }

        override fun clear() = apply { values.clear() }

        override fun commit(): Boolean {
            if (commitFails) return false
            apply()
            return true
        }

        override fun apply() {
            for ((k, v) in pending) if (v == null) values.remove(k) else values[k] = v
        }
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
