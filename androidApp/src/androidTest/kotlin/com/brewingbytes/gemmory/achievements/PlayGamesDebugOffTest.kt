package com.brewingbytes.gemmory.achievements

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

/**
 * R1 for #113 and T45: no build has PlayGamesInitProvider (the main manifest drops it), so nothing starts Play Games'
 * automatic sign-in at a process start (its GamesResolutionActivity covered MainActivity in Test Lab's frame-timing
 * runs, and its profile sheet could cover the starter on a fresh install). Play Games starts only at its turn.
 */
@RunWith(AndroidJUnit4::class)
class PlayGamesDebugOffTest {
    @Test
    fun theSdkInitProviderIsRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = ComponentName(context, "com.google.android.gms.games.provider.PlayGamesInitProvider")
        assertFailsWith<PackageManager.NameNotFoundException> { context.packageManager.getProviderInfo(provider, 0) }
    }
}
