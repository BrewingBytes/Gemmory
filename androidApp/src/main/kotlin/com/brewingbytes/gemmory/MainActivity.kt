package com.brewingbytes.gemmory

import android.Manifest
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.brewingbytes.gemmory.achievements.SendAchievementsWhileShown
import com.brewingbytes.gemmory.ads.RewardTarget
import com.brewingbytes.gemmory.ads.RewardedEvent
import com.brewingbytes.gemmory.ads.RewardedSheet
import com.brewingbytes.gemmory.ads.RewardedViewModel
import com.brewingbytes.gemmory.billing.BillingCopy
import com.brewingbytes.gemmory.billing.PaywallScreen
import com.brewingbytes.gemmory.billing.subscriptionPage
import com.brewingbytes.gemmory.converter.PhotoFlowRules
import com.brewingbytes.gemmory.converter.PreviewPrimary
import com.brewingbytes.gemmory.gallery.INVENTORY_TAG
import com.brewingbytes.gemmory.gallery.VIP_ACTION_TAG
import com.brewingbytes.gemmory.policy.Membership
import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.policy.EarnSource
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.CanvasEntry
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
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.reminder.AskResult
import com.brewingbytes.gemmory.reminder.NotificationPermission
import com.brewingbytes.gemmory.reminder.ReminderAskCopy
import com.brewingbytes.gemmory.reminder.ReminderAskSheet
import com.brewingbytes.gemmory.reminder.ReminderTap
import com.brewingbytes.gemmory.reminder.Reminders
import com.brewingbytes.gemmory.settings.MEMBERSHIP_TAG
import com.brewingbytes.gemmory.settings.SettingsScreen
import com.brewingbytes.gemmory.ui.GemmoryTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val photoFlow: PhotoFlowViewModel by viewModels()
    private val gallery: GalleryViewModel by viewModels()
    private val completion: CompletionViewModel by viewModels()
    private val rewarded: RewardedViewModel by viewModels()

    /** The canvas's ViewModel (PlayScreen's), closed here when a reminder tap leaves an open canvas. */
    private val play: PlayViewModel by viewModels()
    private val app: GemmoryApp get() = application as GemmoryApp

    /** An evening reminder was tapped (T35): routed once the screen is up. */
    private var reminderTap by mutableStateOf<ReminderTap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // UMP on the first launch (EEA only, before any ad request); later launches only refresh the status.
        if (savedInstanceState == null) app.consent.onLaunch(this)
        // A tap that started the activity; after a rotation it was handled already.
        if (savedInstanceState == null) reminderTap = ReminderTap.of(intent)
        setContent {
            GemmoryTheme {
                // Saved state, so the screen survives rotation and the process being killed in the background.
                val stack = rememberSaveable(saver = BackStack.saver) { BackStack() }
                App(stack)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ReminderTap.of(intent)?.let { reminderTap = it }
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
        val inventory by app.monetization.inventory.collectAsStateWithLifecycle()

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
        // The reminder's "Reminders on" line (T35, plan-design-review D12) uses the same place, once.
        val reminderLine by app.reminders.onLine.collectAsStateWithLifecycle()
        val reminderMinutes by app.reminders.minutes.collectAsStateWithLifecycle()
        LaunchedEffect(stack.top == Route.Gallery, reminderLine) {
            if (stack.top == Route.Gallery && reminderLine) {
                galleryNotice = ReminderAskCopy.onLine(Reminders.timeLabel(this@MainActivity, reminderMinutes))
                app.reminders.onLineShown()
            }
        }
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
        val scope = rememberCoroutineScope()
        // The interstitial check runs only once the player has left a just-finished picture's completion screen
        // (design doc Ad rules): never on the canvas, never before or during Share.
        var previous by remember { mutableStateOf<Route?>(null) }
        // The reminder ask (T35): its attempt (1 or 2) while the sheet shows. One interruption per return to the
        // gallery: the ask, else the interstitial check.
        var reminderAsk by rememberSaveable { mutableStateOf<Int?>(null) }
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            app.reminders.askAnswered(if (granted) AskResult.GRANTED else AskResult.DENIED_SYSTEM)
        }
        // Remote pictures (#43): nothing downloads during play. A check still running stops; the next Gallery visit retries.
        LaunchedEffect(top is Route.Canvas) {
            if (top is Route.Canvas) app.remote.pause()
        }
        LaunchedEffect(top) {
            val left = previous
            previous = top
            if (top is Route.Completion && completion.fresh.isFresh(top)) app.interstitials.onFreshCompletion()
            // A rotation restores the completion as a still route; the tracker keeps it fresh (it isn't "leaving").
            if (left is Route.Completion && left != top && completion.fresh.left(left) && top == Route.Gallery) {
                // A return that completes a collection (T37, plan-design-review D7) has no interruption: the ask and
                // the interstitial wait for the next eligible finish, and the gallery brings the row into view.
                app.collections.settle(app.store.summaries().first)
                val reward = left.ref.kind != CanvasKind.PHOTO && app.collections.takeRewardReturn(left.ref.id)
                val attempt = app.reminders.onLeftFreshFinish(ask = !reward)
                if (attempt != null) {
                    app.reminders.askShown(attempt)
                    reminderAsk = attempt
                } else if (!reward) {
                    app.interstitials.onLeftFreshCompletion(this@MainActivity, left.ref)
                }
            }
        }
        // A reminder tap (T35): its canvas, or the gallery when it's stale (design system, navigation map).
        val tap = reminderTap
        LaunchedEffect(tap) {
            if (tap == null) return@LaunchedEffect
            reminderTap = null
            app.analytics.log(AnalyticsEvent.reminderOpened(tap.reason))
            val ref = app.reminderTarget(tap.reason, tap.pictureId, tap.canvasKey, tap.day)
            if (stack.top is Route.Canvas) play.close()
            if (ref == null) stack.popToGallery() else stack.openTile(ref, CanvasEntry.REMINDER, app.store::isFinished)
        }
        // Ads are preloaded: the extra-canvas or More colors video (#29) starts loading as soon as Preview offers it.
        val previewVideo = top == Route.Preview &&
            PhotoFlowRules.previewPrimary(allowance, flow.choice, flow.moreUnlocked) != PreviewPrimary.START
        LaunchedEffect(previewVideo) {
            if (previewVideo) {
                app.ads.start()
                app.ads.preloadRewarded()
            }
        }
        LaunchedEffect(rewarded.event) {
            when (val event = rewarded.event) {
                null -> return@LaunchedEffect
                // A picture that isn't there (yet: a dated drop before its date, T36) leaves the player on the gallery.
                is RewardedEvent.OpenPicture -> app.openableRef(event.id)?.let { stack.openTile(it, isFinished = app.store::isFinished) }
                RewardedEvent.StartPhotoCanvas -> {
                    photoFlow.onMoreColorsUnlocked()
                    photoFlow.start()
                }
                RewardedEvent.MoreColorsUnlocked -> photoFlow.onMoreColorsUnlocked()
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
                SendAchievementsWhileShown(app.achievements)
                GalleryScreen(
                    vm = gallery,
                    decodeFailed = flow.decodeFailed,
                    allowance = allowance,
                    unlocked = unlocked,
                    entitlements = entitlements,
                    onNewFromPhoto = pickPhoto,
                    onOpen = { tile, entry -> scope.launch { stack.openTile(tile.ref, entry, app.store::isFinished) } },
                    onLocked = { tile -> rewarded.open(RewardTarget.Picture(tile.ref.id), this@MainActivity) },
                    onSettings = { stack.push(Route.Settings) },
                    showVip = firstSessionOver,
                    onVip = { openVip(VIP_ACTION_TAG) },
                    notice = galleryNotice,
                    // VIP has everything unlimited, so no inventory (#38).
                    inventory = inventory.takeIf { !entitlements.vip },
                    onWatchFills = { rewarded.open(RewardTarget.Fills, this@MainActivity) },
                    onSpin = {
                        app.monetization.spin()?.also {
                            app.analytics.log(AnalyticsEvent.inventoryEarned(it.item, EarnSource.WHEEL, it.amount))
                        }
                    },
                )
            }
            Route.Settings -> {
                SendAchievementsWhileShown(app.achievements)
                SettingsScreen(onBack = { stack.pop() }, onMembership = { openVip(MEMBERSHIP_TAG) })
            }
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
                kept = inventory.canvases,
                onStart = photoFlow::start,
                onWatchVideo = { rewarded.open(RewardTarget.PhotoCanvas, this@MainActivity) },
                onChoice = photoFlow::onChoice,
                onWatchColors = { rewarded.open(RewardTarget.Colors, this@MainActivity) },
                onChooseAnother = {
                    stack.popToGallery()
                    pickPhoto()
                },
                onBack = { stack.pop() },
            )
            is Route.Canvas -> PlayScreen(
                ref = top.ref,
                entry = top.entry,
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

        val ask = reminderAsk
        if (ask != null && top == Route.Gallery) {
            ReminderAskSheet(
                time = Reminders.timeLabel(this@MainActivity, reminderMinutes),
                onYes = {
                    reminderAsk = null
                    when {
                        NotificationPermission.needed(this@MainActivity) -> {
                            app.reminders.permissionRequested()
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        app.reminders.notificationsAllowed() -> app.reminders.askAnswered(AskResult.GRANTED)
                        // Gemmory's notifications or the channel are off (Android 10-12, or switched off): on, and the
                        // system page opens, as the Settings switch does (founder, 2026-10-20).
                        else -> {
                            app.reminders.askAnswered(AskResult.BLOCKED)
                            NotificationPermission.openSettings(this@MainActivity)
                        }
                    }
                },
                onNotNow = {
                    reminderAsk = null
                    app.reminders.askAnswered(AskResult.DECLINED_IN_APP)
                },
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
                            is RewardTarget.Picture -> tileTag(app.pictureRef(target.id))
                            RewardTarget.PhotoCanvas, RewardTarget.Colors -> "preview-primary"
                            RewardTarget.Fills -> INVENTORY_TAG
                        },
                    )
                },
                onDismiss = rewarded::dismiss,
                // TalkBack focus goes back to the locked tile or Preview's "Watch a video" (design D19).
                returnFocusTo = when (val target = ui.target) {
                    is RewardTarget.Picture -> tileTag(app.pictureRef(target.id))
                    RewardTarget.PhotoCanvas, RewardTarget.Colors -> "preview-primary"
                    RewardTarget.Fills -> INVENTORY_TAG
                },
                // Unlock tokens (#38) on a locked picture's sheet.
                tokens = inventory.tokens,
                onToken = rewarded::useToken,
            )
        }
    }
}
