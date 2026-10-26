package com.brewingbytes.gemmory.telemetry

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.core.content.edit
import com.brewingbytes.gemmory.policy.CheckedStateStore
import com.brewingbytes.gemmory.policy.FirstRunAsk
import com.brewingbytes.gemmory.policy.FirstRunFunnel
import com.brewingbytes.gemmory.policy.FirstRunState
import com.brewingbytes.gemmory.policy.NewPlayer
import com.brewingbytes.gemmory.policy.WhereFound
import com.brewingbytes.gemmory.policy.WhereFoundAnswer
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
class FirstRun(
    filesDir: File,
    streakPrefs: File,
    private val marker: SharedPreferences,
    funnel: SharedPreferences,
    /** Connected tests only (T50): a launch counted as fresh whatever is on the disk. */
    forceFresh: Boolean = false,
    /** Where a T56 decision that didn't save is reported (eng R1). */
    private val reporter: NonFatalReporter? = null,
) {
    /** Nothing of a played install was on the disk when the process started. */
    val freshDisk: Boolean = forceFresh || NewPlayer.isFresh(
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

    /** The raw install referrer of a fresh install, kept on the phone until the share landing (T50) has used it. */
    val referrer: String?
        @Synchronized get() = funnel.referrer

    /** The referrer's read has ended (or this launch never reads one). */
    val referrerReadDone: Boolean
        @Synchronized get() = !funnel.referrerDue(freshDisk)

    /** A share landing opened its picture (T50): the gallery is the normal game from then on (DR4). */
    val landed: Boolean
        @Synchronized get() = funnel.landed

    /** The shared picture the landing opened, while it's free: on the local day it opened (T50). */
    @Synchronized
    fun sharedFree(today: Long): String? = funnel.sharedFree(today)

    /** The share landing ended on [day]: [sharedId] opened, or nothing did (null). The raw referrer is dropped. */
    @Synchronized
    fun landingEnded(sharedId: String?, day: Long) = funnel.landingEnded(sharedId, day)

    /**
     * A fresh finish (T56): whether to show "Where did you find Gemmory?", decided and saved once, at a first-run
     * install's first fresh finish. A decision that didn't reach the disk shows nothing and is reported.
     */
    @Synchronized
    fun firstRunAsk(analyticsGranted: Boolean, seed: Long, firstFinish: Boolean): FirstRunAsk =
        funnel.firstRunAsk(analyticsGranted, seed, firstFinish).also {
            if (it == FirstRunAsk.SaveFailed) reporter?.report(IllegalStateException("T56 decision not saved"))
        }

    /** T56 is still to be decided (so the caller looks for other finished canvases only then). */
    @Synchronized
    fun whereFoundDue(): Boolean = funnel.whereFoundDue()

    /** T56 is out and unanswered. */
    @Synchronized
    fun whereFoundOpen(): Boolean = funnel.whereFoundOpen()

    /** T56's row came to the screen. */
    @Synchronized
    fun whereFoundShown() = funnel.whereFoundShown()

    /** T56 was answered (or skipped): true the first time only. */
    @Synchronized
    fun whereFoundAnswered(answer: WhereFoundAnswer): Boolean = funnel.whereFoundAnswered(answer)

    private class PrefsStore(private val prefs: SharedPreferences) : CheckedStateStore<FirstRunState> {
        override fun load(): FirstRunState? {
            if (!prefs.contains(FIRST_LAUNCH)) return null
            return FirstRunState(
                firstLaunchMillis = prefs.getLong(FIRST_LAUNCH, 0),
                launchLogged = prefs.getBoolean(LAUNCH_LOGGED, false),
                gemLogged = prefs.getBoolean(GEM_LOGGED, false),
                referrerRead = prefs.getBoolean(REFERRER_READ, false),
                referrerRaw = prefs.getString(REFERRER, null),
                sharedId = prefs.getString(SHARED_ID, null),
                sharedDay = prefs.getLong(SHARED_DAY, 0),
                landingEnded = prefs.getBoolean(LANDING_ENDED, false),
                whereFound = prefs.getString(WHERE_FOUND, null)?.let { name -> WhereFound.entries.firstOrNull { it.name == name } },
                whereFoundSeed = prefs.getLong(WHERE_FOUND_SEED, 0),
            )
        }

        /** One synchronous commit, whose result is what T56 trusts (eng R1); KTX's `edit` would drop that result. */
        @SuppressLint("UseKtx")
        override fun trySave(value: FirstRunState): Boolean = prefs.edit().apply {
            putLong(FIRST_LAUNCH, value.firstLaunchMillis)
            putBoolean(LAUNCH_LOGGED, value.launchLogged)
            putBoolean(GEM_LOGGED, value.gemLogged)
            putBoolean(REFERRER_READ, value.referrerRead)
            putString(REFERRER, value.referrerRaw)
            putString(SHARED_ID, value.sharedId)
            putLong(SHARED_DAY, value.sharedDay)
            putBoolean(LANDING_ENDED, value.landingEnded)
            putString(WHERE_FOUND, value.whereFound?.name)
            putLong(WHERE_FOUND_SEED, value.whereFoundSeed)
        }.commit()
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
        private const val SHARED_ID = "shared_id"
        private const val SHARED_DAY = "shared_day"
        private const val LANDING_ENDED = "landing_ended"
        private const val WHERE_FOUND = "where_found"
        private const val WHERE_FOUND_SEED = "where_found_seed"

        /** A canvas directory, `canvases/<kind>/<id>/`: an empty `canvases/<kind>/` isn't a canvas. */
        private fun hasCanvas(canvases: File): Boolean =
            canvases.listFiles()?.any { kind -> kind.isDirectory && kind.listFiles()?.any { it.isDirectory } == true } == true
    }
}
