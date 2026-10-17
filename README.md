# Gemmory: Photo Diamond Art

An Android diamond-painting game. You turn your own photo into a gem canvas you can finish, then share a 9:16 sparkle time-lapse of it. The game also ships 49 bundled pictures, from a 5-minute starter (The Starry Night) through Quick and Medium up to ~15k-gem Big pictures, and more arrive without an app update (remote pictures, checked once a day). It's free with ads, and a VIP subscription removes them.

It's for adults only. User photos never leave the phone: there is no upload, no backend for user content, and photo data is excluded from cloud backup and device transfer.

## Layout

| Module | What's in it |
|---|---|
| `shared` | Kotlin Multiplatform, pure `commonMain` (no `android.*` or `java.awt`): the photo-to-grid converter, game rules, the save and grid codecs, the ad policy and the daily ledger. Build-time tools live in `jvmMain` |
| `androidApp` | Jetpack Compose app: gallery, photo flow (ML Kit face check), play canvas, time-lapse encoder, AdMob + UMP, Play Billing, Firebase Crashlytics/Analytics |
| `tools/bundled` | Source images, review PNGs and `PROVENANCE.md` for every bundled picture |
| `pictures/v1` | The remote pictures' signed catalog and grids, which the app downloads from GitHub (created by the first publish) |
| `tools/remote` | Source images, review PNGs and `PROVENANCE.md` for every remote picture |
| `docs` | Design doc, design system, issue snapshot, Play compliance, privacy policy, deferred work |

## Docs

| Doc | What's in it |
|---|---|
| [docs/designs/gemmory.md](docs/designs/gemmory.md) | Product design doc: problem, scope, tiers, ad rules, sizes, success criteria, timeline, decision ledgers |
| [docs/design-system.md](docs/design-system.md) | Design system "The Sorting Tray": tokens, components, navigation, canvas gestures, copy, accessibility |
| [docs/play-compliance.md](docs/play-compliance.md) | Play Console answers, Firebase and API key settings, Test Lab CI credentials, the production gate |
| [docs/privacy-policy.md](docs/privacy-policy.md) | The published privacy policy |
| [tools/bundled/PROVENANCE.md](tools/bundled/PROVENANCE.md) | Source, license and freeze settings of every bundled picture |
| [tools/remote/PROVENANCE.md](tools/remote/PROVENANCE.md) | The same for every remote picture, with when it was published |

## Build and test

You need JDK 17 and the Android SDK (compile/target SDK 37, min SDK 29).

```sh
./gradlew :shared:allTests                 # converter goldens, game rules, codecs (JVM + Android host)
./gradlew :androidApp:testDebugUnitTest    # app unit tests (Robolectric)
./gradlew :androidApp:assembleDebug :androidApp:lintDebug
./gradlew :androidApp:connectedDebugAndroidTest   # emulator; wrap in `timeout`, a full run takes 3-4 min
```

CI runs `:shared:checkCommonMainPurity :shared:allTests :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug` on every PR. The check is named `build` and `main` requires it.

Debug build switches (`-P…`):

- `gemmory.fakeBilling` (default `true`) uses a fake Play Billing backend.
- `gemmory.firebaseInDebug=true` sends Crashlytics/Analytics from debug builds. By default they only log to Logcat.
- `gemmory.umpDebugEea=true` and `gemmory.umpTestDevice=…` show the EEA consent form.
- `android.testInstrumentationRunnerArguments.deviceChecks=true` runs the `@DeviceCheck` walkthroughs. `@PerfTest` classes (canvas frame timing, gallery load timing) run only on request via `am instrument -e class …`.

## Encoder smoke on Firebase Test Lab

