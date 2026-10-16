# Agent workflow

How changes are made, verified and shipped in this repo. The product rules that no change may break are in [AGENTS.md](../../AGENTS.md). Gotchas learned along the way are in [lessons.md](lessons.md).

## Branches, commits and PRs

- `main` is protected: PRs only, and the `build` CI check must pass. Work on a feature branch off `main`.
- Don't commit or push unless the user asks. When they ask for a PR, commit with the attribution lines from the session, push, and open it.
- One issue per PR. Work the user asks for outside that issue (a README during a feature, say) gets its own PR.
- A PR that builds on another open PR is stacked, not merged into it: `gh stack init <lower-branch> <new-branch>`, then `gh stack submit --auto` (it pushes every branch and opens the missing PRs, so set the real title and body afterwards with `gh pr edit`). Later commits on a stacked branch go up with `gh stack submit --auto`. The upper PR lands after the one below it.
- Never commit keystores, passwords or service-account files. The release upload key is read from `local.properties` (git-ignored).
- After the user says a PR is merged: switch to `main`, pull, delete the merged branch locally, update `.idea-to-pr-state.md`.

## Verification

Run before handing work back, and report every warning and error:

```sh
./gradlew :shared:allTests :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug :androidApp:bundleRelease
```

- Add `:androidApp:connectedDebugAndroidTest` on the emulator whenever app code changes.
- Smoke-test the R8 release build on an emulator after any dependency or keep-rule change.
- The converter goldens, the pinned starter grid CRC and every pinned bundled-picture CRC must stay green. Never re-freeze a shipped picture.
- A change to the time-lapse encoder (`timelapse/`, `FallbackChain`) also runs the Test Lab encoder smoke by hand (Actions → Test Lab → Run workflow) before it merges; it costs 3 of the 5 free physical-device runs a day. Release candidates run it from their `v*` tag (README → Release).
- The only expected warning is Gradle's deprecation summary ("Deprecated Gradle features were used…").

## Emulator and device work

- Every long-running command has a hard time limit: wrap `connectedDebugAndroidTest`, `am instrument` and adb wait loops in `timeout` (about 5-8 minutes; a full instrumented run normally takes 3-4). When one times out, kill it, check the emulator's load (`adb shell cat /proc/loadavg`) and report what happened instead of retrying blindly.
- Before booting, check free disk (`df -h`) and RAM (`sysctl vm.swapusage`). Boot with `-no-audio`, and run one emulator at a time. On a host short of RAM, boot with `-memory 4096 -no-snapshot`, stop the Gradle daemons first (`./gradlew --stop`) and run connected tests one class at a time.
- Gradle `-Pandroid.testInstrumentationRunnerArguments.class=` takes one class; a comma list runs only the first.
- `@DeviceCheck` tests run with `-Pandroid.testInstrumentationRunnerArguments.deviceChecks=true`. `@PerfTest` classes (`CanvasFrameTimingTest`, `GalleryLoadTimingTest`) are left out of `connectedDebugAndroidTest`; run them with `am instrument -w -e class …`.

## Lessons

When a change teaches something the next session would otherwise relearn (a tool quirk, a flaky setup, a non-obvious rule), add a dated line to [lessons.md](lessons.md) in the same PR.
