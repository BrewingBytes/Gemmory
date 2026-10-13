package com.brewingbytes.gemmory.save

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ViewStateCodecTest {
    private val state = ViewState(zoom = 24f, panX = -120.5f, panY = 300f, slot = 7)

    // u8 version 1, f32 24.0, f32 -120.5, f32 300.0, u8 slot 7 (built with Python struct).
    private val golden = "0141c00000c2f100004396000007"

    @Test
    fun encodesGoldenBytesAndRoundTrips() {
        assertEquals(golden, ViewStateCodec.encode(state).toHex())
        assertEquals(state, ViewStateCodec.decode(hex(golden), paletteSize = 8))
    }

    @Test
    fun fallsBackToDefaultsOnMissingOrCorruptFiles() {
        assertNull(ViewStateCodec.decode(null, 8))
        assertNull(ViewStateCodec.decode(ByteArray(0), 8))
        assertNull(ViewStateCodec.decode(hex(golden).copyOf(13), 8))
        assertNull(ViewStateCodec.decode(hex(golden) + 0, 8))
        assertNull(ViewStateCodec.decode(hex(golden).also { it[0] = 2 }, 8))
    }

    @Test
    fun fallsBackToDefaultsWhenOutOfRange() {
        assertNull(ViewStateCodec.decode(hex(golden), paletteSize = 7))
        for (bad in listOf(0f, -1f, 48.5f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertNull(ViewStateCodec.decode(ViewStateCodec.encode(state.copy(zoom = bad)), 8), "zoom $bad")
        }
        assertNull(ViewStateCodec.decode(ViewStateCodec.encode(state.copy(panX = Float.NaN)), 8))
        assertNull(ViewStateCodec.decode(ViewStateCodec.encode(state.copy(panY = Float.NEGATIVE_INFINITY)), 8))
        assertEquals(state.copy(zoom = 48f), ViewStateCodec.decode(ViewStateCodec.encode(state.copy(zoom = 48f)), 8))
    }
}
