# R8 rules for the perf build's test APK (#117). AGP runs R8 on it with the app's mapping, so its calls into the app
# match the renamed classes. Nothing else is shrunk or optimized: the runner and JUnit reach tests by reflection.
-dontshrink
-dontoptimize

# Referenced by test libraries (Material shadows, the accessibility checks) but never loaded by the tests.
-dontwarn androidx.appcompat.graphics.drawable.DrawableWrapper
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.AutoValue$Builder
