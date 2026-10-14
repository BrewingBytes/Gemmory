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
import com.brewingbytes.gemmory.policy.LedgerCodec
import com.brewingbytes.gemmory.policy.RewardType
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

    private companion object {
        const val FILE = "unlocks"
        const val KEY = "pictures"
    }
}

/**
 * A locked picture shows "Watch to unlock" until it's unlocked, or for VIP. One already started stays open whatever
 * the entitlement (eng E4c: entitlement is checked at Start only, so a lapsed VIP keeps started canvases).
 */
fun isLocked(picture: BundledPicture, unlocked: Set<String>, entitlements: Entitlements, started: Boolean): Boolean =
    picture.locked && picture.id !in unlocked && !entitlements.vip && !started

/**
 * App-scoped home of the pure policies (eng E5): [DailyLedger] and [AdPolicy] under `filesDir/ledger` (never backed up,
 * BackupRulesTest), the permanent picture unlocks, and the entitlements (always Free until #8). The policies aren't
 * thread-safe, so every call runs on [policy], one at a time.
 */
class Monetization(
    filesDir: File,
    clock: DayClock,
    val unlocks: PictureUnlocks,
    private val scope: CoroutineScope,
    private val policy: CoroutineDispatcher,
    reporter: NonFatalReporter = NonFatalReporter.Logcat,
) {
    private val ledger by lazy {
        DailyLedger(clock, FileStateStore(File(filesDir, StorageLayout.LEDGER_DAILY), LedgerCodec::encode, LedgerCodec::decode, reporter))
    }
    private val ads by lazy {
        AdPolicy(clock, FileStateStore(File(filesDir, StorageLayout.LEDGER_ADS), AdPolicyCodec::encode, AdPolicyCodec::decode, reporter))
    }

    /** Billing (#8) replaces this with the real entitlement; until then everyone is Free. */
    private val _entitlements = MutableStateFlow(Entitlements.FREE)
    val entitlements: StateFlow<Entitlements> = _entitlements.asStateFlow()

    private val _allowance = MutableStateFlow<Allowance>(Allowance.Free)

    /** Drives the Preview allowance line and the gallery note (design D5). */
    val allowance: StateFlow<Allowance> = _allowance.asStateFlow()

    private var midnight: Job? = null

    init {
        scope.launch(policy) { ads.onProcessStart() }
        armMidnight()
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

    suspend fun interstitialBlock(ctx: InterstitialContext): AdBlock? = onPolicy { ads.blockFor(ctx) }

    /** Main thread (process lifecycle). */
    fun onForeground() {
        scope.launch(policy) { ads.onForeground() }
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
