package com.brewingbytes.gemmory.telemetry

import com.brewingbytes.gemmory.billing.PlayBillingBackend
import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.photo.ConverterException
import com.brewingbytes.gemmory.photo.FaceDetectorException
import com.brewingbytes.gemmory.policy.ConsentModeV2
import com.brewingbytes.gemmory.policy.LoadFailure
import com.brewingbytes.gemmory.policy.RewardType
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.android.billingclient.api.BillingClient.BillingResponseCode
import org.junit.Test
import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** What reaches Crashlytics and Analytics (#12): the build's choice, scrubbing, labels and event params. */
class TelemetryTest {
    private val photo = CanvasRef(CanvasKind.PHOTO, "photo-1760000000000")
    private val starter = CanvasRef(CanvasKind.BUNDLED, "starter")
    private val puppy = CanvasRef(CanvasKind.BUNDLED, "puppy")

    @Test
    fun releaseSendsToFirebaseAndDebugLogs() {
        val crashlytics = NonFatalReporter { }
        assertSame(crashlytics, NonFatalReporter.forBuild(sendsToFirebase = true) { crashlytics })
        assertSame(NonFatalReporter.Logcat, NonFatalReporter.forBuild(sendsToFirebase = false) { error("Firebase must not start") })
        val firebase = Analytics.None
        assertSame(firebase, Analytics.forBuild(sendsToFirebase = true) { firebase })
        assertSame(Analytics.Logcat, Analytics.forBuild(sendsToFirebase = false) { error("Firebase must not start") })
    }

    @Test
    fun pathsAndUrisAreScrubbed() {
        assertEquals(
            "<path> (No such file or directory)",
            Redaction.scrub("/data/user/0/com.brewingbytes.gemmory/files/canvases/photo/photo-1/save (No such file or directory)"),
        )
        assertEquals("decode <uri> failed", Redaction.scrub("decode content://media/picker/0/com.android.providers.media.photopicker/media/1000 failed"))
        assertEquals("open <path>: EACCES", Redaction.scrub("open /storage/emulated/0/DCIM/me.jpg: EACCES"))
        assertEquals("open <uri>", Redaction.scrub("open file:///sdcard/x.png"))
        for (kept in listOf("bundled/starter: damaged", "target=1080x1920@30 codec=c2.android.avc.encoder", "1/2 done", "acknowledge [vip_yearly]: code 6"))
            assertEquals(kept, Redaction.scrub(kept))
    }

    @Test
    fun aCleanErrorIsSentAsIsAndADirtyChainIsCopiedWithItsStackTraces() {
        val clean = IllegalStateException("stage=ENCODE", IllegalArgumentException("bad size"))
        assertSame(clean, Redaction.sanitize(clean))

        val io = FileNotFoundException("/data/user/0/com.brewingbytes.gemmory/cache/timelapse/photo-1.mp4.partial")
        val top = RuntimeException("time-lapse photo failed", io)
        val sent = Redaction.sanitize(top)
        assertEquals("java.lang.RuntimeException: time-lapse photo failed", sent.message)
        assertEquals(top.stackTrace.toList(), sent.stackTrace.toList())
        val cause = assertIs<RedactedException>(sent.cause)
        assertEquals("java.io.FileNotFoundException: <path>", cause.message)
        assertEquals(io.stackTrace.toList(), cause.stackTrace.toList())
        assertNull(cause.cause)
    }

    @Test
    fun aCyclicCauseChainEnds() {
        val a = Exception("/data/a/b")
        val b = Exception("b", a)
        a.initCause(b)
        assertTrue(generateSequence(Redaction.sanitize(b)) { it.cause }.take(100).count() < 100)
    }

    @Test
    fun reportsNameAPhotoCanvasOnlyByItsKind() {
        assertEquals("photo", photo.reportLabel)
        assertEquals("bundled/starter", starter.reportLabel)
    }

    @Test
    fun converterAndFaceReportsCarrySizesAndStagesOnly() {
        val e = ConverterException(CanvasSize.MEDIUM, AspectRatio.PORTRAIT, 768, 1024, OutOfMemoryError())
        assertEquals("convert medium 3:4 from 768x1024 failed", e.message)
        assertIs<OutOfMemoryError>(e.cause)
        assertEquals("face detection init failed", FaceDetectorException("init", IllegalStateException()).message)
    }

    @Test
    fun billingReportsRealErrorsWithPlaysWords() {
        assertNull(PlayBillingBackend.failure("launch vip_weekly", BillingResponseCode.OK, ""))
        for (routine in listOf(
            BillingResponseCode.USER_CANCELED,
            BillingResponseCode.ITEM_ALREADY_OWNED,
            BillingResponseCode.NETWORK_ERROR,
            BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingResponseCode.SERVICE_DISCONNECTED,
            BillingResponseCode.BILLING_UNAVAILABLE,
        )) assertNull(PlayBillingBackend.failure("query purchases subs", routine, "x"), "code $routine")
        for (bug in listOf(
            BillingResponseCode.ERROR,
            BillingResponseCode.DEVELOPER_ERROR,
            BillingResponseCode.ITEM_UNAVAILABLE,
            BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingResponseCode.ITEM_NOT_OWNED,
            99,
        )) assertTrue(PlayBillingBackend.failure("launch vip_weekly", bug, "x") != null, "code $bug")
        assertEquals(
            "launch vip_yearly: code 5 Product details not found.",
            PlayBillingBackend.failure("launch vip_yearly", BillingResponseCode.DEVELOPER_ERROR, "Product details not found.")?.message,
        )
    }

    @Test
    fun aFailedAcknowledgeIsAlwaysReportedButACancelIsNot() {
        assertEquals(
            "acknowledge [vip_yearly]: code 12 Network error.",
            PlayBillingBackend.failure("acknowledge [vip_yearly]", BillingResponseCode.NETWORK_ERROR, "Network error.", always = true)?.message,
        )
        assertEquals("acknowledge [remove_ads]: code 6", PlayBillingBackend.failure("acknowledge [remove_ads]", BillingResponseCode.ERROR, null, always = true)?.message)
        assertNull(PlayBillingBackend.failure("acknowledge [remove_ads]", BillingResponseCode.USER_CANCELED, "", always = true))
    }

    @Test
    fun canvasEventsCarrySourceAndSize() {
        assertEquals(AnalyticsEvent("canvas_started", mapOf("source" to "photo", "size" to "quick")), AnalyticsEvent.canvasStarted(photo, 48, 64))
        assertEquals(mapOf("source" to "photo", "size" to "medium"), AnalyticsEvent.canvasFinished(photo, 90, 90).params)
        assertEquals(mapOf("source" to "bundled", "size" to "big"), AnalyticsEvent.shareTapped(puppy, 122, 122).params)
        assertEquals("starter", AnalyticsEvent.size(starter, 24, 32), "the starter never counts as Quick")
        assertEquals("other", AnalyticsEvent.size(puppy, 10, 10))
        assertEquals(AnalyticsEvent("share_completed", mapOf("source" to "bundled", "size" to "big")), AnalyticsEvent.shareCompleted("bundled", "big"))
    }

    @Test
    fun monetizationEventsMatchTheDesignDoc() {
        assertEquals(AnalyticsEvent("rewarded_offered", mapOf("reward_type" to "photo_canvas")), AnalyticsEvent.rewardedOffered(RewardType.PHOTO_CANVAS))
        assertEquals(AnalyticsEvent("rewarded_watched", mapOf("reward_type" to "picture")), AnalyticsEvent.rewardedWatched(RewardType.PICTURE))
        assertEquals(
            AnalyticsEvent("rewarded_unavailable", mapOf("reward_type" to "picture", "reason" to "no_fill")),
            AnalyticsEvent.rewardedUnavailable(RewardType.PICTURE, UnavailableReason.NO_FILL),
        )
        assertEquals(AnalyticsEvent("grace_unlock_granted", mapOf("reward_type" to "picture")), AnalyticsEvent.graceUnlockGranted(RewardType.PICTURE))
        assertEquals(AnalyticsEvent("interstitial_shown"), AnalyticsEvent.interstitialShown)
        assertEquals(AnalyticsEvent("vip_purchased", mapOf("period" to "weekly")), AnalyticsEvent.vipPurchased("weekly"))
        assertEquals(AnalyticsEvent("remove_ads_purchased"), AnalyticsEvent.removeAdsPurchased)
        assertEquals(AnalyticsEvent("photo_canvas_started"), AnalyticsEvent.photoCanvasStarted)
    }

    @Test
    fun unavailableReasons() {
        assertEquals(UnavailableReason.OFFLINE, UnavailableReason.of(LoadFailure.NO_FILL, online = false))
        assertEquals(UnavailableReason.OFFLINE, UnavailableReason.of(LoadFailure.NETWORK, online = true))
        assertEquals(UnavailableReason.NO_FILL, UnavailableReason.of(LoadFailure.NO_FILL, online = true))
        assertEquals(UnavailableReason.TIMEOUT, UnavailableReason.of(LoadFailure.TIMEOUT, online = true))
        assertEquals(UnavailableReason.ERROR, UnavailableReason.of(LoadFailure.OTHER, online = true))
    }

    @Test
    fun noEventParamIsATitleOrAnId() {
        val all = listOf(
            AnalyticsEvent.canvasStarted(photo, 90, 90),
            AnalyticsEvent.canvasFinished(puppy, 122, 122),
            AnalyticsEvent.shareTapped(photo, 56, 56),
        )
        for (e in all) for (v in e.params.values) assertFalse(v == photo.id || v == puppy.id || v.contains("Puppy"), "${e.name}: $v")
    }

    @Test
    fun consentModeFlagsFollowUmp() {
        assertEquals(
            mapOf("analytics_storage" to true, "ad_storage" to true, "ad_user_data" to false, "ad_personalization" to false),
            Analytics.consentFlags(ConsentModeV2(analyticsStorage = true, adStorage = true, adUserData = false, adPersonalization = false)),
        )
        assertTrue(Analytics.consentFlags(ConsentModeV2.GRANTED).values.all { it })
    }

    @Test
    fun sevenQuickTapsOpenTheCheckAndSlowTapsStartOver() {
        val streak = TapStreak(TelemetryCheck.TAPS, TelemetryCheck.WINDOW_MS)
        assertFalse((0 until 6).any { streak.tap(it * 100L) })
        assertTrue(streak.tap(600))
        assertFalse(streak.tap(700), "a completed streak starts again")
        val slow = TapStreak(7, 3_000)
        assertFalse((0 until 7).any { slow.tap(it * 600L) }, "the 7th tap at 3.6 s is outside the window")
    }
}
