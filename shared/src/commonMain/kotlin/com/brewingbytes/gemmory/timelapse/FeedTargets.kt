package com.brewingbytes.gemmory.timelapse

/**
 * The apps that get the feed cut (T51) instead of the full time-lapse when the player picks them in the share sheet:
 * TikTok (both of its package names), Instagram and YouTube. Every other app gets the full video and the share text.
 * In this order they get the share sheet's two entries of their own: YouTube last, since it reads the swapped stream
 * (checked on the emulator, T51) and the others aren't known to.
 */
object FeedTargets {
    val packages: List<String> = listOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.instagram.android",
        "com.google.android.youtube",
    )

    fun isFeed(packageName: String?): Boolean = packageName in packages
}
