# Gemmory

Android diamond-painting game ("Gemmory: Photo Diamond Art"): turn your own photo into a finishable gem canvas, then share a 9:16 sparkle time-lapse. Kotlin Multiplatform, `:shared` (pure commonMain) + `:androidApp` (Compose). Monetized with AdMob + a VIP subscription.

This file is an index. Details live in `docs/`; read the relevant doc before working in that area.

## Rules that always apply

- **Adults only.** The app is not directed at children (Play Families / AdMob child-directed rules stay off).
- **User photos never leave the phone.** No upload, no backend for user content, and photo data is excluded from cloud backup and device transfer.
- **No ads on the play canvas.** Interstitials only after leaving the completion screen, under the caps in the design doc.
- **`shared/src/commonMain` is pure Kotlin.** No `android.*` or `java.awt` imports.
- **A wrong-color tap is rejected with no state change.** The save log is an ordered list of placed cell indices.
- **UI follows `docs/design-system.md`.** Ask the user before departing from it. When reviewing or QA-ing UI, flag code that doesn't match it.
- **Every long-running command has a hard time limit.** Wrap emulator and device work (`connectedDebugAndroidTest`, `am instrument`, adb wait loops) in `timeout` (about 5-8 minutes; a full instrumented run normally takes 3-4) and never wait on one without a bound. When it times out, kill it, check the emulator's load (`adb shell cat /proc/loadavg`), and report what happened instead of retrying blindly.
- **A PR that builds on another open PR is stacked, not merged into it.** Use `gh stack` (`gh stack init <lower-branch> <new-branch>`, then `gh stack submit`) so each PR keeps its own scope and lands after the one below it.

## Docs index

| Doc | What's in it |
|---|---|
| [README.md](README.md) | What the app is, module layout, a docs index, build/test/release commands, debug `-P` switches, how to add a bundled picture |
| [docs/designs/gemmory.md](docs/designs/gemmory.md) | Product design doc: problem, scope, tiers, ad rules, sizes, success criteria, timeline, plus the CEO and eng review decision ledgers (E1-E7, D1-D20) |
| [docs/design-system.md](docs/design-system.md) | Design system "The Sorting Tray": tokens, fonts, components, navigation map, canvas zoom/gesture/haptics rules, copy for every state, accessibility (TalkBack, font scale, reduced motion, large screens) (`DESIGN.md` at the root is a symlink to it) |
| [docs/issues.md](docs/issues.md) | Snapshot of tasks T1-T14, created as GitHub issues #1-#14 (T*n* = #*n*) |
| [docs/play-compliance.md](docs/play-compliance.md) | Play Console answers (Data safety, content rating, target audience, ads), Firebase and API key settings, and how the production gate (D18) is checked; [docs/privacy-policy.md](docs/privacy-policy.md) is the published policy |
| [docs/todos.md](docs/todos.md) | Deferred work with the conditions for picking it up (`TODOS.md` at the root is a symlink to it) |
| [docs/agents/skill-routing.md](docs/agents/skill-routing.md) | Which gstack skill to use for which kind of request |

## Skill routing

When a request matches an available skill, invoke it. The routing table is in [docs/agents/skill-routing.md](docs/agents/skill-routing.md).
