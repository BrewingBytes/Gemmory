package com.brewingbytes.gemmory.ads

import android.content.Context
import com.iab.gpp.encoder.GppModel
import com.iab.gpp.encoder.field.FieldKey
import com.iab.gpp.encoder.field.UsCaField
import com.iab.gpp.encoder.field.UsCoField
import com.iab.gpp.encoder.field.UsCtField
import com.iab.gpp.encoder.field.UsDeField
import com.iab.gpp.encoder.field.UsFlField
import com.iab.gpp.encoder.field.UsIaField
import com.iab.gpp.encoder.field.UsInField
import com.iab.gpp.encoder.field.UsKyField
import com.iab.gpp.encoder.field.UsMdField
import com.iab.gpp.encoder.field.UsMnField
import com.iab.gpp.encoder.field.UsMtField
import com.iab.gpp.encoder.field.UsNatField
import com.iab.gpp.encoder.field.UsNeField
import com.iab.gpp.encoder.field.UsNhField
import com.iab.gpp.encoder.field.UsNjField
import com.iab.gpp.encoder.field.UsOrField
import com.iab.gpp.encoder.field.UsRiField
import com.iab.gpp.encoder.field.UsTnField
import com.iab.gpp.encoder.field.UsTxField
import com.iab.gpp.encoder.field.UsUtField
import com.iab.gpp.encoder.field.UsVaField
import com.iab.gpp.encoder.section.UsCa
import com.iab.gpp.encoder.section.UsCo
import com.iab.gpp.encoder.section.UsCt
import com.iab.gpp.encoder.section.UsDe
import com.iab.gpp.encoder.section.UsFl
import com.iab.gpp.encoder.section.UsIa
import com.iab.gpp.encoder.section.UsIn
import com.iab.gpp.encoder.section.UsKy
import com.iab.gpp.encoder.section.UsMd
import com.iab.gpp.encoder.section.UsMn
import com.iab.gpp.encoder.section.UsMt
import com.iab.gpp.encoder.section.UsNat
import com.iab.gpp.encoder.section.UsNe
import com.iab.gpp.encoder.section.UsNh
import com.iab.gpp.encoder.section.UsNj
import com.iab.gpp.encoder.section.UsOr
import com.iab.gpp.encoder.section.UsRi
import com.iab.gpp.encoder.section.UsTn
import com.iab.gpp.encoder.section.UsTx
import com.iab.gpp.encoder.section.UsUt
import com.iab.gpp.encoder.section.UsVa
import com.unity3d.ads.metadata.MetaData

/**
 * US-states consent for Unity Ads (T34, ad-mediation.md). Unity doesn't read the GPP string UMP writes, so the app
 * passes `privacy.consent` itself. GDPR needs no code: the adapter forwards `IABTCF_AddtlConsent` on its own, and the
 * app never writes `gdpr.consent`.
 */
object UsPrivacy {
    /** The full GPP string UMP stores in the default shared preferences. */
    const val GPP_STRING = "IABGPP_HDR_GppString"

