package com.brewingbytes.gemmory.game

import kotlin.math.pow
import kotlin.random.Random

/** A sound the canvas or the completion screen plays (design system, Haptics and sound). A wrong color has none. */
enum class SoundEvent(val takes: Int) {
    PLACE(5),
    DRAG(3),
    COLOR_DONE(3),
    MILESTONE(3),
    FINISH(3),
}

/** One sound to play: [take] (0-based) of [event]'s recordings, at [volume] and playback [rate]. */
data class SoundCue(val event: SoundEvent, val take: Int, val volume: Float, val rate: Float = 1f)

/**
 * Which recordings play, how loud and at what pitch (T41). Each event picks a random take, never the same one twice in
 * a row, on top of its random pitch. Nothing plays while [soundOn] is false, read at the moment of each event.
 */
class SoundCues(private val random: Random, private val soundOn: () -> Boolean) {
    private val lastTake = IntArray(SoundEvent.entries.size) { -1 }

    /** A gem placed: a tap's click at ±3 semitones, or a drag's softer tick at ±1.5. */
    fun placed(drag: Boolean): List<SoundCue> = if (drag) {
        cue(SoundEvent.DRAG, DRAG_VOLUME, semitones(DRAG_PITCH_RANGE))
    } else {
        cue(SoundEvent.PLACE, CLICK_VOLUME, semitones(CLICK_PITCH_RANGE))
    }

    /** The [step]th click of a tool's batch (#20): a placing click, a semitone higher each time. */
    fun batchClick(step: Int): List<SoundCue> = cue(SoundEvent.PLACE, CLICK_VOLUME, 2f.pow(step / 12f))

    fun colorDone(): List<SoundCue> = cue(SoundEvent.COLOR_DONE, COLOR_DONE_VOLUME)

    /** A progress milestone (#35): a short bell, recorded a fifth above the finish chime. */
    fun milestone(): List<SoundCue> = cue(SoundEvent.MILESTONE, MILESTONE_VOLUME)

    /** The low finish chime with its fifth and octave above (#57), all three from the same take. */
    fun finish(): List<SoundCue> {
        if (!soundOn()) return emptyList()
        val take = nextTake(SoundEvent.FINISH)
        return listOf(
            SoundCue(SoundEvent.FINISH, take, CHIME_VOLUME),
            SoundCue(SoundEvent.FINISH, take, CHIME_FIFTH_VOLUME, rate = FIFTH),
            SoundCue(SoundEvent.FINISH, take, CHIME_OCTAVE_VOLUME, rate = OCTAVE),
        )
    }

    private fun cue(event: SoundEvent, volume: Float, rate: Float = 1f): List<SoundCue> =
        if (soundOn()) listOf(SoundCue(event, nextTake(event), volume, rate)) else emptyList()

    private fun nextTake(event: SoundEvent): Int {
        val last = lastTake[event.ordinal]
        // Draw from the takes other than the last one, so the same recording never plays twice in a row.
        var take = random.nextInt(if (last < 0) event.takes else event.takes - 1)
        if (last >= 0 && take >= last) take++
        lastTake[event.ordinal] = take
        return take
    }

    private fun semitones(range: Float): Float = 2f.pow((random.nextFloat() * 2f * range - range) / 12f)

    companion object {
        const val CLICK_VOLUME = 0.35f
        const val DRAG_VOLUME = 0.22f
        const val COLOR_DONE_VOLUME = 0.6f
        const val MILESTONE_VOLUME = 0.4f
        const val CHIME_VOLUME = 0.7f
        const val CHIME_FIFTH_VOLUME = 0.35f
        const val CHIME_OCTAVE_VOLUME = 0.25f
        const val CLICK_PITCH_RANGE = 3f
        const val DRAG_PITCH_RANGE = 1.5f
        const val FIFTH = 1.5f
        const val OCTAVE = 2f
    }
}
