package com.brewingbytes.gemmory.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.brewingbytes.gemmory.BuildConfig
import com.brewingbytes.gemmory.policy.ConsentModeV2
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * UMP consent (design doc Consent R3-7; design D3 navigation map). The form shows on the first launch only, where
 * UMP requires it (EEA), before any ad request. If it couldn't show then (offline), it shows before the first ad the
 * player asks for instead, never over a later launch (founder call for #7). Nothing here requests an ad: the gateway
 * asks [canRequestAds] first. Not child-directed (adults only).
 */
class ConsentManager(context: Context, reporter: NonFatalReporter) {
    private val app = context.applicationContext
    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(app)
    private val prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** UMP writes the IAB TCF and GPP strings to the default shared preferences. */
    private val iab: SharedPreferences by lazy { app.getSharedPreferences("${app.packageName}_preferences", Context.MODE_PRIVATE) }

    /** Unity's US-states privacy.consent (T34), rewritten on every [publish] and before the SDK initializes. */
    private val unity = UnityConsent(
        gpp = { runCatching { iab.getString(UsPrivacy.GPP_STRING, null) }.getOrNull() },
        sink = UnityMetaDataPrivacy(app),
        report = reporter::report,
    )

    private val _canRequestAds = MutableStateFlow(info.canRequestAds())
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    /** Settings shows "Privacy choices" only when this is true (design D3). */
    private val _privacyOptionsRequired = MutableStateFlow(privacyOptionsRequired())
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private val _launchSettled = MutableStateFlow(false)

    /**
     * This launch's consent update has ended, however it ended (the form answered, not required, offline, an error), so
     * the saved choice is current: a fresh install's `first_launch` waits for it (T46).
     */
    val launchSettled: StateFlow<Boolean> = _launchSettled.asStateFlow()

    private val _consentMode = MutableStateFlow(readConsentMode())

    /** Consent Mode v2 flags from the UMP result, kept for Firebase Analytics (#12). */
    val consentMode: StateFlow<ConsentModeV2> = _consentMode.asStateFlow()

    private fun parameters(): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false)
        if (BuildConfig.DEBUG && (BuildConfig.UMP_DEBUG_EEA || BuildConfig.UMP_DEBUG_US_STATE || BuildConfig.UMP_TEST_DEVICE.isNotEmpty())) {
            val debug = ConsentDebugSettings.Builder(app)
            if (BuildConfig.UMP_DEBUG_EEA) {
                debug.setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA).setForceTesting(true)
            } else if (BuildConfig.UMP_DEBUG_US_STATE) {
                debug.setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE).setForceTesting(true)
            }
            if (BuildConfig.UMP_TEST_DEVICE.isNotEmpty()) debug.addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE)
            builder.setConsentDebugSettings(debug.build())
        }
        return builder.build()
    }

    /**
     * Every launch refreshes the consent status (it keeps [canRequestAds] and the privacy options current); only the
     * first launch also shows the form when it's required.
     */
    fun onLaunch(activity: Activity) {
        val first = !prefs.getBoolean(KEY_FIRST_LAUNCH_DONE, false)
        prefs.edit { putBoolean(KEY_FIRST_LAUNCH_DONE, true) }
        info.requestConsentInfoUpdate(
            activity,
            parameters(),
            {
                if (first) {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                        if (error != null) Log.w(TAG, "consent form: ${error.errorCode} ${error.message}")
                        publish()
                    }
                } else {
                    publish()
                }
            },
            { error ->
                Log.w(TAG, "consent info: ${error.errorCode} ${error.message}")
                publish()
            },
        )
    }

    /**
     * Before the player's first ad: shows the form when consent is still needed (it couldn't show on the first launch).
     * True when ads may be requested. A declined consent still allows non-personalized ads where UMP permits them.
     */
    suspend fun ensureBeforeAd(activity: Activity): Boolean {
        if (info.canRequestAds()) return true
        val updated = suspendCancellableCoroutine { cont ->
            info.requestConsentInfoUpdate(activity, parameters(), { cont.resume(true) }, { error ->
                Log.w(TAG, "consent info: ${error.errorCode} ${error.message}")
                cont.resume(false)
            })
        }
        if (updated && !activity.isFinishing) {
            suspendCancellableCoroutine { cont ->
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "consent form: ${error.errorCode} ${error.message}")
                    cont.resume(Unit)
                }
            }
        }
        publish()
        return info.canRequestAds()
    }

    /** Settings "Privacy choices". */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "privacy options: ${error.errorCode} ${error.message}")
            publish()
        }
    }

    /** Writes Unity's privacy.consent from the GPP string UMP stored last. Main thread. */
    fun updateUnity() = unity.update()

    /**
     * After every consent update (each launch, the first-ad form, the privacy options form): the flows, and Unity's
     * privacy.consent, so a withdrawal in the privacy options reaches Unity at once.
     */
    private fun publish() {
        updateUnity()
        _canRequestAds.value = info.canRequestAds()
        _privacyOptionsRequired.value = privacyOptionsRequired()
        _consentMode.value = readConsentMode()
        _launchSettled.value = true
        Log.i(TAG, "consent: canRequestAds=${_canRequestAds.value} privacyOptions=${_privacyOptionsRequired.value} mode=${_consentMode.value}")
    }

    private fun privacyOptionsRequired(): Boolean =
        info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Consent Mode from the TCF strings UMP stored. Outside the regulated regions all is granted. */
    private fun readConsentMode(): ConsentModeV2 {
        if (info.consentStatus == ConsentInformation.ConsentStatus.NOT_REQUIRED) return ConsentModeV2.GRANTED
        val tcf = iab
        val applies = when (runCatching { tcf.getInt("IABTCF_gdprApplies", -1) }.getOrDefault(-1)) {
            1 -> true
            0 -> false
            else -> null
        }
        return ConsentModeV2.fromTcf(applies, runCatching { tcf.getString("IABTCF_PurposeConsents", null) }.getOrNull())
    }

    private companion object {
        const val TAG = "GemmoryAds"
        const val FILE = "consent"
        const val KEY_FIRST_LAUNCH_DONE = "firstLaunchDone"
    }
}
