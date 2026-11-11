package com.brewingbytes.gemmory.spike

import kotlin.test.Test
import kotlin.test.assertEquals

/** The iOS bake (R3-12): a placement writes one pixel through Skia, read back through Compose. */
class BakedBitmapIosTest {
    @Test
    fun aPlacementWritesOnePixel() {
        val bake = bakedBitmap(4, 3)
        bake.setAll(IntArray(12) { 0xFF000000.toInt() or it })
        bake.set(5, 0xFF112233.toInt())
        val out = IntArray(12)
        bake.image.readPixels(out)
        assertEquals(0xFF112233.toInt(), out[5])
        assertEquals(0xFF000004.toInt(), out[4])
    }
}
