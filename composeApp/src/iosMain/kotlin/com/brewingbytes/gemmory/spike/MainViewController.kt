package com.brewingbytes.gemmory.spike

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIViewController
import platform.posix.memcpy

/**
 * The iOS entry point (eng R3-11): the Swift app shows this view controller and passes its frame probe. [reset]
 * deletes the spike's save first (the UI test's `-reset`), so a run starts from an empty canvas.
 */
fun MainViewController(probe: FrameProbe?, autoBench: Boolean, reset: Boolean): UIViewController {
    val files = AppSupportFiles()
    if (reset) files.delete(SpikeGame.SAVE)
    return ComposeUIViewController { SpikeApp(files, probe, autoBench) }
}

/**
 * The spike's save in Application Support (eng D8), excluded from backup as the app's photo files will be.
 * Atomic writes; the app's own FileStore (fsync, eng D8) comes in Stage 1c.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class AppSupportFiles : SpikeFiles {
    private val dir: NSURL by lazy {
        val manager = NSFileManager.defaultManager
        val base = manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)!!
        base.URLByAppendingPathComponent("spike", isDirectory = true)!!.also {
            manager.createDirectoryAtURL(it, withIntermediateDirectories = true, attributes = null, error = null)
            it.setResourceValue(true, forKey = platform.Foundation.NSURLIsExcludedFromBackupKey, error = null)
        }
    }

    override fun read(name: String): ByteArray? {
        val data = NSData.dataWithContentsOfURL(dir.URLByAppendingPathComponent(name)!!) ?: return null
        val size = data.length.toInt()
        if (size == 0) return ByteArray(0)
        return ByteArray(size).also { bytes -> bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    }

    fun delete(name: String) {
        NSFileManager.defaultManager.removeItemAtURL(dir.URLByAppendingPathComponent(name)!!, error = null)
    }

    override fun write(name: String, bytes: ByteArray) {
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        data.writeToURL(dir.URLByAppendingPathComponent(name)!!, atomically = true)
    }
}
