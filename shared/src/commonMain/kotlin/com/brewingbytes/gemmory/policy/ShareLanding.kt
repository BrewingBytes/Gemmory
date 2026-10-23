package com.brewingbytes.gemmory.policy

/** What the first gallery session does with a share link's picture (T50). */
enum class LandingAction {
    /** Open its canvas now: it was ready when the gallery first came into view. */
    OPEN,

    /** It became ready after the gallery drew: offer it ("Shared with you: … · Open"), no jump (DR2). */
    OFFER,

    /** Not decided yet: the referrer isn't read, or a remote picture waits for the session's first catalog check. */
    WAIT,

    /** Nothing to open, now or later. */
    DROP,
}

/** One look at the landing: what's known at this moment. */
data class LandingInput(
    /** The gallery is in view: consent has settled and its tiles have loaded. */
    val inView: Boolean,
    /** The install referrer's read has ended (with or without a referrer). */
    val referrerRead: Boolean,
    /** The share link's picture id ([ShareLanding.pictureId]); null when the referrer carries none. */
    val pictureId: String?,
    /** The id is a picture this phone can list now (`openableRef`): not a hidden drop, an unearned keepsake, unknown or crafted. */
    val openable: Boolean,
    /** A catalog check has ended in this session, whatever its outcome. */
    val checkEnded: Boolean,
)

/**
 * The shared picture landing (T50, CEO O4, eng O-E3): at the first launch of a fresh install, a share link's picture
 * replaces the starter as the first canvas, once. It opens directly when it's ready as the gallery first comes into view;
 * ready later (a slow referrer, or a remote picture the first catalog check downloads), it's offered instead; a remote id
 * still unknown after that check is dropped. It ends for good once it opens, is offered, is dropped, or the player
 * leaves the gallery (a canvas opened by hand included). Nothing here waits on the network: the gallery draws first.
 * Not thread-safe; the caller serializes.
 */
class ShareLanding {
    private var looked = false

    /** The landing has ended (opened, offered, dropped or left): every later look is [LandingAction.DROP]. */
    var over = false
        private set

    fun look(input: LandingInput): LandingAction {
        if (over) return LandingAction.DROP
        if (!input.inView) return LandingAction.WAIT
        val first = !looked
        looked = true
        val action = decide(input, first)
        if (action != LandingAction.WAIT) over = true
        return action
    }

    /** The player left the gallery (or opened a canvas): the landing is over, whatever was pending. */
    fun left() {
        over = true
    }

    companion object {
        /** How long the late offer ("Shared with you: … · Open") stays: 8 s, or until it's used while TalkBack is on (D7). */
        const val OFFER_MS = 8_000L

        fun offerTimeoutMs(touchExploration: Boolean): Long? = if (touchExploration) null else OFFER_MS

        /** The share link's picture id: `utm_content`, whatever the source, so a founder's own campaign links land too. */
        fun pictureId(referrer: Referrer): String? = referrer.content

        internal fun decide(input: LandingInput, firstLook: Boolean): LandingAction = when {
            !input.referrerRead -> LandingAction.WAIT
            input.pictureId == null -> LandingAction.DROP
            input.openable -> if (firstLook) LandingAction.OPEN else LandingAction.OFFER
            input.checkEnded -> LandingAction.DROP
            else -> LandingAction.WAIT
        }
    }
}
