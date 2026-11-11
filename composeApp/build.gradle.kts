import org.jetbrains.kotlin.gradle.plugin.mpp.Framework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

/**
 * The shared Compose UI (iOS plan, eng D1). Stage 0 (T59, #167) holds only the spike: the play canvas drawn in common
 * `DrawScope` code, run on the iOS simulator, the iPhone and (for the renderer golden bar) an Android device.
 * `:androidApp` doesn't depend on it until Stage 1a.
 */
kotlin {
    jvmToolchain(17)
    // #163: a new warning fails the build, so the list stays empty (workflow.md).
    compilerOptions { allWarningsAsErrors.set(true) }

    android {
        namespace = "com.brewingbytes.gemmory.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources { enable = true }
        // The renderer golden bar's Android side (R3-1): compares the spike with goldens/renderer/.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // The app's framework for iosApp/ (Xcode links it as GemmoryKit, statically).
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "GemmoryKit"
            isStatic = true
            // :shared isn't exported: Swift only sees what composeApp's API names (eng R3-11), and an export put
            // ReferrerParse.MAX_INPUT in the header, which the C macro of that name in limits.h breaks.
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
            implementation(libs.cmp.components.resources)
            implementation(libs.cmp.navigationevent.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidDeviceTest") {
            // One directory of goldens read by both platforms (Success Criteria, Storage).
            resources.srcDir(rootProject.layout.projectDirectory.dir("goldens"))
            // The golden bar's arithmetic (GoldenMath) is shared with the iOS tests; device tests don't see commonTest.
            kotlin.srcDir("src/commonTest/kotlin")
            dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
                implementation(kotlin("test-junit"))
            }
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.brewingbytes.gemmory.compose.res"
    generateResClass = always
}

/**
 * eng D1: `:composeApp` `commonMain` is platform-free. Fails on any `android.*`, `platform.*` (Apple) or `java.*` import
 * or fully qualified use; the platform code lives in `androidMain` and `iosMain`.
 */
abstract class CheckComposeCommonPurity : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun run() {
        val banned = Regex("""(^|[^\w.])(android|platform|java)\.\w""")
        val violations = sources.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                val code = line.substringBefore("//").trim()
                if (code.startsWith("*") || code.startsWith("/*")) return@mapIndexedNotNull null
                if (banned.containsMatchIn(code)) "${file.path}:${i + 1}: $line" else null
            }
        }
        report.get().asFile.writeText(violations.joinToString("\n"))
        if (violations.isNotEmpty()) {
            throw GradleException(
                "composeApp/src/commonMain must stay platform-free (no android.* / platform.* / java.*):\n" +
                    violations.joinToString("\n"),
            )
        }
    }
}

val checkCommonMainPurity = tasks.register<CheckComposeCommonPurity>("checkCommonMainPurity") {
    group = "verification"
    description = "Fails on android.* / platform.* / java.* imports under composeApp/src/commonMain."
    sources.from(fileTree("src/commonMain") { include("**/*.kt") })
    report.set(layout.buildDirectory.file("reports/commonMainPurity.txt"))
}

tasks.named("check") { dependsOn(checkCommonMainPurity) }
tasks.matching { it.name == "allTests" }.configureEach { dependsOn(checkCommonMainPurity) }

// The iOS golden check (T59) reads goldens/renderer/ and the fonts from the repo: a simulator process can read host
// paths, and simctl passes SIMCTL_CHILD_* variables to it.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    val root = rootProject.layout.projectDirectory
    environment("SIMCTL_CHILD_GEMMORY_ROOT", root.asFile.path)
    inputs.dir(root.dir("goldens")).withPathSensitivity(PathSensitivity.RELATIVE)
    // T60: CI pins the simulator (device and runtime) by passing its UDID; locally Kotlin picks one.
    providers.gradleProperty("gemmory.iosSimulator").orNull?.let { device.set(it) }
}
