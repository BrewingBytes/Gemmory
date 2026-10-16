# Lessons

Gotchas learned while working on Gemmory, newest last. Add a dated line when a change teaches something the next session would otherwise relearn ([workflow.md](workflow.md) → Lessons). When a lesson becomes a rule, move it into workflow.md and delete it here.

## Emulator and connected tests

- 2026-10-15 (#5): a full `connectedDebugAndroidTest` timed out at 8 minutes when the host was out of RAM (16.7 of 18 GB swap), and one run ignored SIGTERM and needed `kill -9`. Running one class at a time (`timeout -k 15 360` each) on a cold-booted emulator with `-memory 4096 -no-snapshot` passed everything.
- 2026-10-15 (#5): `SheetAccessibilityChecksTest` is a second class inside `AccessibilityChecksTest.kt`, so a loop over test files misses it. List classes, not files.
- 2026-10-16 (#31): `adb shell` reads stdin. In a `while read … done < classes.txt` loop it swallows the rest of the list and only the first class runs. Give every adb call `< /dev/null`.
- 2026-10-16 (#31): at emulator load ~15-37, tests that wait for the gallery's starter tile (10 s) or for a canvas to open (5 s) time out, an instrumentation process can crash, and a cold launch can raise an ANR. CanvasAccessibilityTest, BillingFlowTest and HighlightStyleTest all passed once the load was below 3. Recheck on a quiet emulator before calling a timeout a regression.

## Bundled pictures

- 2026-10-16 (#31): a grid's CRC32 is the last 4 bytes of its `.gmrg` file, big-endian (`tail -c 4 <file> | xxd -p`). `GridFile.crc32` returns the same value.
- 2026-10-16 (#31): scripted sourcing works from Wikimedia Commons (license in the API's `extmetadata.LicenseShortName`); Pexels, Pixabay and Unsplash search block scripts. Commons returns HTTP 429 when searches come back to back, so leave a few seconds between requests and honor `Retry-After`.
- 2026-10-16 (#31): free ChatGPT image generation makes a few images a day, so ask for a sheet of tiles. 1536×1024 in 4×2 gives 3:4 tiles, 1024×1536 in 2×4 gives 4:3 tiles, 1536×1024 in 3×2 gives squares. Find the white gutters with per-column and per-row brightness, cut with a 4 px inset, then freeze each tile.
- 2026-10-16 (#31): thumbnail cost grows with the catalog. On gemmory_pixel, rendering all 50 gallery thumbnails took 183 ms (33 ms for the earlier 7), measured by `GalleryLoadTimingTest`.
- 2026-10-16 (#31): an AI tile can carry a protected product shape without any logo (a Vespa-like scooter was dropped). Check AI tiles for recognizable designs, not just for text and brands.
- 2026-10-16 (#31): `open(p, "w").write(open(p).read() + …)` truncates the file before reading it and wiped PROVENANCE.md once. Read first, then write. `BundledPicturesTest.everyPictureHasAProvenanceRow` now catches a lost row.
