package com.brewingbytes.gemmory.timelapse

/**
 * The apps that get the feed cut (T51) instead of the full time-lapse when the player picks them in the share sheet:
 * TikTok (both of its package names), Instagram and YouTube. Every other app gets the full video and the share text.
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
