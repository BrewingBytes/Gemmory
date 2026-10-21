import java.util.Properties

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
        // Local dates and time zones for the daily ledger and ad caps (midnight, DST). Pure Kotlin.
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
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
    // BundledGridToolTest pins the shipped starter grid's CRC.
    inputs.dir("../androidApp/src/main/assets/pictures").withPathSensitivity(PathSensitivity.RELATIVE)
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

/**
 * Freezes a bundled picture's grid (design D13, eng D11): converts the source once, at build time, and writes a
 * GridCodec file that the app ships and never re-converts. Only for new pictures: re-freezing a shipped one changes
 * its grid CRC and orphans saves made on it. Defaults freeze the starter picture.
 */
tasks.register<JavaExec>("freezeBundledGrid") {
    group = "build"
    description = "Converts a bundled picture's source image once and writes its frozen grid asset."
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    dependsOn(jvmMain.compileTaskProvider)
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("com.brewingbytes.gemmory.tools.BundledGridTool")
    val root = rootProject.layout.projectDirectory
    args(
        root.file(providers.gradleProperty("source").getOrElse("tools/bundled/starter-starry-night.jpg")).asFile.path,
        providers.gradleProperty("spec").getOrElse("starter-3:4"),
        providers.gradleProperty("focus").getOrElse("0.2"),
        root.file(providers.gradleProperty("out").getOrElse("androidApp/src/main/assets/pictures/starter.gmrg")).asFile.path,
        root.file(providers.gradleProperty("review").getOrElse("tools/bundled/starter.review.png")).asFile.path,
    )
}

/**
 * Remote pictures (#43): publish one into `pictures/v1/` (freeze, CRC + SHA-256, catalog entry, Ed25519 signature, the
 * app's own checks, provenance row), or withdraw one; `setRemoteFeature` flips a catalog switch (T39). The signing key path comes from `-PsigningKey=` or
 * `remotePictures.signingKey` in local.properties; the key itself is never in a repo. README → Publishing remote pictures.
 */
/** The tool's `key=value` arguments, read when the task is configured (tracked by the configuration cache). */
fun remoteToolArgs(mode: String): List<String> {
    val root = rootProject.layout.projectDirectory
    val local = Properties().apply {
        providers.fileContents(root.file("local.properties")).asText.orNull?.let { load(it.reader()) }
    }
    fun p(name: String) = providers.gradleProperty(name).orNull
    return listOf(
        "mode=$mode",
        "dir=${root.dir("pictures/v1").asFile.path}",
        "bundled=${root.dir("androidApp/src/main/assets/pictures").asFile.path}",
        "provenanceFile=${root.file("tools/remote/PROVENANCE.md").asFile.path}",
        "reviewDir=${root.dir("tools/remote").asFile.path}",
        "signingKey=${p("signingKey") ?: local.getProperty("remotePictures.signingKey").orEmpty()}",
        // Left out, the tool stamps the lowest app that reads the picture right (D14, T44).
        "minAppVersion=${p("minAppVersion").orEmpty()}",
        "id=${p("id").orEmpty()}",
        "title=${p("title").orEmpty()}",
        "feature=${p("feature").orEmpty()}",
        "on=${p("on").orEmpty()}",
        "source=${p("source")?.let { root.file(it).asFile.path }.orEmpty()}",
        "spec=${p("spec").orEmpty()}",
        "focus=${p("focus") ?: "0.5"}",
        "colors=${p("colors") ?: "standard"}",
        "availableFrom=${p("availableFrom").orEmpty()}",
        "locked=${p("locked") ?: "true"}",
        "provenance=${p("provenance").orEmpty()}",
        "contentChecked=${p("contentChecked").orEmpty()}",
    )
}

listOf("publishRemotePicture" to "publish", "withdrawRemotePicture" to "withdraw", "setRemoteFeature" to "feature").forEach { (taskName, mode) ->
    tasks.register<JavaExec>(taskName) {
        group = "build"
        description = when (mode) {
            "publish" -> "Freezes, signs and adds a remote picture to pictures/v1 (#43)."
            "withdraw" -> "Removes a remote picture from pictures/v1 and re-signs the catalog (#43)."
            else -> "Turns a catalog switch (-Pfeature=review) on or off (-Pon=true|false) and re-signs the catalog (T39)."
        }
        val jvmMain = kotlin.jvm().compilations.getByName("main")
        dependsOn(jvmMain.compileTaskProvider)
        classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
        mainClass.set("com.brewingbytes.gemmory.tools.RemotePictureTool")
        args(remoteToolArgs(mode))
    }
}

/**
 * The picture pipeline (T44). `splitPictureSheet` cuts one ChatGPT sheet into its six tiles to look at before the CSV is
 * filled; `publishPictureBatch` publishes a batch folder (sheets + batch.csv) in one signed catalog; `checkDropCalendar`
 * fails when fewer than 7 days of drops are scheduled (the daily CI job). README → Publishing a batch of pictures.
 */
tasks.register<JavaExec>("splitPictureSheet") {
    group = "build"
    description = "Splits a 2×3 picture sheet into six tile images (T44)."
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    dependsOn(jvmMain.compileTaskProvider)
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("com.brewingbytes.gemmory.tools.SheetSplitter")
    val root = rootProject.layout.projectDirectory
    args(
        providers.gradleProperty("sheet").orNull?.let { root.file(it).asFile.path }.orEmpty(),
        providers.gradleProperty("ratio").getOrElse("1:1"),
        root.dir(providers.gradleProperty("out").getOrElse("shared/build/sheet-tiles")).asFile.path,
    )
}

tasks.register<JavaExec>("publishPictureBatch") {
    group = "build"
    description = "Splits, freezes, checks, signs and publishes a batch of remote pictures to pictures/v1 (T44)."
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    dependsOn(jvmMain.compileTaskProvider)
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("com.brewingbytes.gemmory.tools.PictureBatch")
    val root = rootProject.layout.projectDirectory
    val batch = providers.gradleProperty("batch").orNull
    args(
        remoteToolArgs("batch") + listOf(
            "batch=${batch?.let { root.dir(it).asFile.path }.orEmpty()}",
            "previewDir=${layout.buildDirectory.dir("picture-batch/${batch?.trimEnd('/')?.substringAfterLast('/').orEmpty()}").get().asFile.path}",
        ),
    )
}

tasks.register<JavaExec>("checkDropCalendar") {
    group = "verification"
    description = "Fails when the published catalog has fewer than 7 days of drops ahead (T44)."
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    dependsOn(jvmMain.compileTaskProvider)
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("com.brewingbytes.gemmory.tools.DropCalendar")
    args(rootProject.layout.projectDirectory.dir("pictures/v1").asFile.path)
}
