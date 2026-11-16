package com.brewingbytes.gemmory.storage

/** Which folder of [StorageLayout] a canvas lives in (common since iOS Stage 1c, T64: the gallery and its services name canvases by it). */
enum class CanvasKind(val dir: String) {
    PHOTO(StorageLayout.PHOTO_CANVASES),
    BUNDLED(StorageLayout.BUNDLED_CANVASES),

    /** A remote picture (#43): played exactly like a bundled one, its grid copied in from the download cache. */
    REMOTE(StorageLayout.REMOTE_CANVASES),
}

data class CanvasRef(val kind: CanvasKind, val id: String) {
    init {
        require(ID.matches(id)) { "canvas id must match ${ID.pattern}" }
    }

    companion object {
        /** Lowercase, no dots or slashes, so an id is always one plain directory name. */
        val ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")
    }
}

/** How a canvas write went. */
sealed interface WriteResult {
    data object Written : WriteResult

    /** [diskFull] drives "Progress isn't saving: phone storage is full." on the canvas (design D9). */
    data class WriteFailed(val diskFull: Boolean) : WriteResult
}
