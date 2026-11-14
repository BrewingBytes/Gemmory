# Agent workflow

How changes are made, verified and shipped in this repo. The product rules that no change may break are in [AGENTS.md](../../AGENTS.md). Gotchas learned along the way are in [lessons.md](lessons.md).

## Branches, commits and PRs

- `main` is protected: PRs only, and the `build` check (`ci.yml`) and the `ios-gate` check (`ios.yml`, see iOS below) must pass. Work on a feature branch off `main`.
- Don't commit or push unless the user asks. When they ask for a PR, commit with the attribution lines from the session, push, and open it.
- One issue per PR. Work the user asks for outside that issue (a README during a feature, say) gets its own PR.
- A PR that builds on another open PR is stacked, not merged into it: `gh stack init <lower-branch> <new-branch>`, then `gh stack submit --auto` (it pushes every branch and opens the missing PRs, so set the real title and body afterwards with `gh pr edit`). Later commits on a stacked branch go up with `gh stack submit --auto`. The upper PR lands after the one below it.
- Never commit keystores, passwords or service-account files. The release upload key is read from `local.properties` (git-ignored).
- Screenshots (AGENTS.md: every PR shows what it changes): capture them on the emulator during the connected run (`adb exec-out screencap -p > shot.png`, or from a test with `screencap -p /data/local/tmp/…`), commit them to the `pr-screenshots` branch under `<issue number>/` (e.g. `80/1-gallery-return.png`, numbered in reading order), push that branch, and embed each in the PR body with `<img src="https://raw.githubusercontent.com/BrewingBytes/Gemmory/pr-screenshots/<issue>/<file>" width="270">`. Never put them on the feature branch or `main`. Before/after pairs: take "before" from main's APKs (lessons, 2026-10-20 #84: stash, build, copy the APKs out).
- After the user says a PR is merged: switch to `main`, pull, delete the merged branch locally, update `.idea-to-pr-state.md`.

## Verification

Run before handing work back, and report every warning and error:

```sh
./gradlew :shared:allTests :composeApp:allTests :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug :androidApp:bundleRelease
```

- On the Mac, `allTests` includes the iOS simulator tests (`:shared` and `:composeApp`); Linux CI skips the iOS targets (`kotlin.native.ignoreDisabledTargets`).
- A change under `composeApp/` or `iosApp/` also runs `:composeApp:connectedAndroidDeviceTest` (the canvas, gem and end card renderer goldens on Android), androidApp's connected `render.SharedArtGoldenTest` (`am instrument -e class com.brewingbytes.gemmory.render.SharedArtGoldenTest …`: the time-lapse frame, before/after, streak card and Japanese wrap goldens, and the Bold text check) and the iOS UI tests on the iPhone 17 Pro simulator (README → iOS). CI runs the iOS side on every PR anyway (iOS below).

- Add `:androidApp:connectedDebugAndroidTest` on the emulator whenever app code changes.
- Smoke-test the R8 release build on an emulator after any dependency or keep-rule change.
- The converter goldens, the pinned starter grid CRC and every pinned bundled-picture CRC must stay green. Never re-freeze a shipped picture.
- A change to the time-lapse encoder (`timelapse/`, `FallbackChain`) also runs the Test Lab encoder smoke by hand (Actions → Test Lab → Run workflow) before it merges; it costs 3 of the 5 free physical-device runs a day. Release candidates run it from their `v*` tag (README → Release), which also runs the frame-timing check on the Galaxy A06: 4 runs that day (README → Frame timing on Firebase Test Lab).
- **Test Lab quota rule** (eng D24). Spark gives 5 physical-device runs a day, and a `v*` candidate uses 4. Keep it at 4: the hardware-capture and MP4-decode checks (eng D13) run inside each phone's encoder-smoke run, never as runs of their own. A failed candidate is re-checked with the one-phone input (Run workflow → phones `pixel`/`samsung`/`mediatek`), not the whole matrix. The iOS device measurements (eng D12 frame timing, eng D20 encode) share one run, on a day with no Android candidate.
- The only expected warning is Gradle's deprecation summary ("Deprecated Gradle features were used…", AGP 9.4.1's `Configuration.setVisible`). A Kotlin or lint warning fails the build (#163: `allWarningsAsErrors`, lint `warningsAsErrors`, no baseline): fix it, don't suppress it without a reason in a comment.

## iOS

`.github/workflows/ios.yml` (T60, eng D19, D25) runs on every PR and every push to main, with no path filter, as two macOS jobs side by side on the `macos-26` runner with Xcode 26.6 and fresh iOS 26.5 simulators (iPhone 17 Pro, iPad Air 11-inch (M4)). `ios-kotlin` runs `:shared` and `:composeApp` `iosSimulatorArm64Test` (about 16 min cold). `ios` builds GemmoryKit and the app in Release, as the scheme's test action does, then runs the XCTests on the iPhone and then the iPad, one simulator at a time (about 30 min cold; Debug Kotlin/Native is far too slow on the runner's simulator, and two booted simulators slow the tests more than they save). Each job is capped at 45 min and every simulator step has its own `timeout-minutes`. The Kotlin/Native toolchain is cached (`~/.konan`), so only the first run after a Kotlin update is cold.