`.github/workflows/testlab.yml` (#11, design doc D7) runs `TimelapseEncoderSmokeTest` and `EncodeCancelTest` on three real phones in Firebase Test Lab, because video encoders differ by chip family. It runs on all three phones when a `v*` tag is pushed (a release candidate, see Release), and by hand: Actions → Test Lab → Run workflow, where "phones" can pick one phone to re-check a single failure with the day's leftover quota.

- **Phones** (one matrix, picked from `gcloud firebase test android models list` on 2026-10-16; chipsets are the makers' public specs, the catalog doesn't list them):

  | Family | Model (`model,version`) | Why |
  |---|---|---|
  | Pixel / Tensor | Pixel 9 (`tokay,35`) | Tensor G4, which uses Samsung's Exynos encoder under Google's build. Newest Pixel with high Test Lab capacity at API 35 (API 36 is medium), so runs don't queue. |
  | Samsung | Galaxy A52s 5G (`a52sxq,34`) | Snapdragon 778G: Qualcomm's encoder, the most common on Android. The Pixel already covers Samsung's Exynos encoder (Tensor G4 logs `c2.exynos.h264.encoder`), so an Exynos Galaxy (the A35 5G of the first run) added nothing. Mid-range with medium Test Lab capacity; the newer A36 5G has only low capacity. |
  | Low-end MediaTek | Galaxy A06 (`a06,35`) | Helio G85, 720×1600 screen, Samsung's cheapest current phone. The other low-end catalog phones are Unisoc (moto g04/g14, TECNO, realme C53) or Snapdragon (A05s). |

- **What passes:** each phone encodes through the app's fallback chain to an MP4 with 1 track, 14-16 s, 450 frames, at 1080×1920 or the 720×1280 fallback, and frames decode at that size; 720×1280 is also forced on its own; cancel and delete mid-encode leave no file. A fallback is logged (`smoke fallback:` in the logcat).
- **Results:** the run's summary has the Test Lab console link and a pass/fail row per phone. The `test-lab-results` artifact has each phone's `video.mp4` and `logcat`. A failed or inconclusive phone fails the job.
- **Quota:** the project is on the Spark plan: 5 physical-device runs a day at no cost, then Test Lab refuses runs (no bill). Each phone is one run, so one matrix uses 3 and there are no automatic retries. A second run the same day is refused whole (`TEST_QUOTA_EXCEEDED`, the run summary says so); run it again the next day.
- **Catalog:** Run workflow with "Only print the physical-device catalog" ticked lists the phones without using quota; use it when a model above leaves the catalog.
- **Credentials:** Workload Identity Federation, no key file. Setup: [docs/play-compliance.md](docs/play-compliance.md) → "Test Lab CI credentials".
- **Test Lab shuts down on 2027-09-30** (Firebase deprecation notice); the move to Google Cloud's Developer Device Platform is #55.

## Release

`./gradlew :androidApp:bundleRelease` builds an R8-shrunk AAB. It's signed with the upload key when `local.properties` (git-ignored) sets `gemmory.upload.storeFile`, `gemmory.upload.storePassword`, `gemmory.upload.keyAlias` and `gemmory.upload.keyPassword`. The keystore lives outside the repo, and keystores, passwords and service-account files are never committed. The Play Console answers and the production gate are in [docs/play-compliance.md](docs/play-compliance.md).

A release candidate is the `main` commit with the version bump: tag it `v<versionName>` (`git tag v0.3.2 && git push origin v0.3.2`). The tag starts the Test Lab encoder smoke; upload the AAB to Play only after it passes.

## Adding a bundled picture

Grids are frozen once and never re-converted. Re-freezing a shipped picture changes its CRC and orphans saves made on it.

1. Put the source in `tools/bundled/<id>.jpg`. It must be CC0, public domain, or an image-model output with commercial rights (CEO review CONTENT-1). Nothing above PEGI 3 / Everyone, and no identifiable people or brands.
2. Freeze it (`spec` is `quick|medium|big-1:1|3:4|4:3`; `focus` places the crop, 0 = left/top, 1 = right/bottom):
   ```sh
   ./gradlew :shared:freezeBundledGrid -Psource=tools/bundled/<id>.jpg -Pspec=medium-4:3 -Pfocus=0.5 \
     -Pout=androidApp/src/main/assets/pictures/<id>.gmrg -Preview=tools/bundled/<id>.review.png
   ```
3. Add it to its size group (`QUICK`, `MEDIUM` or `BIG`) in `androidApp/src/main/kotlin/com/brewingbytes/gemmory/bundled/BundledPictures.kt`, with the CRC pinned. The CRC is the grid file's last 4 bytes: `tail -c 4 <file> | xxd -p`.
4. Add a row to `tools/bundled/PROVENANCE.md` (source link, author and date, license or terms URL, spec and focus). `BundledPicturesTest` fails without it.

The gallery shows the starter first, then every Quick picture, then Medium, then Big (`BundledPicturesTest` checks the order).

## Publishing remote pictures

Remote pictures (#43) reach players within a day, with no Play release. The app checks `pictures/v1/` on `main` (`https://raw.githubusercontent.com/BrewingBytes/Gemmory/main/pictures/v1/`) once a day from the gallery, and it refuses anything not signed with the key below. Rules are in the design doc (Locked pictures → Remote pictures).

**The signing key** is an Ed25519 key at `~/.gemmory/remote-pictures-ed25519.pem`, with its path in `local.properties` as `remotePictures.signingKey` (or pass `-PsigningKey=`). It never goes in a repo. Keep a copy in a password manager: without it, no catalog can be signed until a release ships a new public key in `RemoteKeys.PRODUCTION`. To rotate it, add the new public key to that list, release, then sign with the new key.

To publish a picture:

1. Put the source in `tools/remote/<id>.jpg`, under the same rules as a bundled picture (CC0, public domain, or an image-model output with commercial rights). The id is new and never reused, and it isn't a bundled picture's id.
2. Run the publishing task. It prints the content check and stops until you pass `-PcontentChecked=yes`: PEGI 3 / Everyone, made for adults, no identifiable people, brands or protected designs, and the license recorded.
   ```sh
   ./gradlew :shared:publishRemotePicture -Pid=<id> -Ptitle="Fox in Snow" -Psource=tools/remote/<id>.jpg \
     -Pspec=big-3:4 -Pfocus=0.5 -Plocked=true \
     -Pprovenance="<source link> | <author, date> | <license or terms URL>" -PcontentChecked=yes
   ```
   The task freezes the grid like `freezeBundledGrid` and writes `pictures/v1/grids/<id>.gmrg`. It adds the catalog entry (CRC, SHA-256, size, colors, `addedAt`, and `minAppVersion`, which defaults to the app's current versionCode; pass `-PminAppVersion=` for an older one). Then it signs `catalog.json` and checks the result with the app's own verifier and the app's public key before writing anything. Last, it writes `tools/remote/<id>.review.png` and appends the provenance row.
3. Look at the review PNG, then commit `pictures/v1/` and `tools/remote/`, and open a PR. Phones pick the picture up within a day of the merge.

To withdraw a picture, run `./gradlew :shared:withdrawRemotePicture -Pid=<id>`, mark its provenance row withdrawn, and open a PR. The picture leaves every phone that hasn't started it. Never edit `catalog.json` by hand (the signature would no longer match and every phone would refuse it), and never re-freeze a published grid. A fix is a new id.

Debug builds can read another catalog folder with `-Pgemmory.remotePicturesUrl=<url ending in />`.
