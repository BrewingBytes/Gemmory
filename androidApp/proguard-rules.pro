# R8 rules for the release build. Libraries (Compose, lifecycle, Play Billing, Play services ads, UMP, ML Kit) ship
# their own consumer rules; add a rule here only for something R8 removes that the app reaches by reflection.

# Readable stack traces in Play Console: keep file names and line numbers (the mapping file restores the names).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Found on the first R8 release run (0.2.2): created by reflection through a no-arg constructor that R8's full mode
# removed. WorkManager's Room database (Play services ads schedules work through it) crashed the app at launch;
# ML Kit's component registrars failed to load, so the face check would never run.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# Crashlytics titles an issue by the exception's class name, which the mapping file doesn't restore (#12: the first
# forced crash showed as "w00"). Keep the names, not the code, of the app's own exceptions.
-keepnames class com.brewingbytes.gemmory.** extends java.lang.Throwable
