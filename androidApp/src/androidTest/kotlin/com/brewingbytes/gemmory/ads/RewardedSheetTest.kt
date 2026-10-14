package com.brewingbytes.gemmory.ads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brewingbytes.gemmory.policy.Unavailable
import com.brewingbytes.gemmory.ui.GemmoryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Every Rewarded-states row with its exact copy (design system), for both reward types. */
@RunWith(AndroidJUnit4::class)
class RewardedSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private var ui by mutableStateOf(RewardedUi(RewardTarget.PhotoCanvas, RewardPhase.Loading))
    private val clicks = mutableListOf<String>()

    private fun show(target: RewardTarget, phase: RewardPhase, closedEarly: Boolean = false) {
        ui = RewardedUi(target, phase, closedEarly)
        compose.waitForIdle()
    }

    private fun start() {
        compose.setContent {
            GemmoryTheme {
                RewardedSheet(
                    ui = ui,
                    onWatch = { clicks += "watch" },
                    onGrace = { clicks += "grace" },
                    onTryAgain = { clicks += "try" },
                    onVip = { clicks += "vip" },
                    onDismiss = { clicks += "dismiss" },
                )
            }
        }
    }

    private fun text(s: String) = compose.onNodeWithText(s, useUnmergedTree = true)

    @Test
    fun lockedPictureRows() {
        val picture = RewardTarget.Picture("sunflowers")
        ui = RewardedUi(picture, RewardPhase.Loading)
        start()
        text("Watch to unlock").assertExists()
        text("Watch a short video to unlock this picture for good.").assertExists()
        compose.onNodeWithTag("rewarded-primary").assertIsNotEnabled()
        text("Loading video…").assertExists()

        show(picture, RewardPhase.Ready)
        text("Watch video").assertExists()
        compose.onNodeWithTag("rewarded-primary").assertIsEnabled().performClick()

        show(picture, RewardPhase.NotAvailable(Unavailable.GRACE_OFFER))
        text("No video right now. This one's on us today.").assertExists()
        text("Unlock picture").performClick()
        text("Or get VIP: every picture unlocked").assertExists()

        show(picture, RewardPhase.NotAvailable(Unavailable.NO_VIDEO))
        text("No video right now. Try again in a bit.").assertExists()
        text("Try again").performClick()
        text("Get VIP: no videos needed").performClick()

        show(picture, RewardPhase.NotAvailable(Unavailable.OFFLINE))
        text("You're offline, connect to watch a video").assertExists()
        text("Try again").assertExists()
        text("Get VIP: no videos needed").assertExists()

        show(picture, RewardPhase.Ready, closedEarly = true)
        text("Watch to the end to unlock.").assertExists()
        compose.onNodeWithTag("rewarded-primary").assertIsEnabled()

        show(picture, RewardPhase.Unlocked)
        text("Unlocked").assertExists()
        compose.onNodeWithTag("rewarded-primary").assertDoesNotExist()

        assertEquals(listOf("watch", "grace", "try", "vip"), clicks)
    }

    @Test
    fun extraPhotoCanvasRows() {
        ui = RewardedUi(RewardTarget.PhotoCanvas, RewardPhase.Ready)
        start()
        text("Another photo canvas").assertExists()
        text("Watch a short video for one more photo canvas today.").assertExists()

        show(RewardTarget.PhotoCanvas, RewardPhase.NotAvailable(Unavailable.GRACE_OFFER))
        text("Start canvas").assertExists()
        text("Or get VIP: unlimited photo canvases").assertExists()

        show(RewardTarget.PhotoCanvas, RewardPhase.Loading, closedEarly = true)
        text("Watch to the end to unlock.").assertExists()
        text("Loading video…").assertExists()
    }
}
