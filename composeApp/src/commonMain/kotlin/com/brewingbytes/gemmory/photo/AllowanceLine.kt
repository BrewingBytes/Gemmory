package com.brewingbytes.gemmory.photo

import com.brewingbytes.gemmory.copy.Words
import com.brewingbytes.gemmory.policy.Allowance

/**
 * The allowance line on Preview and the gallery note (design D5); null when there is none (VIP). [kept]: photo
 * canvases kept in the inventory (#38); a bonus from before the update, used today, reads as before.
 */
fun allowanceLine(s: Words, allowance: Allowance, kept: Int = 0): String? = when (allowance) {
    Allowance.Unlimited -> null
    Allowance.Free -> s.text("allowance_free")
    Allowance.Bonus -> if (kept > 0) s.plural("allowance_kept", kept, kept) else s.text("allowance_bonus")
    Allowance.WatchVideo -> s.text("allowance_watch")
}
