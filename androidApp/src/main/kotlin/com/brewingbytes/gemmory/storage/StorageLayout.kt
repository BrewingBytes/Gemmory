package com.brewingbytes.gemmory.storage

/**
 * Paths under `filesDir`. The backup rules in res/xml/backup_rules.xml and data_extraction_rules.xml exclude
 * [PHOTO_CANVASES] and [LEDGER] by these exact paths (BackupRulesTest keeps them in sync). Everything else may be
 * backed up, so photo-derived data must only ever live under [PHOTO_CANVASES] (or cacheDir, which is never
 * backed up).
 */
object StorageLayout {
    const val CANVASES = "canvases"

    /** One dir per photo canvas: grid, progress, view state and, later, the cropped source. Never backed up. */
    const val PHOTO_CANVASES = "$CANVASES/photo"

    /** Bundled-picture progress. No user content, so it may be backed up. */
    const val BUNDLED_CANVASES = "$CANVASES/bundled"

    /** Reserved for the daily allowance + grace counters (DailyLedger). Never backed up. */
    const val LEDGER = "ledger"
}
