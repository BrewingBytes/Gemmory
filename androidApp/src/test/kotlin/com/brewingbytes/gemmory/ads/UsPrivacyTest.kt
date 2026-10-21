package com.brewingbytes.gemmory.ads

import com.iab.gpp.encoder.GppModel
import com.iab.gpp.encoder.field.FieldKey
import com.iab.gpp.encoder.field.TcfEuV2Field
import com.iab.gpp.encoder.field.UsCaField
import com.iab.gpp.encoder.field.UsNatField
import com.iab.gpp.encoder.field.UsVaField
import com.iab.gpp.encoder.section.TcfEuV2
import com.iab.gpp.encoder.section.UsCa
import com.iab.gpp.encoder.section.UsNat
import com.iab.gpp.encoder.section.UsVa
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unity's US-states privacy.consent from the GPP string (T34, ad-mediation.md): strings captured from UMP, plus cases
 * built with the GPP library's own encoder for the opt-outs UMP's single choice doesn't separate.
 */
class UsPrivacyTest {
    private val reports = mutableListOf<Throwable>()

    private fun consent(gpp: String?) = UsPrivacy.consent(gpp) { reports += it }

    private fun gpp(vararg fields: Triple<String, FieldKey, Int>): String = GppModel().apply {
        fields.forEach { (section, field, value) -> setFieldValue(section, field, value) }
    }.encode()

    /** usnat with the three opt-outs; 2 = didn't opt out (UMP's answer for "no"). */
    private fun usnat(sale: Int = 2, sharing: Int = 2, targeted: Int = 2) = gpp(
        Triple(UsNat.NAME, UsNatField.SALE_OPT_OUT, sale),
        Triple(UsNat.NAME, UsNatField.SHARING_OPT_OUT, sharing),
        Triple(UsNat.NAME, UsNatField.TARGETED_ADVERTISING_OPT_OUT, targeted),
    )

    /**
     * Captured from UMP 4.0.0 on the emulator (2026-10-21, debug build with `-Pgemmory.umpDebugUsState=true`, so the
     * US-states message of Google's sample app ID): the first launch, "Don't sell or share my data" in Settings → Privacy choices, then "Allow"
     * again. Unity read false after the opt-out and true after it (UnityAdsStorage-public-data.json).
     */
    @Test
    fun stringsCapturedFromUmp() {
        assertTrue(consent("DBABL~BAAAAAAAAg"))
        assertFalse(consent("DBABL~BVQVAAAAAg"))
        assertTrue(consent("DBABL~BVQqAAAAAg"))
        assertEquals(emptyList(), reports)
    }

    @Test
    fun saleOptOutIsNoConsent() = assertFalse(consent(usnat(sale = 1)))

    @Test
    fun sharingOptOutIsNoConsent() = assertFalse(consent(usnat(sharing = 1)))

    @Test
    fun targetedAdvertisingOptOutIsNoConsent() = assertFalse(consent(usnat(targeted = 1)))

    @Test
    fun noOptOutIsConsent() {
        assertTrue(consent(usnat()))
        // 0 = not applicable counts as no opt-out too.
        assertTrue(consent(usnat(sale = 0, sharing = 0, targeted = 0)))
        assertEquals(emptyList(), reports)
    }

    @Test
    fun aStateSectionOptOutCounts() {
        assertFalse(consent(gpp(Triple(UsCa.NAME, UsCaField.SALE_OPT_OUT, 1))))
        assertFalse(consent(gpp(Triple(UsVa.NAME, UsVaField.TARGETED_ADVERTISING_OPT_OUT, 1))))
        // An opt-out in one US section wins over "no" in another.
        assertFalse(consent(gpp(Triple(UsNat.NAME, UsNatField.SALE_OPT_OUT, 2), Triple(UsCa.NAME, UsCaField.SHARING_OPT_OUT, 1))))
        assertTrue(consent(gpp(Triple(UsVa.NAME, UsVaField.SALE_OPT_OUT, 2))))
    }

    @Test
    fun noGppStringOrNoUsSectionIsConsent() {
        assertTrue(consent(null))
        assertTrue(consent(""))
        // An EEA player: only the TCF section, no US section.
        assertTrue(consent(gpp(Triple(TcfEuV2.NAME, TcfEuV2Field.CMP_ID, 300))))
        assertEquals(emptyList(), reports)
    }

    @Test
    fun aMalformedStringFailsSafeAndIsReported() {
        assertFalse(consent("not a gpp string"))
        // A valid header naming usnat, with a section that isn't one.
        assertFalse(consent(usnat(sale = 1).substringBefore('~') + "~%%%"))
        assertEquals(2, reports.size)
    }

    /** A GPP library update with a new US state can't slip past the opt-out table. */
    @Test
    fun everyUsSectionTheLibraryKnowsHasAnOptOutRow() {
        val known = (UsNat.ID..64).filter { id ->
            val error = runCatching { GppModel().setFieldValue(id, UsNatField.SALE_OPT_OUT, 0) }.exceptionOrNull()
            error == null || error.message?.endsWith("not found") != true
        }
        assertTrue(known.size >= 21, "the library knows $known")
        assertEquals(known.toSet(), UsPrivacy.OPT_OUTS.keys)
    }
}
