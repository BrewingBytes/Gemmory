plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "com.brewingbytes.gemmory.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        // Runs commonTest on the Android host too, so ART-only issues in shared code show up in CI.
        withHostTest {}
    }

    // Host JVM target: runs commonTest fast on CI and hosts build-time tools (converter goldens, starter grid).
    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

/**
 * AGENTS.md: `shared/src/commonMain` is pure Kotlin. Fails on any `android.*` or `java.awt` import
 * or fully qualified use, so a violation breaks `check` and `allTests` (and therefore CI).
 */
abstract class CheckCommonMainPurity : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun run() {
        val banned = Regex("""(^|[^\w.])(android|java\.awt)\.\w""")
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
                "shared/src/commonMain must stay pure Kotlin (no android.* / java.awt):\n" +
                    violations.joinToString("\n"),
            )
        }
    }
}

val checkCommonMainPurity = tasks.register<CheckCommonMainPurity>("checkCommonMainPurity") {
    group = "verification"
    description = "Fails on android.* / java.awt imports under shared/src/commonMain."
    sources.from(fileTree("src/commonMain") { include("**/*.kt") })
    report.set(layout.buildDirectory.file("reports/commonMainPurity.txt"))
}

tasks.named("check") { dependsOn(checkCommonMainPurity) }
tasks.named("allTests") { dependsOn(checkCommonMainPurity) }

// Golden files are test inputs, so editing them must invalidate a cached jvmTest result.
tasks.named<Test>("jvmTest") {
    inputs.dir("src/jvmTest/fixtures").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/jvmTest/goldens").withPathSensitivity(PathSensitivity.RELATIVE)
}

/** Regenerates the 27 converter goldens (+ review PNGs). Only after an intended converter change (eng D19). */
tasks.register<Test>("updateConverterGoldens") {
    group = "verification"
    description = "Rewrites shared/src/jvmTest/goldens from the current converter output."
    val jvmTest = tasks.named<Test>("jvmTest")
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    filter { includeTestsMatching("*ConverterGoldenTest") }
    systemProperty("gemmory.updateGoldens", "true")
    outputs.upToDateWhen { false }
}
