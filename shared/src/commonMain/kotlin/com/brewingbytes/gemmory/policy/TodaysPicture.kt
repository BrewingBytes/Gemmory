package com.brewingbytes.gemmory.policy

/**
 * Today's picture as picked on [day] (local epoch days): [pictureId], or null when there was nothing to pick. [vip]
 * is the tier it was picked for, and [before] the last earlier pick, which the next day avoids.
 */
data class TodayState(val day: Long, val pictureId: String?, val vip: Boolean, val before: String? = null)

/**
 * Today's picture (#34): one bundled picture a day, picked on the phone with no server. For a free player it's a
 * locked picture they haven't unlocked or started, free to start today with no video; for VIP (nothing is locked)
 * it's a picture they haven't started, "Today's pick". The pick is date-seeded and remembered for the day, so
 * starting it, unlocking it or reopening the app doesn't move it. It changes at local midnight (the allowance's
 * [DayClock]); a moved clock or time zone can show another picture, an accepted risk. Not thread-safe.
 */
class TodaysPicture(private val clock: DayClock, private val store: StateStore<TodayState>) {
    private var state: TodayState? = store.load()

    /**
     * Today's picture id. The first call of a day picks from [candidates] (in catalog order); later calls that day
     * return the same one, whatever the candidates are by then. A tier change re-picks, as does a day that had
     * nothing to pick once something is.
     */
    fun pick(candidates: List<String>, vip: Boolean): String? {
        val today = clock.today()
        val s = state
        if (s != null && s.day == today && s.vip == vip && (s.pictureId != null || candidates.isEmpty())) return s.pictureId
        val before = if (s != null && s.day != today) s.pictureId ?: s.before else s?.before
        val next = TodayState(today, choose(today, candidates, before), vip, before)
        state = next
        store.save(next)
        return next.pictureId
    }

    /** Today's picture for [vip] if one was picked today, without picking. */
    fun picked(vip: Boolean): String? = state?.takeIf { it.day == clock.today() && it.vip == vip }?.pictureId

    companion object {
        /**
         * The pick for [day] from [candidates]: a date-seeded index, skipping [avoid] (the last pick) when there's
         * anything else. Null when there's nothing to pick.
         */
        fun choose(day: Long, candidates: List<String>, avoid: String?): String? {
            val pool = candidates.filter { it != avoid }.ifEmpty { candidates }
            if (pool.isEmpty()) return null
            return pool[(mix(day) ushr 1).rem(pool.size.toLong()).toInt()]
        }

        /** SplitMix64's finalizer: neighboring days land far apart, the same on every platform. */
        private fun mix(day: Long): Long {
            var z = day + -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
