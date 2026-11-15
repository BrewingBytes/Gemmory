package com.brewingbytes.gemmory.storage

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * [KeyValueStore] over the SharedPreferences file [name], the same file and keys as before T64 (eng D7: no data
 * moves). [prefs] is that file; [of] opens it from a context.
 */
class SharedPrefsStore(override val name: String, private val prefs: SharedPreferences) : KeyValueStore {
    override val keys: Set<String> get() = prefs.all.keys.toSet()

    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)

    override fun getStringSet(key: String, default: Set<String>?): Set<String>? = prefs.getStringSet(key, default)?.toSet()

    override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) {
        val editor = prefs.edit()
        Editor(editor).block()
        if (commit) editor.commit() else editor.apply()
    }

    private class Editor(private val e: SharedPreferences.Editor) : KeyValueStore.Editor {
        override fun putBoolean(key: String, value: Boolean) = apply { e.putBoolean(key, value) }

        override fun putInt(key: String, value: Int) = apply { e.putInt(key, value) }

        override fun putLong(key: String, value: Long) = apply { e.putLong(key, value) }

        override fun putString(key: String, value: String?) = apply { e.putString(key, value) }

        override fun putStringSet(key: String, value: Set<String>?) = apply { e.putStringSet(key, value) }

        override fun remove(key: String) = apply { e.remove(key) }

        override fun clear() = apply { e.clear() }
    }

    companion object {
        /** The app's file [name] of [StorageLayout.Prefs]. */
        fun of(context: Context, name: String): SharedPrefsStore =
            SharedPrefsStore(name, context.getSharedPreferences(name, Context.MODE_PRIVATE))
    }
}

/**
 * [FileStore] over [root] (`filesDir`), writing through [AtomicFiles]: the same paths, bytes and crash safety as
 * before T64 (eng D8). [writer] lets tests fail a write, a full disk included.
 */
class AndroidFileStore(private val root: File, writer: SyncedWriter = SyncedWriter.Default) : FileStore {
    private val atomic = AtomicFiles(writer)

    private fun file(path: String): File {
        require(path.isNotEmpty() && !path.startsWith("/") && path.split('/').none { it == ".." }) { "not a store path: $path" }
        return File(root, path)
    }

    override fun read(path: String): ByteArray? = wrap("read $path") {
        val f = file(path)
        if (f.isFile) f.readBytes() else null
    }

    override fun writeAtomic(path: String, bytes: ByteArray) = wrap("write $path") { atomic.write(file(path), bytes) }

    override fun exists(path: String): Boolean = file(path).exists()

    override fun list(path: String): List<String> = file(path).list()?.sorted().orEmpty()

    override fun delete(path: String): Boolean = file(path).let { it.isFile && it.delete() }

    override fun mkdirs(path: String): Boolean = file(path).let { it.isDirectory || it.mkdirs() }

    override fun moveDir(from: String, to: String) = wrap("move $from") {
        val target = file(to)
        if (target.exists()) throw IOException("exists: $to")
        Files.move(file(from).toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        AtomicFiles.syncDir(target.parentFile)
    }

    override fun copy(from: String, to: String) = wrap("copy $from") {
        val bytes = file(from).readBytes()
        atomic.write(file(to), bytes)
    }

    override fun deleteRecursive(path: String): Boolean = file(path).let { it.deleteRecursively() || !it.exists() }

    private inline fun <T> wrap(what: String, block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw FileStoreException("$what failed: ${e::class.simpleName}", AtomicFiles.isDiskFull(e), e)
    }
}
