package com.brewingbytes.gemmory.game

import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SoundCuesTest {
    private fun session(cues: SoundCues): List<SoundCue> = buildList {
        repeat(20) { addAll(cues.placed(drag = it % 3 == 0)) }
        repeat(5) { addAll(cues.batchClick(it)) }
        repeat(4) { addAll(cues.colorDone()) }
        repeat(3) { addAll(cues.milestone()) }
        repeat(3) { addAll(cues.finish()) }
    }

    @Test
    fun aSeededRandomPicksTheSameTakesAndPitches() {
        assertEquals(session(SoundCues(Random(7)) { true }), session(SoundCues(Random(7)) { true }))
        assertNotEquals(session(SoundCues(Random(7)) { true }), session(SoundCues(Random(8)) { true }))
    }

    @Test
    fun theSameTakeNeverPlaysTwiceInARow() {
        val cues = SoundCues(Random(1)) { true }
        for (event in listOf(SoundEvent.PLACE, SoundEvent.DRAG, SoundEvent.COLOR_DONE, SoundEvent.MILESTONE, SoundEvent.FINISH)) {
            val takes = (0 until 200).map {
                when (event) {
                    SoundEvent.PLACE -> cues.placed(drag = false)
                    SoundEvent.DRAG -> cues.placed(drag = true)
                    SoundEvent.COLOR_DONE -> cues.colorDone()
                    SoundEvent.MILESTONE -> cues.milestone()
                    SoundEvent.FINISH -> cues.finish()
                }.first().also { cue -> assertEquals(event, cue.event) }.take
            }
            assertTrue(takes.zipWithNext().none { (a, b) -> a == b }, "$event repeated a take")
            assertEquals((0 until event.takes).toSet(), takes.toSet(), "$event uses every take")
        }
    }

    @Test
    fun aDragTickIsSofterAndSteadierThanATap() {
        val cues = SoundCues(Random(3)) { true }
        repeat(200) {
            val tap = cues.placed(drag = false).single()
            val drag = cues.placed(drag = true).single()
            assertTrue(drag.volume < tap.volume)
            assertTrue(tap.rate in 2f.pow(-3f / 12f)..2f.pow(3f / 12f), "a tap stays within ±3 semitones")
            assertTrue(drag.rate in 2f.pow(-1.5f / 12f)..2f.pow(1.5f / 12f), "a drag stays within ±1.5 semitones")
        }
    }

    @Test
    fun aBatchClimbsASemitoneAClick() {
        val cues = SoundCues(Random(4)) { true }
        for (step in 0 until 5) {
            val cue = cues.batchClick(step).single()
            assertEquals(SoundEvent.PLACE, cue.event)
            assertEquals(2f.pow(step / 12f), cue.rate)
        }
    }

    // R1: the Sound toggle still governs every sound, read at the moment each one plays.
    @Test
    fun nothingPlaysWithSoundOff() {
        var on = false
        val cues = SoundCues(Random(5)) { on }
        assertTrue(session(cues).isEmpty())
        on = true
        assertTrue(cues.placed(drag = false).isNotEmpty())
        on = false
        assertTrue(cues.finish().isEmpty())
    }

    // R1: the finish keeps the low chime with its fifth and octave at today's volumes, all from one take (#57).
    @Test
    fun theFinishKeepsItsFifthAndOctave() {
        val chime = SoundCues(Random(6)) { true }.finish()
        assertEquals(listOf(1f to 0.7f, 1.5f to 0.35f, 2f to 0.25f), chime.map { it.rate to it.volume })
        assertEquals(1, chime.map { it.take }.toSet().size)
    }
}
