package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.policy.ReleaseAdIds
import kotlin.system.exitProcess

/** `./gradlew :shared:validateReleaseAdIds` (T34): fails the release build on missing or test AdMob IDs. */
object ReleaseAdIdsTool {
    /** `<appId> <rewardedUnit> <interstitialUnit>`, each possibly empty. */
    @JvmStatic
    fun main(args: Array<String>) {
        val problems = ReleaseAdIds.problems(args.getOrNull(0), args.getOrNull(1), args.getOrNull(2))
        if (problems.isEmpty()) {
            println("release ad IDs OK")
            return
        }
        System.err.println("Release builds need the production AdMob IDs in gradle.properties (README → Release):")
        problems.forEach { System.err.println("  $it") }
        exitProcess(1)
    }
}
