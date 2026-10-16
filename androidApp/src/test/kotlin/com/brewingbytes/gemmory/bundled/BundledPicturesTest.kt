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
import kotlin.test.assertFalse
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

    /** The picker size whose grid this picture's asset has. */
    private fun sizeOf(picture: BundledPicture): CanvasSize {
        val pattern = assertIs<Decoded.Ok<GridFile>>(
            GridCodec.decode(File("src/main/assets/${picture.asset}").readBytes()), picture.id,
        ).value.pattern
        val size = CanvasSize.entries.firstOrNull { size ->
            AspectRatio.entries.any { GridSpec.of(size, it).let { s -> s.width == pattern.width && s.height == pattern.height } }
        }
        assertTrue(size != null && pattern.palette.size <= size.paletteCap, "${picture.id} is not a picker size")
        return size
    }

    /** #31: after the starter, every Quick picture comes before every Medium one, and every Medium before every Big one. */
    @Test
    fun theGalleryRampsFromQuickThroughMediumToBig() {
        val sizes = BundledPictures.ALL.drop(1).map(::sizeOf)
        assertEquals(sizes.sortedBy { it.ordinal }, sizes)
        assertTrue(sizes.count { it == CanvasSize.QUICK } >= 4)
        assertTrue(sizes.count { it == CanvasSize.MEDIUM } >= 4)
    }

    /** #32: the starter is free whatever FREE says. */
    @Test
    fun theStarterIsNeverLocked() {
        assertFalse(BundledPictures.STARTER.locked)
        assertFalse(BundledPictures.ALL.first().locked)
    }

    /** #32: a typo in FREE would quietly lock a picture meant to be free. */
    @Test
    fun everyFreeIdIsInTheCatalog() {
        val ids = BundledPictures.ALL.map { it.id }.toSet()
        for (id in BundledPictures.FREE) assertTrue(id in ids, id)
        assertTrue(BundledPictures.STARTER.id !in BundledPictures.FREE, "the starter is free already")
    }

    /** #32: everything but the starter and FREE is locked, so a newly added picture is locked by default. */
    @Test
    fun everyOtherPictureIsLocked() {
        val locked = BundledPictures.ALL.filter { it.locked }
        assertEquals(BundledPictures.ALL.size - 1 - BundledPictures.FREE.size, locked.size)
        assertTrue(locked.none { it.id in BundledPictures.FREE })
    }

    /** #32: a free player always has something of every size to play, and finds it first in its size. */
    @Test
    fun aFreePlayerHasAtLeastTwoOfEachSizeShownFirst() {
        val bySize = BundledPictures.ALL.drop(1).groupBy(::sizeOf)
        for (size in CanvasSize.entries) {
            val pictures = bySize[size].orEmpty()
            val free = pictures.count { !it.locked }
            assertTrue(free >= 2, "$size has $free free pictures")
            assertTrue(pictures.take(free).none { it.locked }, "$size lists a locked picture before a free one")
        }
    }
}
