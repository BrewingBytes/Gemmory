package com.brewingbytes.gemmory

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick

/**
 * How long a test waits for something that has to load: the gallery's tiles, a photo canvas's tile, the play canvas
 * laid out. A cold process on a fresh or busy emulator can take well past 10s to list the gallery (2026-11-23, #172:
 * 5s and 10s waits failed on main and on the branch alike), and a wait ends as soon as its condition holds, so a long
 * one costs nothing when the app is quick. Waits that time something the player sees (a chip going after 8s, a hold)
 * keep their own bounds.
 */
const val LOAD_WAIT_MS = 30_000L

/** Taps the gallery's My Photos tab once the gallery is up (a cold gallery can still be listing when a test starts). */
fun ComposeTestRule.openMyPhotos() {
    waitUntil("the My Photos tab", LOAD_WAIT_MS) { onAllNodes(hasText("My Photos")).fetchSemanticsNodes().isNotEmpty() }
    onNode(hasText("My Photos")).performClick()
}
