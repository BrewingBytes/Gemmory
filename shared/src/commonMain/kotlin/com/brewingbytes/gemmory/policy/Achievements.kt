package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.save.ByteReader
import com.brewingbytes.gemmory.save.ByteWriter
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.Envelope
import com.brewingbytes.gemmory.save.MalformedException
import com.brewingbytes.gemmory.save.Refusal

/**
 * The Play Games achievements (#49, founder list 2026-10-18; names, descriptions and points live in Play Console).
 * [code] is the save file's id and never changes. [steps] above 1 makes it incremental. Every one is earned by play
 * only: a fresh finish, a share or a streak day, never a purchase, a video or VIP.
 */
enum class Achievement(val code: Int, val steps: Int = 1) {
    /** Finish your first picture (the starter counts). */
    FIRST_SPARKLE(0),

    /** Finish a Quick picture (a picture or a photo canvas). */
    QUICK_STUDY(1),

    /** Finish a Medium picture. */
    STEADY_HANDS(2),

    /** Finish a Big picture. */
    THE_LONG_EVENING(3),

    /** Finish a canvas made from one of your photos. */
    YOUR_OWN_GEM(4),

    /** Share a finished picture (time-lapse or before/after). */
    SHOW_AND_TELL(5),

    /** Share a picture before its last gem (#36). */
    WORK_IN_PROGRESS(6),

    /** Finish 5 pictures. */
    TRAY_REGULAR(7, 5),

    /** Finish 25 pictures. */
    GALLERY_WALL(8, 25),

    /** Finish 5 canvases made from your photos. */
    PHOTO_ALBUM(9, 5),

    /** Finish a picture 7 days in a row: the best daily streak (#33). */
    A_WEEK_OF_GEMS(10, 7),

    /** Finish a picture 30 days in a row. */
    A_MONTH_OF_GEMS(11, 30),
    ;

    val incremental: Boolean get() = steps > 1

    companion object {
        fun ofCode(code: Int): Achievement? = entries.firstOrNull { it.code == code }
    }
}

/** What happened in play that achievements count. */
sealed interface GameEvent {
    /**
     * A fresh finish: the last gem placed now, never a finished canvas reopened. [size] is the canvas's Quick /
     * Medium / Big (null for the starter); [photo] is a photo canvas.
     */
    data class Finished(val size: CanvasSize?, val photo: Boolean) : GameEvent

    /** The player picked an app in the share sheet. [progress]: shared before the last gem. */
    data class Shared(val progress: Boolean) : GameEvent

    /** A new streak day (#33) of [length] days. */
    data class StreakDay(val length: Int) : GameEvent
}

/** One achievement to send to Play Games: unlock it, or set an incremental one's [steps]. */
data class PendingAchievement(val achievement: Achievement, val steps: Int)

/**
 * What this phone has earned (#49): [finishes], [photoFinishes] and the [bestStreak] for the incremental ones, the
 * one-off achievements in [unlocked], and whether the one-time [backfilled] count from the phone ran. [confirmed]
 * holds the steps Play Games confirmed (1 for a one-off) for [player], the signed-in Play Games player id.
 */
data class AchievementState(
    val finishes: Int = 0,
    val photoFinishes: Int = 0,
    val bestStreak: Int = 0,
    val unlocked: Set<Achievement> = emptySet(),
    val backfilled: Boolean = false,
    val player: String = "",
    val confirmed: Map<Achievement, Int> = emptyMap(),
) {
    /** Steps earned on this phone: an incremental one's count up to its target, else 1 once earned or 0. */
    fun progress(achievement: Achievement): Int = when (achievement) {
        Achievement.TRAY_REGULAR, Achievement.GALLERY_WALL -> finishes.coerceAtMost(achievement.steps)
        Achievement.PHOTO_ALBUM -> photoFinishes.coerceAtMost(achievement.steps)
        Achievement.A_WEEK_OF_GEMS, Achievement.A_MONTH_OF_GEMS -> bestStreak.coerceAtMost(achievement.steps)
        else -> if (achievement in unlocked) 1 else 0
    }
}

/**
 * The unlock rules (#49), pure: game events in, what to send out. Counters live only here and go to Play Games as
 * absolute steps (setSteps), never as increments, so replaying a send can't count twice.
 */
object AchievementRules {
    /** [state] after [event]. */
    fun apply(state: AchievementState, event: GameEvent): AchievementState = when (event) {
        is GameEvent.Finished -> {
            val earned = buildSet {
                add(Achievement.FIRST_SPARKLE)
                when (event.size) {
                    CanvasSize.QUICK -> add(Achievement.QUICK_STUDY)
                    CanvasSize.MEDIUM -> add(Achievement.STEADY_HANDS)
                    CanvasSize.BIG -> add(Achievement.THE_LONG_EVENING)
                    null -> Unit
                }
                if (event.photo) add(Achievement.YOUR_OWN_GEM)
            }
            state.copy(
                finishes = state.finishes + 1,
                photoFinishes = state.photoFinishes + if (event.photo) 1 else 0,
                unlocked = state.unlocked + earned,
            )
        }
        is GameEvent.Shared ->
            state.copy(unlocked = state.unlocked + if (event.progress) Achievement.WORK_IN_PROGRESS else Achievement.SHOW_AND_TELL)
        is GameEvent.StreakDay -> state.copy(bestStreak = maxOf(state.bestStreak, event.length))
    }

