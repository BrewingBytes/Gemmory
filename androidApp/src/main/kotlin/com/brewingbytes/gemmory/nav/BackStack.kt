package com.brewingbytes.gemmory.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef

/** Screens of the navigation map (design D3). UMP consent and the rewarded sheet aren't routes. */
sealed interface Route {
    data object Gallery : Route

    data object Settings : Route

    /** VIP and remove-ads (#8): from the gallery's "VIP", Settings' Membership row or a rewarded sheet's VIP text. */
    data object Paywall : Route

    /** Photo flow: the Photo Picker itself is a system screen, so the flow starts at Crop. */
    data object Crop : Route

    data object Size : Route

    data object Preview : Route

    data class Canvas(val ref: CanvasRef) : Route

    /** [fresh]: reached by finishing the picture just now (lights down); false for the completion still. */
    data class Completion(val ref: CanvasRef, val fresh: Boolean) : Route

    companion object {
        /**
         * Saved-state form. A fresh completion is saved as a still: after the process dies the lights-down moment is
         * over, and the screen shows the finished picture at rest (rotation keeps it fresh through its ViewModel).
         */
        fun encode(route: Route): String = when (route) {
            Gallery -> "gallery"
            Settings -> "settings"
            Paywall -> "paywall"
            Crop -> "crop"
            Size -> "size"
            Preview -> "preview"
            is Canvas -> "canvas:${route.ref.kind.name}:${route.ref.id}"
            is Completion -> "completion:${route.ref.kind.name}:${route.ref.id}"
        }

        fun decode(saved: String): Route? {
            val parts = saved.split(':')
            fun ref(): CanvasRef? = runCatching { CanvasRef(CanvasKind.valueOf(parts[1]), parts[2]) }.getOrNull()
            return when (parts[0]) {
                "gallery" -> Gallery
                "settings" -> Settings
                "paywall" -> Paywall
                "crop" -> Crop
                "size" -> Size
                "preview" -> Preview
                "canvas" -> ref()?.let(::Canvas)
                "completion" -> ref()?.let { Completion(it, fresh = false) }
                else -> null
            }.takeIf { parts.size == if (parts[0] == "canvas" || parts[0] == "completion") 3 else 1 }
        }
    }
}

/**
 * The app's back stack. Gallery is always at the bottom; system back pops one step (design D3) and, on the Gallery,
 * leaves the app. Main thread only (snapshot state).
 */
class BackStack(initial: List<Route> = listOf(Route.Gallery)) {
    private val stack: SnapshotStateList<Route> = mutableStateListOf<Route>().apply {
        addAll(if (initial.firstOrNull() == Route.Gallery) initial else listOf(Route.Gallery) + initial)
    }

    val routes: List<Route> get() = stack.toList()
    val top: Route get() = stack.last()

    fun push(route: Route) {
        if (top != route) stack += route
    }

    /** One step up. False on the Gallery, where back leaves the app. */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Back to the Gallery, dropping everything above it. */
    fun popToGallery() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Leaves the photo flow for the gallery when its draft is gone (e.g. the cache was cleared). */
    fun dropPhotoFlow() {
        stack.removeAll { it == Route.Crop || it == Route.Size || it == Route.Preview }
    }

    /**
     * A tile tap: a finished canvas opens its completion still, anything else the canvas (resumed). [isFinished] reads
     * the canvas itself, not the tile, which can be stale right after a visit: a just-finished canvas opened as the
     * canvas finishes a second time (#50). Nothing opens if the player went elsewhere while it was read.
     */
    suspend fun openTile(ref: CanvasRef, isFinished: suspend (CanvasRef) -> Boolean) {
        val from = routes
        val finished = isFinished(ref)
        if (routes != from) return
        popToGallery()
        push(if (finished) Route.Completion(ref, fresh = false) else Route.Canvas(ref))
    }

    /** Start on Preview: the photo flow is replaced by the new canvas, so back goes to the Gallery. */
    fun started(ref: CanvasRef) {
        popToGallery()
        push(Route.Canvas(ref))
    }

    /** The last gem placed: the canvas becomes its completion, so back and Done go to the Gallery. */
    fun finished(ref: CanvasRef) {
        if (top == Route.Canvas(ref)) stack[stack.lastIndex] = Route.Completion(ref, fresh = true)
    }

    /** Play again on the completion still (after the Start over confirm). */
    fun playAgain(ref: CanvasRef) {
        popToGallery()
        push(Route.Canvas(ref))
    }

    companion object {
        val saver: Saver<BackStack, List<String>> = Saver(
            save = { s -> s.routes.map(Route::encode) },
            restore = { saved -> BackStack(saved.mapNotNull(Route::decode)) },
        )
    }
}
