package com.brewingbytes.gemmory.timelapse

/**
 * The share text's tracked Play link (eng D14): `utm_medium` says whether a bundled picture or a photo was shared, or
 * the streak card (#104); `utm_campaign` whether it was a time-lapse or the streak card. A bundled or remote picture's
 * share also carries its id in `utm_content` (T50), so a fresh install from the link opens that picture; a photo and
 * the streak card never do.
 */
object ShareLink {
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory"

    enum class Medium(val value: String) { BUNDLED("bundled"), PHOTO("photo"), STREAK("streak") }

    /**
     * The install referrer, URL-encoded once inside the `referrer` parameter, as Play expects. [pictureId] goes in
     * `utm_content` for a picture share only; catalog ids are already URL-safe.
     */
    fun url(medium: Medium, campaign: String = TIMELAPSE, pictureId: String? = null): String {
        val content = pictureId?.takeIf { medium == Medium.BUNDLED }?.let { "%26utm_content%3D$it" }.orEmpty()
        return "$PLAY_URL&referrer=utm_source%3Dshare%26utm_medium%3D${medium.value}%26utm_campaign%3D$campaign$content"
    }

    /** The milestone share card's link (#104). The share text around a link is a string resource (T48). */
    fun streakUrl(): String = url(Medium.STREAK, STREAK_CARD)

    private const val TIMELAPSE = "timelapse"
    private const val STREAK_CARD = "streak_card"
}
