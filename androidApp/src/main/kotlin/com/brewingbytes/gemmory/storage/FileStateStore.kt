package com.brewingbytes.gemmory.storage

import com.brewingbytes.gemmory.policy.StateStore
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import java.io.File
import java.io.IOException

/**
 * A policy's state in one small file (temp + fsync + rename, eng E2). An unreadable or refused file loads as null,
 * so the policy starts fresh; a failed write is reported and the in-memory state carries on. Under
 * [StorageLayout.LEDGER], which the backup rules exclude.
 */
internal class FileStateStore<T>(
    private val file: File,
    private val encode: (T) -> ByteArray,
    private val decode: (ByteArray) -> Decoded<T>,
    private val reporter: NonFatalReporter = NonFatalReporter.Logcat,
    private val files: AtomicFiles = AtomicFiles(),
) : StateStore<T> {
    override fun load(): T? {
        val bytes = try {
            file.takeIf { it.isFile }?.readBytes()
        } catch (e: IOException) {
            reporter.report(e)
            null
        } ?: return null
        return when (val d = decode(bytes)) {
            is Decoded.Ok -> d.value
            is Decoded.Refused -> {
                reporter.report(IOException("${file.name}: ${d.reason}"))
                null
            }
        }
    }

    override fun save(value: T) {
        try {
            file.parentFile?.mkdirs()
            files.write(file, encode(value))
        } catch (e: IOException) {
            reporter.report(e)
        }
    }
}
