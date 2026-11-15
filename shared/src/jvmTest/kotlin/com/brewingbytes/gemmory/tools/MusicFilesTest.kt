package com.brewingbytes.gemmory.tools

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The music both platforms play (T63): AAC in MP4 under composeApp's shared resources, each track with its row in
 * `tools/sounds/PROVENANCE.md` (source, author, license), and no row without its file.
 */
class MusicFilesTest {
    private val folder = File("../composeApp/src/commonMain/composeResources/files/music")
    private val provenance = File("../tools/sounds/PROVENANCE.md").readText()

    private val files: List<File> get() = folder.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }

    @Test
    fun theSixTracksAreThere() {
        assertEquals((1..6).map { "music_$it.m4a" }, files.map { it.name })
    }

    @Test
    fun everyTrackHasItsProvenanceRowAndEveryRowItsTrack() {
        val rows = Regex("""^\| `(music_\d+\.[a-z0-9]+)` \|.*\| CC0 1\.0 \|$""", RegexOption.MULTILINE).findAll(provenance)
            .map { it.groupValues[1] }.toList()
        assertEquals(files.map { it.name }, rows.sorted())
    }

    /** AAC-LC in an MP4 container, which AVAudioPlayer and MediaPlayer both play (Ogg isn't, on iOS: R10-4). */
    @Test
    fun everyTrackIsAacInMp4() {
        for (file in files) {
            val bytes = file.readBytes()
            assertEquals("ftyp", String(bytes, 4, 4, Charsets.US_ASCII), "${file.name} is not MP4")
            assertTrue(indexOf(bytes, "mp4a".toByteArray()) >= 0, "${file.name} has no AAC track")
            // +faststart: the index (moov) comes before the audio (mdat), so a player starts without seeking to the end.
            assertTrue(indexOf(bytes, "moov".toByteArray()) < indexOf(bytes, "mdat".toByteArray()), "${file.name} isn't faststart")
        }
    }

    /** 96 kbps over the six tracks' 20 minutes is about 14.7 MB; a bigger total means a track was encoded otherwise. */
    @Test
    fun theMusicStaysWithinItsBudget() {
        val total = files.sumOf { it.length() }
        assertTrue(total < 16_000_000, "music is $total bytes")
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
