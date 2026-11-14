package com.brewingbytes.gemmory.timelapse

/** Which 9:16 frame a time-lapse is drawn in. */
enum class ShareFrame {
    /** The player's share (T53): the picture fitted above the Gemmory row, and the end card. */
    PLAYER,

    /** The founder clips (T47): their own feed-safe frame (#144), with the store lockup, the hook and the end card. */
    CLIP,

    /**
     * The feed cut (T51): 7 s in the founder clip's feed-safe frame, with no end card, made beside a finished non-VIP
     * player share for the apps in [FeedTargets].
     */
    FEED,
}
