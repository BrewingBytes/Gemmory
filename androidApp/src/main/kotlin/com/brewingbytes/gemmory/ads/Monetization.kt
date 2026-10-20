package com.brewingbytes.gemmory.ads

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.brewingbytes.gemmory.bundled.BundledPicture
import com.brewingbytes.gemmory.policy.AdBlock
import com.brewingbytes.gemmory.policy.AdPolicy
import com.brewingbytes.gemmory.policy.AdPolicyCodec
import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.policy.ConsumeResult
import com.brewingbytes.gemmory.policy.DailyLedger
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.InterstitialContext
import com.brewingbytes.gemmory.policy.Inventory
import com.brewingbytes.gemmory.policy.InventoryCodec
import com.brewingbytes.gemmory.policy.InventoryState
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.policy.LedgerCodec
import com.brewingbytes.gemmory.policy.RewardType
import com.brewingbytes.gemmory.policy.WheelPrize
import com.brewingbytes.gemmory.storage.FileStateStore
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Locked bundled pictures the player unlocked (design doc, unlock storage): permanent, in shared preferences that
 * may be backed up (no user content).
 */
class PictureUnlocks(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    private val _unlocked = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    val unlocked: StateFlow<Set<String>> = _unlocked.asStateFlow()

    fun unlock(pictureId: String) {
        _unlocked.update { it + pictureId }
        prefs.edit { putStringSet(KEY, _unlocked.value) }
    }

    companion object {
        /** Backed up, so unlocks survive a reinstall (#32, BackupRulesTest). */
        internal const val FILE = "unlocks"
        private const val KEY = "pictures"
    }
}

/**
 * A locked picture shows "Watch to unlock" until it's unlocked, or for VIP. One already started stays open whatever
 * the entitlement (eng E4c: entitlement is checked at Start only, so a lapsed VIP keeps started canvases).
 */
fun isLocked(picture: BundledPicture, unlocked: Set<String>, entitlements: Entitlements, started: Boolean): Boolean =
    isLocked(picture.locked, picture.id, unlocked, entitlements, started)

/** The same rule for any picture by its catalog flag [locked]: bundled, or remote (#43). */
fun isLocked(locked: Boolean, id: String, unlocked: Set<String>, entitlements: Entitlements, started: Boolean): Boolean =
    locked && id !in unlocked && !entitlements.vip && !started

/**
 * App-scoped home of the pure policies (eng E5): [DailyLedger] and [AdPolicy] under `filesDir/ledger` (never backed up,
 * BackupRulesTest), the permanent picture unlocks, and [entitlements] from billing (#8), which the allowance follows.
 * The policies aren't thread-safe, so every call runs on [policy], one at a time.
 */
