package com.brewingbytes.gemmory.achievements

import android.app.Activity
import android.content.Intent

/** How a sign-in the player asked for (the Settings row) ended. */
sealed interface SignIn {
    data class SignedIn(val player: String) : SignIn

    /** The player closed or declined Play Games' sign-in: nothing is shown. */
    data object Declined : SignIn

    /** Play Games couldn't be reached (offline, or an error). */
    data object Failed : SignIn
}

/**
 * Play Games Services (#49), behind an interface so tests and debug builds run without it. The v2 clients are bound
 * to an Activity, so the app opens a [GamesSession] on the one showing.
 */
interface GamesBackend {
    /** Play Games can run here: Google Play services is present and the build has a Play Games project id. */
    val available: Boolean

    fun session(activity: Activity): GamesSession

    companion object {
        /**
         * Play Games off: debug builds without `-Pgemmory.playGames=true`, and any build before games-ids.xml has the
         * project id. Earned achievements wait in the ledger; the Settings row is hidden.
         */
        val Off = object : GamesBackend {
            override val available = false

            override fun session(activity: Activity): GamesSession = GamesSession.SignedOut
        }
    }
}

/** Play Games calls for one Activity. Nothing here shows UI except [signIn] and the achievements screen, both on a tap. */
interface GamesSession {
    /** The signed-in player's id, or null when nobody is signed in (automatic sign-in failed or was declined). Silent. */
    suspend fun player(): String?

    /** A sign-in the player asked for. */
    suspend fun signIn(): SignIn

    /** Unlocks [id] on Play Games' servers; true once they confirmed it. */
    suspend fun unlock(id: String): Boolean

    /** Sets incremental [id] to at least [steps] on Play Games' servers; true once they confirmed it. */
    suspend fun setSteps(id: String, steps: Int): Boolean

    /** Play Games' own achievements screen, or null when it can't be had. */
    suspend fun achievementsIntent(): Intent?

    companion object {
        val SignedOut = object : GamesSession {
            override suspend fun player(): String? = null

            override suspend fun signIn(): SignIn = SignIn.Failed

            override suspend fun unlock(id: String) = false

            override suspend fun setSteps(id: String, steps: Int) = false

            override suspend fun achievementsIntent(): Intent? = null
        }
    }
}
