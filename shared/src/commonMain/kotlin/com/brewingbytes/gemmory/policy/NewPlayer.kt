package com.brewingbytes.gemmory.policy

/**
 * Whether a launch is a fresh install (T46, round-3 CEO O6, eng O-E7/8): nothing the app writes once it's played is on
 * the disk. Not `firstInstallTime == lastUpdateTime`, since an auto-update before the first open must still count, and
 * not "any preferences", since a boot or update receiver can write some before the first open. `ledger/` isn't asked:
 * it isn't backed up, so a restored phone has none. A restored phone isn't fresh (its canvases, achievements, streak or
 * marker came back); an 0.8.1 install never opened is; an 0.8.1 tester who opened the app but never started a canvas
 * counts as fresh (accepted).
 */
object NewPlayer {
    /**
     * What's on the disk at process start, before anything of this launch writes. [canvas]: a canvas directory under
     * `canvases/`; [achievements]: `achievements/state`; [streak]: the streak preferences; [marker]: the backed-up
     * marker written at the first foreground.
     */
    data class Disk(val canvas: Boolean, val achievements: Boolean, val streak: Boolean, val marker: Boolean)

    fun isFresh(disk: Disk): Boolean = !disk.canvas && !disk.achievements && !disk.streak && !disk.marker
}

/**
 * A fresh install's funnel (T46) on this phone only (never backed up): when it first came to the screen, whether
 * `first_launch` and `first_gem` were logged, and its install referrer, read once. Absent for any other install.
 */
data class FirstRunState(
    val firstLaunchMillis: Long,
    val launchLogged: Boolean = false,
    val gemLogged: Boolean = false,
    val referrerRead: Boolean = false,
    /** Play's raw install referrer (`utm_source=…&…`), kept until T50 hands its picture id over. */
    val referrerRaw: String? = null,
)

/**
 * `first_launch` and `first_gem` (T46, CEO O5): each once, and only for a fresh install. [begin] starts the funnel at
 * a fresh install's first foreground; the referrer read ([referrerDue], [referrerRead]) can end before it (it starts
 * at process start), so its result waits in memory until then. Not thread-safe.
 */
class FirstRunFunnel(private val store: StateStore<FirstRunState>) {
    private var state: FirstRunState? = store.load()
    private var referrerBeforeBegin: String? = null
    private var readBeforeBegin = false

    /** This install's funnel has begun: it was fresh. */
    val fresh: Boolean get() = state != null

    /** The raw install referrer, once read (null when Play gave none). */
    val referrer: String? get() = state?.referrerRaw ?: referrerBeforeBegin

    /** A fresh install came to the screen for the first time: its clock starts at [nowMillis]. Once. */
    fun begin(nowMillis: Long) {
        if (state != null) return
        save(FirstRunState(nowMillis, referrerRead = readBeforeBegin, referrerRaw = referrerBeforeBegin))
    }

    /** Whether to log `first_launch` now (consent has settled): true once. */
    fun launchDue(): Boolean {
        val s = state ?: return false
        if (s.launchLogged) return false
        save(s.copy(launchLogged = true))
        return true
    }

    /** A gem was placed at [nowMillis]: `first_gem`'s milliseconds since the first launch, once; else null. */
    fun gemPlaced(nowMillis: Long): Long? {
        val s = state ?: return null
        if (s.gemLogged) return null
        save(s.copy(gemLogged = true))
        return (nowMillis - s.firstLaunchMillis).coerceAtLeast(0)
    }

    /** Whether to read the install referrer: once, for a launch that's fresh on [freshDisk] or a begun funnel. */
    fun referrerDue(freshDisk: Boolean): Boolean = when (val s = state) {
        null -> freshDisk && !readBeforeBegin
        else -> !s.referrerRead
    }

    /** The read ended with [raw], or null when Play gave nothing (unavailable, unsupported, slow or an error). */
    fun referrerRead(raw: String?) {
        val s = state
        if (s == null) {
            readBeforeBegin = true
            referrerBeforeBegin = raw
        } else if (!s.referrerRead) {
            save(s.copy(referrerRead = true, referrerRaw = raw))
        }
    }

    private fun save(value: FirstRunState) {
        state = value
        store.save(value)
    }
}
