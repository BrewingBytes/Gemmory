package com.brewingbytes.gemmory.telemetry

import android.content.SharedPreferences
import androidx.core.content.edit
import com.brewingbytes.gemmory.policy.FirstRunFunnel
import com.brewingbytes.gemmory.policy.FirstRunState
import com.brewingbytes.gemmory.policy.NewPlayer
import com.brewingbytes.gemmory.policy.StateStore
import com.brewingbytes.gemmory.storage.StorageLayout
import java.io.File

/**
 * The launch funnel on this phone (T46): whether this install is fresh ([NewPlayer], read from the disk when the
 * process starts, before anything of it writes), and `first_launch` / `first_gem` once each for a fresh install.
 * The backed-up [marker] ("new_player") is written at the first foreground, not at process start, so a boot or
 * update receiver starting the process of a never-opened install doesn't use up "fresh". The funnel itself lives
 * in [funnel] ("first_run"), excluded from backup and device transfer: a restored phone never inherits a pending
 * `first_gem` or the raw install referrer. Safe from any thread.
 */
class FirstRun(filesDir: File, streakPrefs: File, private val marker: SharedPreferences, funnel: SharedPreferences) {
    /** Nothing of a played install was on the disk when the process started. */
    val freshDisk: Boolean = NewPlayer.isFresh(
        NewPlayer.Disk(
            canvas = hasCanvas(File(filesDir, StorageLayout.CANVASES)),
            achievements = File(filesDir, StorageLayout.ACHIEVEMENTS).exists(),
            streak = streakPrefs.exists(),
            marker = marker.getBoolean(LAUNCHED, false),
        ),
    )

    private val funnel = FirstRunFunnel(PrefsStore(funnel))

    /** The process came to the screen for the first time: a fresh install's clock starts, and the marker is written. */
    @Synchronized
    fun onFirstForeground(nowMillis: Long) {
        if (freshDisk) funnel.begin(nowMillis)
        if (!marker.getBoolean(LAUNCHED, false)) marker.edit(commit = true) { putBoolean(LAUNCHED, true) }
    }

    /** Consent has settled: whether to log `first_launch` (once, a fresh install only). */
    @Synchronized
    fun launchDue(): Boolean = funnel.launchDue()

    /** A gem was placed: `first_gem`'s milliseconds since the first launch, once for a fresh install; else null. */
    @Synchronized
    fun gemPlaced(nowMillis: Long): Long? = funnel.gemPlaced(nowMillis)

    /** Whether this launch reads the install referrer: once, for a fresh install. */
    @Synchronized
    fun referrerDue(): Boolean = funnel.referrerDue(freshDisk)

    @Synchronized
    fun referrerRead(raw: String?) = funnel.referrerRead(raw)

    /** The raw install referrer of a fresh install, kept on the phone until T50 reads its picture id. */
    val referrer: String?
        @Synchronized get() = funnel.referrer

    private class PrefsStore(private val prefs: SharedPreferences) : StateStore<FirstRunState> {
        override fun load(): FirstRunState? {
            if (!prefs.contains(FIRST_LAUNCH)) return null
            return FirstRunState(
                firstLaunchMillis = prefs.getLong(FIRST_LAUNCH, 0),
                launchLogged = prefs.getBoolean(LAUNCH_LOGGED, false),
                gemLogged = prefs.getBoolean(GEM_LOGGED, false),
                referrerRead = prefs.getBoolean(REFERRER_READ, false),
                referrerRaw = prefs.getString(REFERRER, null),
            )
        }

        override fun save(value: FirstRunState) = prefs.edit(commit = true) {
            putLong(FIRST_LAUNCH, value.firstLaunchMillis)
            putBoolean(LAUNCH_LOGGED, value.launchLogged)
            putBoolean(GEM_LOGGED, value.gemLogged)
            putBoolean(REFERRER_READ, value.referrerRead)
            putString(REFERRER, value.referrerRaw)
        }
    }

    companion object {
        /** The marker's preferences, backed up (a restored phone isn't fresh). */
        const val MARKER_FILE = "new_player"

        /** The funnel's preferences, never backed up or transferred (backup_rules.xml, data_extraction_rules.xml). */
        const val FUNNEL_FILE = "first_run"

        private const val LAUNCHED = "launched"
        private const val FIRST_LAUNCH = "first_launch_millis"
        private const val LAUNCH_LOGGED = "launch_logged"
        private const val GEM_LOGGED = "gem_logged"
        private const val REFERRER_READ = "referrer_read"
        private const val REFERRER = "referrer"

        /** A canvas directory, `canvases/<kind>/<id>/`: an empty `canvases/<kind>/` isn't a canvas. */
        private fun hasCanvas(canvases: File): Boolean =
            canvases.listFiles()?.any { kind -> kind.isDirectory && kind.listFiles()?.any { it.isDirectory } == true } == true
    }
}
