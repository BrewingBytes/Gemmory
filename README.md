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
| `tools/sounds` | `PROVENANCE.md` for every app sound, which `tools/make_sounds.py` synthesizes into `res/raw`, and for the CC0 music tracks (T42) |
| `docs` | Design doc, design system, issue snapshot, Play compliance, privacy policy, deferred work |

## Docs

| Doc | What's in it |
|---|---|
| [docs/designs/gemmory.md](docs/designs/gemmory.md) | Product design doc: problem, scope, tiers, ad rules, sizes, success criteria, timeline, decision ledgers |
| [docs/designs/gemmory-round2.md](docs/designs/gemmory-round2.md) | Round 2 design doc (T35-T44) and its review ledgers |
| [docs/designs/streak-screen.md](docs/designs/streak-screen.md) | Streak screen design doc, its review ledgers and mockup |
| [docs/design-system.md](docs/design-system.md) | Design system "The Sorting Tray": tokens, components, navigation, canvas gestures, copy, accessibility |
| [docs/play-compliance.md](docs/play-compliance.md) | Play Console answers, Firebase and API key settings, Test Lab CI credentials, the production gate |
| [docs/privacy-policy.md](docs/privacy-policy.md) | The published privacy policy |
| [tools/bundled/PROVENANCE.md](tools/bundled/PROVENANCE.md) | Source, license and freeze settings of every bundled picture |
| [tools/remote/PROVENANCE.md](tools/remote/PROVENANCE.md) | The same for every remote picture, with when it was published |
| [tools/sounds/PROVENANCE.md](tools/sounds/PROVENANCE.md) | Every app sound, made in-house by `tools/make_sounds.py`, and every music track (CC0, with source and author) |

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
- `gemmory.playGames=true` turns Play Games achievements on in a debug build (#49). By default debug builds keep earned achievements in the ledger and hide the Settings row. It needs the debug key's SHA-1 on a Play Games OAuth client and a tester account (Play Games setup below).
- `gemmory.umpDebugEea=true` and `gemmory.umpTestDevice=…` show the EEA consent form; `gemmory.umpDebugUsState=true` shows the US-states one (T34). Logcat `GemmoryAds` then prints the consent result, and `adb shell run-as com.brewingbytes.gemmory cat shared_prefs/com.brewingbytes.gemmory_preferences.xml` shows the GPP string UMP stored.
- `android.testInstrumentationRunnerArguments.deviceChecks=true` runs the `@DeviceCheck` walkthroughs. `@PerfTest` classes (canvas frame timing, gallery load timing) run only on request via `am instrument -e class …`. The canvas frame-timing test fails past its pass line (Frame timing on Firebase Test Lab below).

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
- **Quota:** the project is on the Spark plan: 5 physical-device runs a day at no cost, then Test Lab refuses runs (no bill). Each phone is one run, so one matrix uses 3 and there are no automatic retries. A `v*` tag also runs frame timing on the Galaxy A06 (below), so a release-candidate day uses 4 and leaves 1 for a one-phone re-check. A second run the same day is refused whole (`TEST_QUOTA_EXCEEDED`, the run summary says so); run it again the next day.
- **Catalog:** Run workflow with "Only print the physical-device catalog" ticked lists the phones without using quota; use it when a model above leaves the catalog.
- **Credentials:** Workload Identity Federation, no key file. Setup: [docs/play-compliance.md](docs/play-compliance.md) → "Test Lab CI credentials".
- **Test Lab shuts down on 2027-09-30** (Firebase deprecation notice); the move to Google Cloud's Developer Device Platform is #55.

## Frame timing on Firebase Test Lab

The same workflow runs `CanvasFrameTimingTest` (#54, design doc → #10 note) on the low-end phone alone, the Galaxy A06 (`a06,35`), as a second matrix: a Big canvas (106×142, 36 colors, a third placed) is panned and pinched across every zoom level with two fingers, and the test fails past the pass line.

- **Pass line:** a frame is missed when it ends more than 1 ms past its own deadline (`FrameMetrics.DEADLINE`, so it holds at any refresh rate; the 1 ms covers the emulator, whose on-time frames end up to 0.6 ms late). Each pan phase may miss at most 5% of its frames, each pinch phase 10% (the first frames at a new zoom bucket build its sprite atlas), the whole run 5%. A phase with fewer than 30 frames fails too. The fit-zoom pan is logged but not checked: the whole canvas shows, so it draws only a few frames. The numbers are constants in the test's companion object.
- **When:** every `v*` tag, after the encoder smoke (it runs even if the smoke failed). By hand: Actions → Test Lab → Run workflow → phones `frames`.
- **Build (#117):** the release-like `perf` build type: R8 and not debuggable like release, signed with the debug key, with Google's test ad IDs, no Firebase, no Play Games and the fake billing backend. The pass line applies to it; the test logs `build perf debuggable=false` first and fails if a perf APK is debuggable. "frame build" `debug` in Run workflow times the debug APK instead, for comparison only. `androidApp/proguard-perf.pro` keeps what the in-process test reaches (AndroidX whole but optimized, Kotlin and coroutines unoptimized, the app classes the test drives) and turns off renaming; `proguard-perf-test.pro` is the test APK's. Build locally with `./gradlew -Pgemmory.testBuildType=perf :androidApp:assemblePerf :androidApp:assemblePerfAndroidTest` (connected tests stay on debug without the switch).
- **Quota:** 1 physical-device run. A tag day uses 4 of the 5 (3 smoke + 1 frames); a by-hand `frames` run fits after a tag run that day, but a full 3-phone re-run doesn't.
- **Results:** the run summary has a "Frame timing on Test Lab" block with the pass/fail row and the per-phase lines (`frames`, p50-max, `missed`) from the A06's logcat; the `test-lab-results` artifact has its video and logcat under `results-frames/`. On the emulator (boot with `-gpu host`), install both perf (or debug) APKs and run `adb shell am instrument -w -e class com.brewingbytes.gemmory.play.CanvasFrameTimingTest com.brewingbytes.gemmory.test/androidx.test.runner.AndroidJUnitRunner`; the lines are under the `CanvasFrameTiming` logcat tag.
- **No Play Games over the canvas (#113):** Test Lab runs with `--no-auto-google-login`, and debug builds drop the Play Games SDK's init provider, so automatic sign-in can't open its overlay on top of the app (the v0.7.0 and v0.8.0 runs failed with "No compose hierarchies found" that way).

## Release

`./gradlew :androidApp:bundleRelease` builds an R8-shrunk AAB. It's signed with the upload key when `local.properties` (git-ignored) sets `gemmory.upload.storeFile`, `gemmory.upload.storePassword`, `gemmory.upload.keyAlias` and `gemmory.upload.keyPassword`. The keystore lives outside the repo, and keystores, passwords and service-account files are never committed. The Play Console answers and the production gate are in [docs/play-compliance.md](docs/play-compliance.md).

Real ads (T34): release builds take the production AdMob IDs from the committed `gradle.properties` (`gemmory.admob.appId`, `gemmory.admob.rewardedUnit`, `gemmory.admob.interstitialUnit`; they aren't secret). `:shared:validateReleaseAdIds` runs first in every release build and fails it when any is missing, blank or Google's test publisher, so a release can't ship test ads or none. Debug builds and connected tests always use Google's test IDs. Unity Ads bids through AdMob mediation with no Unity IDs in the app (they live in the AdMob console). Console setup, test mode and rollback: [docs/play-compliance.md](docs/play-compliance.md) → Real ads.

The app icon (launcher foreground and monochrome, and the 512×512 Play Store icon `tools/store/icon-512.png`) comes from `tools/store/icon.py`; its docstring has the render command.

A release candidate is the `main` commit with the version bump: tag it `v<versionName>` (`git tag v0.3.2 && git push origin v0.3.2`). The tag starts the Test Lab encoder smoke and the frame-timing check (4 of the day's 5 runs); upload the AAB to Play only after both pass.

## Streak widget

The home-screen streak widget (#103) is built with Jetpack Glance (`androidx.glance:glance-appwidget`, in `androidApp/.../widget/`). On the emulator: long-press the home screen → Widgets → Gemmory → Streak. The number is a bitmap in the design-system mono (`numberBitmap`); the line is in the system font. The picker preview is `res/drawable-nodpi/widget_streak_preview.png` on every Android version, a 3x headless-Chrome capture of the tile (Atkinson Hyperlegible Mono number, Roboto line, pulled from the emulator's `/system/fonts`), so redo it when the layout changes. The Glance layout test (`StreakWidgetTest`) is a connected test: Glance's test environment needs Android's `Bundle`.

## Play Games setup

Achievements (#49, no leaderboards) go through Play Games Services v2. The founder does the Play Console part; the app needs only `androidApp/src/main/res/values/games-ids.xml`. While its `game_services_project_id` is blank, Play Games stays off: no SDK start, no Settings row, and what players earn waits in the ledger (`filesDir/achievements/state`) for a build with the ids.

1. **Project.** Play Console → Gemmory → Grow users → Play Games Services → Setup and management → Configuration. Create the Play Games Services project (Gemmory's is project 121638784035, its own Cloud project; Firebase isn't linked and doesn't need to be).
2. **OAuth consent screen** (Cloud Console, only if Play Console asks): External, app name Gemmory, the support email, no extra scopes.
3. **Credentials.** Configuration → Credentials → Add credential → Android, one per signing key, package `com.brewingbytes.gemmory`:
   - the **app signing key** SHA-1 (Protected with Play → Play Store protection → Manage Play app signing): every install from Play. Gemmory's: `60:28:23:2B:95:9E:A8:57:B7:5D:2B:FC:32:C9:F0:13:0E:B9:7F:D1`;
   - the **upload key** SHA-1 (same page): builds signed locally with the upload key. Gemmory's: `97:F7:D0:BA:23:7F:4C:58:03:B3:24:DA:7A:87:AF:B0:61:C7:0B:45`;
   - optionally the **debug key** SHA-1 (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`), for `-Pgemmory.playGames=true`.
4. **Achievements.** Setup and management → Achievements → Add (Save as draft), in this order, initial state Revealed. Icons are `tools/achievements/<name>.png` (512×512 PNG, patina table cuts since T40; Play Games greys the revealed one itself and masks the pop-up's icon to a circle, so the art stays in the middle).

   | Name | Description | Incremental (steps) | Points | Icon |
   |---|---|---|---|---|
   | First Sparkle | Finish your first picture. | no | 25 | first_sparkle |
   | Quick Study | Finish a Quick picture. | no | 25 | quick_study |
   | Steady Hands | Finish a Medium picture. | no | 50 | steady_hands |
   | The Long Evening | Finish a Big picture. | no | 100 | the_long_evening |
   | Your Own Gem | Finish a canvas made from one of your photos. | no | 50 | your_own_gem |
   | Show and Tell | Share a finished picture. | no | 25 | show_and_tell |
   | Work in Progress | Share a picture before its last gem. | no | 25 | work_in_progress |
   | Tray Regular | Finish 5 pictures. | yes (5) | 50 | tray_regular |
   | Gallery Wall | Finish 25 pictures. | yes (25) | 150 | gallery_wall |
   | Photo Album | Finish 5 canvases made from your photos. | yes (5) | 100 | photo_album |
   | A Week of Gems | Finish a picture 7 days in a row. | yes (7) | 100 | a_week_of_gems |
   | A Month of Gems | Finish a picture 30 days in a row. | yes (30) | 150 | a_month_of_gems |

   850 of the 2,000 points; the rest stays in reserve. The rules behind each one are `AchievementRules` (`shared/…/policy/Achievements.kt`). To redraw an icon, edit `tools/achievements/icons.html` and run `brave --headless=new --allow-file-access-from-files --hide-scrollbars --window-size=512,512 --virtual-time-budget=2000 --screenshot=$PWD/<name>.png "file://$PWD/icons.html#<key>"` in that folder (keys are in the file's `ICONS`).
5. **Resources.** Achievements → Get resources → Android (XML). Put the export's app id in `game_services_project_id` and each achievement id in the `achievement_*` string of the same name (the names match the export). The ids aren't secret; commit them.
6. **Testers.** Setup and management → Testers → add the closed-test accounts. While Play Games Services is unpublished, only testers can sign in and unlock.
7. **Publish** (Review and publish → Play Games Services) once a closed-test build shows the achievements unlocking. Achievements can be added later. Once published, an achievement can't be deleted, and its initial state and its type (incremental or not) are fixed. Player progress can be reset only on draft achievements.

How it behaves: Play Games signs the player in on its own at app start, with no sign-in screen; when that fails or the player declines, nothing is shown. Unlocks are counted on the phone as they happen and sent only while the Gallery or Settings shows, so Play Games' own "Achievement unlocked" pop-up never covers the canvas or the completion screen. Settings → Achievements opens Play Games' achievements screen (signing in first if needed). A player without a Play Games profile sees Play Games' own "Create a Play Games profile" sheet once, at the first start. On a google_apis emulator image Play Games can't run (its Google Play services module never downloads); use a Play Store image (`gemmory_play`, lessons) or a phone, with the upload-signed release build and a tester account.

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
   The task freezes the grid like `freezeBundledGrid` and writes `pictures/v1/grids/<id>.gmrg`. It adds the catalog entry (CRC, SHA-256, size, colors, `addedAt`, and `minAppVersion`). The task stamps `minAppVersion` with the lowest app that reads the picture right (D14, `RemoteCatalogs.lowestApp`): 11 for a plain picture (the first app with remote pictures), 12 above the Standard colors, 13 for a dated drop. `-PminAppVersion=` can raise it; a lower one is refused. Then it signs `catalog.json` and checks the result with the app's own verifier and the app's public key before writing anything. Last, it writes `tools/remote/<id>.review.png` and appends the provenance row.
   To schedule a drop (T36), add `-PavailableFrom=YYYY-MM-DD`: the picture is downloaded ahead but stays hidden on phones until that local date, when it's New, Today's picture with "New today", and free to start that day only. Its `addedAt` is that date, and it's stamped 13, since an older app would show it at once. One drop opens per date: the task refuses a second picture on a date already taken. Ship an app with drops (0.6.1, versionCode 14; drops need 13 or later) to production before the first dated publish.
   For a richer picture, add `-Pcolors=more` (or `fewer`): it converts at the size's More cap like the photo flow (#29: Quick 24, Medium 40, Big 60, with near-twin shades merged). A picture above the size's Standard count (16 / 24 / 36) is stamped 12: an older app would refuse the whole catalog over it.
3. Look at the review PNG, then commit `pictures/v1/` and `tools/remote/`, and open a PR. Phones pick the picture up within a day of the merge.

### Publishing a batch of pictures

A drop every date is about 31 pictures a month (T44, #85), so they're made six at a time and published in one signed batch.

1. **Make the sheets.** Use the prompt in `tools/remote/prompts/sheet-2x3.md`: one ChatGPT image is six pictures in one theme and one aspect. Save each sheet in a new batch folder, `tools/remote/batches/<yyyy-mm>-<letter>/` (e.g. `2026-11-a`). Look at the tiles first: `./gradlew :shared:splitPictureSheet -Psheet=tools/remote/batches/2026-11-a/sheet-1.png -Pratio=1:1` writes them to `shared/build/sheet-tiles/`. A sheet that doesn't split into six tiles of at least 256 px (blank cell, no white gutters, not 3×2 or 2×3) stops with a named error; regenerate it.
2. **Write `batch.csv`** in the batch folder, one row per picture (fields with a comma go in "quotes"):
   ```csv
   sheet,tile,id,title,size,availableFrom,subject,generatedOn,contentChecked
   sheet-1.png,1,autumn-lane,Autumn Lane,medium-1:1,2026-11-01,a country lane under orange trees,2026-10-30,no
   ```
   `tile` is 1-6, left to right then down. `size` names the sheet's aspect, and every row of a sheet has the same one. `subject` is a few words for the provenance row. Optional columns: `colors` (`fewer`, `standard`, `more`), `locked` (`true` by default, since Today's picture only picks locked ones), `focus` (0.5), `prompt` (`prompts/sheet-2x3.md`) and `model` (`ChatGPT image generation (OpenAI)`), and `rewardOf` (T37: a collection id; the row is that collection's keepsake, hidden on phones until the collection is finished, so it has no `availableFrom` and is stamped min app 15), and `mystery` (T43: `yes` on a dated row makes it a mystery picture, its title and art hidden on phones until it's finished; stamped min app 16, so ship an app with versionCode 16 or later (0.8.1) to production before the first one). Leave a bad tile's row out. Dates must be after today, one per date, counting the ones already published.
   For a collection (T37), add `collections.csv` beside it: `id,title,members`, members separated by `;` (e.g. `seaside,Seaside,autumn-lane;harbor;lemons`). Members are published or bundled picture ids, in the order the row shows them; a collection already published with the same id is replaced in place, a new one goes last (the last one is the newest). Collections never raise a picture's min app. Each collection gets at most one keepsake. For a weekly event (T38), add an `event` column (`id,title,members,event`) and put `yes` on its row: its 7 members must be dated drops on 7 consecutive dates, and exactly one picture has `rewardOf` = the event id (its exclusive, earned with 3 finishes in the week). The tool refuses any other shape; a file without the column still reads. Ship an app with collections and events (0.7.0, versionCode 15) to production before the first keepsake or event: an older app skips keepsakes and shows an event's 7 drops as plain drops.
3. **Run it, then do the content check.** `./gradlew :shared:publishPictureBatch -Pbatch=tools/remote/batches/2026-11-a`. While any row's `contentChecked` isn't `yes`, it prints the check (PEGI 3 / Everyone, made for adults, no real people, brands, protected designs or copyrighted characters, provenance recorded), writes each unchecked picture's review PNG to `shared/build/picture-batch/<batch>/`, and stops. Look at each one, set `yes` on its row, and run it again.
4. **Read what it prints.** It splits, freezes and stamps every picture (a dated one gets `addedAt` = `availableFrom` and min app 13), signs one catalog, and checks it with the app's verifier. If anything fails, nothing is written. If everything passes, it writes `pictures/v1/`, each tile as `tools/remote/<id>.jpg` with its review PNG, and one AI-generated provenance row per picture (model, sheet, tile, prompt file, date). Last, it prints how many days of drops are scheduled from tomorrow. Below 14 it warns you to make the next batch.
5. **Open one PR** with `pictures/v1/`, `tools/remote/` and the batch folder (sheets and CSV): catalog, grids and provenance together, or none of them.

The **Drop calendar** workflow (`.github/workflows/calendar.yml`) runs `./gradlew :shared:checkDropCalendar` every day. It verifies the published catalog and fails when fewer than 7 consecutive days of drops are scheduled from today (in UTC+14, where a date opens first). It passes while no picture is dated.

To withdraw a picture, run `./gradlew :shared:withdrawRemotePicture -Pid=<id>`, mark its provenance row withdrawn, and open a PR. The picture leaves every phone that hasn't started it. Never edit `catalog.json` by hand (the signature would no longer match and every phone would refuse it), and never re-freeze a published grid. A fix is a new id.

The catalog also carries switches the founder flips without an app release. Today there is one: `review`, the in-app review prompt (T39). Turn it on with `./gradlew :shared:setRemoteFeature -Pfeature=review -Pon=true` (`-Pon=false` turns it off), then open a PR with `pictures/v1/`. The tool re-signs the catalog with a later `issuedAt` and keeps every picture and collection. Later publishes and withdraws keep the switch. When to flip it is in `docs/play-compliance.md` → In-app review.

Debug builds can read another catalog folder with `-Pgemmory.remotePicturesUrl=<url ending in />`.
