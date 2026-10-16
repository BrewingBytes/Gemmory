package com.brewingbytes.gemmory.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.timelapse.EncodeRequest
import com.brewingbytes.gemmory.timelapse.EncodeState
import com.brewingbytes.gemmory.timelapse.FrameArt
import com.brewingbytes.gemmory.ui.GemRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Share progress (#36) for the canvas or gallery screen that owns it (kept in its ViewModel, so a rotation keeps the
 * sheet): the same time-lapse as a finished share, replaying the log so far and ending on the canvas as it stands.
 * It goes through the app's EncodeManager and fallback chain like the completion screen's Share; no ad is involved.
 */
class ProgressSharing(private val app: GemmoryApp) {
    /** What the sheet on screen is for; null when nothing was shared from this screen. */
    var request by mutableStateOf<EncodeRequest?>(null)
        private set

    /** Share progress tap. VIP gets no watermark, decided now, as on the completion screen. */
    fun share(ref: CanvasRef, pattern: Pattern, log: IntArray, title: String) {
        val source = app.store.repository.sourceFile(ref).takeIf { ref.kind == CanvasKind.PHOTO && it.isFile }
        val next = EncodeRequest(ref, pattern, log, title, source, watermark = !app.billing.entitlements.value.vip)
        request = next
        app.analytics.log(AnalyticsEvent.shareTapped(ref, pattern.width, pattern.height, progress = next.inProgress))
        app.encodes.share(next)
    }

    fun cancel() {
        request?.let { app.encodes.cancel(it.ref) }
    }

    /** The share sheet was opened for the result, or the player closed the message. */
    fun handled() {
        request?.let { app.encodes.acknowledge(it.ref) }
    }

    /** The screen moved on to another canvas: its sheet belongs to nothing now. A running encode goes on. */
    fun clear() {
        request = null
    }

    fun tryAgain() {
        val last = request ?: return
        handled()
        share(last.ref, last.pattern, last.log, last.title)
    }
}

/**
 * The share progress sheet over the canvas or the gallery for a [ProgressSharing] (design system, Share progress
 * sheet): a finished video goes to the Android share sheet once the app is in front, the PNG fallback shows "Before"
 * and "So far", and Cancel or a dismissed sheet comes back with no message.
 */
@Composable
fun ProgressShareHost(sharing: ProgressSharing, returnFocusTo: String? = null) {
    val request = sharing.request ?: return
    val context = LocalContext.current
    val app = context.applicationContext as GemmoryApp
    val encode by remember(request.ref) { app.encodes.state(request.ref) }.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(encode) {
        val video = encode as? EncodeState.Video ?: return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        context.startActivity(ShareIntents.chooser(context, video.file, request))
        sharing.handled()
    }
    val picture = encode is EncodeState.Picture
    val thumbs by produceState<Pair<ImageBitmap, ImageBitmap>?>(null, request, picture) {
        value = if (picture) withContext(Dispatchers.Default) { thumbs(request) } else null
    }
    ShareSheet(
        state = encode,
        before = thumbs?.first,
        after = thumbs?.second,
        aspect = request.pattern.width / request.pattern.height.toFloat(),
        onCancel = sharing::cancel,
        onSharePicture = {
            (encode as? EncodeState.Picture)?.let { context.startActivity(ShareIntents.chooser(context, it.file, request)) }
            sharing.handled()
        },
        onTryAgain = sharing::tryAgain,
        onClose = sharing::handled,
        returnFocusTo = returnFocusTo,
        progress = true,
    )
}

/** The fallback sheet's "Before" (cropped photo or empty tint grid) and "So far" (the canvas as shared). */
private fun thumbs(request: EncodeRequest): Pair<ImageBitmap, ImageBitmap> {
    val pattern = request.pattern
    val cellPx = (720 / pattern.width).coerceIn(4, 24)
    val placed = BooleanArray(pattern.cells.size).also { for (cell in request.log) it[cell] = true }
    val now = GemRenderer.render(pattern, cellPx) { placed[it] }
    val before = request.source?.let { FrameArt.decodeSource(it, now.width, now.height) }
        ?: GemRenderer.render(pattern, cellPx) { false }
    return before.asImageBitmap() to now.asImageBitmap()
}
