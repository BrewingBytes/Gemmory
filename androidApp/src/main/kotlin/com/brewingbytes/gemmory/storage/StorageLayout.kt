package com.brewingbytes.gemmory.storage

/**
 * Paths under `filesDir`. The backup rules in res/xml/backup_rules.xml and data_extraction_rules.xml exclude
 * [PHOTO_CANVASES], [LEDGER] and [REMOTE] by these exact paths (BackupRulesTest keeps them in sync). Everything else may be
 * backed up, so photo-derived data must only ever live under [PHOTO_CANVASES] (or cacheDir, which is never
 * backed up).
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
}
