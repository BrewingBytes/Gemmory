package com.brewingbytes.gemmory.storage

/**
 * The app's files for shared code (eng D8, OV-4; iOS Stage 1c). Paths are relative to the app's files folder and use
 * `/` ([StorageLayout] names them). On Android it is today's `AtomicFiles` code over `filesDir`, the same paths and
 * bytes; on iOS (Stage 2) POSIX writes with `F_FULLFSYNC` under Application Support, with [StorageLayout.neverBackedUp]
 * excluded from backup.
 *
 * Failures throw [FileStoreException]; [FileStoreException.diskFull] tells a full disk (the save-fail copy) from
 * anything else.
 */
interface FileStore {
    /** The file's bytes, or null when there is no file at [path]. */
    fun read(path: String): ByteArray?

    /**
     * Writes [bytes] to [path] so that a reader sees the old file or the new one, never a mix (eng E2): to a temp file
     * next to it, fsync, rename over [path], then sync the folder. Its folder must exist ([mkdirs]).
     */
    fun writeAtomic(path: String, bytes: ByteArray)

    fun exists(path: String): Boolean

    /** The names in the folder at [path], sorted; empty when it doesn't exist. */
    fun list(path: String): List<String>

    /** Deletes the file at [path]; true when there was one. */
    fun delete(path: String): Boolean

    /** Creates the folder at [path] and its parents; true when it exists afterwards. */
    fun mkdirs(path: String): Boolean

    /**
     * Renames the folder [from] to [to] in one step, so [to] appears whole or not at all (a staged canvas folder,
     * `CanvasRepository.create`). [to] must not exist.
     */
    fun moveDir(from: String, to: String)

    /** Copies the file [from] to [to] through [writeAtomic]. */
    fun copy(from: String, to: String)

    /** Deletes [path] and everything under it; true when nothing is left there. */
    fun deleteRecursive(path: String): Boolean
}

/** A [FileStore] operation that failed; [diskFull] when the disk had no room (ENOSPC). */
class FileStoreException(message: String, val diskFull: Boolean, cause: Throwable? = null) : Exception(message, cause)
