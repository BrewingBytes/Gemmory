package com.brewingbytes.gemmory.share

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.policy.GameEvent
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
            .putExtra(Intent.EXTRA_TEXT, ShareLink.text(request.medium, request.pictureId))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the read grant through the chooser to the app the player picks.
        send.clipData = ClipData.newRawUri(null, uri)
        // The sheet calls back when an app is picked: that's share_completed (#12). Mutable, so the sheet can add
        // which app it was; the intent is explicit and the receiver isn't exported.
        val picked = Intent(context, ShareCompletedReceiver::class.java)
            .putExtra(AnalyticsEvent.SOURCE, AnalyticsEvent.source(request.ref))
            .putExtra(AnalyticsEvent.SIZE, AnalyticsEvent.size(request.ref, request.pattern.width, request.pattern.height))
            .putExtra(AnalyticsEvent.KIND, AnalyticsEvent.kind(request.inProgress))
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            picked,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return Intent.createChooser(send, null, callback.intentSender).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** The milestone share card (#104): the PNG and the streak card's tracked link; an app picked is `streak_share_completed`. */
    fun streakChooser(context: Context, file: File, milestone: Int): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, ShareLink.streakText())
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        val picked = Intent(context, StreakShareCompletedReceiver::class.java).putExtra(StreakShareCompletedReceiver.MILESTONE, milestone)
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            picked,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return Intent.createChooser(send, null, callback.intentSender).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/** Logs share_completed (and counts the share for achievements, #49) with the canvas's source, size and share kind (#36); which app was picked isn't recorded. */
class ShareCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val source = intent.getStringExtra(AnalyticsEvent.SOURCE) ?: return
        val size = intent.getStringExtra(AnalyticsEvent.SIZE) ?: return
        val kind = intent.getStringExtra(AnalyticsEvent.KIND) ?: AnalyticsEvent.kind(progress = false)
        val app = context.applicationContext as GemmoryApp
        app.analytics.log(AnalyticsEvent.shareCompleted(source, size, kind))
        // Show and Tell / Work in Progress (#49): counted now, sent once the Gallery shows.
        app.achievements.record(GameEvent.Shared(progress = kind == AnalyticsEvent.kind(progress = true)))
    }
}

/**
 * Logs `streak_share_completed` with the card's milestone (#104). Not `share_completed`, and no Show and Tell: those
 * count shared pictures.
 */
class StreakShareCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val milestone = intent.getIntExtra(MILESTONE, 0).takeIf { it > 0 } ?: return
        (context.applicationContext as GemmoryApp).analytics.log(AnalyticsEvent.streakShareCompleted(milestone))
    }

    companion object {
        const val MILESTONE = "milestone"
    }
}
