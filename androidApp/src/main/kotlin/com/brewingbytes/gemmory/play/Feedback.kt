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
        val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        haptic(view, confirm)
        play(thump, THUMP_VOLUME)
    }

    /** One 400ms soft vibration and one low chime. */
    fun pictureFinished() {
        play(chime, CHIME_VOLUME)
        if (!hapticsOn) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Touch usage follows the system touch-feedback setting.
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else if (legacyTouchFeedbackOn()) {
            vibrator.vibrate(effect)
        }
    }

    fun release() = sounds.release()

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
    }
}
