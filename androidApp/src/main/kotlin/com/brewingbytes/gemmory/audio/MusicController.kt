package com.brewingbytes.gemmory.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.game.Music
import com.brewingbytes.gemmory.game.MusicGates
import com.brewingbytes.gemmory.game.MusicTrack
import com.brewingbytes.gemmory.settings.AppSettings
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Ambient music (T42; design system, Settings → Music), app-scoped: [Music] fed by the Settings switch and volume, the
 * first canvas (D24), the ringer, the foreground, full-screen ads and time-lapse exports. Main thread only. Music is
 * never part of a time-lapse: the encoder writes video only.
 */
class MusicController(
    context: Context,
    private val settings: AppSettings,
    private val reporter: NonFatalReporter,
    adShowing: Flow<Boolean>,
    exporting: Flow<Boolean>,
    scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val foreground = MutableStateFlow(false)
    private val ringerNormal = MutableStateFlow(true)
    private val _playing = MutableStateFlow(false)

    /** Music is playing now: the gallery's one-time chip waits for it, and the volume keys move it. */
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    /** Connected tests only: the ringer as a test sets it (silent and vibrate can't be set without DND access). */
    @VisibleForTesting
    internal var ringerModeForTest: Int? = null
        set(value) {
            field = value
            readRinger()
        }

    private val output: MediaMusicOutput = MediaMusicOutput(
        app,
        TRACKS.map { it.first },
        onEnded = {
            music.onTrackEnded()
            publish()
        },
        onError = {
            music.onError(it)
            publish()
        },
        onFocus = {
            music.onFocus(it)
            publish()
        },
    )
    private val music: Music = Music(output, TRACKS.map { it.second }, Random.Default) { reporter.report(it) }

    /** Connected tests only: the gain the music is at or fading to. */
    @get:VisibleForTesting
    internal val volumeForTest: Float get() = output.gain

    init {
        // Silent or vibrate pauses it at once (eng C5); back on normal, it carries on.
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = readRinger()
            },
            IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        readRinger()
        scope.launch {
            combine(
                combine(settings.music, settings.musicUnlocked, ringerNormal, ::Triple),
                combine(foreground, adShowing, exporting, ::Triple),
            ) { (on, unlocked, ringer), (front, ad, export) ->
                MusicGates(on = on, unlocked = unlocked, ringerNormal = ringer, foreground = front, adShowing = ad, exporting = export)
            }.collect {
                music.update(it)
                publish()
            }
        }
        scope.launch { settings.musicVolume.collect(music::setVolume) }
    }

    /** The process came to the screen or left it. Leaving stops the music. */
    fun setForeground(on: Boolean) {
        if (on) readRinger()
        foreground.value = on
    }

    /** A canvas opened (D24): from now on music plays, in the gallery too. */
    fun canvasOpened() = settings.unlockMusic()

    private fun readRinger() {
        ringerNormal.value = (ringerModeForTest ?: audio.ringerMode) == AudioManager.RINGER_MODE_NORMAL
    }

    private fun publish() {
        _playing.value = music.playing
    }

    private companion object {
        /**
         * The six loops (tools/sounds/PROVENANCE.md), each played once except the 34s Heavenly Loop, three times in a
         * row so it isn't heard too often.
         */
        val TRACKS = listOf(
            R.raw.music_1 to MusicTrack(plays = 3),
            R.raw.music_2 to MusicTrack(),
            R.raw.music_3 to MusicTrack(),
            R.raw.music_4 to MusicTrack(),
            R.raw.music_5 to MusicTrack(),
            R.raw.music_6 to MusicTrack(),
        )
    }
}
