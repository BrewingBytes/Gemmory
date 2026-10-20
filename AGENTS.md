# Gemmory

Android diamond-painting game ("Gemmory: Photo Diamond Art"): turn your own photo into a finishable gem canvas, then share a 9:16 sparkle time-lapse. Kotlin Multiplatform, `:shared` (pure commonMain) + `:androidApp` (Compose). Monetized with AdMob + a VIP subscription.

This file is an index. Details live in `docs/`; read the relevant doc before working in that area. How changes are made, verified and shipped is in [docs/agents/workflow.md](docs/agents/workflow.md); read it before your first change, and add what you learn to [docs/agents/lessons.md](docs/agents/lessons.md).

## Rules that always apply

- **Adults only.** The app is not directed at children (Play Families / AdMob child-directed rules stay off).
- **User photos never leave the phone.** No upload, no backend for user content, and photo data is excluded from cloud backup and device transfer.
- **No ads on the play canvas.** Interstitials only after leaving the completion screen, under the caps in the design doc.
- **`shared/src/commonMain` is pure Kotlin.** No `android.*` or `java.awt` imports.
- **A wrong-color tap is rejected with no state change.** The save log is an ordered list of placed cell indices.
- **UI follows `docs/design-system.md`.** Ask the user before departing from it. When reviewing or QA-ing UI, flag code that doesn't match it.
- **Every long-running command has a hard time limit.** Wrap emulator and device work in `timeout` and never wait on one without a bound (details in [docs/agents/workflow.md](docs/agents/workflow.md)).

## Docs index

| Doc | What's in it |
|---|---|
| [README.md](README.md) | What the app is, module layout, a docs index, build/test/release commands, debug `-P` switches, how to add a bundled picture and how to publish a remote one (#43) and a dated batch from ChatGPT sheets (#85) |
| [docs/designs/gemmory.md](docs/designs/gemmory.md) | Product design doc: problem, scope, tiers, ad rules, sizes, success criteria, timeline, plus the CEO and eng review decision ledgers (E1-E7, D1-D20) |
| [docs/designs/ad-mediation.md](docs/designs/ad-mediation.md) | Real ads design (T34, #74): production AdMob IDs from committed build config, Unity Ads via AdMob mediation bidding, Unity and US-states consent, app-ads.txt, closed-test vs production-launch criteria |
| [docs/design-system.md](docs/design-system.md) | Design system "The Sorting Tray": tokens, fonts, components, navigation map, canvas zoom/gesture/haptics rules, copy for every state, accessibility (TalkBack, font scale, reduced motion, large screens) (`DESIGN.md` at the root is a symlink to it) |
| [docs/designs/gemmory-round2.md](docs/designs/gemmory-round2.md) | Round 2 design doc (T35-T44: evening reminder, scheduled drops, collections, weekly event, review prompt, gems, sounds, music, mystery picture, picture pipeline) with the CEO, eng, design consultation and design review ledgers |
| [docs/issues.md](docs/issues.md) | Snapshot of every task, T1-T44, with the task-to-issue mapping at its top (round 2: T*n* = #(*n*+41)) |
| [docs/play-compliance.md](docs/play-compliance.md) | Play Console answers (Data safety, content rating, target audience, ads), Firebase and API key settings, the Test Lab CI credentials (#11), and how the production gate (D18) is checked; [docs/privacy-policy.md](docs/privacy-policy.md) is the published policy |
| [docs/todos.md](docs/todos.md) | Deferred work with the conditions for picking it up (`TODOS.md` at the root is a symlink to it) |
| [docs/agents/workflow.md](docs/agents/workflow.md) | Branches, commits and stacked PRs, the verification commands, emulator and device rules |
| [docs/agents/lessons.md](docs/agents/lessons.md) | Dated gotchas learned in earlier changes (emulator, connected tests, bundled-picture sourcing) |
| [docs/agents/skill-routing.md](docs/agents/skill-routing.md) | Which gstack skill to use for which kind of request |
| [tools/bundled/PROVENANCE.md](tools/bundled/PROVENANCE.md) | Source, license and freeze settings of every bundled picture |
| [tools/remote/PROVENANCE.md](tools/remote/PROVENANCE.md) | The same for every remote picture (#43), published to `pictures/v1/` with `:shared:publishRemotePicture` (README) |

## Skill routing

When a request matches an available skill, invoke it. The routing table is in [docs/agents/skill-routing.md](docs/agents/skill-routing.md).
