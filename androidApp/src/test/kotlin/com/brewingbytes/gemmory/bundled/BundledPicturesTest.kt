package com.brewingbytes.gemmory.bundled

import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.GridFile
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BundledPicturesTest {
    @Test
    fun theStarterIsTheFirstPicture() {
        assertEquals(BundledPictures.STARTER, BundledPictures.ALL.first())
    }

    /** Every asset decodes and matches its pinned CRC, so a re-frozen grid never orphans players' saves. */
    @Test
    fun everyAssetMatchesItsPinnedCrc() {
        for (picture in BundledPictures.ALL) {
            val bytes = File("src/main/assets/${picture.asset}").readBytes()
            val grid = assertIs<Decoded.Ok<GridFile>>(GridCodec.decode(bytes), picture.id).value
            assertEquals(picture.gridCrc32, grid.crc32, picture.id)
        }
    }

    @Test
    fun theStarterIs24By32With8Colors() {
        val bytes = File("src/main/assets/${BundledPictures.STARTER.asset}").readBytes()
        val pattern = assertIs<Decoded.Ok<GridFile>>(GridCodec.decode(bytes)).value.pattern
        assertEquals(GridSpec.STARTER.width to GridSpec.STARTER.height, pattern.width to pattern.height)
        assertEquals(8, pattern.palette.size)
    }

    /** #31: every shipped picture has its source and license in tools/bundled/PROVENANCE.md. */
    @Test
    fun everyPictureHasAProvenanceRow() {
        val provenance = File("../tools/bundled/PROVENANCE.md").readText()
        for (picture in BundledPictures.ALL) {
            assertTrue("| `androidApp/src/main/assets/${picture.asset}` |" in provenance, picture.id)
        }
    }

    @Test
    fun idsAreUniqueCanvasIds() {
        val ids = BundledPictures.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { CanvasRef(CanvasKind.BUNDLED, it) }
    }

    /** #31: after the starter, every Quick picture comes before every Medium one, and every Medium before every Big one. */
    @Test
    fun theGalleryRampsFromQuickThroughMediumToBig() {
        val sizes = BundledPictures.ALL.drop(1).map { picture ->
            val pattern = assertIs<Decoded.Ok<GridFile>>(
                GridCodec.decode(File("src/main/assets/${picture.asset}").readBytes()), picture.id,
            ).value.pattern
            val size = CanvasSize.entries.firstOrNull { size ->
                AspectRatio.entries.any { GridSpec.of(size, it).let { s -> s.width == pattern.width && s.height == pattern.height } }
            }
            assertTrue(size != null && pattern.palette.size <= size.paletteCap, "${picture.id} is not a picker size")
            size
        }
        assertEquals(sizes.sortedBy { it.ordinal }, sizes)
        assertTrue(sizes.count { it == CanvasSize.QUICK } >= 4)
        assertTrue(sizes.count { it == CanvasSize.MEDIUM } >= 4)
    }
}
