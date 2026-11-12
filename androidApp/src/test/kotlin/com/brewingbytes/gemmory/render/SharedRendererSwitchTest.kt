package com.brewingbytes.gemmory.render

import com.brewingbytes.gemmory.BuildConfig
import org.junit.Test
import kotlin.test.assertTrue

/**
 * The renderer switch that ships (iOS Stage 1a, T62, eng R10-8). `SHARED_RENDERER` is set once for every build type
 * (androidApp/build.gradle.kts), so this debug test reads the release value too: a build with the old renderers
 * switched on by accident fails here. A hotfix that means to ship them (`-Pgemmory.sharedRenderer=false`) changes this
 * test in the same commit.
 */
class SharedRendererSwitchTest {
    @Test
    fun theSharedRenderersShip() {
        assertTrue(BuildConfig.SHARED_RENDERER, "SHARED_RENDERER is false: the old renderers would ship")
    }
}
