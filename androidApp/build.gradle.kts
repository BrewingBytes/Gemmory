import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Firebase (#12): google-services.json (committed; it names the project, it isn't a secret) and Crashlytics,
    // which also uploads the release R8 mapping file so Console stack traces are readable.
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// Release signing reads the upload key from local.properties (git-ignored); the keystore lives outside the repo.
// Keys: gemmory.upload.storeFile, gemmory.upload.storePassword, gemmory.upload.keyAlias, gemmory.upload.keyPassword.
// Without them the release build is unsigned and Play rejects it.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val uploadStoreFile: String? = localProps.getProperty("gemmory.upload.storeFile")

/** Remote pictures (#43): `pictures/v1/` in this (public) repo, served by GitHub. README → Publishing remote pictures. */
val REMOTE_PICTURES_URL = "https://raw.githubusercontent.com/BrewingBytes/Gemmory/main/pictures/v1/"

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.brewingbytes.gemmory"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.brewingbytes.gemmory"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 18
        versionName = "0.8.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Frame-timing runs (@PerfTest) only on request: am instrument -e annotation com.brewingbytes.gemmory.PerfTest
        // (the runner takes one annotation here; @DeviceCheck tests skip themselves unless -e deviceChecks true).
        testInstrumentationRunnerArguments["notAnnotation"] = "com.brewingbytes.gemmory.PerfTest"

        // Google's sample AdMob IDs: debug builds and connected tests only ever get test ads. Release builds take the
        // production IDs from gradle.properties (T34), checked by :shared:validateReleaseAdIds.
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        buildConfigField("String", "ADMOB_REWARDED", "\"ca-app-pub-3940256099942544/5224354917\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL", "\"ca-app-pub-3940256099942544/1033173712\"")
        // UMP debug settings for device checks: `-Pgemmory.umpDebugEea=true` forces the EEA geography,
        // `-Pgemmory.umpDebugUsState=true` a regulated US state (T34: the US-states form, GPP strings for UsPrivacyTest),
        // `-Pgemmory.umpTestDevice=<hashed id from logcat>` marks the device as a test device. Debug builds only.
        buildConfigField("boolean", "UMP_DEBUG_EEA", "false")
        buildConfigField("boolean", "UMP_DEBUG_US_STATE", "false")
        buildConfigField("String", "UMP_TEST_DEVICE", "\"\"")
        buildConfigField("boolean", "FAKE_BILLING", "false")
        // Crashlytics and Analytics send from release builds only (#12). The manifest reads the same switch.
        buildConfigField("boolean", "FIREBASE", "true")
        manifestPlaceholders["firebaseEnabled"] = "true"
        // Remote pictures (#43): the catalog folder (pictures/v1/ on main), checked once a day. Debug builds can point it
        // elsewhere with `-Pgemmory.remotePicturesUrl=…` (a local test server, say).
        buildConfigField("String", "REMOTE_PICTURES_URL", "\"$REMOTE_PICTURES_URL\"")
        // Play Games achievements (#49): on in release builds once games-ids.xml has the project id.
        buildConfigField("boolean", "PLAY_GAMES", "true")
        // The streak celebration (#101, O5): one switch turns it (and #102's nudge) off. Debug builds can try either
        // with `-Pgemmory.streakCelebration=false`.
        buildConfigField("boolean", "STREAK_CELEBRATION", "true")
        // The install referrer (T46): Play's, unless a debug build sets `-Pgemmory.fakeReferrer=<utm string>`
        // (`utm_source=share&utm_medium=bundled&utm_content=<id>`, say), which a fresh install reads instead.
        buildConfigField("String", "FAKE_REFERRER", "\"\"")
    }

    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = localProps.getProperty("gemmory.upload.storePassword")
                keyAlias = localProps.getProperty("gemmory.upload.keyAlias")
                keyPassword = localProps.getProperty("gemmory.upload.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "UMP_DEBUG_EEA", providers.gradleProperty("gemmory.umpDebugEea").getOrElse("false"))
            buildConfigField("boolean", "UMP_DEBUG_US_STATE", providers.gradleProperty("gemmory.umpDebugUsState").getOrElse("false"))
            buildConfigField("String", "UMP_TEST_DEVICE", "\"${providers.gradleProperty("gemmory.umpTestDevice").getOrElse("")}\"")
            // Debug builds use the fake billing backend (src/debug) unless `-Pgemmory.fakeBilling=false`, which the
            // license-tester run on a Play Store emulator needs (#8). Release builds always use Google Play.
            buildConfigField("boolean", "FAKE_BILLING", providers.gradleProperty("gemmory.fakeBilling").getOrElse("true"))
            // Debug builds keep crashes, non-fatals and events on the phone (Logcat) unless `-Pgemmory.firebaseInDebug=true`.
            val firebaseInDebug = providers.gradleProperty("gemmory.firebaseInDebug").getOrElse("false")
            buildConfigField("boolean", "FIREBASE", firebaseInDebug)
            manifestPlaceholders["firebaseEnabled"] = firebaseInDebug
            val remoteUrl = providers.gradleProperty("gemmory.remotePicturesUrl").getOrElse(REMOTE_PICTURES_URL)
            buildConfigField("String", "REMOTE_PICTURES_URL", "\"$remoteUrl\"")
            // Debug builds log achievements to Logcat and hide the Settings row unless `-Pgemmory.playGames=true`, which
            // also needs the debug key's SHA-1 on a Play Games OAuth client (README → Play Games setup). The debug manifest
            // removes the SDK's init provider, which would start Play Games regardless (#113).
            buildConfigField("boolean", "PLAY_GAMES", providers.gradleProperty("gemmory.playGames").getOrElse("false"))
            buildConfigField("boolean", "STREAK_CELEBRATION", providers.gradleProperty("gemmory.streakCelebration").getOrElse("true"))
            val fakeReferrer = providers.gradleProperty("gemmory.fakeReferrer").getOrElse("").replace("\\", "").replace("\"", "")
            buildConfigField("String", "FAKE_REFERRER", "\"$fakeReferrer\"")
        }
        release {
            // R8: the mapping file goes into the App Bundle, so Play Console shows readable stack traces for crashes
            // and ANRs. No native debug symbols to add: the only .so files (ML Kit face detection, androidx graphics)
            // ship already stripped, so Play's "no native debug symbols" note stays.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (uploadStoreFile != null) signingConfig = signingConfigs.getByName("upload")
            // Real ads (T34): the committed production IDs. The task below fails the build when any is missing or a test ID.
            manifestPlaceholders["admobAppId"] = providers.gradleProperty("gemmory.admob.appId").getOrElse("")
            buildConfigField("String", "ADMOB_REWARDED", "\"${providers.gradleProperty("gemmory.admob.rewardedUnit").getOrElse("")}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL", "\"${providers.gradleProperty("gemmory.admob.interstitialUnit").getOrElse("")}\"")
        }
        // Release-like build for the frame-timing check (#117): R8 and not debuggable, like what players run, but signed
        // with the debug key so Test Lab installs it beside its test APK. Nothing leaves the phone: Google's test ad IDs,
        // no Firebase, no Play Games, the fake billing backend. `-Pgemmory.firebaseInPerf=true` sends its events to
        // Firebase for the DebugView check of the launch funnel (T46), still with the test ad IDs.
        create("perf") {
            initWith(getByName("release"))
            proguardFile("proguard-perf.pro")
            testProguardFile("proguard-perf-test.pro")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_REWARDED", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL", "\"ca-app-pub-3940256099942544/1033173712\"")
            val firebaseInPerf = providers.gradleProperty("gemmory.firebaseInPerf").getOrElse("false")
            buildConfigField("boolean", "FIREBASE", firebaseInPerf)
            manifestPlaceholders["firebaseEnabled"] = firebaseInPerf
            buildConfigField("boolean", "PLAY_GAMES", "false")
            buildConfigField("boolean", "FAKE_BILLING", "true")
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> { mappingFileUploadEnabled = false }
        }
    }

    sourceSets {
        getByName("perf") {
            kotlin.srcDir("src/debug/kotlin")
        }
    }

    // Connected tests run against debug; `-Pgemmory.testBuildType=perf` runs them against the release-like build (#117).
    testBuildType = providers.gradleProperty("gemmory.testBuildType").getOrElse("debug")

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.process)
    // Rewarded + interstitial ads and the UMP consent form (#7). Real units in release builds (T34, BuildConfig.ADMOB_*).
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    // Unity Ads as a second bidder through AdMob mediation (T34, ad-mediation.md). No code races the two SDKs.
    implementation(libs.unity.ads)
    implementation(libs.unity.adapter)
    // Reads the US-states opt-out UMP stores (IABGPP_HDR_GppString) for Unity's privacy.consent (T34).
    implementation(libs.iabgpp.encoder)
    // Play Billing (#8): VIP subscriptions and remove_ads. Its manifest adds com.android.vending.BILLING.
    implementation(libs.play.billing.ktx)
    // Play Games Services v2 (#49): achievements only, automatic sign-in. No leaderboards or friends.
    implementation(libs.play.services.games.v2)
    // Play In-App Review (T39): Play's own rating sheet after a finished picture, once features.review is on.
    implementation(libs.play.review)
    // Play Install Referrer (T46): read once at a fresh install's first launch, on the phone, for T50's shared picture.
    implementation(libs.installreferrer)
    // The home-screen streak widget (#103): Glance over RemoteViews, so the launcher draws it with no app process.
    implementation(libs.glance.appwidget)
    // Bundled (on-device) model: the face check never sends the photo anywhere (design doc, photo privacy).
    implementation(libs.mlkit.face.detection)
    // Crash + non-fatal reports (always on, D17) and Analytics events under UMP consent (Consent Mode v2, R3-7).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.glance.appwidget.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Accessibility Test Framework checks (the Accessibility Scanner's engine) in AccessibilityChecksTest (#14).
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// The Crashlytics plugin makes the mapping upload task but doesn't hang it on the App Bundle: every release AAB
// uploads its R8 mapping, so the Crashlytics Console shows readable stack traces (#12). Needs network.
tasks.matching { it.name == "bundleRelease" }.configureEach { finalizedBy("uploadCrashlyticsMappingFileRelease") }

// Real ads (T34): a release can never ship with test ads or no ads. Hung on the release variant's first task, so
// bundleRelease and assembleRelease fail in seconds, before R8; debug builds and connected tests never run it.
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(":shared:validateReleaseAdIds") }
