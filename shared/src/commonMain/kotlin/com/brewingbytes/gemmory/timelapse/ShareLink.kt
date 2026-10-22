package com.brewingbytes.gemmory.timelapse

/**
 * The share text's tracked Play link (eng D14): `utm_medium` says whether a bundled picture or a photo was shared, or
 * the streak card (#104); `utm_campaign` whether it was a time-lapse or the streak card.
 */
object ShareLink {
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory"

    enum class Medium(val value: String) { BUNDLED("bundled"), PHOTO("photo"), STREAK("streak") }

    /** The install referrer, URL-encoded once inside the `referrer` parameter, as Play expects. */
    fun url(medium: Medium, campaign: String = TIMELAPSE): String =
        "$PLAY_URL&referrer=utm_source%3Dshare%26utm_medium%3D${medium.value}%26utm_campaign%3D$campaign"

    fun text(medium: Medium): String = "Made with Gemmory: ${url(medium)}"

    /** The milestone share card's text (#104). */
    fun streakText(): String = "Made with Gemmory: ${url(Medium.STREAK, STREAK_CARD)}"

    private const val TIMELAPSE = "timelapse"
    private const val STREAK_CARD = "streak_card"
}
