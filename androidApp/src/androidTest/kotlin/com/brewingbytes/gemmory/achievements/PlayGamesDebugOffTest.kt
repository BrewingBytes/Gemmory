package com.brewingbytes.gemmory.achievements

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.BuildConfig
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

/**
 * R1 for #113: with Play Games off, the debug build has no PlayGamesInitProvider, so nothing starts Play Games' automatic
 * sign-in (its GamesResolutionActivity covered MainActivity in Test Lab's frame-timing runs).
 */
@RunWith(AndroidJUnit4::class)
class PlayGamesDebugOffTest {
    @Test
    fun theSdkInitProviderIsRemovedWhilePlayGamesIsOff() {
        assumeFalse(BuildConfig.PLAY_GAMES)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = ComponentName(context, "com.google.android.gms.games.provider.PlayGamesInitProvider")
        assertFailsWith<PackageManager.NameNotFoundException> { context.packageManager.getProviderInfo(provider, 0) }
    }
}
