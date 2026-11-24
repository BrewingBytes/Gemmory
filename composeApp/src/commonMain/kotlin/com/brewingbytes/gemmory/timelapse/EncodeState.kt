package com.brewingbytes.gemmory.timelapse

/**
 * Where one canvas's share stands, for the progress sheet (design system, Share progress sheet). Shared since iOS
 * Stage 1c (T64): a result names its file by path, which each platform's share sheet opens (Android's `file`
 * extensions read it as a `java.io.File`).
 */
sealed interface EncodeState {
    data object Idle : EncodeState

    /** [done] of [total] frames of the current attempt. */
    data class Running(val done: Int, val total: Int) : EncodeState {
        val percent: Int get() = if (total == 0) 0 else done * 100 / total
    }

    /**
     * The finished MP4 at [path], ready for the share sheet, and the feed cut made beside it for TikTok, Instagram and
     * YouTube at [feedPath] (T51; null when there isn't one). Not a data class: every result is a new value, so a
     * re-share of the same cached file still reaches the screen.
     */
    class Video(val path: String, val feedPath: String? = null) : EncodeState {
        override fun toString() = "Video(${nameOf(path)}${feedPath?.let { ", ${nameOf(it)}" }.orEmpty()})"
    }

    /** No video target worked: the before/after PNG at [path] (eng E1). Identity-equal like [Video]. */
    class Picture(val path: String) : EncodeState {
        override fun toString() = "Picture(${nameOf(path)})"
    }

    data object Failed : EncodeState

    data object DiskFull : EncodeState
}

/** The file name of [path], for logs: never the folders. */
private fun nameOf(path: String): String = path.substringAfterLast('/')
