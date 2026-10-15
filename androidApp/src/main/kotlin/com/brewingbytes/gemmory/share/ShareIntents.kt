package com.brewingbytes.gemmory.share

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.timelapse.EncodeRequest
import com.brewingbytes.gemmory.timelapse.ShareLink
import java.io.File

/** The Android share sheet for a time-lapse MP4 or the before/after PNG, with the tracked Play link (eng D14). */
object ShareIntents {
    fun chooser(context: Context, file: File, request: EncodeRequest): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(if (file.name.endsWith(".png")) "image/png" else "video/mp4")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, ShareLink.text(request.medium))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the read grant through the chooser to the app the player picks.
        send.clipData = ClipData.newRawUri(null, uri)
        // The sheet calls back when an app is picked: that's share_completed (#12). Mutable, so the sheet can add
        // which app it was; the intent is explicit and the receiver isn't exported.
        val picked = Intent(context, ShareCompletedReceiver::class.java)
            .putExtra(AnalyticsEvent.SOURCE, AnalyticsEvent.source(request.ref))
            .putExtra(AnalyticsEvent.SIZE, AnalyticsEvent.size(request.ref, request.pattern.width, request.pattern.height))
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            picked,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return Intent.createChooser(send, null, callback.intentSender).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/** Logs share_completed with the canvas's source and size; which app was picked isn't recorded. */
class ShareCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val source = intent.getStringExtra(AnalyticsEvent.SOURCE) ?: return
        val size = intent.getStringExtra(AnalyticsEvent.SIZE) ?: return
        (context.applicationContext as GemmoryApp).analytics.log(AnalyticsEvent.shareCompleted(source, size))
    }
}
