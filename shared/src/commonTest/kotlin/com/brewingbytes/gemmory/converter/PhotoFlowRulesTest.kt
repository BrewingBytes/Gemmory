package com.brewingbytes.gemmory.converter

import com.brewingbytes.gemmory.converter.CanvasSize.BIG
import com.brewingbytes.gemmory.converter.CanvasSize.MEDIUM
import com.brewingbytes.gemmory.converter.CanvasSize.QUICK
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoFlowRulesTest {
    @Test
    fun cropDefaultFollowsOrientation() {
        assertEquals(AspectRatio.PORTRAIT, PhotoFlowRules.defaultRatio(768, 1024))
        assertEquals(AspectRatio.LANDSCAPE, PhotoFlowRules.defaultRatio(1024, 768))
        assertEquals(AspectRatio.SQUARE, PhotoFlowRules.defaultRatio(800, 800))
    }

    @Test
    fun shipsWithMediumAfterTheSpikeGo() {
        assertEquals(FaceMinSize.MEDIUM, PhotoFlowRules.FACE_MIN_SIZE)
    }

    /** One row per line of the faceMinSize table in docs/designs/gemmory.md. */
    @Test
    fun faceMinSizeTableRows() {
        val rows = listOf(
            FaceMinSize.QUICK to SizeRules(emptySet(), MEDIUM, null, startAllowed = true),
            FaceMinSize.MEDIUM to SizeRules(setOf(QUICK), MEDIUM, FaceHint.LOOK_BEST_MEDIUM_OR_BIG, startAllowed = true),
            FaceMinSize.BIG to SizeRules(setOf(QUICK, MEDIUM), BIG, FaceHint.NEED_BIG, startAllowed = true),
            FaceMinSize.DISABLED to SizeRules(setOf(QUICK, MEDIUM, BIG), null, FaceHint.NOT_SUPPORTED, startAllowed = false),
        )
        for ((min, expected) in rows) assertEquals(expected, PhotoFlowRules.sizeRules(FaceCheck.FACE, min), "$min")
    }

    @Test
    fun noFaceOffersEverySizeAndPreselectsMediumWhateverTheGate() {
        for (min in FaceMinSize.entries) {
            assertEquals(SizeRules(emptySet(), MEDIUM, null, startAllowed = true), PhotoFlowRules.sizeRules(FaceCheck.NO_FACE, min))
        }
    }

    @Test
    fun whileTheCheckRunsRowsStayEnabledWithNoHintOrPreselection() {
        assertEquals(SizeRules(emptySet(), null, null, startAllowed = true), PhotoFlowRules.sizeRules(FaceCheck.RUNNING))
    }

    @Test
    fun selectionAfterTheCheck() {
        val face = PhotoFlowRules.sizeRules(FaceCheck.FACE)
        // Default selection follows the rule.
        assertEquals(MEDIUM, PhotoFlowRules.selectionAfterCheck(QUICK, pickedByPlayer = false, rules = face))
        // A player's Quick becomes disabled by the face rule, so it moves to Medium.
        assertEquals(MEDIUM, PhotoFlowRules.selectionAfterCheck(QUICK, pickedByPlayer = true, rules = face))
        // A player's Big is still allowed and is kept.
        assertEquals(BIG, PhotoFlowRules.selectionAfterCheck(BIG, pickedByPlayer = true, rules = face))
        val noFace = PhotoFlowRules.sizeRules(FaceCheck.NO_FACE)
        assertEquals(QUICK, PhotoFlowRules.selectionAfterCheck(QUICK, pickedByPlayer = true, rules = noFace))
    }
}
