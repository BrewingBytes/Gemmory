package com.brewingbytes.gemmory.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicTest {
    private class FakeOutput : MusicOutput {
        val calls = mutableListOf<String>()
        val started = mutableListOf<Int>()
        var gain = 0f
        var otherAudio = false
        var focusGranted = true
        var focusHeld = false
        var failOn: Int? = null

        override fun start(track: Int, volume: Float) {
            if (track == failOn) throw IllegalStateException("decode")
            started += track
            gain = volume
            calls += "start"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun resume(volume: Float) {
            gain = volume
            calls += "resume"
        }

        override fun setVolume(volume: Float) {
            gain = volume
        }

        override fun stop() {
            calls += "stop"
        }

        override fun otherAudioPlaying() = otherAudio

        override fun requestFocus(): Boolean {
            focusHeld = focusGranted
            return focusGranted
        }

        override fun abandonFocus() {
            focusHeld = false
        }
    }

    private val open = MusicGates(on = true, unlocked = true, ringerNormal = true, foreground = true)
    private val out = FakeOutput()
    private val failures = mutableListOf<Throwable>()
    private val tracks = listOf(MusicTrack(plays = 3), MusicTrack(), MusicTrack(), MusicTrack(), MusicTrack(), MusicTrack())
    private val music = Music(out, tracks, Random(3)) { failures += it }

    @Test
    fun playsAtThirtyPercentOnceEveryGateAllowsIt() {
        music.update(open)
        assertTrue(music.playing)
        assertEquals(0.3f, out.gain)
        assertTrue(out.focusHeld)
    }

    @Test
    fun theColdOpenedGalleryIsSilentUntilTheFirstCanvas() {
        music.update(open.copy(unlocked = false))
        assertFalse(music.playing)
        assertTrue(out.started.isEmpty())
        music.update(open)
        assertTrue(music.playing)
    }

    @Test
    fun eachGateStopsItOrKeepsItFromStarting() {
        for (gates in listOf(
            open.copy(on = false),
            open.copy(ringerNormal = false),
            open.copy(adShowing = true),
            open.copy(exporting = true),
        )) {
            val o = FakeOutput()
            val m = Music(o, tracks, Random(1)) {}
            m.update(gates)
            assertFalse(m.playing, "$gates")
            assertTrue(o.started.isEmpty(), "$gates")
            m.update(open)
            assertTrue(m.playing, "$gates")
            m.update(gates)
            assertFalse(m.playing, "$gates")
            assertEquals("pause", o.calls.last(), "$gates")
            assertFalse(o.focusHeld, "$gates gives focus back")
            m.update(open)
            assertEquals("resume", o.calls.last(), "$gates resumes the same track")
        }
    }

    @Test
    fun theBackgroundStopsItAndTheNextStartIsAFreshTrack() {
        music.update(open)
        music.update(open.copy(foreground = false))
        assertFalse(music.playing)
        assertEquals("stop", out.calls.last())
        assertFalse(out.focusHeld)
        music.update(open)
        assertEquals("start", out.calls.last(), "a track from its start, not a resume")
    }

    @Test
    fun neverStartsOverOtherMediaOrWithoutFocus() {
        out.otherAudio = true
        music.update(open)
        assertFalse(music.playing)
        out.otherAudio = false
        out.focusGranted = false
        music.update(open)
        assertFalse(music.playing)
        assertTrue(out.started.isEmpty())
    }

    /** Android still reports a track paused a moment ago as active: resuming its own pause mustn't read that as other media. */
    @Test
    fun resumingItsOwnPauseIgnoresTheOtherMediaCheck() {
        music.update(open)
        music.update(open.copy(ringerNormal = false))
        out.otherAudio = true
        music.update(open)
        assertTrue(music.playing)
        assertEquals("resume", out.calls.last())
    }

    @Test
    fun aTransientFocusLossPausesUntilTheGain() {
        music.update(open)
        music.onFocus(FocusChange.LOSS_TRANSIENT)
        assertFalse(music.playing)
        music.update(open)
        assertFalse(music.playing, "no resume before the gain")
        music.onFocus(FocusChange.GAIN)
        assertTrue(music.playing)
        assertEquals("resume", out.calls.last())
    }

    @Test
    fun aPermanentFocusLossWaitsForTheNextForeground() {
        music.update(open)
        music.onFocus(FocusChange.LOSS)
        assertFalse(music.playing)
        assertFalse(out.focusHeld)
        music.update(open)
        assertFalse(music.playing)
        music.update(open.copy(foreground = false))
        music.update(open)
        assertTrue(music.playing)
    }

    @Test
    fun aTrackThatFailsToDecodeTurnsMusicOffForTheSession() {
        out.failOn = Random(3).nextInt(tracks.size)
        music.update(open)
        assertFalse(music.playing)
        assertTrue(music.failed)
        assertEquals(1, failures.size)
        assertFalse(out.focusHeld)
        // Nothing brings it back this session, not a new foreground.
        out.failOn = null
        music.update(open.copy(foreground = false))
        music.update(open)
        assertFalse(music.playing)
        assertTrue(out.started.isEmpty())
    }

    @Test
    fun anErrorMidTrackStopsItAndReportsOnce() {
        music.update(open)
        music.onError(IllegalStateException("what=1"))
        music.onError(IllegalStateException("what=1"))
        assertFalse(music.playing)
        assertEquals("stop", out.calls.last())
        assertEquals(1, failures.size)
    }

    @Test
    fun randomTracksNeverRepeatAndTheShortLoopPlaysThreeTimes() {
        music.update(open)
        repeat(300) { music.onTrackEnded() }
        val runs = mutableListOf<Pair<Int, Int>>()
        for (t in out.started) if (runs.lastOrNull()?.first == t) runs[runs.lastIndex] = t to runs.last().second + 1 else runs += t to 1
        // The last run may be cut short.
        for ((t, n) in runs.dropLast(1)) assertEquals(tracks[t].plays, n, "track $t")
        assertEquals(tracks.indices.toSet(), out.started.toSet())
    }

    @Test
    fun volumeMovesInStepsOfTenAndAppliesWhilePlaying() {
        music.update(open)
        music.setVolume(54)
        assertEquals(0.5f, out.gain)
        assertEquals(10, MusicRules.volumeStep(0))
        assertEquals(100, MusicRules.volumeStep(140))
        assertEquals(30, MusicRules.volumeStep(MusicRules.DEFAULT_VOLUME))
    }

    @Test
    fun theChipShowsOnceAndOnlyWhileMusicPlays() {
        assertTrue(MusicRules.showChip(chipShown = false, playing = true, onGallery = true, sheetUp = false))
        assertFalse(MusicRules.showChip(chipShown = true, playing = true, onGallery = true, sheetUp = false))
        assertFalse(MusicRules.showChip(chipShown = false, playing = false, onGallery = true, sheetUp = false), "music didn't start")
        assertFalse(MusicRules.showChip(chipShown = false, playing = true, onGallery = false, sheetUp = false))
        assertFalse(MusicRules.showChip(chipShown = false, playing = true, onGallery = true, sheetUp = true), "after the sheet closes")
        assertEquals(8_000L, MusicRules.chipTimeoutMs(touchExploration = false))
        assertNull(MusicRules.chipTimeoutMs(touchExploration = true))
    }

    /** R1: Music is its own switch: off, nothing plays, whatever Sound is (sound effects follow only Sound, SoundCues). */
    @Test
    fun musicOffPlaysNothing() {
        music.update(open.copy(on = false))
        music.onFocus(FocusChange.GAIN)
        music.onTrackEnded()
        assertTrue(out.started.isEmpty())
        assertFalse(out.focusHeld)
    }
}
