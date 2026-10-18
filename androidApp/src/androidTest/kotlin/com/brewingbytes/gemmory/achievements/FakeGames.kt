package com.brewingbytes.gemmory.achievements

import android.app.Activity
import android.content.Intent
import java.util.Collections

/**
 * A fake Play Games client for connected tests (#49): signed in as [player] (null = nobody), confirming every send
 * while [online], and recording what reached its "server" (an unlock's id, or `id=steps`). It has no achievements
 * screen to open.
 */
class FakeGames(@Volatile var player: String? = "test-player", @Volatile var online: Boolean = true) : GamesBackend, GamesSession {
    val server: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override val available = true

    override fun session(activity: Activity): GamesSession = this

    override suspend fun player(): String? = player

    override suspend fun signIn(): SignIn = player?.let { SignIn.SignedIn(it) } ?: SignIn.Declined

    override suspend fun unlock(id: String): Boolean {
        if (online) server += id
        return online
    }

    override suspend fun setSteps(id: String, steps: Int): Boolean {
        if (online) server += "$id=$steps"
        return online
    }

    override suspend fun achievementsIntent(): Intent? = null
}
