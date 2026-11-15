package com.brewingbytes.gemmory.storage

import com.brewingbytes.gemmory.policy.StateStore
import com.brewingbytes.gemmory.save.Decoded

/**
 * A policy's state in one small file of [store] at [path] (temp + fsync + rename, eng E2). An unreadable or refused
 * file loads as null, so the policy starts fresh; a failed write goes to [report] and the in-memory state carries on.
 * The ledger's files live under [StorageLayout.LEDGER], which is never backed up. Common since T64, on [FileStore].
 */
class FileStateStore<T>(
    private val store: FileStore,
    private val path: String,
    private val encode: (T) -> ByteArray,
    private val decode: (ByteArray) -> Decoded<T>,
    private val report: (Throwable) -> Unit = {},
) : StateStore<T> {
    override fun load(): T? {
        val bytes = try {
            store.read(path)
        } catch (e: FileStoreException) {
            report(e)
            null
        } ?: return null
        return when (val d = decode(bytes)) {
            is Decoded.Ok -> d.value
            is Decoded.Refused -> {
                report(FileStoreException("${path.substringAfterLast('/')}: ${d.reason}", diskFull = false))
                null
            }
        }
    }

    override fun save(value: T) {
        try {
            if ('/' in path) store.mkdirs(path.substringBeforeLast('/'))
            store.writeAtomic(path, encode(value))
        } catch (e: FileStoreException) {
            report(e)
        }
    }
}
