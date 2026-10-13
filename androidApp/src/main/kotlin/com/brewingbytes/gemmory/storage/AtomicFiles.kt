package com.brewingbytes.gemmory.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Writes [bytes] to [file] and fsyncs it. A parameter of [AtomicFiles] so tests can fail it (e.g. disk full). */
internal fun interface SyncedWriter {
    @Throws(IOException::class)
    fun write(file: File, bytes: ByteArray)

    companion object {
        val Default = SyncedWriter { file, bytes ->
            FileOutputStream(file).use {
                it.write(bytes)
                it.fd.sync()
            }
        }
    }
}

/** Temp + fsync + rename writes (eng E2): a reader sees the old file or the new one, never a torn mix. */
internal class AtomicFiles(private val writer: SyncedWriter = SyncedWriter.Default) {
    @Throws(IOException::class)
    fun write(file: File, bytes: ByteArray) {
        val tmp = tempFor(file)
        try {
            writer.write(tmp, bytes)
            publish(tmp, file)
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
    }

    private fun publish(tmp: File, file: File) {
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        syncDir(file.parentFile)
    }

    companion object {
        const val TEMP_SUFFIX = ".tmp"

        fun tempFor(file: File) = File(file.parentFile, file.name + TEMP_SUFFIX)

        /** Makes a rename durable. Best effort: some platforms can't open a directory as a channel. */
        fun syncDir(dir: File?) {
            if (dir == null) return
            try {
                FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
            } catch (_: IOException) {
            } catch (_: UnsupportedOperationException) {
            }
        }

        /** ENOSPC shows up in the message of Android's ErrnoException-backed IOExceptions and of the JDK's. */
        fun isDiskFull(e: Throwable): Boolean = generateSequence(e) { it.cause }.any {
            val msg = it.message.orEmpty()
            "ENOSPC" in msg || "No space left" in msg
        }
    }
}
