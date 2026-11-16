package com.brewingbytes.gemmory.storage

/** A [KeyValueStore] in memory, written at once. */
internal class MemoryStore(override val name: String) : KeyValueStore {
    private val values = mutableMapOf<String, Any>()

    override val keys: Set<String> get() = values.keys.toSet()

    override fun contains(key: String) = key in values

    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default

    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default

    override fun getLong(key: String, default: Long) = values[key] as? Long ?: default

    override fun getString(key: String, default: String?) = values[key] as? String ?: default

    @Suppress("UNCHECKED_CAST") // Only sets of strings are put under a key read this way.
    override fun getStringSet(key: String, default: Set<String>?) = values[key] as? Set<String> ?: default

    override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) {
        object : KeyValueStore.Editor {
            override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }

            override fun putInt(key: String, value: Int) = apply { values[key] = value }

            override fun putLong(key: String, value: Long) = apply { values[key] = value }

            override fun putString(key: String, value: String?) = apply { if (value == null) values.remove(key) else values[key] = value }

            override fun putStringSet(key: String, value: Set<String>?) = apply { if (value == null) values.remove(key) else values[key] = value.toSet() }

            override fun remove(key: String) = apply { values.remove(key) }

            override fun clear() = apply { values.clear() }
        }.block()
    }
}
