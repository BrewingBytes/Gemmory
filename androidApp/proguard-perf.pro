# R8 rules for the perf build type only (#117), on top of the release rules. CanvasFrameTimingTest runs in the app's
# process, and its APK leaves out every library the app already has (Kotlin, coroutines, AndroidX, Compose), so the
# test reaches them, and the app classes it drives, inside the app's dex.

# No renaming: the test APK's calls into renamed Kotlin helpers missed (NoSuchMethodError), and names don't change
# frame time. Shrinking and optimizing stay on.
-dontobfuscate

# R8 would remove what the app itself doesn't call. Keep AndroidX whole but still optimized: the Compose code the app
# runs is inlined and optimized as in release; the cost is unused library code staying in the APK.
-keep,allowoptimization class androidx.** { *; }
# The test libraries subclass Kotlin and coroutines classes (TestScopeImpl overrides toString, which R8's optimizer made
# final in the app): keep those unoptimized. They aren't on the canvas's per-frame path.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# The app classes the test drives, with their companions (R8 makes companion methods static), unoptimized. The
# renderer and the canvas's Compose code aren't among them.
-keep class com.brewingbytes.gemmory.GemmoryApp { public *; }
-keep class com.brewingbytes.gemmory.MainActivity
-keep class com.brewingbytes.gemmory.settings.AppSettings { public *; }
-keep class com.brewingbytes.gemmory.play.HighlightStyle* { *; }
-keep class com.brewingbytes.gemmory.play.CanvasStore* { public *; }
-keep class com.brewingbytes.gemmory.play.PlayViewModel { public *; }
-keep class com.brewingbytes.gemmory.play.PlayUi* { public *; }
-keep class com.brewingbytes.gemmory.play.PlaySession { public *; }
-keep class com.brewingbytes.gemmory.play.CanvasViewport { public *; }
-keep class com.brewingbytes.gemmory.storage.** { public *; }
-keep class com.brewingbytes.gemmory.converter.Pattern* { public *; }
-keep class com.brewingbytes.gemmory.converter.PaletteColor* { public *; }
-keep class com.brewingbytes.gemmory.converter.Lab* { public *; }
