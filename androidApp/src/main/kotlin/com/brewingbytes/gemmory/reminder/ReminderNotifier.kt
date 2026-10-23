package com.brewingbytes.gemmory.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.Messages
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.Frost
import com.brewingbytes.gemmory.policy.ReminderCopy
import com.brewingbytes.gemmory.policy.ReminderOutcome
import com.brewingbytes.gemmory.policy.ReminderReason

/**
 * What the notification needs to know about a picture: its title, size and (lazily) its finished grid. [mystery]: an
 * unfinished mystery picture (T43), named neutrally and drawn as its frost.
 */
class ReminderPicture(val title: String, val size: CanvasSize?, val mystery: Boolean = false, val pattern: () -> Pattern?)

/**
 * Posts the evening reminder (T35; design system, Evening reminder): channel "Evening reminder" at default importance,
 * the white square-gem small icon with the `patina` accent, the picture drawn flat as the large icon (the app icon
 * for a streak or any photo canvas, never the photo), public on the lock screen, no actions, no progress bar and no
 * big-picture style. A tap opens [MainActivity] with an explicit, immutable intent that names only the reason, the
 * picture or canvas, and the day.
 */
class ReminderNotifier(context: Context) : ReminderChannel {
    private val app = context.applicationContext
    private val manager = NotificationManagerCompat.from(app)

    override fun ensureChannel() {
        val strings = app.strings
        val channel = NotificationChannel(CHANNEL, strings.text(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = strings.text(R.string.reminder_channel_description)
        }
        app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** False when the system blocks Gemmory's notifications, or the player turned the channel off. */
    override fun allowed(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * [picture]: the drop, Today's picture, the almost-finished picture or the streak's target (#102); null for a photo
     * canvas. A streak shows the app icon, never the picture (G5).
     */
    fun post(post: ReminderOutcome.Post, picture: ReminderPicture?, vip: Boolean, day: Long): Boolean {
        if (!allowed()) return false
        ensureChannel()
        val text = Messages.reminder(app.strings, ReminderCopy.of(post, picture?.title, picture?.size, vip, picture?.mystery == true))
        val icon = picture?.takeIf { post.reason != ReminderReason.STREAK }?.pattern?.invoke()?.let { if (picture.mystery) frost(it) else flat(it) } ?: appIcon()
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_gem)
            .setColor(PATINA)
            .setLargeIcon(icon)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(tap(post, day))
            .build()
        return try {
            manager.notify(NOTIFICATION, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun tap(post: ReminderOutcome.Post, day: Long): PendingIntent {
        val intent = Intent(app, MainActivity::class.java)
            .setAction(ACTION_TAP)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_REASON, post.reason.name)
            .putExtra(EXTRA_DAY, day)
        post.pictureId?.let { intent.putExtra(EXTRA_PICTURE, it) }
        post.canvas?.let { intent.putExtra(EXTRA_CANVAS, it.key) }
        return PendingIntent.getActivity(app, NOTIFICATION, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun appIcon(): Bitmap = app.packageManager.getApplicationIcon(app.applicationInfo).toBitmap(ICON_PX, ICON_PX)

    companion object {
        const val CHANNEL = "evening_reminder"
        const val NOTIFICATION = 35
        const val ACTION_TAP = "com.brewingbytes.gemmory.action.REMINDER_TAP"
        const val EXTRA_REASON = "reminder_reason"
        const val EXTRA_PICTURE = "reminder_picture"
        const val EXTRA_CANVAS = "reminder_canvas"
        const val EXTRA_DAY = "reminder_day"
        private const val PATINA = 0xFF7FB3A0.toInt()
        private const val FELT_DEEP = 0xFF3E4741.toInt()
        private const val ICON_PX = 256

        /** The picture drawn flat: one color per cell, whole pixels per cell, center-cropped square (no gem shading). */
        fun flat(pattern: Pattern): Bitmap {
            val side = minOf(pattern.width, pattern.height)
            val x0 = (pattern.width - side) / 2
            val y0 = (pattern.height - side) / 2
            val pixels = IntArray(side * side) { i ->
                pattern.palette[pattern.cells[(y0 + i / side) * pattern.width + x0 + i % side]].argb
            }
            val cells = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
            val scale = (ICON_PX / side).coerceAtLeast(1)
            return cells.scale(side * scale, side * scale, filter = false)
        }

        /**
         * A mystery picture's frost (T43; design system, Hidden pictures), square, with the `felt-deep` cover baked in:
         * a notification has no theme to lay it on.
         */
        fun frost(pattern: Pattern): Bitmap =
            Bitmap.createBitmap(Frost.image(pattern, ICON_PX, ICON_PX, FELT_DEEP), ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)

        /** The reason named by a tap's [EXTRA_REASON], or null for anything else. */
        fun reasonOf(name: String?): ReminderReason? = ReminderReason.entries.firstOrNull { it.name == name }
    }
}

/** A reminder tap as [MainActivity] receives it (T35): only ids and a day, re-checked before anything opens. */
data class ReminderTap(val reason: ReminderReason, val pictureId: String?, val canvasKey: String?, val day: Long) {
    companion object {
        /**
         * The tap in [intent], or null. A relaunch from Recents carries the task's original intent again (on Android
         * 10-11, Back finishes the root activity, so a notification that started the task comes back with
         * FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY); that's not a tap, and must not open the canvas or count again.
         */
        fun of(intent: Intent?): ReminderTap? {
            if (intent?.action != ReminderNotifier.ACTION_TAP) return null
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
            val reason = ReminderNotifier.reasonOf(intent.getStringExtra(ReminderNotifier.EXTRA_REASON)) ?: return null
            return ReminderTap(
                reason,
                intent.getStringExtra(ReminderNotifier.EXTRA_PICTURE),
                intent.getStringExtra(ReminderNotifier.EXTRA_CANVAS),
                intent.getLongExtra(ReminderNotifier.EXTRA_DAY, Long.MIN_VALUE),
            )
        }
    }
}
