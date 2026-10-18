package com.brewingbytes.gemmory.converter

import com.brewingbytes.gemmory.converter.CanvasSize.BIG
import com.brewingbytes.gemmory.converter.CanvasSize.MEDIUM
import com.brewingbytes.gemmory.converter.CanvasSize.QUICK
import com.brewingbytes.gemmory.policy.Allowance
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

    @Test
    fun startWaitsForTheFaceCheck() {
        // A quick Quick → Next → Start during the check must not make a Quick face canvas.
        assertEquals(false, PhotoFlowRules.canStart(FaceCheck.RUNNING, QUICK, converted = true))
        assertEquals(false, PhotoFlowRules.canStart(FaceCheck.FACE, QUICK, converted = true))
        assertEquals(true, PhotoFlowRules.canStart(FaceCheck.FACE, MEDIUM, converted = true))
        assertEquals(true, PhotoFlowRules.canStart(FaceCheck.NO_FACE, QUICK, converted = true))
    }

    @Test
    fun startNeedsTheConversionAndAnAllowedRow() {
        assertEquals(false, PhotoFlowRules.canStart(FaceCheck.NO_FACE, MEDIUM, converted = false))
        for (size in CanvasSize.entries) {
            assertEquals(false, PhotoFlowRules.canStart(FaceCheck.FACE, size, converted = true, faceMinSize = FaceMinSize.DISABLED))
        }
        assertEquals(false, PhotoFlowRules.canStart(FaceCheck.FACE, MEDIUM, converted = true, faceMinSize = FaceMinSize.BIG))
        assertEquals(true, PhotoFlowRules.canStart(FaceCheck.FACE, BIG, converted = true, faceMinSize = FaceMinSize.BIG))
    }

    /** #29: every allowance × choice × unlocked, against the Preview table in the design system. */
    @Test
    fun previewPrimaryForEveryColorChoice() {
        val allowances = listOf(Allowance.Free, Allowance.Bonus, Allowance.WatchVideo, Allowance.Unlimited)
        for (allowance in allowances) for (choice in ColorChoice.entries) for (unlocked in listOf(false, true)) {
            val expected = when {
                allowance == Allowance.WatchVideo -> PreviewPrimary.WATCH_FOR_CANVAS
                choice == ColorChoice.MORE && allowance != Allowance.Unlimited && !unlocked -> PreviewPrimary.WATCH_FOR_COLORS
                else -> PreviewPrimary.START
            }
            assertEquals(expected, PhotoFlowRules.previewPrimary(allowance, choice, unlocked), "$allowance $choice unlocked=$unlocked")
        }
        assertEquals(false, PhotoFlowRules.colorsAllowed(ColorChoice.MORE, Allowance.Free, moreUnlocked = false))
        assertEquals(true, PhotoFlowRules.colorsAllowed(ColorChoice.MORE, Allowance.Unlimited, moreUnlocked = false), "VIP")
        assertEquals(true, PhotoFlowRules.colorsAllowed(ColorChoice.FEWER, Allowance.WatchVideo, moreUnlocked = false))
    }
}
