package com.brewingbytes.gemmory.game

import kotlin.random.Random

/**
 * What the music waits for (T42; design system, Settings → Music). It plays only while every one allows it: Music on,
 * the first canvas opened once (plan-design-review D24), the ringer on normal (not silent or vibrate, eng C5), the app
 * on screen, and no full-screen ad or time-lapse export running.
 */
data class MusicGates(
    val on: Boolean = true,
    val unlocked: Boolean = false,
    val ringerNormal: Boolean = true,
    val foreground: Boolean = false,
    val adShowing: Boolean = false,
    val exporting: Boolean = false,
) {
    val allowed: Boolean get() = on && unlocked && ringerNormal && foreground && !adShowing && !exporting
}

/** Android's audio focus changes, as [Music] reads them. A duck isn't one: the system lowers the music itself. */
enum class FocusChange { GAIN, LOSS_TRANSIENT, LOSS }

/** The player and audio focus behind [Music]: MediaPlayer and AudioManager in the app, a fake in tests. */
interface MusicOutput {
    /** Plays [track] from its start at [volume]; throws when it can't be decoded. */
    fun start(track: Int, volume: Float)

    fun pause()

    fun resume(volume: Float)

    fun setVolume(volume: Float)

    /** Stops and lets the player go. */
    fun stop()

    /** Another app is playing media right now. */
    fun otherAudioPlaying(): Boolean

    /** Asks for audio focus; false when it's refused (a call, another app holding it). */
    fun requestFocus(): Boolean

    fun abandonFocus()
}

/** The tracks in their order in the app; a short loop plays [plays] times in a row so it doesn't come round too often. */
data class MusicTrack(val plays: Int = 1)

/** Volume, the one-time chip and the rotation's rules (T42). */
object MusicRules {
    /** On at 30% by default (CEO C5). */
    const val DEFAULT_VOLUME = 30
    const val VOLUME_STEP = 10
    const val MIN_VOLUME = 10
    const val MAX_VOLUME = 100

    /** The first-music chip goes after 8s, except under TalkBack, where it waits to be used (plan-design-review D20). */
    const val CHIP_MS = 8_000L

    /** A volume the slider can show: a multiple of [VOLUME_STEP] from [MIN_VOLUME] to [MAX_VOLUME]. */
    fun volumeStep(percent: Int): Int =
        ((percent + VOLUME_STEP / 2) / VOLUME_STEP * VOLUME_STEP).coerceIn(MIN_VOLUME, MAX_VOLUME)

    /** The player's gain for a Settings percentage. */
    fun gain(percent: Int): Float = volumeStep(percent) / 100f

    /**
     * The "Music is playing" chip (plan-design-review D24): once ever, on a gallery visit while music plays, once no
     * sheet is up. Never when music didn't start.
     */
    fun showChip(chipShown: Boolean, playing: Boolean, onGallery: Boolean, sheetUp: Boolean): Boolean =
        !chipShown && playing && onGallery && !sheetUp

    /** How long the chip stays: [CHIP_MS], or until it's used while TalkBack is on (null). */
    fun chipTimeoutMs(touchExploration: Boolean): Long? = if (touchExploration) null else CHIP_MS
}

/**
 * Ambient music (T42): random tracks, never the same one twice in a row, played only while [MusicGates.allowed]. A
 * fresh start doesn't go over other media, asks for audio focus first, pauses for a transient focus loss and comes back on the
 * gain, and after a permanent loss waits for the next foreground. Leaving the screen stops it, and the next start picks a
 * new track. A track that fails to decode turns music off for the session ([onFailure] reports it).
 *
 * Not thread-safe: the app calls it on the main thread.
 */
class Music(
    private val output: MusicOutput,
    private val tracks: List<MusicTrack>,
    private val random: Random,
    private val onFailure: (Throwable) -> Unit,
) {
    private enum class State { IDLE, PLAYING, PAUSED }

    private var state = State.IDLE
    private var gates = MusicGates()
    private var volume = MusicRules.DEFAULT_VOLUME
    private var focusHeld = false
    private var transientLoss = false
    private var lostForGood = false
    private var track = -1
    private var playsLeft = 0

    /** A track failed to decode: no music for the rest of the session. */
    var failed = false
        private set

    val playing: Boolean get() = state == State.PLAYING

    fun update(next: MusicGates) {
        // A new foreground or the switch turned back on tries again after a permanent focus loss.
        if ((next.foreground && !gates.foreground) || (next.on && !gates.on)) lostForGood = false
        gates = next
        apply()
    }

    fun setVolume(percent: Int) {
        volume = MusicRules.volumeStep(percent)
        if (state == State.PLAYING) output.setVolume(MusicRules.gain(volume))
    }

    fun onFocus(change: FocusChange) {
        when (change) {
            FocusChange.GAIN -> transientLoss = false
            FocusChange.LOSS_TRANSIENT -> {
                transientLoss = true
                if (state == State.PLAYING) {
                    output.pause()
                    state = State.PAUSED
                }
                return
            }
            FocusChange.LOSS -> {
                lostForGood = true
                transientLoss = false
                focusHeld = false
                if (state == State.PLAYING) {
                    output.pause()
                    state = State.PAUSED
                }
                output.abandonFocus()
                return
            }
        }
        apply()
    }

    /** The current track came to its end: again while it has plays left, else another one. */
    fun onTrackEnded() {
        if (state != State.PLAYING) return
        if (playsLeft > 1) {
            playsLeft--
            play(track)
        } else {
            play(nextTrack())
        }
    }

    /** The player reported an error mid-track. */
    fun onError(error: Throwable) = fail(error)

    private fun apply() {
        if (failed) return
        if (!gates.foreground) {
            // Leaving the screen stops it; the next start is a new track.
            if (state != State.IDLE) output.stop()
            state = State.IDLE
            track = -1
            releaseFocus()
            return
        }
        if (!gates.allowed || lostForGood) {
            if (state == State.PLAYING) {
                output.pause()
                state = State.PAUSED
            }
            releaseFocus()
            return
        }
        if (transientLoss || state == State.PLAYING) return
        if (!focusHeld) {
            // A fresh start never goes over other media. Resuming its own pause skips that check: Android still counts a
            // track paused a moment ago as playing. Either way, only with focus.
            if (state == State.IDLE && output.otherAudioPlaying()) return
            if (!output.requestFocus()) return
            focusHeld = true
        }
        if (state == State.PAUSED && track >= 0) {
            output.resume(MusicRules.gain(volume))
            state = State.PLAYING
        } else {
            play(nextTrack())
        }
    }

    private fun play(index: Int) {
        if (track != index) playsLeft = tracks[index].plays
        track = index
        try {
            output.start(index, MusicRules.gain(volume))
            state = State.PLAYING
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun fail(error: Throwable) {
        if (failed) return
        failed = true
        if (state != State.IDLE) output.stop()
        state = State.IDLE
        releaseFocus()
        onFailure(error)
    }

    private fun releaseFocus() {
        if (focusHeld) output.abandonFocus()
        focusHeld = false
        transientLoss = false
    }

    private fun nextTrack(): Int {
        if (tracks.size == 1) return 0
        // Draw from the tracks other than the last one, so the same one never plays twice in a row.
        val last = track
        var next = random.nextInt(if (last < 0) tracks.size else tracks.size - 1)
        if (last >= 0 && next >= last) next++
        return next
    }
}