    /**
     * The opt-out fields of every US section the GPP library knows (usnat and each state). State sections have no
     * SharingOptOut (only California does) and California has no TargetedAdvertisingOptOut; usnat has all three.
     * [UsPrivacyTest] fails when a library update brings a US section that isn't listed.
     */
    internal val OPT_OUTS: Map<Int, List<FieldKey>> = mapOf(
        UsNat.ID to listOf(UsNatField.SALE_OPT_OUT, UsNatField.SHARING_OPT_OUT, UsNatField.TARGETED_ADVERTISING_OPT_OUT),
        UsCa.ID to listOf(UsCaField.SALE_OPT_OUT, UsCaField.SHARING_OPT_OUT),
        UsVa.ID to listOf(UsVaField.SALE_OPT_OUT, UsVaField.TARGETED_ADVERTISING_OPT_OUT),
        UsCo.ID to listOf(UsCoField.SALE_OPT_OUT, UsCoField.TARGETED_ADVERTISING_OPT_OUT),
        UsUt.ID to listOf(UsUtField.SALE_OPT_OUT, UsUtField.TARGETED_ADVERTISING_OPT_OUT),
        UsCt.ID to listOf(UsCtField.SALE_OPT_OUT, UsCtField.TARGETED_ADVERTISING_OPT_OUT),
        UsFl.ID to listOf(UsFlField.SALE_OPT_OUT, UsFlField.TARGETED_ADVERTISING_OPT_OUT),
        UsMt.ID to listOf(UsMtField.SALE_OPT_OUT, UsMtField.TARGETED_ADVERTISING_OPT_OUT),
        UsOr.ID to listOf(UsOrField.SALE_OPT_OUT, UsOrField.TARGETED_ADVERTISING_OPT_OUT),
        UsTx.ID to listOf(UsTxField.SALE_OPT_OUT, UsTxField.TARGETED_ADVERTISING_OPT_OUT),
        UsDe.ID to listOf(UsDeField.SALE_OPT_OUT, UsDeField.TARGETED_ADVERTISING_OPT_OUT),
        UsIa.ID to listOf(UsIaField.SALE_OPT_OUT, UsIaField.TARGETED_ADVERTISING_OPT_OUT),
        UsNe.ID to listOf(UsNeField.SALE_OPT_OUT, UsNeField.TARGETED_ADVERTISING_OPT_OUT),
        UsNh.ID to listOf(UsNhField.SALE_OPT_OUT, UsNhField.TARGETED_ADVERTISING_OPT_OUT),
        UsNj.ID to listOf(UsNjField.SALE_OPT_OUT, UsNjField.TARGETED_ADVERTISING_OPT_OUT),
        UsTn.ID to listOf(UsTnField.SALE_OPT_OUT, UsTnField.TARGETED_ADVERTISING_OPT_OUT),
        UsMn.ID to listOf(UsMnField.SALE_OPT_OUT, UsMnField.TARGETED_ADVERTISING_OPT_OUT),
        UsIn.ID to listOf(UsInField.SALE_OPT_OUT, UsInField.TARGETED_ADVERTISING_OPT_OUT),
        UsMd.ID to listOf(UsMdField.SALE_OPT_OUT, UsMdField.TARGETED_ADVERTISING_OPT_OUT),
        UsKy.ID to listOf(UsKyField.SALE_OPT_OUT, UsKyField.TARGETED_ADVERTISING_OPT_OUT),
        UsRi.ID to listOf(UsRiField.SALE_OPT_OUT, UsRiField.TARGETED_ADVERTISING_OPT_OUT),
    )

    /** GPP ids below this are not US sections (tcfeuv2 = 2, tcfcav1 = 5, uspv1 = 6). */
    private const val FIRST_US_ID = UsNat.ID

    private const val OPTED_OUT = 1

    /**
     * Unity's `privacy.consent`: false when any US section records a sale, sharing or targeted-advertising opt-out
     * (1 = opted out; 0 = not applicable and 2 = didn't opt out). True with no GPP string or no US section in it.
     * A string that doesn't decode, or a US section with no row in [OPT_OUTS], fails safe to false and is reported.
     */
    fun consent(gpp: String?, report: (Throwable) -> Unit): Boolean {
        if (gpp.isNullOrBlank()) return true
        return try {
            val model = GppModel(gpp)
            model.sectionIds.toList().filter { it >= FIRST_US_ID }.none { id ->
                val fields = OPT_OUTS[id] ?: throw IllegalStateException("GPP US section $id has no opt-out row")
                fields.any { (model.getFieldValue(id, it) as? Int) == OPTED_OUT }
            }
        } catch (e: Exception) {
            report(IllegalStateException("GPP string didn't decode for privacy.consent", e))
            false
        }
    }
}

/** Where `privacy.consent` goes: Unity's metadata in the app, a list in tests. */
fun interface UnityPrivacy {
    fun write(consent: Boolean)
}

/** Unity's own metadata store, read by the SDK at initialize and on each ad request. */
class UnityMetaDataPrivacy(context: Context) : UnityPrivacy {
    private val app = context.applicationContext

    // MetaData is deprecated in Unity 4.21, but it's still how Google's guide passes privacy.consent, and the adapter
    // (4.21.0.0) writes gdpr.consent through it too.
    @Suppress("DEPRECATION")
    override fun write(consent: Boolean) {
        MetaData(app).apply {
            set("privacy.consent", consent)
            commit()
        }
    }
}

/** Writes Unity's `privacy.consent` from the GPP string UMP stored ([gpp] reads it fresh each time). */
class UnityConsent(
    private val gpp: () -> String?,
    private val sink: UnityPrivacy,
    private val report: (Throwable) -> Unit,
) {
    fun update() = sink.write(UsPrivacy.consent(gpp(), report))
}
