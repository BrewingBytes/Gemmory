package com.brewingbytes.gemmory.telemetry

import android.os.Bundle
import com.brewingbytes.gemmory.policy.ConsentModeV2
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType

/**
 * Firebase Analytics (design doc → Analytics, R3-7). The manifest starts it with collection off and every consent
 * type denied; [consent] turns collection on only while analytics storage is granted, so the metrics cover
 * consented players only. No user id is ever set.
 */
class FirebaseAnalyticsLogger(private val analytics: FirebaseAnalytics) : Analytics {
    override fun log(event: AnalyticsEvent) {
        analytics.logEvent(event.name, Bundle().apply { event.params.forEach { (k, v) -> putString(k, v) } })
    }

    override fun consent(mode: ConsentModeV2) {
        val flags = Analytics.consentFlags(mode)
        analytics.setConsent(
            mapOf(
                ConsentType.ANALYTICS_STORAGE to status(flags.getValue("analytics_storage")),
                ConsentType.AD_STORAGE to status(flags.getValue("ad_storage")),
                ConsentType.AD_USER_DATA to status(flags.getValue("ad_user_data")),
                ConsentType.AD_PERSONALIZATION to status(flags.getValue("ad_personalization")),
            ),
        )
        analytics.setAnalyticsCollectionEnabled(mode.analyticsStorage)
    }

    private fun status(granted: Boolean) = if (granted) ConsentStatus.GRANTED else ConsentStatus.DENIED
}
