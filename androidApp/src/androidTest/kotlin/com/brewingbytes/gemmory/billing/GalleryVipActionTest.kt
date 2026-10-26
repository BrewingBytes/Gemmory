package com.brewingbytes.gemmory.billing

import com.brewingbytes.gemmory.copy.strings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.gallery.GalleryScreen
import com.brewingbytes.gemmory.gallery.GalleryViewModel
import com.brewingbytes.gemmory.gallery.VIP_ACTION_TAG
import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.ui.GemmoryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * The gallery's quiet "VIP" text action (design D3): absent in the first session, a 48dp text button after it, and
 * the one-time billing line. MainActivity decides paywall or Play's subscription page.
 */
@RunWith(AndroidJUnit4::class)
class GalleryVipActionTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as GemmoryApp

    @Test
    fun hiddenInTheFirstSessionThenAQuietTextAction() {
        val vm = GalleryViewModel(app, SavedStateHandle())
        var showVip by mutableStateOf(false)
        var notice by mutableStateOf<String?>(null)
        var opened = 0
        compose.setContent {
            GemmoryTheme {
                GalleryScreen(
                    vm = vm,
                    decodeFailed = false,
                    allowance = Allowance.Free,
                    unlocked = emptySet(),
                    entitlements = Entitlements.FREE,
                    onNewFromPhoto = {},
                    onOpen = { _, _ -> },
                    onLocked = {},
                    onSettings = {},
                    showVip = showVip,
                    onVip = { opened++ },
                    notice = notice,
                )
            }
        }
        compose.onNodeWithTag(VIP_ACTION_TAG).assertDoesNotExist()
        showVip = true
        compose.onNodeWithTag(VIP_ACTION_TAG).assertTextEquals("VIP").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, opened)
        compose.onNodeWithTag("gallery-notice").assertDoesNotExist()
        notice = BillingCopy(app.strings).notice(Notice.VIP_STARTED)
        compose.onNodeWithText("You're VIP. Every gallery picture is unlocked.").assertExists()
    }
}
