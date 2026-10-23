package com.brewingbytes.gemmory.photo

import com.brewingbytes.gemmory.copy.XmlStrings
import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.converter.PreviewPrimary
import com.brewingbytes.gemmory.policy.Allowance
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Preview's copy for the color choice (#29), as in the design system's Preview table. */
class PreviewCopyTest {
    @Test
    fun theSizeLineUsesTheChoicesCapUntilConverted() {
        assertEquals("15,052 gems · 36 colors", summary(XmlStrings.en, CanvasSize.BIG, AspectRatio.PORTRAIT, null))
        assertEquals("15,052 gems · 60 colors", summary(XmlStrings.en, CanvasSize.BIG, AspectRatio.PORTRAIT, Conversion.Running, ColorChoice.MORE))
        assertEquals("3,072 gems · 12 colors", summary(XmlStrings.en, CanvasSize.QUICK, AspectRatio.LANDSCAPE, null, ColorChoice.FEWER))
        assertEquals("8,100 gems · 40 colors", summary(XmlStrings.en, CanvasSize.MEDIUM, AspectRatio.SQUARE, null, ColorChoice.MORE))
    }

    @Test
    fun theLineUnderTellsWhatMoreNeeds() {
        assertEquals("More colors: watch a video, or get VIP.", previewLine(XmlStrings.en, Allowance.Free, ColorChoice.MORE, moreUnlocked = false))
        assertEquals("Uses today's free photo canvas.", previewLine(XmlStrings.en, Allowance.Free, ColorChoice.MORE, moreUnlocked = true))
        assertEquals("One video gets this canvas with more colors.", previewLine(XmlStrings.en, Allowance.WatchVideo, ColorChoice.MORE, moreUnlocked = false))
        assertEquals(
            "Today's free one is used. Watch a video for another.",
            previewLine(XmlStrings.en, Allowance.WatchVideo, ColorChoice.STANDARD, moreUnlocked = false),
        )
        assertEquals("Uses a kept photo canvas (2 left).", previewLine(XmlStrings.en, Allowance.Bonus, ColorChoice.FEWER, moreUnlocked = false, kept = 2))
        assertNull(previewLine(XmlStrings.en, Allowance.Unlimited, ColorChoice.MORE, moreUnlocked = false), "VIP: no line, nothing to unlock")
    }

    @Test
    fun primaryAndTroughLabels() {
        assertEquals("Start", primaryText(XmlStrings.en, PreviewPrimary.START))
        assertEquals("Watch a video", primaryText(XmlStrings.en, PreviewPrimary.WATCH_FOR_CANVAS))
        assertEquals("Watch a video for more colors", primaryText(XmlStrings.en, PreviewPrimary.WATCH_FOR_COLORS))
        assertEquals("More, 60 colors, needs a video or VIP", colorTroughLabel(XmlStrings.en, CanvasSize.BIG, ColorChoice.MORE, needsVideo = true))
        assertEquals("Fewer, 16 colors", colorTroughLabel(XmlStrings.en, CanvasSize.MEDIUM, ColorChoice.FEWER, needsVideo = false))
    }
}
