# Lessons

Gotchas learned while working on Gemmory, newest last. Add a dated line when a change teaches something the next session would otherwise relearn ([workflow.md](workflow.md) → Lessons). When a lesson becomes a rule, move it into workflow.md and delete it here.

## Emulator and connected tests

- 2026-10-15 (#5): a full `connectedDebugAndroidTest` timed out at 8 minutes when the host was out of RAM (16.7 of 18 GB swap), and one run ignored SIGTERM and needed `kill -9`. Running one class at a time (`timeout -k 15 360` each) on a cold-booted emulator with `-memory 4096 -no-snapshot` passed everything.
- 2026-10-15 (#5): `SheetAccessibilityChecksTest` is a second class inside `AccessibilityChecksTest.kt`, so a loop over test files misses it. List classes, not files.
- 2026-10-16 (#31): `adb shell` reads stdin. In a `while read … done < classes.txt` loop it swallows the rest of the list and only the first class runs. Give every adb call `< /dev/null`.
- 2026-10-16 (#31): at emulator load ~15-37, tests that wait for the gallery's starter tile (10 s) or for a canvas to open (5 s) time out, an instrumentation process can crash, and a cold launch can raise an ANR. CanvasAccessibilityTest, BillingFlowTest and HighlightStyleTest all passed once the load was below 3. Recheck on a quiet emulator before calling a timeout a regression.
- 2026-10-16 (#32): the app reads the ledger (allowance, grace, ad caps) into memory at startup, so deleting `ledger/` files in a connected test's `@Before` doesn't reset it. Call `GemmoryApp.resetMonetizationForTest(fakeAds)` after clearing them, and `resetMonetizationForTest()` in `@After` to put AdMob back. A fake `AdGateway` also skips the UMP form (`ensureConsent`).
- 2026-10-16 (#50): the `emulator` on this host's PATH is a shim that resolves `../emulator` against the current directory and fails ("Qt library not found"). Boot with `$ANDROID_HOME/emulator/emulator` (`~/Library/Android/sdk/emulator/emulator`).
- 2026-10-16 (#50): after repeated `wm size` / `wm density` / `font_scale` changes, the emulator's launcher can raise an ANR dialog that keeps window focus, and every launch wait then times out (the app's starter tile never shows). `adb shell dumpsys window | grep mCurrentFocus` shows "Application Not Responding: …nexuslauncher". Cold-reboot the emulator; retrying doesn't help.
- 2026-10-16 (#50): a walkthrough or connected test that leaves a fresh completion on real AdMob can get a test interstitial (`AdActivity`) over the app about 4 s after Done, depending on the ad caps left by earlier runs ("No compose hierarchies found"). Use a fake `AdGateway` through `GemmoryApp.resetMonetizationForTest(fake)`.
- 2026-10-16 (#40): for a by-hand check on the emulator, `adb shell monkey -p … -c android.intent.category.LAUNCHER 1` printed its args and launched nothing; `adb shell am start -W -n com.brewingbytes.gemmory/.MainActivity` works. `uiautomator dump` right after a launch or a screen change can return the previous screen ("null root node returned"); wait 2-3 s and dump again before reading it.
- 2026-10-16 (#40): after a full per-class connected run, CanvasGesturesTest kept timing out in `open()` (5 s for the photo tile) even run alone at load ~3; logcat showed GC blocked ~2 s and class verification at ~60 bytecodes/s (host swap 14/15 GB). A cold reboot of the emulator made the class pass 12/12. When the same launch timeouts repeat after a long run, cold-reboot before re-running.

## CI

- 2026-10-16 (#11): Firebase Test Lab is deprecated and shuts down on 2027-09-30 (#55). Its pricing page gives Spark 5 physical-device runs a day but doesn't say how a multi-device matrix counts; until a real run shows otherwise, count each phone in the matrix as one run.
- 2026-10-16 (#11): `gcloud` isn't installed on this Mac. The Test Lab catalog comes from the workflow itself: Actions → Test Lab → Run workflow with "Only print the physical-device catalog" (no quota used).
- 2026-10-16 (#11): Gradle's `testInstrumentationRunnerArguments` (the `@PerfTest` exclusion) don't reach Test Lab, which runs the APKs directly. Pick classes with `--test-targets`, never a whole-package run.

## Bundled pictures

- 2026-10-16 (#31): a grid's CRC32 is the last 4 bytes of its `.gmrg` file, big-endian (`tail -c 4 <file> | xxd -p`). `GridFile.crc32` returns the same value.
- 2026-10-16 (#31): scripted sourcing works from Wikimedia Commons (license in the API's `extmetadata.LicenseShortName`); Pexels, Pixabay and Unsplash search block scripts. Commons returns HTTP 429 when searches come back to back, so leave a few seconds between requests and honor `Retry-After`.
- 2026-10-16 (#31): free ChatGPT image generation makes a few images a day, so ask for a sheet of tiles. 1536×1024 in 4×2 gives 3:4 tiles, 1024×1536 in 2×4 gives 4:3 tiles, 1536×1024 in 3×2 gives squares. Find the white gutters with per-column and per-row brightness, cut with a 4 px inset, then freeze each tile.
- 2026-10-16 (#31): thumbnail cost grows with the catalog. On gemmory_pixel, rendering all 50 gallery thumbnails took 183 ms (33 ms for the earlier 7), measured by `GalleryLoadTimingTest`.
- 2026-10-16 (#31): an AI tile can carry a protected product shape without any logo (a Vespa-like scooter was dropped). Check AI tiles for recognizable designs, not just for text and brands.
- 2026-10-16 (#31): `open(p, "w").write(open(p).read() + …)` truncates the file before reading it and wiped PROVENANCE.md once. Read first, then write. `BundledPicturesTest.everyPictureHasAProvenanceRow` now catches a lost row.
- 2026-10-16 (#32): what's free is `BundledPictures.FREE`, and everything else is locked. A connected test or walkthrough that opens a picture outside that set must unlock it first (`app.monetization.unlocks.unlock(id)`) or run as VIP, or the tap opens the rewarded sheet instead of the canvas.

## App code

- 2026-10-16 (#50): the gallery's tiles are what it last read: `GalleryViewModel.refresh()` re-reads and re-renders every canvas asynchronously each time the Gallery shows, so for a moment after Done a just-finished canvas still looks unfinished. Anything that acts on a tap reads the canvas itself (`CanvasStore.isFinished`), not `Tile.done`.
