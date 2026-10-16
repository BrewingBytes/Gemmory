package com.brewingbytes.gemmory.play

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.game.FinishCelebration
import com.brewingbytes.gemmory.settings.AppSettings
import kotlin.math.pow
import kotlin.random.Random

/**
 * Haptics and sound for the play canvas and the completion screen (design system, Haptics and sound). Each follows
 * its Settings toggle, read at the moment it plays; haptics also follow the system touch-feedback setting.
 */
class Feedback(context: Context, private val settings: AppSettings) {
    private val appContext = context.applicationContext
    private val sounds = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val click = sounds.load(appContext, R.raw.gem_click, 1)
    private val thump = sounds.load(appContext, R.raw.flap_thump, 1)
    private val chime = sounds.load(appContext, R.raw.finish_chime, 1)
    private var lastTickAt = 0L
    private val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.CONTEXT_CLICK
    }

    private val soundOn: Boolean get() = settings.sound.value
    private val hapticsOn: Boolean get() = settings.haptics.value

    /** Tick throttled to one per 40ms (drag paint lands with #10); soft click at a random ±3 semitone pitch. */
    fun gemPlaced(view: View) {
        val now = SystemClock.uptimeMillis()
        if (now - lastTickAt >= TICK_THROTTLE_MS) {
            lastTickAt = now
            haptic(view, HapticFeedbackConstants.CLOCK_TICK)
        }
        val semitones = Random.nextFloat() * 6f - 3f
        play(click, CLICK_VOLUME, rate = 2f.pow(semitones / 12f))
    }

    /** Lighter than the tick, and silent. */
    fun wrongColor(view: View) = haptic(view, HapticFeedbackConstants.TEXT_HANDLE_MOVE)

    fun colorFinished(view: View) {
        haptic(view, confirm)
        play(thump, THUMP_VOLUME)
    }

    /** A progress milestone (#35): the finish chime a fifth higher and softer, and the color-finished haptic. */
    fun milestone(view: View) {
        haptic(view, confirm)
        play(chime, MILESTONE_VOLUME, rate = MILESTONE_RATE)
    }

    /** Whether [crescendo] is playing, so the chime doesn't add the soft finish vibration on top of its peak. */
    private var crescendoPlaying = false

    /**
     * The finish celebration's haptic (#57): ticks that speed up and swell across the 2.4s sweep, then a peak and a
     * quick fall on the chime. Primitives where the phone has them, else an amplitude waveform; a phone with neither
     * gets today's soft vibration from [pictureFinished] instead.
     */
    fun crescendo() {
        crescendoPlaying = false
        if (!hapticsOn) return
        val vibrator = vibrator() ?: return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
            )
        ) {
            val composition = VibrationEffect.startComposition()
            for (tick in FinishCelebration.crescendoTicks()) {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, tick.scale, tick.delayMs)
            }
            composition
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.6f)
                .compose()
        } else if (vibrator.hasAmplitudeControl()) {
            val (timings, amplitudes) = FinishCelebration.crescendoWaveform()
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            return
        }
        crescendoPlaying = vibrate(vibrator, effect)
    }

    /**
     * The chime: the low finish chime with its fifth and octave above (#57), and the 400ms soft vibration unless the
     * [crescendo] is already peaking on it.
     */
    fun pictureFinished() {
        play(chime, CHIME_VOLUME)
        play(chime, CHIME_FIFTH_VOLUME, rate = 1.5f)
        play(chime, CHIME_OCTAVE_VOLUME, rate = 2f)
        if (crescendoPlaying) {
            crescendoPlaying = false
            return
        }
        if (!hapticsOn) return
        val vibrator = vibrator() ?: return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
            )
        ) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.35f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.25f)
                .compose()
        } else {
            val amplitude = if (vibrator.hasAmplitudeControl()) SOFT_AMPLITUDE else VibrationEffect.DEFAULT_AMPLITUDE
            VibrationEffect.createOneShot(FINISH_VIBRATION_MS, amplitude)
        }
        vibrate(vibrator, effect)
    }

    private fun vibrator(): Vibrator? {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        }
        return vibrator?.takeIf { it.hasVibrator() }
    }

    /** False if the system touch-feedback setting is off before API 33, where the vibration can't carry the usage. */
    private fun vibrate(vibrator: Vibrator, effect: VibrationEffect): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Touch usage follows the system touch-feedback setting.
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            return true
        }
        if (!legacyTouchFeedbackOn()) return false
        vibrator.vibrate(effect)
        return true
    }

    /** Also stops a [crescendo] still rising when the completion screen is left mid-sequence. */
    fun release() {
        if (crescendoPlaying) vibrator()?.cancel()
        sounds.release()
    }

    private fun haptic(view: View, constant: Int) {
        if (hapticsOn) view.performHapticFeedback(constant)
    }

    private fun play(sound: Int, volume: Float, rate: Float = 1f) {
        if (soundOn) sounds.play(sound, volume, volume, 1, 0, rate)
    }

    /** Before API 33 the vibration can't carry a touch usage, so the touch-feedback setting is read directly. */
    @Suppress("DEPRECATION")
    private fun legacyTouchFeedbackOn(): Boolean =
        Settings.System.getInt(appContext.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private companion object {
        const val TICK_THROTTLE_MS = 40L
        const val FINISH_VIBRATION_MS = 400L
        const val SOFT_AMPLITUDE = 60
        const val CLICK_VOLUME = 0.35f
        const val THUMP_VOLUME = 0.6f
        const val CHIME_VOLUME = 0.7f
        const val CHIME_FIFTH_VOLUME = 0.35f
        const val CHIME_OCTAVE_VOLUME = 0.25f
        const val MILESTONE_VOLUME = 0.4f
        const val MILESTONE_RATE = 1.5f
    }
}
