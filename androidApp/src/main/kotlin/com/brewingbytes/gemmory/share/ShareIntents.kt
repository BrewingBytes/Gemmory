package com.brewingbytes.gemmory.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.brewingbytes.gemmory.timelapse.ShareLink
import java.io.File

/** The Android share sheet for a time-lapse MP4 or the before/after PNG, with the tracked Play link (eng D14). */
object ShareIntents {
    fun chooser(context: Context, file: File, medium: ShareLink.Medium): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(if (file.name.endsWith(".png")) "image/png" else "video/mp4")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, ShareLink.text(medium))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the read grant through the chooser to the app the player picks.
        send.clipData = ClipData.newRawUri(null, uri)
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
