package com.brewingbytes.gemmory.bundled

import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.GridFile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
}
