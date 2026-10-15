package com.brewingbytes.gemmory

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.brewingbytes.gemmory.ads.RewardTarget
import com.brewingbytes.gemmory.ads.RewardedEvent
import com.brewingbytes.gemmory.ads.RewardedSheet
import com.brewingbytes.gemmory.ads.RewardedViewModel
import com.brewingbytes.gemmory.billing.BillingCopy
import com.brewingbytes.gemmory.billing.PaywallScreen
import com.brewingbytes.gemmory.billing.subscriptionPage
import com.brewingbytes.gemmory.gallery.VIP_ACTION_TAG
import com.brewingbytes.gemmory.policy.Membership
import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brewingbytes.gemmory.completion.CompletionScreen
import com.brewingbytes.gemmory.completion.CompletionViewModel
import com.brewingbytes.gemmory.gallery.GalleryScreen
import com.brewingbytes.gemmory.gallery.GalleryViewModel
import com.brewingbytes.gemmory.gallery.tileTag
import com.brewingbytes.gemmory.nav.BackStack
import com.brewingbytes.gemmory.nav.Route
import com.brewingbytes.gemmory.photo.CropScreen
import com.brewingbytes.gemmory.photo.PhotoFlowViewModel
import com.brewingbytes.gemmory.photo.PhotoNav
import com.brewingbytes.gemmory.photo.PreviewScreen
import com.brewingbytes.gemmory.photo.SizeScreen
import com.brewingbytes.gemmory.play.PlayScreen
import com.brewingbytes.gemmory.settings.MEMBERSHIP_TAG
import com.brewingbytes.gemmory.settings.SettingsScreen
import com.brewingbytes.gemmory.ui.GemmoryTheme

