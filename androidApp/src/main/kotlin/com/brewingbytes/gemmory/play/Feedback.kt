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
import com.brewingbytes.gemmory.game.SoundCue
import com.brewingbytes.gemmory.game.SoundCues
import com.brewingbytes.gemmory.game.SoundEvent
import com.brewingbytes.gemmory.game.Sweep
import com.brewingbytes.gemmory.settings.AppSettings
import kotlin.random.Random

/**
 * Haptics and sound for the play canvas and the completion screen (design system, Haptics and sound). Each follows
 * its Settings toggle, read at the moment it plays; haptics also follow the system touch-feedback setting. Which
 * recording plays, and at what pitch, comes from [SoundCues] (T41; made by tools/make_sounds.py).
 */
class Feedback(context: Context, private val settings: AppSettings) {
    private val appContext = context.applicationContext
    private val sounds = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val takes = mapOf(
        SoundEvent.PLACE to listOf(R.raw.gem_place_1, R.raw.gem_place_2, R.raw.gem_place_3, R.raw.gem_place_4, R.raw.gem_place_5),
        SoundEvent.DRAG to listOf(R.raw.gem_drag_1, R.raw.gem_drag_2, R.raw.gem_drag_3),
        SoundEvent.COLOR_DONE to listOf(R.raw.color_done_1, R.raw.color_done_2, R.raw.color_done_3),
        SoundEvent.MILESTONE to listOf(R.raw.milestone_1, R.raw.milestone_2, R.raw.milestone_3),
        SoundEvent.FINISH to listOf(R.raw.finish_chime_1, R.raw.finish_chime_2, R.raw.finish_chime_3),
    ).mapValues { (_, raws) -> raws.map { sounds.load(appContext, it, 1) } }
    private val cues = SoundCues(Random.Default) { settings.sound.value }
    private var lastTickAt = 0L
    private val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.CONTEXT_CLICK
    }

    private val hapticsOn: Boolean get() = settings.haptics.value

    /**
     * Tick throttled to one per 40ms; a placing click at a random ±3 semitone pitch, or while [drag] painting a softer
     * tick at ±1.5.
     */
    fun gemPlaced(view: View, drag: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (now - lastTickAt >= TICK_THROTTLE_MS) {
            lastTickAt = now
            haptic(view, HapticFeedbackConstants.CLOCK_TICK)
        }
        play(cues.placed(drag))
    }

    /**
     * A tool's batch (#20) over [durationMs]: a light haptic sweep, ticks every 60ms swelling from 0.3 to 0.6
     * (primitives, else an amplitude waveform, else one tick). The clicks come from [sweepClick], timed by the caller.
     */
    fun sweep(view: View, durationMs: Int) {
        if (!hapticsOn) return
        val vibrator = vibrator()
        val ticks = Sweep.hapticTicks(durationMs)
        val effect = when {
            vibrator == null -> null
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK) -> {
                val composition = VibrationEffect.startComposition()
                for (tick in ticks) composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, tick.scale, tick.delayMs)
                composition.compose()
            }
            vibrator.hasAmplitudeControl() -> {
                // Each tick a 12ms pulse at its scale, then silence to the next.
                val timings = LongArray(ticks.size * 2) { if (it % 2 == 0) SWEEP_PULSE_MS else (Sweep.TICK_GAP_MS - SWEEP_PULSE_MS) }
                val amplitudes = IntArray(ticks.size * 2) { if (it % 2 == 0) (ticks[it / 2].scale * 255).toInt() else 0 }
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            }
            else -> null
        }
        if (effect == null || !vibrate(vibrator!!, effect)) haptic(view, HapticFeedbackConstants.CLOCK_TICK)
    }

    /** The [step]th click of a batch's run (#20): a placing click, a semitone higher each time. */
    fun sweepClick(step: Int) = play(cues.batchClick(step))

    /** Lighter than the tick, and silent. */
    fun wrongColor(view: View) = haptic(view, HapticFeedbackConstants.TEXT_HANDLE_MOVE)

    fun colorFinished(view: View) {
        haptic(view, confirm)
        play(cues.colorDone())
    }

    /** A progress milestone (#35): a short bell a fifth above the finish chime, and the color-finished haptic. */
    fun milestone(view: View) {
        haptic(view, confirm)
        play(cues.milestone())
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
        play(cues.finish())
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

    /** [SoundCues] has already left out every cue while Sound is off. */
    private fun play(cues: List<SoundCue>) {
        for (cue in cues) sounds.play(takes.getValue(cue.event)[cue.take], cue.volume, cue.volume, 1, 0, cue.rate)
    }

    /** Before API 33 the vibration can't carry a touch usage, so the touch-feedback setting is read directly. */
    @Suppress("DEPRECATION")
    private fun legacyTouchFeedbackOn(): Boolean =
        Settings.System.getInt(appContext.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private companion object {
        const val TICK_THROTTLE_MS = 40L
        const val FINISH_VIBRATION_MS = 400L
        const val SWEEP_PULSE_MS = 12L
        const val SOFT_AMPLITUDE = 60
        // The finish's three layers ring for ~1.8s; the rest leaves room for clicks over them.
        const val MAX_STREAMS = 6
    }
}
