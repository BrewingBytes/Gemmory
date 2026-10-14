package com.brewingbytes.gemmory.timelapse

/** The share text's tracked Play link (eng D14): `utm_medium` says whether a bundled picture or a photo was shared. */
object ShareLink {
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory"

    enum class Medium(val value: String) { BUNDLED("bundled"), PHOTO("photo") }

    /** The install referrer, URL-encoded once inside the `referrer` parameter, as Play expects. */
    fun url(medium: Medium): String =
        "$PLAY_URL&referrer=utm_source%3Dshare%26utm_medium%3D${medium.value}%26utm_campaign%3Dtimelapse"

    fun text(medium: Medium): String = "Made with Gemmory: ${url(medium)}"
}
