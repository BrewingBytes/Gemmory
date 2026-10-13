package com.brewingbytes.gemmory.save

/**
 * Where the player left a canvas (design D25): [zoom] is the on-screen cell size in dp, [panX]/[panY] the viewport
 * center in dp measured from the canvas's top-left (so it means the same spot on any screen size or density), [slot]
 * the selected palette slot. Kept next to the canvas, outside the GMRY log, so it never
 * affects progress or the time-lapse.
 */
data class ViewState(val zoom: Float, val panX: Float, val panY: Float, val slot: Int)

/**
 * The `view` file: u8 version, f32 zoom, f32 panX, f32 panY, u8 slot (big-endian, 14 bytes). Written atomically
 * on onStop only. Anything unreadable or out of range decodes to null, and the canvas then opens with its defaults
 * (fit zoom, first unfinished color). The canvas still clamps zoom to its fit minimum and pan to its bounds,
 * which depend on the screen.
 */
object ViewStateCodec {
    const val VERSION = 1
    const val SIZE = 14

    /** Max zoom is 48dp cells (design system, Canvas zoom). */
    const val MAX_ZOOM_DP = 48f

    fun encode(state: ViewState): ByteArray {
        require(state.slot in 0 until GridCodec.MAX_PALETTE) { "slot out of range: ${state.slot}" }
        val w = ByteWriter(SIZE)
        w.u8(VERSION)
        w.f32(state.zoom)
        w.f32(state.panX)
        w.f32(state.panY)
        w.u8(state.slot)
        return w.toByteArray()
    }

    fun decode(bytes: ByteArray?, paletteSize: Int): ViewState? {
        if (bytes == null || bytes.size != SIZE) return null
        val r = ByteReader(bytes)
        if (r.u8() != VERSION) return null
        val state = ViewState(zoom = r.f32(), panX = r.f32(), panY = r.f32(), slot = r.u8())
        val zoomOk = state.zoom.isFinite() && state.zoom > 0f && state.zoom <= MAX_ZOOM_DP
        val panOk = state.panX.isFinite() && state.panY.isFinite()
        return if (zoomOk && panOk && state.slot < paletteSize) state else null
    }
}
