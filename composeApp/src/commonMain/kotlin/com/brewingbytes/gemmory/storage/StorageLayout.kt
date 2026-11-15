package com.brewingbytes.gemmory.storage

/**
 * Where the app keeps its data, on both platforms (common since iOS Stage 1c, T64): paths under the app's files folder
 * (`filesDir` on Android, Application Support on iOS) and the key-value files. [neverBackedUp] is the one list of what
 * stays on the phone: Android's backup rules (res/xml/backup_rules.xml, data_extraction_rules.xml) must exclude exactly
 * it plus Firebase's own files (BackupRulesTest), and the iOS store excludes the same paths from backup. Everything
 * else may be backed up, so photo-derived data must only ever live under [PHOTO_CANVASES] (or the cache, which is
 * never backed up).
 */
object StorageLayout {
    const val CANVASES = "canvases"

    /** One dir per photo canvas: grid, progress, view state and, later, the cropped source. Never backed up. */
    const val PHOTO_CANVASES = "$CANVASES/photo"

    /** Bundled-picture progress. No user content, so it may be backed up. */
    const val BUNDLED_CANVASES = "$CANVASES/bundled"

    /**
     * Remote-picture progress (#43): the grid copied in from [REMOTE_GRIDS] on first open, then progress exactly like
     * a bundled picture. No user content, so it's backed up, grid included (16 KB at most), so a restored save opens
     * even after its picture left the catalog.
     */
    const val REMOTE_CANVASES = "$CANVASES/remote"

    /**
     * The remote catalog's download cache (#43): `catalog.json`, its `.sig`, the "GMRR" state (ETag, check times,
     * "New" bookkeeping) and `grids/<id>.gmrg`. Never backed up: all of it can be downloaded again.
     */
    const val REMOTE = "remote"

    const val REMOTE_CATALOG = "$REMOTE/catalog.json"
    const val REMOTE_SIGNATURE = "$REMOTE/catalog.json.sig"
    const val REMOTE_STATE = "$REMOTE/state"
    const val REMOTE_GRIDS = "$REMOTE/grids"

    /**
     * The daily allowance + grace counters (DailyLedger), the interstitial caps (AdPolicy) and the entitlement cache
     * (EntitlementCache). Never backed up or transferred: a restored cache would carry VIP to a phone Play hasn't
     * confirmed it on.
     */
    const val LEDGER = "ledger"

    /** DailyLedger, "GMRL". */
    const val LEDGER_DAILY = "$LEDGER/daily"

    /** AdPolicy, "GMRA". */
    const val LEDGER_ADS = "$LEDGER/ads"

    /** EntitlementCache, "GMRE" (#8). */
    const val LEDGER_ENTITLEMENTS = "$LEDGER/entitlements"

    /**
     * The inventory (#38), "GMRI": fills, kept photo canvases, unlock tokens and the streak wheel's spins. Backed up and
     * transferred (no user content), like the picture unlocks, so a new phone keeps what was earned.
     */
    const val INVENTORY = "inventory/items"

    /**
     * Play Games achievements (#49), "GMRA": what this phone earned and what Play Games confirmed. Backed up and
     * transferred like the streak (no user content): a new phone doesn't resend what was confirmed, nor lose what wasn't.
     */
    const val ACHIEVEMENTS = "achievements/state"

    /**
     * The streak's day log (#101), "GMRS": each streak day, how it counted and the first canvas finished that day (an
     * id, never photo data). Backed up and transferred with the streak preferences; a restore can't add days.
     */
    const val STREAK_HISTORY = "streak/history"

    /**
     * The key-value files (SharedPreferences on Android, one `NSUserDefaults` suite each on iOS), by their names on
     * Android today. Never renamed (eng D7: Android player data is never migrated); `PrefsKeysTest` pins every file
     * and key against `goldens/storage/prefs.txt`.
     */
    object Prefs {
        const val SETTINGS = "settings"
        const val UNLOCKS = "unlocks"
        const val CONSENT = "consent"
        const val REMINDER = "reminder"
        const val TODAY = "today"
        const val PLAYED = "played"
        const val REVIEW = "review"
        const val NEW_PLAYER = "new_player"
        const val FIRST_RUN = "first_run"
        const val HINTS = "hints"
        const val STREAK = "streak"
        const val COLLECTIONS = "collections"

        val ALL: List<String> = listOf(
            SETTINGS, UNLOCKS, CONSENT, REMINDER, TODAY, PLAYED, REVIEW, NEW_PLAYER, FIRST_RUN, HINTS, STREAK, COLLECTIONS,
        )
    }

    /** One thing that never leaves the phone: a folder under the files folder, or a key-value file. */
    sealed interface Kept {
        /** A folder under the files folder, everything in it included. */
        data class Folder(val path: String) : Kept

        /** A key-value file of [Prefs]. */
        data class PrefsFile(val name: String) : Kept
    }

    /**
     * What is never backed up or transferred (OV-9): the photo canvases (user photos never leave the phone), the
     * ledger (a restored entitlement cache would carry VIP to an unconfirmed phone), the remote download cache (it
     * can be downloaded again) and the launch funnel (T46: a restored phone is no fresh install). On iOS `first_run`
     * is a file under `telemetry/` rather than a defaults suite, which can't be excluded from backup.
     */
    val neverBackedUp: List<Kept> = listOf(
        Kept.Folder(PHOTO_CANVASES),
        Kept.Folder(LEDGER),
        Kept.Folder(REMOTE),
        Kept.PrefsFile(Prefs.FIRST_RUN),
    )
}