- **The required check is `ios-gate`, a commit status, not a job.** It is pending while `ios` and `ios-kotlin` run, then green only when both are (`.github/scripts/ios-gate.sh`); a job stopped by its time limit counts as a failure. No job may be named `ios-gate`: a green job of that name would pass the check on its own.
- **`android-hotfix` label.** On a PR that only fixes Android in a hurry, add the label: `ios-gate` turns green at once and the PR merges on `ci.yml` alone. The iOS jobs still run and report, so read them. Removing the label puts the gate back on their results. Don't use it to get past a red iOS build on a normal PR.
- **Screenshots for PR bodies.** `iosAppUITests/ScreenshotTests` attaches each screen as `screen-<n>-<name>`, and the run uploads them as the `ios-screenshots` artifact (`iphone/` and `ipad/`). A PR that adds or changes an iOS screen adds a shot there, then `gh run download <run id> -n ios-screenshots` and commits the files to the `pr-screenshots` branch like the emulator ones (Branches above). Before/after: the "before" is the same artifact from main's latest `iOS` run.
- **What the gate can't do.** It runs the PR's own copy of `ios-gate.sh`, so it checks a collaborator's work, not an attacker's: anyone who can push can make it green. A PR from a fork gets a read-only token, can't post `ios-gate` at all and stays blocked; merge such a PR by re-opening its branch in this repo. If the runner image drops the pinned Xcode or runtime, every `ios` run fails at "Select Xcode" with that message: move the pins, and use `android-hotfix` for Android-only PRs meanwhile.
- **Locally**, the Mac runs the same steps (README → iOS). Its Xcode may be newer than CI's 26.6; the iOS 26.5 runtime (`xcodebuild -downloadPlatform iOS -buildVersion 26.5`) and `-Pgemmory.iosSimulator=<udid>` reproduce CI's simulator. Moving CI to a new Xcode or runtime is its own PR: change `XCODE_APP`, `SIM_RUNTIME` and the device names in `ios.yml` together.
- **TestFlight.** Tag the `main` commit `ios-v<marketing>+<build>` (`git tag ios-v0.1.0+1 && git push origin ios-v0.1.0+1`). The `testflight` job archives with the Apple Distribution certificate, uploads to TestFlight and sends the dSYMs to Crashlytics. The tagged commit must be on `main` (the job refuses otherwise). The build number must be new for that version; after a failed upload tag `+<build+1>`. A failed Crashlytics upload doesn't fail the job: the dSYMs are in the `ios-dsyms-…` artifact. `v*` tags stay Android-only. Secrets and their rotation: [play-compliance.md](../play-compliance.md) → iOS signing and TestFlight.

## Emulator and device work

- Every long-running command has a hard time limit: wrap `connectedDebugAndroidTest`, `am instrument` and adb wait loops in `timeout` (about 5-8 minutes; a full instrumented run normally takes 3-4). When one times out, kill it, check the emulator's load (`adb shell cat /proc/loadavg`) and report what happened instead of retrying blindly.
- The pinned emulator image (the renderer goldens, T59): `gemmory_pixel` = `system-images;android-36;google_apis;arm64-v8a`, device `pixel_7`, booted with `-gpu host -memory 4096 -no-audio -no-snapshot`. Recreate it with `avdmanager create avd -n gemmory_pixel -k "system-images;android-36;google_apis;arm64-v8a" -d pixel_7` if it's gone.
- Before booting, check free disk (`df -h`) and RAM (`sysctl vm.swapusage`). Boot with `-no-audio`, and run one emulator at a time. On a host short of RAM, boot with `-memory 4096 -no-snapshot`, stop the Gradle daemons first (`./gradlew --stop`) and run connected tests one class at a time.
- Gradle `-Pandroid.testInstrumentationRunnerArguments.class=` takes one class; a comma list runs only the first.
- `@DeviceCheck` tests run with `-Pandroid.testInstrumentationRunnerArguments.deviceChecks=true`. `@PerfTest` classes (`CanvasFrameTimingTest`, `GalleryLoadTimingTest`) are left out of `connectedDebugAndroidTest`; run them with `am instrument -w -e class …`.

## Lessons

When a change teaches something the next session would otherwise relearn (a tool quirk, a flaky setup, a non-obvious rule), add a dated line to [lessons.md](lessons.md) in the same PR.
