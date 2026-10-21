package com.brewingbytes.gemmory.policy

/**
 * Real ads (T34, ad-mediation.md "Fail closed"): a release build must carry the production AdMob IDs from
 * `gradle.properties`, never Google's test publisher and never nothing. `:shared:validateReleaseAdIds` runs this before
 * every `bundleRelease` / `assembleRelease`.
 */
object ReleaseAdIds {
    /** Google's sample publisher: its IDs only ever serve test ads. */
    const val TEST_PUBLISHER = "ca-app-pub-3940256099942544"

    const val APP_ID = "gemmory.admob.appId"
    const val REWARDED_UNIT = "gemmory.admob.rewardedUnit"
    const val INTERSTITIAL_UNIT = "gemmory.admob.interstitialUnit"

    private val APP = Regex("""ca-app-pub-\d{16}~\d{10}""")
    private val UNIT = Regex("""ca-app-pub-\d{16}/\d{10}""")

    /** One line per problem, naming the property; empty when the three IDs may ship. */
    fun problems(appId: String?, rewardedUnit: String?, interstitialUnit: String?): List<String> = listOfNotNull(
        problem(APP_ID, appId, APP, "ca-app-pub-<16 digits>~<10 digits>"),
        problem(REWARDED_UNIT, rewardedUnit, UNIT, "ca-app-pub-<16 digits>/<10 digits>"),
        problem(INTERSTITIAL_UNIT, interstitialUnit, UNIT, "ca-app-pub-<16 digits>/<10 digits>"),
    )

    private fun problem(name: String, value: String?, shape: Regex, expected: String): String? = when {
        value.isNullOrBlank() -> "$name is missing"
        value.startsWith(TEST_PUBLISHER) -> "$name is Google's test publisher ($value): it only serves test ads"
        !shape.matches(value) -> "$name is not an AdMob ID ($value, expected $expected)"
        else -> null
    }
}
