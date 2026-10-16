# Gemmory: Photo Diamond Art

An Android diamond-painting game. You turn your own photo into a gem canvas you can finish, then share a 9:16 sparkle time-lapse of it. The game also ships 49 bundled pictures, from a 5-minute starter (The Starry Night) through Quick and Medium up to ~15k-gem Big pictures. It's free with ads, and a VIP subscription removes them.

It's for adults only. User photos never leave the phone: there is no upload, no backend for user content, and photo data is excluded from cloud backup and device transfer.

## Layout

| Module | What's in it |
|---|---|
| `shared` | Kotlin Multiplatform, pure `commonMain` (no `android.*` or `java.awt`): the photo-to-grid converter, game rules, the save and grid codecs, the ad policy and the daily ledger. Build-time tools live in `jvmMain` |
| `androidApp` | Jetpack Compose app: gallery, photo flow (ML Kit face check), play canvas, time-lapse encoder, AdMob + UMP, Play Billing, Firebase Crashlytics/Analytics |
| `tools/bundled` | Source images, review PNGs and `PROVENANCE.md` for every bundled picture |
| `docs` | Design doc, design system, issue snapshot, Play compliance, privacy policy, deferred work |

## Docs

| Doc | What's in it |
|---|---|
| [AGENTS.md](AGENTS.md) | The rules that always apply (adults only, photos stay on the phone, no ads on the canvas, pure `commonMain`, design system, time limits) and the entry point for agents |
| [docs/designs/gemmory.md](docs/designs/gemmory.md) | Product design doc: problem, scope, tiers, ad rules, sizes, success criteria, timeline, and the CEO and eng review decision ledgers |
| [docs/design-system.md](docs/design-system.md) | Design system "The Sorting Tray": tokens, fonts, components, navigation, canvas gestures and haptics, copy for every state, accessibility (`DESIGN.md` links here) |
| [docs/issues.md](docs/issues.md) | Snapshot of the tasks, created as GitHub issues (T*n* = #*n* for #1-#14) |
| [docs/play-compliance.md](docs/play-compliance.md) | Play Console answers (Data safety, content rating, target audience, ads), Firebase and API key settings, the production gate |
| [docs/privacy-policy.md](docs/privacy-policy.md) | The published privacy policy |
| [docs/todos.md](docs/todos.md) | Deferred work and when to pick it up (`TODOS.md` links here) |
| [docs/agents/skill-routing.md](docs/agents/skill-routing.md) | Which gstack skill to use for which kind of request |
| [tools/bundled/PROVENANCE.md](tools/bundled/PROVENANCE.md) | Source, license and freeze settings of every bundled picture |

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

## Release

`./gradlew :androidApp:bundleRelease` builds an R8-shrunk AAB. It's signed with the upload key when `local.properties` (git-ignored) sets `gemmory.upload.storeFile`, `gemmory.upload.storePassword`, `gemmory.upload.keyAlias` and `gemmory.upload.keyPassword`. The keystore lives outside the repo, and keystores, passwords and service-account files are never committed. The Play Console answers and the production gate are in [docs/play-compliance.md](docs/play-compliance.md).

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
