package com.brewingbytes.gemmory.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.brewingbytes.gemmory.SharedResources
import com.brewingbytes.gemmory.game.FocusChange
import com.brewingbytes.gemmory.game.MusicOutput

/** A music track that couldn't be opened or failed while playing (T42). Codes only, no paths. */
class MusicException(message: String) : Exception(message)

/**
 * [MusicOutput] on MediaPlayer and AudioManager (T42), one player at a time, on the main thread. Music is game audio
 * ([AudioAttributes.USAGE_GAME], [AudioAttributes.CONTENT_TYPE_MUSIC]) with focus [AudioManager.AUDIOFOCUS_GAIN]: the
 * system ducks it under a navigation prompt or a notification, and a call or another app's media takes the focus. Each
 * start and resume fades in over [FADE_MS].
 */
class MediaMusicOutput(
    context: Context,
    /** Each track's path under the shared resources (`music/music_<n>.m4a`, T63), as [SharedResources.file] takes it. */
    private val tracks: List<String>,
    private val onEnded: () -> Unit,
    private val onError: (Throwable) -> Unit,
    onFocus: (FocusChange) -> Unit,
) : MusicOutput {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> onFocus(FocusChange.GAIN)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onFocus(FocusChange.LOSS_TRANSIENT)
                AudioManager.AUDIOFOCUS_LOSS -> onFocus(FocusChange.LOSS)
                // A duck: the system lowers the music itself.
                else -> Unit
            }
        }, handler)
        .build()
    private var player: MediaPlayer? = null
    private var fade: Runnable? = null

    /** The gain the music is at or fading to; tests read it. */
    var gain = 0f
        private set

    override fun start(track: Int, volume: Float) {
        stop()
        val p = open(track)
        p.setOnCompletionListener { onEnded() }
        p.setOnErrorListener { _, what, extra ->
            onError(MusicException("music track=$track what=$what extra=$extra"))
            true
        }
        player = p
        fadeIn(p, volume)
        p.start()
    }

    /**
     * The track straight from the APK's assets, where it is stored uncompressed (aapt doesn't compress `.m4a`), so
     * MediaPlayer reads it in place as it read the raw resource before T63.
     */
    private fun open(track: Int): MediaPlayer {
        val player = MediaPlayer()
        try {
            // In MediaPlayer.create's order: the session id is only accepted before a data source is set.
            player.setAudioAttributes(attributes)
            player.audioSessionId = audio.generateAudioSessionId()
            app.assets.openFd(SharedResources.file(tracks[track])).use { fd ->
                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            player.prepare()
        } catch (e: Exception) {
            player.release()
            throw MusicException("music track=$track failed to open: ${e::class.simpleName}")
        }
        return player
    }

    override fun pause() {
        cancelFade()
        player?.pause()
    }

    override fun resume(volume: Float) {
        val p = player ?: return
        fadeIn(p, volume)
        p.start()
    }

    override fun setVolume(volume: Float) {
        cancelFade()
        gain = volume
        player?.setVolume(volume, volume)
    }

    override fun stop() {
        cancelFade()
        player?.release()
        player = null
    }

    /**
     * The music stream is active and a media player is among the active ones. Ours and the sound effects are game
     * audio, so a track of ours that just stopped (Android counts it a moment longer) isn't read as other media.
     */
    override fun otherAudioPlaying(): Boolean =
        audio.isMusicActive && audio.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA }

    override fun requestFocus(): Boolean = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun abandonFocus() {
        audio.abandonAudioFocusRequest(focus)
    }

    private fun fadeIn(p: MediaPlayer, target: Float) {
        cancelFade()
        gain = target
        var step = 0
        p.setVolume(0f, 0f)
        val r = object : Runnable {
            override fun run() {
                step++
                val v = target * step / FADE_STEPS
                p.setVolume(v, v)
                if (step < FADE_STEPS) handler.postDelayed(this, FADE_MS / FADE_STEPS) else fade = null
            }
        }
        fade = r
        handler.postDelayed(r, FADE_MS / FADE_STEPS)
    }

    private fun cancelFade() {
        fade?.let(handler::removeCallbacks)
        fade = null
    }

    private companion object {
        const val FADE_MS = 1_000L
        const val FADE_STEPS = 20
    }
}
