plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

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
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Frame-timing runs (@PerfTest) and long device checks (@DeviceCheck) only on request:
        // am instrument -e annotation com.brewingbytes.gemmory.PerfTest (or .DeviceCheck)
        testInstrumentationRunnerArguments["notAnnotation"] = "com.brewingbytes.gemmory.PerfTest,com.brewingbytes.gemmory.DeviceCheck"

        // Google's sample AdMob app ID: only test ads until production IDs are set up (#7 scope).
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        // UMP debug settings for device checks: `-Pgemmory.umpDebugEea=true` forces the EEA geography,
        // `-Pgemmory.umpTestDevice=<hashed id from logcat>` marks the device as a test device. Debug builds only.
        buildConfigField("boolean", "UMP_DEBUG_EEA", "false")
        buildConfigField("String", "UMP_TEST_DEVICE", "\"\"")
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "UMP_DEBUG_EEA", providers.gradleProperty("gemmory.umpDebugEea").getOrElse("false"))
            buildConfigField("String", "UMP_TEST_DEVICE", "\"${providers.gradleProperty("gemmory.umpTestDevice").getOrElse("")}\"")
        }
        release {
            isMinifyEnabled = false
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
    // Bundled (on-device) model: the face check never sends the photo anywhere (design doc, photo privacy).
    implementation(libs.mlkit.face.detection)
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
