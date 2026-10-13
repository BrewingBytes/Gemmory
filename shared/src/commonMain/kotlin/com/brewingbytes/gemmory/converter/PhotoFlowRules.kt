package com.brewingbytes.gemmory.converter

/**
 * The smallest size a face photo may use, set by the spike gate (design doc, faceMinSize table). The gate said GO
 * with Quick untested for faces, so the app ships [MEDIUM].
 */
enum class FaceMinSize { QUICK, MEDIUM, BIG, DISABLED }

/** Result of the on-device face check on the cropped photo. A failed or timed-out check counts as [NO_FACE]. */
enum class FaceCheck { RUNNING, FACE, NO_FACE }

/** Face hint copy keys, one per faceMinSize row (design system, Size option). */
enum class FaceHint { LOOK_BEST_MEDIUM_OR_BIG, NEED_BIG, NOT_SUPPORTED }

/**
 * What the size picker and Preview offer for one photo (design D4, faceMinSize table).
 * [preselect] is null while the face check runs: pre-selection applies when it ends.
 */
data class SizeRules(
    val disabled: Set<CanvasSize>,
    val preselect: CanvasSize?,
    val hint: FaceHint?,
    /** False only when faces are disabled outright: Preview replaces Start with "Choose another photo". */
    val startAllowed: Boolean,
)

object PhotoFlowRules {
    /** What the app ships with after the spike GO (2026-10-13). */
    val FACE_MIN_SIZE = FaceMinSize.MEDIUM

    /** Crop default from the photo's orientation: portrait → 3:4, landscape → 4:3, square → 1:1. */
    fun defaultRatio(width: Int, height: Int): AspectRatio = when {
        width > height -> AspectRatio.LANDSCAPE
        height > width -> AspectRatio.PORTRAIT
        else -> AspectRatio.SQUARE
    }

    fun sizeRules(check: FaceCheck, faceMinSize: FaceMinSize = FACE_MIN_SIZE): SizeRules = when (check) {
        // Rows stay enabled while the check runs; the rule lands when it ends (≤ 1 s).
        FaceCheck.RUNNING -> SizeRules(emptySet(), preselect = null, hint = null, startAllowed = true)
        FaceCheck.NO_FACE -> SizeRules(emptySet(), CanvasSize.MEDIUM, hint = null, startAllowed = true)
        FaceCheck.FACE -> when (faceMinSize) {
            FaceMinSize.QUICK -> SizeRules(emptySet(), CanvasSize.MEDIUM, hint = null, startAllowed = true)
            FaceMinSize.MEDIUM ->
                SizeRules(setOf(CanvasSize.QUICK), CanvasSize.MEDIUM, FaceHint.LOOK_BEST_MEDIUM_OR_BIG, startAllowed = true)
            FaceMinSize.BIG ->
                SizeRules(setOf(CanvasSize.QUICK, CanvasSize.MEDIUM), CanvasSize.BIG, FaceHint.NEED_BIG, startAllowed = true)
            FaceMinSize.DISABLED ->
                SizeRules(CanvasSize.entries.toSet(), preselect = null, FaceHint.NOT_SUPPORTED, startAllowed = false)
        }
    }

    /**
     * The size selected once the check ends: the rule's pre-selection, unless the player already picked a size
     * that's still allowed.
     */
    fun selectionAfterCheck(current: CanvasSize, pickedByPlayer: Boolean, rules: SizeRules): CanvasSize {
        if (pickedByPlayer && current !in rules.disabled) return current
        return rules.preselect ?: current
    }
}
