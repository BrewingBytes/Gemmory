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
    /** Play's raw install referrer (`utm_source=…&…`), kept until the share landing (T50) has used it, then dropped. */
    val referrerRaw: String? = null,
    /** The shared picture the landing opened (T50): free to start on [sharedDay] (a local day), unlocked by its first gem. */
    val sharedId: String? = null,
    val sharedDay: Long = 0,
    /** The share landing has ended (T50): a referrer read that ends later isn't kept. */
    val landingEnded: Boolean = false,
    /** Where "Where did you find Gemmory?" (T56) stands; null until the first fresh finish. */
    val whereFound: WhereFound? = null,
    /** Shuffles T56's answers, once per install, so a lazy tap for the reward favors no channel (design D10). */
    val whereFoundSeed: Long = 0,
)

/**
 * "Where did you find Gemmory?" (T56) on a first-run install, decided at its first fresh finish and never again:
 *
 *     null ──first fresh finish──► DECIDED ──row shown──► SHOWN ──answer──► ANSWERED
 *       │                            │                     └──Skip, back, dismiss──► SKIPPED
 *       └──first fresh finish, analytics consent denied, or the decision didn't save──► NOT_SHOWN
 *
 * DECIDED is kept when the player leaves before the row shows. NOT_SHOWN, ANSWERED and SKIPPED are final.
 */
enum class WhereFound { DECIDED, NOT_SHOWN, SHOWN, ANSWERED, SKIPPED }

/** T56's choices as reported (`where_found` `answer`): fixed values, whatever the words in any language say. */
enum class WhereFoundAnswer(val value: String) {
    VIDEO("video"),
    COMMUNITY("community"),
    FRIEND("friend"),
    PLAY_SEARCH("play_search"),
    OTHER("other"),
    SKIP("skip"),
    ;

    companion object {
        /** The five answers in this install's order: the first four shuffled by [seed], "Somewhere else" last (D10). */
        fun order(seed: Long): List<WhereFoundAnswer> =
            listOf(VIDEO, COMMUNITY, FRIEND, PLAY_SEARCH).shuffled(kotlin.random.Random(seed)) + OTHER
    }
}

/** What a first-run install's first fresh finish decided for T56. */
sealed interface FirstRunAsk {
    /** Show the row, its answers in [order]. */
    data class Show(val order: List<WhereFoundAnswer>) : FirstRunAsk

    /** Not this finish: not a first-run install, already decided, or consent denied. */
    data object No : FirstRunAsk

    /** The decision couldn't be saved, so nothing is shown (eng R1); the caller reports it. */
    data object SaveFailed : FirstRunAsk
}

/**
 * `first_launch` and `first_gem` (T46, CEO O5): each once, and only for a fresh install. [begin] starts the funnel at
 * a fresh install's first foreground; the referrer read ([referrerDue], [referrerRead]) can end before it (it starts
 * at process start), so its result waits in memory until then. Not thread-safe.
 */
class FirstRunFunnel(private val store: CheckedStateStore<FirstRunState>) {
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
            // A landing that ended before the read did (the player left the gallery) has no use for it (T50).
            save(s.copy(referrerRead = true, referrerRaw = raw.takeUnless { s.landingEnded }))
        }
    }

    /** The shared picture the landing opened (T50), while it's free: on the day it opened. Null otherwise. */
    fun sharedFree(today: Long): String? = state?.takeIf { it.sharedDay == today }?.sharedId

    /** A share landing opened (T50): the player is in the normal game from here on, whether or not they placed a gem. */
    val landed: Boolean get() = state?.sharedId != null

    /**
     * The share landing has ended (T50): it opened [sharedId] on [day] (null when nothing opened). The raw referrer
     * isn't needed any more, so it's dropped from the phone.
     */
    fun landingEnded(sharedId: String?, day: Long) {
        val s = state ?: return
        save(s.copy(referrerRaw = null, sharedId = sharedId ?: s.sharedId, sharedDay = if (sharedId != null) day else s.sharedDay, landingEnded = true))
    }

    /**
     * A fresh finish (T56): on a first-run install's first one, decide "Where did you find Gemmory?" and save that
     * before anything shows (eng R1): the row shows only when [analyticsGranted] and the decision reached the disk.
     * [seed] shuffles the answers. Every later finish, and any other install, gets [FirstRunAsk.No].
     */
    fun firstRunAsk(analyticsGranted: Boolean, seed: Long): FirstRunAsk {
        val s = state ?: return FirstRunAsk.No
        if (s.whereFound != null) return FirstRunAsk.No
        val next = s.copy(whereFound = if (analyticsGranted) WhereFound.DECIDED else WhereFound.NOT_SHOWN, whereFoundSeed = seed)
        if (!store.trySave(next)) {
            // Never asked in this process; a later save of the funnel keeps "not shown", which is what happened.
            state = s.copy(whereFound = WhereFound.NOT_SHOWN)
            return FirstRunAsk.SaveFailed
        }
        state = next
        return if (analyticsGranted) FirstRunAsk.Show(WhereFoundAnswer.order(seed)) else FirstRunAsk.No
    }

    /** The row came to the screen (best effort). */
    fun whereFoundShown() {
        val s = state ?: return
        if (s.whereFound == WhereFound.DECIDED) save(s.copy(whereFound = WhereFound.SHOWN))
    }

    /**
     * The player answered [answer] ([WhereFoundAnswer.SKIP] for Skip, back or a dismissed sheet): true the first time,
     * false after, so a second tap does nothing.
     */
    fun whereFoundAnswered(answer: WhereFoundAnswer): Boolean {
        val s = state ?: return false
        if (s.whereFound != WhereFound.DECIDED && s.whereFound != WhereFound.SHOWN) return false
        save(s.copy(whereFound = if (answer == WhereFoundAnswer.SKIP) WhereFound.SKIPPED else WhereFound.ANSWERED))
        return true
    }

    private fun save(value: FirstRunState) {
        state = value
        store.save(value)
    }
}