class MainActivity : ComponentActivity() {
    private val photoFlow: PhotoFlowViewModel by viewModels()
    private val gallery: GalleryViewModel by viewModels()
    private val completion: CompletionViewModel by viewModels()
    private val rewarded: RewardedViewModel by viewModels()
    private val app: GemmoryApp get() = application as GemmoryApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // UMP on the first launch (EEA only, before any ad request); later launches only refresh the status.
        if (savedInstanceState == null) app.consent.onLaunch(this)
        setContent {
            GemmoryTheme {
                // Saved state, so the screen survives rotation and the process being killed in the background.
                val stack = rememberSaveable(saver = BackStack.saver) { BackStack() }
                App(stack)
            }
        }
    }

    @Composable
    private fun App(stack: BackStack) {
        val flow by photoFlow.state.collectAsStateWithLifecycle()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Cancelled: back to the gallery with no message (design D4).
            if (uri != null) photoFlow.onPhotoPicked(uri)
        }
        val pickPhoto = {
            photoFlow.onDecodeMessageShown()
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        LaunchedEffect(flow.nav) {
            when (val nav = flow.nav) {
                null -> return@LaunchedEffect
                PhotoNav.OpenCrop -> stack.push(Route.Crop)
                is PhotoNav.Started -> {
                    // Back from the new canvas lands on My Photos, where its tile is.
                    gallery.selectTab(1)
                    stack.started(nav.ref)
                }
                PhotoNav.Lost -> stack.dropPhotoFlow()
            }
            photoFlow.onNavHandled()
        }

        val allowance by app.monetization.allowance.collectAsStateWithLifecycle()
        val unlocked by app.monetization.unlocks.unlocked.collectAsStateWithLifecycle()
        val entitlements by app.monetization.entitlements.collectAsStateWithLifecycle()
        val membership by app.billing.membership.collectAsStateWithLifecycle()
        val firstSessionOver by app.monetization.firstSessionOver.collectAsStateWithLifecycle()
        val notice by app.billing.notice.collectAsStateWithLifecycle()

        // Where TalkBack focus goes back to when the paywall closes: what opened it.
        var paywallTrigger by rememberSaveable { mutableStateOf<String?>(null) }

        /**
         * "VIP", Membership and the rewarded sheet's VIP texts (design D3, D6): the paywall, or Play's subscription
         * page for a VIP, whom the paywall never shows.
         */
        fun openVip(trigger: String?) {
            if (entitlements.vip || membership == Membership.VIP_UNCONFIRMED) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, subscriptionPage(packageName, entitlements.plan)))
                } catch (_: ActivityNotFoundException) {
                }
                return
            }
            paywallTrigger = trigger
            app.billing.purchaseHandled()
            app.billing.loadPrices()
            stack.push(Route.Paywall)
        }

        // Billing's one-time gallery line: taken when the gallery is up, kept while it stays up.
        var galleryNotice by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(stack.top == Route.Gallery, notice) {
            val n = notice
            if (stack.top != Route.Gallery) {
                galleryNotice = null
            } else if (n != null) {
                galleryNotice = BillingCopy.notice(n)
                app.billing.noticeShown(n)
            }
        }

        val top = stack.top
        // The interstitial check runs only once the player has left a just-finished picture's completion screen
        // (design doc Ad rules): never on the canvas, never before or during Share.
        var previous by remember { mutableStateOf<Route?>(null) }
        LaunchedEffect(top) {
            val left = previous
            previous = top
            if (top is Route.Completion && completion.fresh.isFresh(top)) app.interstitials.onFreshCompletion()
            // A rotation restores the completion as a still route; the tracker keeps it fresh (it isn't "leaving").
            if (left is Route.Completion && left != top && completion.fresh.left(left) && top == Route.Gallery) {
                app.interstitials.onLeftFreshCompletion(this@MainActivity, left.ref)
            }
        }
        // Ads are preloaded: the extra-canvas video starts loading as soon as Preview offers it.
        LaunchedEffect(top == Route.Preview && allowance == Allowance.WatchVideo) {
            if (top == Route.Preview && allowance == Allowance.WatchVideo) {
                app.ads.start()
                app.ads.preloadRewarded()
            }
        }
        LaunchedEffect(rewarded.event) {
            when (val event = rewarded.event) {
                null -> return@LaunchedEffect
                is RewardedEvent.OpenPicture -> stack.openTile(CanvasRef(CanvasKind.BUNDLED, event.id), finished = false)
                RewardedEvent.StartPhotoCanvas -> photoFlow.start()
            }
            rewarded.onEventHandled()
        }
        val inPhotoFlow = top == Route.Crop || top == Route.Size || top == Route.Preview
        LaunchedEffect(inPhotoFlow) {
            if (inPhotoFlow && !photoFlow.hasDraft) stack.dropPhotoFlow()
        }
        // System back always goes one step up (design D3); on the Gallery it leaves the app. The canvas handles
        // its own back so it can close its session first.
        BackHandler(enabled = top != Route.Gallery && top !is Route.Canvas) { stack.pop() }
        LaunchedEffect(top is Route.Completion) { if (top !is Route.Completion) completion.onLeft() }

        when (top) {
            Route.Gallery -> {
                LaunchedEffect(Unit) { if (!flow.decoding) photoFlow.clearDraft() }
                GalleryScreen(
                    vm = gallery,
                    decodeFailed = flow.decodeFailed,
                    allowance = allowance,
                    unlocked = unlocked,
                    entitlements = entitlements,
                    onNewFromPhoto = pickPhoto,
                    onOpen = { tile -> stack.openTile(tile.ref, tile.done) },
                    onLocked = { tile -> rewarded.open(RewardTarget.Picture(tile.ref.id), this@MainActivity) },
                    onSettings = { stack.push(Route.Settings) },
                    showVip = firstSessionOver,
                    onVip = { openVip(VIP_ACTION_TAG) },
                    notice = galleryNotice,
                )
            }
            Route.Settings -> SettingsScreen(onBack = { stack.pop() }, onMembership = { openVip(MEMBERSHIP_TAG) })
            Route.Paywall -> {
                val prices by app.billing.prices.collectAsStateWithLifecycle()
                val purchase by app.billing.purchase.collectAsStateWithLifecycle()
                PaywallScreen(
                    prices = prices,
                    purchase = purchase,
                    entitlements = entitlements,
                    onBuy = { product -> app.billing.buy(this@MainActivity, product) },
                    onRetryPrices = app.billing::loadPrices,
                    onClose = {
                        app.billing.purchaseHandled()
                        stack.pop()
                    },
                    returnFocusTo = paywallTrigger,
                )
            }
            Route.Crop -> CropScreen(
                state = flow,
                onRatio = photoFlow::onRatio,
                onView = photoFlow::onCropView,
                onNext = { rect ->
                    photoFlow.onCropNext(rect)
                    stack.push(Route.Size)
                },
                onBack = { stack.pop() },
            )
            Route.Size -> SizeScreen(flow, photoFlow::onSize, onNext = { stack.push(Route.Preview) }, onBack = { stack.pop() })
            Route.Preview -> PreviewScreen(
                state = flow,
                allowance = allowance,
                onStart = photoFlow::start,
                onWatchVideo = { rewarded.open(RewardTarget.PhotoCanvas, this@MainActivity) },
                onChooseAnother = {
                    stack.popToGallery()
                    pickPhoto()
                },
                onBack = { stack.pop() },
            )
            is Route.Canvas -> PlayScreen(
                ref = top.ref,
                onBack = { stack.pop() },
                onFinished = {
                    app.monetization.onPictureFinished()
                    completion.onFinished(top.ref)
                    stack.finished(top.ref)
                },
            )
            is Route.Completion -> CompletionScreen(
                ref = top.ref,
                // Still fresh after a rotation (no "Play again", leaving checks the interstitial).
                fresh = completion.fresh.isFresh(top),
                vm = completion,
                onDone = { stack.popToGallery() },
                onPlayAgain = { stack.playAgain(top.ref) },
            )
        }

        rewarded.ui?.let { ui ->
            RewardedSheet(
                ui = ui,
                onWatch = { rewarded.watch(this@MainActivity) },
                onGrace = rewarded::useGrace,
                onTryAgain = { rewarded.tryAgain(this@MainActivity) },
                // "Or get VIP…" / "Get VIP…": the sheet closes and the paywall opens over the same screen.
                onVip = {
                    rewarded.dismiss()
                    openVip(
                        when (val target = ui.target) {
                            is RewardTarget.Picture -> tileTag(CanvasRef(CanvasKind.BUNDLED, target.id))
                            RewardTarget.PhotoCanvas -> "preview-primary"
                        },
                    )
                },
                onDismiss = rewarded::dismiss,
                // TalkBack focus goes back to the locked tile or Preview's "Watch a video" (design D19).
                returnFocusTo = when (val target = ui.target) {
                    is RewardTarget.Picture -> tileTag(CanvasRef(CanvasKind.BUNDLED, target.id))
                    RewardTarget.PhotoCanvas -> "preview-primary"
                },
            )
        }
    }
}