class Monetization(
    filesDir: File,
    clock: DayClock,
    val unlocks: PictureUnlocks,
    private val scope: CoroutineScope,
    private val policy: CoroutineDispatcher,
    reporter: NonFatalReporter = NonFatalReporter.Logcat,
    val entitlements: StateFlow<Entitlements> = MutableStateFlow(Entitlements.FREE),
    /**
     * Counts this process as a cold start now. The app passes false and calls [onProcessStart] at the process's first
     * foreground instead (T35, eng D11), so a reminder or reboot starting it in the background never ends the first
     * session. False also for a test's second instance in the same process.
     */
    processStart: Boolean = true,
) {
    /** The inventory (#38), backed up unlike the ledger; the ledger keeps its photo canvases in it. */
    private val items by lazy {
        Inventory(FileStateStore(File(filesDir, StorageLayout.INVENTORY), InventoryCodec::encode, InventoryCodec::decode, reporter))
    }
    private val ledger by lazy {
        DailyLedger(
            clock,
            FileStateStore(File(filesDir, StorageLayout.LEDGER_DAILY), LedgerCodec::encode, LedgerCodec::decode, reporter),
            bank = items,
        )
    }
    private val ads by lazy {
        AdPolicy(clock, FileStateStore(File(filesDir, StorageLayout.LEDGER_ADS), AdPolicyCodec::encode, AdPolicyCodec::decode, reporter))
    }

    private val _allowance = MutableStateFlow<Allowance>(Allowance.Free)

    /** Drives the Preview allowance line and the gallery note (design D5). */
    val allowance: StateFlow<Allowance> = _allowance.asStateFlow()

    private val _inventory = MutableStateFlow(InventoryState())

    /** What the inventory holds (#38): the gallery's Inventory sheet, the tool well's count and the kept canvases. */
    val inventory: StateFlow<InventoryState> = _inventory.asStateFlow()

    private val _firstSessionOver = MutableStateFlow(false)

    /** From the 2nd session on, the gallery offers its quiet "VIP" action (design D3; the session is eng D6's). */
    val firstSessionOver: StateFlow<Boolean> = _firstSessionOver.asStateFlow()

    private var midnight: Job? = null

    private val following: Job

    init {
        scope.launch(policy) {
            if (processStart) ads.onProcessStart()
            _firstSessionOver.value = ads.firstSessionOver
        }
        armMidnight()
        // VIP starting or lapsing changes the allowance at once (Unlimited, or back to today's free canvas).
        following = scope.launch { entitlements.collect { withContext(policy) { refreshLocked() } } }
    }

    /** The process came to the screen for the first time (eng D11): a cold start after the first one ends the first session. */
    fun onProcessStart() {
        scope.launch(policy) {
            ads.onProcessStart()
            _firstSessionOver.value = ads.firstSessionOver
        }
    }

    /** Stops the midnight refresh and the entitlement follower, for a test that replaces this instance. */
    @Synchronized
    fun close() {
        midnight?.cancel()
        following.cancel()
    }

    /**
     * Refreshes at every local midnight while the process lives. Re-armed on each foreground, so a clock or time zone
     * moved while the app was away can't leave it waiting for the wrong midnight.
     */
    @Synchronized
    private fun armMidnight() {
        midnight?.cancel()
        midnight = scope.launch {
            while (true) {
                val wait = withContext(policy) {
                    refreshLocked()
                    ledger.millisUntilReset()
                }
                delay(wait + MIDNIGHT_SLACK_MS)
            }
        }
    }

    private fun refreshLocked() {
        _allowance.value = ledger.allowance(entitlements.value)
        _inventory.value = items.state
    }

    private suspend fun <T> onPolicy(block: () -> T): T = withContext(policy) {
        block().also { refreshLocked() }
    }

    suspend fun refresh() = onPolicy { }

    /** Start on Preview (eng E4a): idempotent per draft. */
    suspend fun consume(draftId: String): ConsumeResult = onPolicy { ledger.consume(draftId, entitlements.value) }

    /** The canvas couldn't be written: give the allowance back. */
    suspend fun release(draftId: String) = onPolicy { ledger.release(draftId) }

    /** An extra-canvas video was watched to the end. */
    suspend fun grantReward() = onPolicy { ledger.grantReward() }

    suspend fun graceAvailable(type: RewardType): Boolean = onPolicy { ledger.graceAvailable(type) }

    suspend fun useGrace(type: RewardType): Boolean = onPolicy { ledger.useGrace(type) }

    /** Fills from a rewarded video or today's fills grace (#38). */
    suspend fun earnFills() = onPolicy { items.earn(Item.FILLS, Inventory.FILLS_PER_VIDEO) }

    /** Spends one [item] for [useId] (idempotent per use); false when none is left. VIP spends nothing. */
    suspend fun spend(item: Item, useId: String): Boolean = onPolicy {
        entitlements.value.vip || items.spend(item, useId)
    }

    /** Opens [pictureId] for good with an unlock token (#38); false when there is none. */
    suspend fun useToken(pictureId: String): Boolean {
        if (!spend(Item.UNLOCK_TOKEN, "unlock-$pictureId")) return false
        unlocks.unlock(pictureId)
        return true
    }

    /** The 5-day streak gift's spin (#38): the prize is added at once. Null when none is waiting, and for VIP. */
    suspend fun spin(): WheelPrize? = onPolicy { if (entitlements.value.vip) null else items.spin() }

    /** The streak reached [length] days on a new day (#33): every 5th earns a spin, for players without VIP. */
    fun onStreakDay(length: Int) {
        scope.launch(policy) {
            if (!entitlements.value.vip) items.onStreakDay(length)
            refreshLocked()
        }
    }

    suspend fun interstitialBlock(ctx: InterstitialContext): AdBlock? = onPolicy { ads.blockFor(ctx) }

    /** Main thread (process lifecycle). */
    fun onForeground() {
        scope.launch(policy) {
            ads.onForeground()
            _firstSessionOver.value = ads.firstSessionOver
        }
        armMidnight()
    }

    fun onBackground() {
        scope.launch(policy) { ads.onBackground() }
    }

    fun onPictureFinished() {
        scope.launch(policy) { ads.onPictureFinished() }
    }

    fun onRewardedShown() {
        scope.launch(policy) { ads.onRewardedShown() }
    }

    fun onInterstitialShown() {
        scope.launch(policy) { ads.onInterstitialShown() }
    }

    private companion object {
        /** Wakes just after midnight, so the new day is certainly "today". */
        const val MIDNIGHT_SLACK_MS = 1_000L
    }
}
