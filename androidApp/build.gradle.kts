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
        versionCode = 11
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Frame-timing runs (@PerfTest) only on request: am instrument -e annotation com.brewingbytes.gemmory.PerfTest
        // (the runner takes one annotation here; @DeviceCheck tests skip themselves unless -e deviceChecks true).
        testInstrumentationRunnerArguments["notAnnotation"] = "com.brewingbytes.gemmory.PerfTest"

        // Google's sample AdMob app ID: only test ads until production IDs are set up (#7 scope).
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        // UMP debug settings for device checks: `-Pgemmory.umpDebugEea=true` forces the EEA geography,
        // `-Pgemmory.umpTestDevice=<hashed id from logcat>` marks the device as a test device. Debug builds only.
        buildConfigField("boolean", "UMP_DEBUG_EEA", "false")
        buildConfigField("String", "UMP_TEST_DEVICE", "\"\"")
        buildConfigField("boolean", "FAKE_BILLING", "false")
        // Crashlytics and Analytics send from release builds only (#12). The manifest reads the same switch.
        buildConfigField("boolean", "FIREBASE", "true")
        manifestPlaceholders["firebaseEnabled"] = "true"
        // Remote pictures (#43): the catalog folder (pictures/v1/ on main), checked once a day. Debug builds can point it
        // elsewhere with `-Pgemmory.remotePicturesUrl=…` (a local test server, say).
        buildConfigField("String", "REMOTE_PICTURES_URL", "\"$REMOTE_PICTURES_URL\"")
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
        }
        release {
            // R8: the mapping file goes into the App Bundle, so Play Console shows readable stack traces for crashes
            // and ANRs. No native debug symbols to add: the only .so files (ML Kit face detection, androidx graphics)
            // ship already stripped, so Play's "no native debug symbols" note stays.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (uploadStoreFile != null) signingConfig = signingConfigs.getByName("upload")
        }
    }

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
    // Rewarded + interstitial ads and the UMP consent form (#7). Test ad units only for now (ads/AdUnits.kt).
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    // Play Billing (#8): VIP subscriptions and remove_ads. Its manifest adds com.android.vending.BILLING.
    implementation(libs.play.billing.ktx)
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
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Accessibility Test Framework checks (the Accessibility Scanner's engine) in AccessibilityChecksTest (#14).
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// The Crashlytics plugin makes the mapping upload task but doesn't hang it on the App Bundle: every release AAB
// uploads its R8 mapping, so the Crashlytics Console shows readable stack traces (#12). Needs network.
tasks.matching { it.name == "bundleRelease" }.configureEach { finalizedBy("uploadCrashlyticsMappingFileRelease") }
