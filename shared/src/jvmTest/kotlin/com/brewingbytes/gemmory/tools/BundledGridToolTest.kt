package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BundledGridToolTest {
    private val fixture = ImageIO.read(File("src/jvmTest/fixtures/landscape.png"))

    @Test
    fun frozenGridRoundTripsThroughTheGridCodec() {
        val bytes = BundledGridTool.freeze(fixture, GridSpec.STARTER)
        val grid = assertIs<Decoded.Ok<com.brewingbytes.gemmory.save.GridFile>>(GridCodec.decode(bytes)).value
        assertEquals(24, grid.pattern.width)
        assertEquals(32, grid.pattern.height)
        assertTrue(grid.pattern.palette.size <= 8)
        assertContentEquals(bytes, GridCodec.encode(grid.pattern))
    }

    @Test
    fun freezingIsDeterministic() {
        assertContentEquals(BundledGridTool.freeze(fixture, GridSpec.STARTER), BundledGridTool.freeze(fixture, GridSpec.STARTER))
    }

    @Test
    fun cropFocusPlacesTheCropAlongTheCutAxis() {
        // Column x holds the color x, so a crop's first pixel tells where it starts.
        val wide = BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 400) for (y in 0 until 300) wide.setRGB(x, y, x)
        val left = BundledGridTool.crop(wide, GridSpec.STARTER, focus = 0.0)
        assertEquals(225 to 300, left.width to left.height)
        assertEquals(0, left.getRGB(0, 0) and 0xFFFFFF)
        assertEquals(175, BundledGridTool.crop(wide, GridSpec.STARTER, focus = 1.0).getRGB(0, 0) and 0xFFFFFF)
        assertEquals(88, BundledGridTool.crop(wide, GridSpec.STARTER, focus = 0.5).getRGB(0, 0) and 0xFFFFFF)
    }

    /**
     * The shipped starter grid is frozen: its CRC binds every save made on it (eng D11). If this fails, the asset
     * was re-frozen or edited, and players' starter progress would be refused.
     */
    @Test
    fun shippedStarterGridIsStable() {
        val bytes = File("../androidApp/src/main/assets/pictures/starter.gmrg").readBytes()
        val grid = assertIs<Decoded.Ok<com.brewingbytes.gemmory.save.GridFile>>(GridCodec.decode(bytes)).value
        assertEquals(STARTER_CRC, grid.crc32, "starter CRC is %08x".format(grid.crc32))
        assertEquals(GridSpec.STARTER.width to GridSpec.STARTER.height, grid.pattern.width to grid.pattern.height)
        assertEquals(GridSpec.STARTER.paletteCap, grid.pattern.palette.size)
    }

    private companion object {
        const val STARTER_CRC = 0x099b53e8
    }
}
