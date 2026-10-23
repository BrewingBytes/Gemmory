package com.brewingbytes.gemmory.policy

import kotlin.test.Test
import kotlin.test.assertEquals

/** T46: Play's install referrer to its `utm_*` fields, garbage dropped. */
class ReferrerParseTest {
    @Test
    fun aShareLinksReferrerReadsBack() {
        assertEquals(
            Referrer("share", "bundled", "timelapse", null),
            ReferrerParse.parse("utm_source=share&utm_medium=bundled&utm_campaign=timelapse"),
        )
        assertEquals(
            Referrer("share", "bundled", "timelapse", "fox-in-snow"),
            ReferrerParse.parse("utm_source=share&utm_medium=bundled&utm_campaign=timelapse&utm_content=fox-in-snow"),
        )
        assertEquals(Referrer("google-play", "organic", null, null), ReferrerParse.parse("utm_source=google-play&utm_medium=organic"))
    }

    @Test
    fun valuesAreDecodedOnceAndUnknownKeysDropped() {
        assertEquals(Referrer("share", null, null, "a_b"), ReferrerParse.parse("utm_source=sh%61re&gclid=xyz&utm_content=a%5Fb&foo"))
        assertEquals(Referrer(null, "x", null, null), ReferrerParse.parse("utm_source=%2541&utm_medium=x"), "%25 decodes to a % once, not a token")
    }

    @Test
    fun garbageAndOversizeValuesAreDropped() {
        assertEquals(Referrer.NONE, ReferrerParse.parse(null))
        assertEquals(Referrer.NONE, ReferrerParse.parse(""))
        assertEquals(Referrer.NONE, ReferrerParse.parse("not a referrer"))
        assertEquals(Referrer.NONE, ReferrerParse.parse("=share&utm_source&utm_medium="))
        assertEquals(Referrer.NONE, ReferrerParse.parse("utm_source=%zz&utm_medium=%4"), "malformed escapes")
        assertEquals(Referrer.NONE, ReferrerParse.parse("utm_source=<script>&utm_content=a b"), "not plain tokens")
        assertEquals(Referrer(null, "ok", null, null), ReferrerParse.parse("utm_source=${"a".repeat(101)}&utm_medium=ok"))
        assertEquals("a".repeat(100), ReferrerParse.parse("utm_content=${"a".repeat(100)}").content)
        assertEquals(Referrer("first", null, null, null), ReferrerParse.parse("utm_source=first&utm_source=second"), "the first wins")
    }

    @Test
    fun inputPastTheCapIsIgnored() {
        val padded = "x=${"y".repeat(ReferrerParse.MAX_INPUT)}&utm_source=share"
        assertEquals(Referrer.NONE, ReferrerParse.parse(padded))
    }
}
