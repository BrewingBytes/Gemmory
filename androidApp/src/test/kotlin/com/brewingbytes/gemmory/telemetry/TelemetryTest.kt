package com.brewingbytes.gemmory.telemetry

import com.brewingbytes.gemmory.billing.PlayBillingBackend
import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.photo.ConverterException
import com.brewingbytes.gemmory.photo.FaceDetectorException
import com.brewingbytes.gemmory.policy.ConsentModeV2
import com.brewingbytes.gemmory.policy.EarnSource
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.policy.LoadFailure
import com.brewingbytes.gemmory.policy.ReminderReason
import com.brewingbytes.gemmory.policy.RewardType
import com.brewingbytes.gemmory.reminder.AskResult
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
        assertEquals("convert medium 3:4 standard from 768x1024 failed", e.message)
        val more = ConverterException(CanvasSize.BIG, AspectRatio.SQUARE, 1024, 1024, OutOfMemoryError(), ColorChoice.MORE)
        assertEquals("convert big 1:1 more from 1024x1024 failed", more.message)
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
        assertEquals(AnalyticsEvent("canvas_started", mapOf("source" to "photo", "size" to "quick", "entry" to "gallery")), AnalyticsEvent.canvasStarted(photo, 48, 64))
        // #43: a remote picture is its own source, sized by its grid like a bundled one.
        val fox = CanvasRef(CanvasKind.REMOTE, "fox-in-snow")
        assertEquals(AnalyticsEvent("canvas_started", mapOf("source" to "remote", "size" to "big", "entry" to "gallery")), AnalyticsEvent.canvasStarted(fox, 106, 142))
        assertEquals(AnalyticsEvent("canvas_finished", mapOf("source" to "remote", "size" to "big", "entry" to "gallery")), AnalyticsEvent.canvasFinished(fox, 106, 142))
        assertEquals("remote/fox-in-snow", fox.reportLabel)
        assertEquals(mapOf("source" to "photo", "size" to "medium", "entry" to "gallery"), AnalyticsEvent.canvasFinished(photo, 90, 90).params)
        assertEquals(mapOf("source" to "bundled", "size" to "big", "kind" to "finished"), AnalyticsEvent.shareTapped(puppy, 122, 122, progress = false).params)
        assertEquals(mapOf("source" to "photo", "size" to "quick", "kind" to "progress"), AnalyticsEvent.shareTapped(photo, 48, 64, progress = true).params)
        assertEquals("starter", AnalyticsEvent.size(starter, 24, 32), "the starter never counts as Quick")
        assertEquals("other", AnalyticsEvent.size(puppy, 10, 10))
        assertEquals(
            AnalyticsEvent("share_completed", mapOf("source" to "bundled", "size" to "big", "kind" to "progress")),
            AnalyticsEvent.shareCompleted("bundled", "big", "progress"),
        )
    }

    /** T36 (eng D13): where a canvas was opened from, a drop's age, and one `day_played` a day; never an id. */
    @Test
    fun dropEventsCarryEntryAndAgeButNoIds() {
        val drop = CanvasRef(CanvasKind.REMOTE, "lighthouse")
        assertEquals(
            mapOf("source" to "remote", "size" to "medium", "entry" to "today_card", "days_since_available" to 0L),
            AnalyticsEvent.canvasStarted(drop, 90, 90, entry = CanvasEntry.TODAY_CARD, daysSinceAvailable = 0).params,
        )
        assertEquals(
            mapOf("source" to "remote", "size" to "medium", "entry" to "gallery", "days_since_available" to 3L),
            AnalyticsEvent.canvasFinished(drop, 90, 90, CanvasEntry.GALLERY, daysSinceAvailable = 3).params,
        )
        assertEquals(AnalyticsEvent("day_played", mapOf("entry" to "today_card")), AnalyticsEvent.dayPlayed(CanvasEntry.TODAY_CARD))
        assertEquals(AnalyticsEvent("teaser_tapped"), AnalyticsEvent.teaserTapped)
        assertEquals(listOf("gallery", "today_card", "reminder", "collection", "event"), CanvasEntry.entries.map { it.param })
        val params = listOf(AnalyticsEvent.dayPlayed(CanvasEntry.REMINDER), AnalyticsEvent.canvasStarted(drop, 90, 90, daysSinceAvailable = 1))
            .flatMap { it.params.values }
        assertTrue(params.none { it == "lighthouse" }, "no picture id in any param")
    }

    /**
     * T37: a collection's id is the one id an event may carry (a content id, the documented exception): on the canvas
     * events of its pictures and on `collection_completed`. A picture's own id still never is.
     */
    @Test
    fun collectionEventsCarryTheCollectionIdOnly() {
        val harbor = CanvasRef(CanvasKind.REMOTE, "harbor")
        assertEquals(
            mapOf("source" to "remote", "size" to "medium", "entry" to "collection", "collection_id" to "seaside"),
            AnalyticsEvent.canvasStarted(harbor, 90, 90, entry = CanvasEntry.COLLECTION, collectionId = "seaside").params,
        )
        assertEquals(
            mapOf("source" to "remote", "size" to "medium", "entry" to "gallery", "collection_id" to "seaside"),
            AnalyticsEvent.canvasFinished(harbor, 90, 90, collectionId = "seaside").params,
        )
        assertEquals(AnalyticsEvent("collection_completed", mapOf("collection_id" to "seaside")), AnalyticsEvent.collectionCompleted("seaside"))
        assertEquals(
            AnalyticsEvent("inventory_earned", mapOf("item" to "unlock_token", "source" to "collection", "amount" to 1L)),
            AnalyticsEvent.inventoryEarned(Item.UNLOCK_TOKEN, EarnSource.COLLECTION, 1),
        )
        assertFalse("collection_id" in AnalyticsEvent.canvasStarted(harbor, 90, 90).params, "no collection, no param")
        assertTrue(AnalyticsEvent.canvasFinished(harbor, 90, 90, collectionId = "seaside").params.values.none { it == "harbor" })
    }

    /**
     * T38: an event member's canvas events carry `event_id` beside `collection_id` (founder 2026-10-20: both name the
     * event), and `event_reward_unlocked` carries the event id and how many finished in the window. R1: an ordinary
     * collection's params are unchanged (no `event_id`).
     */
    @Test
    fun eventEventsCarryTheEventId() {
        val day = CanvasRef(CanvasKind.REMOTE, "w3")
        assertEquals(
            mapOf("source" to "remote", "size" to "medium", "entry" to "event", "collection_id" to "golden-week", "event_id" to "golden-week"),
            AnalyticsEvent.canvasStarted(day, 90, 90, entry = CanvasEntry.EVENT, collectionId = "golden-week", eventId = "golden-week").params,
        )
        assertEquals(
            mapOf("source" to "remote", "size" to "medium", "entry" to "gallery", "collection_id" to "golden-week", "event_id" to "golden-week"),
            AnalyticsEvent.canvasFinished(day, 90, 90, collectionId = "golden-week", eventId = "golden-week").params,
        )
        assertEquals(
            AnalyticsEvent("event_reward_unlocked", mapOf("event_id" to "golden-week", "finished_in_window" to 3L)),
            AnalyticsEvent.eventRewardUnlocked("golden-week", 3),
        )
        assertFalse("event_id" in AnalyticsEvent.canvasFinished(day, 90, 90, collectionId = "seaside").params, "R1: a collection alone")
        assertTrue(AnalyticsEvent.canvasStarted(day, 90, 90, eventId = "golden-week").params.values.none { it == "w3" })
    }

    /** T35: the reminder's events, with reasons, results, minutes and attempts only. */
    @Test
    fun reminderEventsCarryReasonsAndTimesButNoIds() {
        assertEquals(AnalyticsEvent("reminder_prompt_shown", mapOf("attempt" to 2L)), AnalyticsEvent.reminderPromptShown(2))
        assertEquals(AnalyticsEvent("reminder_prompt_result", mapOf("result" to "denied_system")), AnalyticsEvent.reminderPromptResult("denied_system"))
        assertEquals(
            AnalyticsEvent("reminder_settings_changed", mapOf("on" to "true", "minutes" to 1230L)),
            AnalyticsEvent.reminderSettingsChanged(on = true, minutes = 1230),
        )
        assertEquals(AnalyticsEvent("reminder_shown", mapOf("reason" to "almost_finished")), AnalyticsEvent.reminderShown(ReminderReason.ALMOST_FINISHED))
        assertEquals(AnalyticsEvent("reminder_late", mapOf("minutes" to 135L)), AnalyticsEvent.reminderLate(135))
        assertEquals(AnalyticsEvent("reminder_opened", mapOf("reason" to "drop")), AnalyticsEvent.reminderOpened(ReminderReason.DROP))
        assertEquals(listOf("drop", "today", "streak", "almost_finished"), ReminderReason.entries.map { it.param })
        assertEquals(listOf("granted", "declined_in_app", "denied_system", "blocked"), AskResult.entries.map { it.param })
        assertEquals(
            mapOf("source" to "bundled", "size" to "medium", "entry" to "reminder"),
            AnalyticsEvent.canvasStarted(CanvasRef(CanvasKind.BUNDLED, "cherries"), 104, 78, entry = CanvasEntry.REMINDER).params,
        )
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
        // #29: a photo canvas carries its color choice; a picture has none.
        assertEquals(AnalyticsEvent("photo_canvas_started", mapOf("colors" to "fewer")), AnalyticsEvent.photoCanvasStarted(ColorChoice.FEWER))
        assertEquals(
            AnalyticsEvent("canvas_started", mapOf("source" to "photo", "size" to "big", "colors" to "more", "entry" to "gallery")),
            AnalyticsEvent.canvasStarted(photo, 106, 142, ColorChoice.MORE),
        )
        assertEquals(AnalyticsEvent("rewarded_watched", mapOf("reward_type" to "colors")), AnalyticsEvent.rewardedWatched(RewardType.COLORS))
        assertEquals(AnalyticsEvent("streak_day", mapOf("length" to 3L)), AnalyticsEvent.streakDay(3))
        assertEquals(
            AnalyticsEvent("todays_picture_started", mapOf("size" to "medium", "tier" to "free")),
            AnalyticsEvent.todaysPictureStarted(CanvasRef(CanvasKind.BUNDLED, "cherries"), 104, 78, vip = false),
        )
        assertEquals(
            AnalyticsEvent("inventory_earned", mapOf("item" to "fills", "source" to "wheel", "amount" to 10L)),
            AnalyticsEvent.inventoryEarned(Item.FILLS, EarnSource.WHEEL, 10),
        )
        assertEquals(AnalyticsEvent("inventory_spent", mapOf("item" to "unlock_token")), AnalyticsEvent.inventorySpent(Item.UNLOCK_TOKEN))
        assertEquals(
            AnalyticsEvent("rewarded_offered", mapOf("reward_type" to "fills")),
            AnalyticsEvent.rewardedOffered(RewardType.FILLS),
        )
        assertEquals(
            AnalyticsEvent("fill_tool_used", mapOf("tool" to "region", "size" to "big", "tier" to "vip")),
            AnalyticsEvent.fillToolUsed(photo, 106, 142, "region", "vip"),
        )
        assertEquals(
            AnalyticsEvent("timelapse_made", mapOf("source" to "photo", "size" to "big", "outcome" to "picture", "wall_ms" to 41_000L)),
            AnalyticsEvent.timelapseMade(photo, 106, 142, "picture", 41_000),
        )
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
            AnalyticsEvent.shareTapped(photo, 56, 56, progress = true),
        )
        for (e in all) for (v in e.params.values.map { it.toString() }) assertFalse(v == photo.id || v == puppy.id || "Puppy" in v, "${e.name}: $v")
    }

    /** T35 (eng D6, R1): nothing is logged before the saved consent is applied; later changes still pass through. */
    @Test
    fun nothingLogsBeforeConsentAndLaterChangesStillApply() {
        val seen = mutableListOf<String>()
        val gate = ConsentFirst(object : Analytics {
            override fun log(event: AnalyticsEvent) {
                seen += event.name
            }

            override fun consent(mode: ConsentModeV2) {
                seen += "consent ${mode.analyticsStorage}"
            }
        })
        gate.log(AnalyticsEvent.reminderShown(ReminderReason.STREAK))
        assertEquals(emptyList(), seen, "a background start's first log, before onCreate applied consent")
        val denied = ConsentModeV2(analyticsStorage = false, adStorage = false, adUserData = false, adPersonalization = false)
        gate.consent(denied)
        gate.log(AnalyticsEvent.reminderShown(ReminderReason.STREAK))
        gate.consent(ConsentModeV2.GRANTED)
        assertEquals(listOf("consent false", "reminder_shown", "consent true"), seen)
        assertEquals(ConsentModeV2.GRANTED, gate.applied)
    }

    @Test
    fun consentModeFlagsFollowUmp() {
        assertEquals(
            mapOf("analytics_storage" to true, "ad_storage" to true, "ad_user_data" to false, "ad_personalization" to false),
            Analytics.consentFlags(ConsentModeV2(analyticsStorage = true, adStorage = true, adUserData = false, adPersonalization = false)),
        )
        assertTrue(Analytics.consentFlags(ConsentModeV2.GRANTED).values.all { it })
    }
}