    /**
     * The one-time count from what's on the phone at the first start with achievements (founder, 2026-10-18): every
     * canvas finished on disk and today's streak. Shares were never recorded, so they start at zero. Runs once.
     */
    fun backfill(state: AchievementState, finished: List<GameEvent.Finished>, streak: Int): AchievementState {
        if (state.backfilled) return state
        val counted = finished.fold(state) { s, event -> apply(s, event) }
        return apply(counted, GameEvent.StreakDay(streak)).copy(backfilled = true)
    }

    /**
     * The state for [player]: confirmations belong to the player who confirmed them, so another signed-in player is
     * sent everything earned on this phone again.
     */
    fun forPlayer(state: AchievementState, player: String): AchievementState =
        if (state.player == player) state else state.copy(player = player, confirmed = emptyMap())

    /** Everything earned that Play Games hasn't confirmed, in [Achievement] order. */
    fun pending(state: AchievementState): List<PendingAchievement> = Achievement.entries.mapNotNull { achievement ->
        val steps = state.progress(achievement)
        if (steps > (state.confirmed[achievement] ?: 0)) PendingAchievement(achievement, steps) else null
    }

    /** Play Games confirmed [sent] for [player]; ignored if another player signed in meanwhile. */
    fun confirm(state: AchievementState, player: String, sent: PendingAchievement): AchievementState {
        if (state.player != player) return state
        val before = state.confirmed[sent.achievement] ?: 0
        return state.copy(confirmed = state.confirmed + (sent.achievement to maxOf(before, sent.steps)))
    }

    /** A grid's Quick / Medium / Big from its shape (the size picker's grids), or null (the starter, say). */
    fun sizeOf(width: Int, height: Int): CanvasSize? =
        GridSpec.PICKER.firstOrNull { it.width == width && it.height == height }
            ?.let { spec -> CanvasSize.entries.first { spec.id.startsWith(it.name.lowercase()) } }
}

/** The achievements on this phone, saved after every change, so an earned one survives process death until sent. */
class AchievementLedger(private val store: StateStore<AchievementState>) {
    var state: AchievementState = store.load() ?: AchievementState()
        private set

    /** Counts [event]. True when it earned something new to send. */
    fun record(event: GameEvent): Boolean = commit(AchievementRules.apply(state, event))

    /** The one-time count from the phone ([AchievementRules.backfill]); a no-op once it ran. */
    fun backfill(finished: List<GameEvent.Finished>, streak: Int): Boolean =
        commit(AchievementRules.backfill(state, finished, streak))

    /** What to send for [player]. */
    fun pendingFor(player: String): List<PendingAchievement> {
        commit(AchievementRules.forPlayer(state, player))
        return AchievementRules.pending(state)
    }

    fun confirm(player: String, sent: PendingAchievement) {
        commit(AchievementRules.confirm(state, player, sent))
    }

    private fun commit(next: AchievementState): Boolean {
        if (next == state) return false
        state = next
        store.save(next)
        return true
    }
}

/**
 * The achievements file `filesDir/achievements/state`, "GMRA" v1 (same envelope and CRC trailer as the save files).
 * Body: varint finishes, varint photoFinishes, varint bestStreak, u8 flags (bit 0 backfilled), u32 unlocked one-offs
 * (bit = code), u16 player byte length, the player id in UTF-8, u8 count, then per confirmation: u8 code, varint steps.
 */
object AchievementCodec {
    val MAGIC = "GMRA".encodeToByteArray()
    const val VERSION = 1

    fun encode(state: AchievementState): ByteArray {
        val w = ByteWriter()
        w.varint(state.finishes)
        w.varint(state.photoFinishes)
        w.varint(state.bestStreak)
        w.u8(if (state.backfilled) 1 else 0)
        w.u32(state.unlocked.fold(0) { bits, a -> bits or (1 shl a.code) })
        val player = state.player.encodeToByteArray()
        w.u16(player.size)
        w.bytes(player)
        w.u8(state.confirmed.size)
        for ((achievement, steps) in state.confirmed.entries.sortedBy { it.key.code }) {
            w.u8(achievement.code)
            w.varint(steps)
        }
        return Envelope.wrap(MAGIC, VERSION, w.toByteArray())
    }

    fun decode(bytes: ByteArray): Decoded<AchievementState> {
        val opened = when (val o = Envelope.open(bytes, MAGIC)) {
            is Decoded.Refused -> return o
            is Decoded.Ok -> o.value
        }
        if (opened.version > VERSION) return Decoded.Refused(Refusal.NewerVersion(opened.version))
        if (opened.version < VERSION) return Decoded.Refused(Refusal.NoMigration(opened.version))
        val state = try {
            val r = ByteReader(opened.body)
            val finishes = r.varint()
            val photoFinishes = r.varint()
            val bestStreak = r.varint()
            val flags = r.u8()
            val bits = r.u32()
            val length = r.u16()
            if (length > r.remaining) throw MalformedException()
            val player = ByteArray(length) { r.u8().toByte() }.decodeToString()
            val confirmed = buildMap {
                repeat(r.u8()) { put(Achievement.ofCode(r.u8()) ?: throw MalformedException(), r.varint()) }
            }
            val unlocked = Achievement.entries.filter { bits and (1 shl it.code) != 0 }.toSet()
            val valid = r.remaining == 0 && finishes >= 0 && photoFinishes in 0..finishes && bestStreak >= 0 &&
                flags in 0..1 && unlocked.none { it.incremental } && bits.countOneBits() == unlocked.size
            if (!valid) throw MalformedException()
            AchievementState(finishes, photoFinishes, bestStreak, unlocked, flags == 1, player, confirmed)
        } catch (_: MalformedException) {
            null
        } ?: return Decoded.Refused(Refusal.Malformed)
        return Decoded.Ok(state)
    }
}
