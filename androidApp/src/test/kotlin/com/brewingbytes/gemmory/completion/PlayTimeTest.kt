package com.brewingbytes.gemmory.completion

import com.brewingbytes.gemmory.copy.XmlStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayTimeTest {
    @Test
    fun metaLineTime() {
        assertEquals("1 min", CompletionViewModel.playTime(XmlStrings.en.words, 0))
        assertEquals("1 min", CompletionViewModel.playTime(XmlStrings.en.words, 20_000))
        assertEquals("5 min", CompletionViewModel.playTime(XmlStrings.en.words, 4 * 60_000 + 1))
        assertEquals("1 h 12 min", CompletionViewModel.playTime(XmlStrings.en.words, 72 * 60_000L))
    }
}
