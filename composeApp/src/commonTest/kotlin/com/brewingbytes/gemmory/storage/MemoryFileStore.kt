package com.brewingbytes.gemmory.storage

/**
 * A [FileStore] in memory: files by path, folders as a set. [failWrites] makes every write throw, a full disk when
 * [diskFull]. Folder rules follow `AndroidFileStore`: a write needs its folder, [moveDir] refuses an existing target.
 */
internal class MemoryFileStore : FileStore {
    val files = mutableMapOf<String, ByteArray>()
    val dirs = mutableSetOf<String>()
    var failWrites = false
    var diskFull = false

    private fun parentOf(path: String) = path.substringBeforeLast('/', "")

    private fun under(path: String, root: String) = path == root || path.startsWith("$root/")

    override fun read(path: String): ByteArray? = files[path]?.copyOf()

    override fun writeAtomic(path: String, bytes: ByteArray) {
        if (failWrites) throw FileStoreException("write $path failed", diskFull)
        val parent = parentOf(path)
        if (parent.isNotEmpty() && parent !in dirs) throw FileStoreException("no folder $parent", diskFull = false)
        files[path] = bytes.copyOf()
    }

    override fun exists(path: String): Boolean = path in files || path in dirs

    override fun isDirectory(path: String): Boolean = path in dirs

    override fun list(path: String): List<String> =
        (files.keys + dirs).filter { parentOf(it) == path }.map { it.substringAfterLast('/') }.sorted()

    override fun delete(path: String): Boolean = files.remove(path) != null

    override fun mkdirs(path: String): Boolean {
        if (path in files) return false
        var p = path
        while (p.isNotEmpty()) {
            dirs += p
            p = parentOf(p)
        }
        return true
    }

    override fun moveDir(from: String, to: String) {
        if (exists(to)) throw FileStoreException("exists: $to", diskFull = false)
        if (from !in dirs) throw FileStoreException("no folder $from", diskFull = false)
        mkdirs(parentOf(to))
        for (d in dirs.filter { under(it, from) }) {
            dirs -= d
            dirs += to + d.removePrefix(from)
        }
        for (f in files.keys.filter { under(it, from) }) files[to + f.removePrefix(from)] = files.remove(f)!!
    }

    override fun copy(from: String, to: String) = writeAtomic(to, files[from] ?: throw FileStoreException("no file $from", diskFull = false))

    override fun deleteRecursive(path: String): Boolean {
        files.keys.removeAll { under(it, path) }
        dirs.removeAll { under(it, path) }
        return true
    }

    override fun syncDir(path: String) = Unit
}
