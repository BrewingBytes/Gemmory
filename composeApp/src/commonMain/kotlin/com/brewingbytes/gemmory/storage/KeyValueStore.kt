package com.brewingbytes.gemmory.storage

/**
 * One key-value file of [StorageLayout.Prefs], for shared code (eng D7, iOS Stage 1c). On Android it is the
 * SharedPreferences file of the same name, read and written as it is (no data moves: `PrefsKeysTest` pins every file
 * and key, and `KeyValueSeedTest` reads today's files back through it); on iOS an `NSUserDefaults` suite of that name.
 * The getters and the editor mirror SharedPreferences', so a class moves onto it line for line.
 */
interface KeyValueStore {
    /** The file's name in [StorageLayout.Prefs]. */
    val name: String

    /** Every key in the file now. */
    val keys: Set<String>

    fun contains(key: String): Boolean

    fun getBoolean(key: String, default: Boolean): Boolean

    fun getInt(key: String, default: Int): Int

    fun getLong(key: String, default: Long): Long

    fun getString(key: String, default: String?): String?

    fun getStringSet(key: String, default: Set<String>?): Set<String>?

    /**
     * Applies [block]'s changes together. [commit] writes them to disk before returning (SharedPreferences'
     * `commit()`), for state a process death must not lose; otherwise they are written in the background (`apply()`).
     */
    fun edit(commit: Boolean = false, block: Editor.() -> Unit)

    /** The changes of one [edit]. */
    interface Editor {
        fun putBoolean(key: String, value: Boolean): Editor

        fun putInt(key: String, value: Int): Editor

        fun putLong(key: String, value: Long): Editor

        /** A null [value] removes [key], as SharedPreferences does. */
        fun putString(key: String, value: String?): Editor

        fun putStringSet(key: String, value: Set<String>?): Editor

        fun remove(key: String): Editor

        fun clear(): Editor
    }
}
