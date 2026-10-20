# Gemmory: issue drafts (from the eng review, 2026-10-12)

Source: `docs/designs/gemmory.md` → "Implementation Tasks" (T1-T12) and "Decision ledger (Eng Review)" (E1-E7, D1-D20).
Created on GitHub on 2026-10-13 as issues #1-#14 (T*n* = #*n*, https://github.com/BrewingBytes/Gemmory/issues). The issues are the live source; this file is the snapshot they were created from. Each section is one issue: the `##` line is the title, `Labels:` lists its labels, and the rest is the body.
Updated after the Phase 5 design review (2026-10-13, D3-D27): T2, T3, T4, T7, T8 and T10 gained UI scope, and T13 (app shell and screens) and T14 (accessibility and large screens) were added. UI specs and exact copy live in `docs/design-system.md`.
Later issues break the T*n* = #*n* mapping: T15 = #18 (plan step S6, which had no issue), T16 = #19 and T17 = #20 (both from playing #18 on the emulator), T18 = #29 (founder request during #19), T19-T24 = #31-#36 (from an app review after #19), T25 = #38 (founder request), T26 = #40 (follow-up of #12), T27 = #43 (founder request during #5), T28 = #49 (Play Console suggestion, founder request during the 0.3.0 release), T29 = #50 (an existing device-check failure found during #32), T30 = #54 and T31 = #55 (both from #11), T32 = #57 and T33 = #58 (both founder requests during #35), T34 = #74 (ad mediation design, 2026-10-18).
Round 2 (T35-T44, eng review 2026-10-19) is at the end of this file, created on 2026-10-19 as #76-#85 (T*n* = #(*n*+41)).

---

## T1: Scaffold the 2-module KMP project with CI
Labels: P1, build

**Why:** D1 locked the layout: `:shared` (KMP, commonMain) + `:androidApp`.

**Scope**
- `settings.gradle.kts`, version catalog `gradle/libs.versions.toml`
- `:shared` with commonMain packages `converter/`, `game/`, `save/`, `policy/`, `timelapse/` and `commonTest` (kotlin.test)
- `:androidApp` (Compose) depending on `:shared`
- GitHub Actions: `./gradlew :shared:allTests :androidApp:assembleDebug`
- Lint/CI rule: no `android.*` / `java.awt` imports under `shared/src/commonMain`

**Done when:** CI is green on an empty project; the import rule fails a deliberate violation.
Effort: human ~1d / CC ~20min

---

## T2: Photo → grid converter (commonMain)
Labels: P1, converter

**Why:** E3, finding 14, D19. The converter is risk #1 (spike).

**Scope**
- `convert(pixels: IntArray /*ARGB*/, width, height, spec: GridSpec, seed: Long): Pattern`
- Area-average to grid resolution in Lab, seeded k-means++ (palette cap 16/24/36), region merge, numbered slots
- 9 GridSpecs: Quick 56², 48×64, 64×48 · Medium 90², 78×104, 104×78 · Big 122², 106×142, 142×106
- androidApp decodes with `ImageDecoder.setTargetSize` to ≤1024 px (never full resolution)
- Tests now: determinism (same seed → same grid), palette cap, degenerate inputs (1 color, tiny, alpha)
- At spike GO: run `updateConverterGoldens` once, commit 27 goldens (9 specs × face/pet/landscape), make `ConverterGoldenTest` required in CI
- Build-time only: a 10th spec, Starter 24×32 with 8 colors, used to produce the free starter picture's frozen grid (design D13); it is never offered in the size picker

**Done when:** tests pass; at GO the goldens are frozen.
Effort: human ~3d / CC ~1h

---

## T3: Versioned saves, grid codec, atomic storage, save scheduler
Labels: P1, persistence

**Why:** E2, D3, D4, D8, D11.

**Scope**
- `SaveCodec`: magic "GMRY", u16 schemaVersion, ordered cell-index log (varints) only, grid CRC32, CRC32 trailer; migration registry
- `GridCodec`: magic, version, w/h, palette (Lab+RGB), index map, CRC32
- Bundled pictures ship as frozen grid assets (never re-converted at runtime)
- Load: refuse unknown newer version, bad CRC, grid-CRC mismatch, duplicate/out-of-range index → keep the last good file + Crashlytics non-fatal
- Atomic write: temp + fsync + rename (`CanvasRepository`, app-private, backup-excluded for photo canvases)
- `SaveScheduler`: write 1 s after the last placement, max wait 5 s, plus `onStop` and on completion
- View state (zoom, pan, selected color) is stored with the canvas record, outside the GMRY log, and restored on resume (design D25): `files/canvases/<id>/view` (u8 version + zoom, panX, panY, slot), atomic write on onStop only, same backup rule as its canvas dir, ignored → defaults if missing/corrupt/out of range, deleted with the canvas (eng re-check)
- `CanvasRepository.load` returns `Loaded | RecoveredFromLastGood | Damaged`; the writer reports `WriteFailed(diskFull)` to the canvas UI (eng re-check)
- A save that fails because storage is full is surfaced to the UI ("Progress isn't saving: phone storage is full.", design D9); a refused load with no last good file marks the canvas damaged so the gallery can show "Can't open"

**Tests:** SaveCodec golden bytes, v0→v1 migration, corrupt/truncated/newer/duplicate cases, GridCodec golden, grid mismatch, scheduler with a fake clock.
Effort: human ~2d / CC ~45min

---

## T4: Time-lapse encoder with fallback chain, progress, share
Labels: P1, timelapse

**Why:** E1 (the CEO review's CRITICAL GAP), E7, D2, D14, finding 12.

**Scope**
- `FramePlan` (pure): 450 frames (15 s @ 30 fps) = 1 s intro (photo canvases: cropped source photo crossfading to the empty tint grid; bundled: tint grid only) + 12 s build + 2 s still hold (design D26)
- Frame band: title max 2 lines then ellipsis; watermark on its own bottom row over a 25% scrim; photo canvas titles default to "My photo · 13 Oct" (design D26)
- Edge copy (design D8): no foreground service; killed mid-encode → "The video stopped when the app closed. Tap Share to try again." on the next completion visit; total failure → "Couldn't make the video. Try again."; disk full → "Not enough space on this phone to make the video."; Cancel or dismissing the share sheet → back to completion, no message
- `FallbackChain` (pure): [1080×1920, 720×1280] → probe `areSizeAndRateSupported` + alignment → encode; any configure/encode/mux error deletes the partial file, logs a non-fatal and moves to the next target; all fail → before/after PNG + "Video isn't supported on this phone, so here's a before/after picture instead."
- `MediaCodecEncoder`: AVC, Surface input, one offscreen bitmap updated incrementally per frame → `lockHardwareCanvas` → MediaMuxer
- `EncodeManager`: app-scoped, single-flight per canvas, cancellable; starts on **Share tap** (D2)
- Progress sheet with % and Cancel (Cancel deletes the partial file)
- Cache MP4 in cacheDir keyed by canvasId + log length + watermark flag; Start over, Play again and Delete call `EncodeManager.cancel(canvasId)` then delete that canvas's cached MP4s (eng re-check R-X1)
- Pending marker `cacheDir/encode-<canvasId>.pending` written at encode start, removed on success/cancel/handled failure; startup sweep deletes partial MP4s; completion screen shows the killed-encode message once when a marker has no MP4 (eng re-check)
- Share intent EXTRA_TEXT: "Made with Gemmory: <Play URL>&referrer=utm_source%3Dshare%26utm_medium%3D<bundled|photo>%26utm_campaign%3Dtimelapse"

**Tests:** FallbackChainTest (every branch, fake encoder), FramePlanTest (3k/8k/15k; 30 intro + 360 build + 60 hold frames), instrumented `TimelapseEncoderSmokeTest`; EncodeCancelTest rows for restart → fresh MP4 and the pending marker.
Effort: human ~4d / CC ~1.5h

---

## T5: Play compliance before the first closed-track upload + production gate
Labels: P1, release

**Why:** D10 (the closed-test clock can't start without these), D17, D18.

**Scope**
- Privacy policy (photos never leave the device; Crashlytics under legitimate interest; ads/analytics under UMP consent)
- Data safety: AdMob, Firebase Analytics, Crashlytics, ML Kit diagnostics, all declared up front
- Content rating, target audience (not children), ads declaration, store listing
- Budget 3-7 days for the first review before the clock starts
- Production gate: crash-free users ≥99% and no unrecovered save-load failures; closed-test percentages are informational

**Done when:** Play Console shows no blocking tasks and the closed release is accepted.
Effort: human ~4h / CC ~30min

---

## T6: Spike protocol: score sheet, Medium-only recognition, decoys
Labels: P1, spike

**Why:** D12, D13, D20 (gate scoring must be valid).

**Scope**
- Score sheet: every tester gives a yes/no "I'd play this" per photo at every size they see; a photo passes at a size at ≥60% yes; GO rating = ≥15/20 photos pass at their best size; pets/landscapes bar = ≥75% of non-face photos pass at Medium
- Known-subject + lineup tests at **Medium only**, one exposure per tester per subject/photo; Quick counts as failing faces (faceMinSize = Medium); a Big re-test needs a fresh circle + fresh lineup testers
- ≥18 lineup decoys from licensed stock, matched on age/gender presentation/hair/glasses/pose, logged in the provenance sheet

Effort: human ~2h / CC ~15min

---

## T7: Ad policy, daily ledger, AdMob + UMP
Labels: P2, ads

**Why:** E5, D6, D15, CEO D6/D7.

**Scope**
- `AdPolicy(clock, tz, store)` (commonMain): leave-completion only; not in the first session (ends after ≥30 min in background or a later cold start); not before the 2nd finish; ≥10 min gap; ≤4/day; not within 2 min of a rewarded ad; never for VIP/remove-ads
- `DailyLedger`: allowance (1 free photo canvas/day + 1 per rewarded video, no cap since #7), idempotent `consume(draftId)`, grace 1/day/reward type, reset on local date change
- Grace only when online (validated network) + MobileAds initialized + load ended in no-fill/timeout; offline → "You're offline, connect to watch a video" + VIP offer, no grace
- Picture unlocks are permanent (DataStore, backup OK)
- AdMobGateway (preload, 5 s "Loading video…"), ConsentManager (UMP + Consent Mode v2)
- UMP form on first launch only (EEA), before any ad request; Settings "Privacy choices" shown only when UMP says privacy options are required (design D3)
- Rewarded UI (design D11): reward granted → locked picture shows "Unlocked" for 600 ms then opens the canvas; extra canvas → back to Preview with "Uses a bonus canvas from a video."; closed early → muted "Watch to the end to unlock."
- `DailyLedger` exposes the allowance state that drives the Preview line and the gallery note (design D5)

**Tests:** table-driven AdPolicyTest / DailyLedgerTest (every rule, midnight, DST, online/offline).
Effort: human ~1.5d / CC ~30min

---

## T8: Entitlements, billing, restore
Labels: P2, billing

**Why:** E6, D5, D16.

**Scope**
- `Entitlements.from(purchases)` (commonMain): VIP = `vip_weekly`/`vip_yearly` PURCHASED; adsRemoved = `remove_ads` PURCHASED or VIP; PENDING grants nothing
- `BillingRepository` (PBL 8): auto-reconnect, `enablePendingPurchases(PendingPurchasesParams)`, `queryPurchasesAsync` on cold start/resume/after purchase, acknowledge every unacknowledged purchase, "Restore purchases" in Settings
- Cache Entitlements + `lastVerifiedAt` in DataStore; VIP falls back to Free after 7 days with no successful query; `remove_ads` never expires once verified (only a successful query without it clears it)
- Paywall states (design D6): placeholders while prices load; "Prices couldn't load. Connect and try again."; pending "Payment pending. VIP starts when Google Play confirms it."; error "Google Play couldn't finish the purchase. You weren't charged."; success line in the gallery; hide "Just remove ads" when owned; Settings "Manage subscription" opens Play
- VIP unconfirmed (design D7): Membership "VIP · connect to Google Play to confirm" + one-time gallery line after the 7-day fallback

**Tests:** EntitlementsTest table incl. day 6/7, clock moved backward, remove_ads exemption; device test with a license tester (buy → reinstall → Restore).
Effort: human ~1.5d / CC ~30min

---

## T9: Idempotent Start, entitlement at Start, delete cancels encode
Labels: P2, game

**Why:** E4.

**Scope**
- Preview mints `draftId`; Start button single-flight; double tap = one canvas, one allowance
- Midnight reset touches only ledger counters, never canvases
- Entitlement checked only at Start, so a lapsed VIP keeps started canvases
- Delete: `EncodeManager.cancel(canvasId)` before deleting files; encoder publishes output only if the canvas still exists

**Tests:** DailyLedgerTest idempotency rows; instrumented `EncodeCancelTest`.
Effort: human ~4h / CC ~20min

---

## T10: Canvas renderer performance
Labels: P2, renderer

**Why:** finding 13 (15k cells in one Compose Canvas).

**Scope:** visible-cell culling, one cached gem sprite per palette color, baked full-canvas bitmap below the number zoom threshold, updated incrementally per placement.
Design review additions (specs in `docs/design-system.md` → Play canvas, Palette tray):
- ~~Two-tone selected-color ring (D17)~~: superseded by the stronger own tint, built in #18; the ring is a player option in #19
- Raised placed gems from 8dp (1dp edges, baked into the per-color sprite) (D18)
- Gesture rules: start cell decides paint vs pan, silent skip during drags, two fingers navigate, double-tap zoom, long-press selects color (D23)
- Find button at ≤20 cells left (D24); auto-select first/next color, restore view state on resume (D25)
- Pure `shared/game` `nextRemainingCell(grid, placed, slot, after)` shared by Find and the TalkBack actions; "Place next gem" uses the normal placement rule (eng re-check)
- Tray font scaling up to 72dp troughs (D20); 3 one-time coach hints (D12)

**Done when:** a Big canvas pans/zooms smoothly (~60 fps) on the low-end Test Lab device; the selected-color tint and raised edges are visible on white and skin-tone fixtures; instrumented tests cover the gesture table.
Effort: human ~1d / CC ~30min

---

## T11: Firebase Test Lab encoder smoke job
Labels: P2, ci

**Why:** D7 (encoders differ by chip family).

**Scope:** GitHub Actions runs `gcloud firebase test android run` with `TimelapseEncoderSmokeTest` + `EncodeCancelTest` on Pixel/Tensor, Samsung (Exynos or Snapdragon), low-end MediaTek for every release-candidate AAB. Assert a playable MP4, 1 track, 14-16 s, target or fallback size.
Effort: human ~4h / CC ~20min

---

## T12: Crashlytics always on + non-fatals + encode timing
Labels: P2, telemetry

**Why:** D17, finding 16, CEO OBS-1.

**Scope:** Crashlytics enabled for everyone (crash + non-fatal diagnostics only, no user IDs or photo data); non-fatals for converter, encoder (codec name, target, stage, error), billing, ML Kit init, save-load refusals; encode wall time as a custom key.
**Done when:** a forced crash and a forced encoder non-fatal show up from the internal track.
Effort: human ~2h / CC ~10min

---

## T13: App shell and non-canvas screens
Labels: P1, ui

**Why:** design review D3, D4, D5, D9, D10, D13, D14, D26, D27. Specs and exact copy: `docs/design-system.md` (Navigation map, Gallery tile, Photo flow states, Preview + Start, Completion, Settings).

**Scope**
- Navigation map: UMP → Gallery; Settings icon; "VIP" text action from the 2nd session; Done tile → completion still (Share, Done, "Play again"); tile ⋮ menu (Rename / Delete / Start over) with confirm copy
- Gallery: starter picture as the first Pictures tile; locked label as a vector icon + 13sp bone; damaged "Can't open" tile + sheet; My Photos empty state; FAB on both tabs with 88dp padding; allowance note
- Photo flow: crop with 1:1/3:4/4:3 chips and orientation default; decode error, face check, "Setting the gems…", converter error, faces-disabled "Choose another photo"
- Preview allowance line (5 states)
- Analytics `size` gains `starter`, so the starter never counts as Quick (eng re-check)
- Settings: Manage subscription, conditional Privacy choices, "Delete all photo canvases" + confirm (cancels running encodes first)

**Done when:** every row of those tables is walked on a device; a fresh install shows the starter tile first and reaches completion in about 5 minutes.
Effort: human ~3d / CC ~1.5h

---

## T14: Accessibility and large screens
Labels: P2, a11y

**Why:** design review D15, D16, D19, D20, D21, D22. Specs: `docs/design-system.md` → Accessibility, Colors (valid grounds), Layout (Large screens), Motion.

**Scope**
- Announcements via a Compose live region (`LiveRegionMode.Polite`), not the deprecated `announceForAccessibility` (eng re-check)
- TalkBack: canvas node with 5 custom actions (Place next gem, Find next gem, Zoom in, Zoom out, Whole picture); "Not color 7" and "Color 7 done. Color 8 selected." announcements; labeled troughs, tiles (⋮ actions as custom actions), icon buttons; sheet focus handling
- Font scale to 200%: tray and top-bar rules; no fixed heights in sheets, paywall rows or size options
- Reduced motion: nudge → blink, slides → 150 ms fades, instant lift, static Find ring
- Contrast: enforce the valid-grounds table; 48 dp targets incl. text buttons
- Large screens: <600 / ≥600 / ≥840 dp layouts; canvas tray rail in landscape at ≥600 dp; state survives rotation and fold

**Done when:** TalkBack alone can finish the starter picture; Accessibility Scanner is clean; 200% font on a 360 dp phone has no clipping; tablet and foldable emulators pass in both orientations.
Effort: human ~2d / CC ~1h

---

## T15: Game core, play canvas and save wiring
Labels: P1, game

**Why:** plan step S6 ("gameplay UI + persistence wiring", eng parallelization table), MVP gameplay scope, eng D4/D8, design D9/D23/D25. No issue owned it: #10 adds renderer performance and the full gesture set on top of this, and #13 builds the gallery, navigation and completion screens around it. This is the step that gets to a playable build (the closed-test clock starts there).

**Scope**
- `shared/game` (pure): `CanvasState` built from a `Pattern` + the ordered placement log. It keeps the in-memory placed bitset and the remaining count per color. `place(cell, selectedSlot)` accepts only an unplaced cell of the selected color. Anything else (wrong color, already placed, out of range) is rejected with **no state change** (eng D4), so the log stays an ordered list of unique cell indices. Restoring a log replays it through the same rule.
- Events from placements: gem placed, color finished, picture finished.
- Selection (design D25): first unfinished color in tray order on open, next unfinished color auto-selected when one finishes.
- Play screen (single Compose `Canvas`, never one composable per cell), per the zoom table in `docs/design-system.md` → Play canvas: pale tints, placed gems, numbers from 16dp, placement only from 16dp, tap-to-zoom (24dp cells) below 16dp, pinch/pan, min zoom = fit, max = 48dp cells
- Palette tray: troughs with number + remaining count, selected lift + lamp outline, finished flap moved to the end, auto-scroll to the selected trough
- Wrong-color tap feedback: soft haptic + 2dp trough nudge over 150ms, nothing else
- Haptics and sounds per the Haptics and sound table (the Settings toggles come with #13 and default to on)
- Save wiring on a background dispatcher, one writer per canvas: `SaveScheduler` (1 s / 5 s) → `CanvasRepository.writeSave`. Immediate write on Activity onStop and on picture finished. View state written on onStop and restored on open.
- "Progress isn't saving: phone storage is full." under the top bar while `WriteFailed(diskFull = true)`, cleared by the next successful write (design D9)
- Open uses `CanvasRepository.load`: `Loaded` and `RecoveredFromLastGood` open the canvas; `Damaged` doesn't (the gallery tile and sheet are #13)
- `CanvasRepository.sweep()` once at app start
- Canvas, zoom and selection survive rotation and fold changes
- Temporary entry point until #13: the spike's preview gets a "Play" action that creates a photo canvas (`GridCodec.encode` → `CanvasRepository.create`) and opens it

**Out of scope:** visible-cell culling, sprites, baked bitmap, selected-color ring, raised edges, drag-paint/long-press/double-tap gesture table, Find, tray font scaling, coach hints (#10); gallery, navigation, completion screen, starter picture (#13); time-lapse (#4); TalkBack actions (#14).

**Tests:** `CanvasStateTest` (wrong color, already placed and out of range leave state and log unchanged; log order; color and picture finished; restore replay; selection rules); save loop with a fake clock (idle write, 5 s max, onStop, completion, disk-full line set and cleared); instrumented kill-and-resume (place, kill the process, reopen: at most 5 s of placements lost, view state restored).

**Done when:** a photo made in the spike flow can be played to the end on a device, survives a process kill with at most 5 s lost, and reopens at the same zoom, pan and color.
Effort: human ~3d / CC ~1.5h

---

## T16: Let players choose how the selected color is highlighted
Labels: P2, ui, renderer

**Why:** while playing #18 on the emulator, the founder replaced the design D17 outline ring with a stronger own tint for the remaining cells of the selected color (2026-10-13, recorded in `docs/design-system.md` → Play canvas and its decision log). The tint is weak for light colors (cream, skin tones), where the ring or dimming reads better, so the player should be able to choose.

**Scope**
- A Settings row "Selected color" (in the Settings screen from #13) with three options:
  - **Stronger tint** (default): selected cells use their gem color mixed 40% toward bone, number in on-bone or bone, whichever reads better.
  - **Outline**: the D17 two-tone ring (1.5dp on-bone outer + 1dp lamp inner), from 8dp cells up.
  - **Dim the rest**: selected cells keep the pale tint; every other unplaced cell darkens 35% toward felt-deep.
- Stored with the other settings (no photo data, so it may be backed up); applied live without reopening the canvas.
- The canvas renderer takes the style as a parameter, so #10's sprite and baked-bitmap work covers all three.
- Copy and the option list go into `docs/design-system.md` (Settings, Play canvas) before building.

**Done when:** each style is selectable in Settings, the canvas switches immediately, the choice survives an app restart, and all three are checked on a dark, a cream and a skin-tone color.

Depends on #13 (Settings screen) and #18 (play canvas). Effort: human ~4h / CC ~20min

---

## T17: Placement tools beyond single taps (fill, bigger brush)
Labels: P2, game, ui

**Why:** founder feedback while playing #18 on the emulator (2026-10-13): a Quick canvas is 3,136 single taps and a Big one ~15k, so players will want a faster way to fill large areas, such as a "fill" or a bigger crayon. Which tools to offer isn't decided yet, so this issue starts with design.

**Step 1: decide the tool set (design, before code)**
- Candidates to compare:
  - **Bigger brush**: one tap or drag places every unplaced cell of the selected color inside a 2×2 or 3×3 area.
  - **Region fill**: one tap fills the connected region of the selected color around the tapped cell.
  - **Color fill**: places every remaining cell of the selected color, possibly unlocked only below N cells left.
- Questions to answer:
  - Do tools keep the game satisfying (the sorting-tray feel, haptic per gem), or make it trivial?
  - Free, limited per canvas, earned by a rewarded ad, or VIP? This has to fit the ad rules and tiers in `docs/designs/gemmory.md`.
  - Where does the tool picker live without adding chrome to the canvas (design system: no other chrome on the play canvas)?
  - Haptic and sound for a multi-gem placement (one tick per gem would buzz).
- Record the chosen tools, copy and states in `docs/design-system.md` (Play canvas, Palette tray) and the decision in its log.

**Rules every tool must keep** (AGENTS.md, eng D4):
- A tool is a batch of ordinary placements: each cell goes through `CanvasState.place(cell, selectedSlot)`, so wrong-color and already-placed cells are skipped, and nothing else changes state.
- The save log stays an ordered list of placed cell indices, in a defined order (for example, distance from the tapped cell), so the time-lapse replays the fill as a sweep.
- The save loop treats a batch like any placement (1 s / 5 s cadence); picture finished still writes immediately.

**Done when:** the tool set is decided and specified, and the chosen tools work on a Big canvas, with log order, time-lapse replay and kill-and-resume covered by tests.

Builds on #18 (game core and play canvas) and #10 (gestures). Effort: design ~2h; build depends on the chosen tools.

---

## T18: More colors: a color-count choice for photo canvases, richer bundled palettes
Labels: P2, converter, ui

**Why:** founder feedback (2026-10-15, during #19): photo canvases are capped at 16 / 24 / 36 colors (Quick / Medium / Big, `CanvasSize.paletteCap`), the starter picture has 8 and the six Big bundled pictures 36. Players should be able to pick more colors for their own photos, and the bundled pictures should use more distinct colors so they look good, instead of sitting at the same fixed caps.

**Step 1: decide (design, before code)**
- Photo canvases: a color-count choice in the photo flow (on the size picker or Preview), for example "Fewer / Standard / More colors" or a slider with a live Preview, per size. Which ranges per size (for example Big up to 48-60)? Free, or VIP for the top range (it has to fit the tiers in `docs/designs/gemmory.md`)?
- Bundled pictures: re-convert them with more colors (each picture gets its own count, chosen by eye), or keep the frozen grids and add new pictures.
- Record the copy, states and decision in `docs/design-system.md` (Size picker / Preview, decisions log) and `docs/designs/gemmory.md`.

**Things to check**
- Converter: k-means with a higher cap, while keeping colors distinct (a minimum Lab distance between palette colors, so two near-identical shades don't become two numbers) and finish times reasonable.
- Tray and canvas at more colors: tray scrolling and the large-screen rail, 2-digit numbers on 16dp cells (the number font is 0.46 × cell), sprite atlas size (one sprite per color × kind per zoom bucket, #10/#19), frame time (CanvasFrameTimingTest, @PerfTest), and the tint/dim readability rules (Colors, #19).
- The save format: `GridCodec` stores the palette size as u16 and cells by slot, so check the cell encoding for more than 255 colors only if that range is ever allowed.
- Re-converted bundled pictures change their pinned CRCs and the starter's pinned grid CRC; the converter goldens change only if the converter's defaults change. Replace them on purpose in the same PR and note why.
- Existing saves stay valid: a canvas keeps the palette it was made with.

**Done when:** the player can choose a color count for a photo canvas (within the decided ranges), the bundled pictures ship with their new palettes and updated pinned CRCs, and tray, canvas, perf and accessibility checks pass on a high-color Big canvas.

Builds on #2 (converter), #13 (photo flow) and #10/#19 (renderer). Effort: design ~1h; build human ~1-2d / CC ~1h.

---

## T19: More bundled pictures: a Quick/Medium ramp after the starter, ~30 in total
Labels: P1, game

**Why:** the gallery has the 24×32 starter (768 gems, ~5 min) and then six Big pictures (122×122, ~15k gems, 36 colors) with nothing in between (`bundled/BundledPictures.kt`). A new player who finishes the starter faces a ~20× jump, and the closed-test metric "≥50% finish at least 1 Quick canvas" (`docs/designs/gemmory.md` → Success Criteria) can only be met with photo canvases, which are 1 free a day. The plan calls for about 30 bundled pictures; there are 7.

**Scope**
- A difficulty ramp after the starter: several Quick (~3k gems, 16 colors) and Medium (~8k, 24 colors) pictures before the Big ones, in all three ratios (1:1, 3:4, 4:3).
- Grow the catalog toward ~30 pictures, ~10 of them to be locked by #32 (prefer licensed stock for those, per CONTENT-1: AI-art copyright is unsettled).
- Gallery order: starter, then Quick, Medium, Big (or decide a different order in `docs/design-system.md` → Gallery first).
- Every picture goes through `./gradlew :shared:freezeBundledGrid`, with a pinned CRC in `BundledPictures` and a provenance row in `tools/bundled/PROVENANCE.md` (source, license or model, terms URL, date).
- Check APK size growth and the gallery's first-load time with ~30 summaries (each one decodes its grid).

**Done when:** at least 4 Quick and 4 Medium pictures ship before the Big ones, the catalog is at ~30 with provenance for each, `BundledPicturesTest` and `CanvasStoreTest` cover the new list, and the gallery still opens fast on the emulator.

Must land before the closed-test clock starts (content ready by week 2-3, `docs/designs/gemmory.md`). Effort: content sourcing ~1d; code human ~2h / CC ~15min.


---

## T20: Lock ~10 bundled pictures behind a rewarded video or VIP
Labels: P1, ads, billing

**Why:** the paywall's first VIP perk is "Every picture unlocked" and the purchase line says "You're VIP. Every picture is unlocked." (`billing/BillingCopy.kt`), but no bundled picture has `locked = true`, so the perk sells nothing. That is a Play subscriptions-policy risk (benefits must be accurate), and the locked-picture rewarded video, half of the planned rewarded-ad revenue, never runs.

The unlock path already exists: `isLocked()` in `ads/Monetization.kt` (VIP bypass, permanent unlock after a rewarded video, a started picture stays open), the gallery's locked label (D14) and the rewarded sheet with grace and no-fill handling (#7).

**Scope**
- Mark ~10 of the ~30 bundled pictures `locked` (tier table in `docs/designs/gemmory.md`: ~20 free, ~10 locked, unlock with one rewarded ad each, VIP unlocks all). Never the starter, and keep enough free Quick/Medium pictures that a free player never runs out of something to play.
- Check the whole flow end to end with real locked pictures: locked label and TalkBack text, rewarded video → "Unlocked" → canvas opens, offline and no-fill copy, the daily grace unlock, VIP showing all unlocked, VIP lapsing (unlocked-by-video ones stay unlocked, VIP-only ones lock again unless started).
- Decide whether unlocks survive a reinstall: today they live under `ledger/`, which is excluded from backup. Record the answer in the design doc.
- Keep the paywall copy true: if VIP doesn't unlock something, the perk text changes.

**Done when:** ~10 pictures are locked, each opens through a rewarded video or VIP, the flow is covered by instrumented tests, and the paywall perks match what VIP gives.

Depends on #31 (needs more pictures first). Effort: human ~3h / CC ~20min.


---

## T21: Daily play streak
Labels: P2, game, ui

**Why:** early-production targets are day-1 retention ≥35% and day-7 ≥12% (`docs/designs/gemmory.md` → Success Criteria). Nothing gives a player a reason to open the app tomorrow. A streak is a cheap, backend-free daily pull.

**Step 1: decide (design, before code)**
- What counts as a day: at least one gem placed (proposed), local midnight like the allowance.
- Where it shows: the gallery (a small chip near the tabs?) and the completion screen. Never on the play canvas (no chrome there).
- Forgiveness: a missed day resets, or one "streak freeze" a week, or one restored by a rewarded video / always for VIP (has to fit the ad rules and tiers).
- Rewards, if any: milestone days (3, 7, 30) could grant a fill power-up (#20) or a picture unlock (#32). Keep it calm: no guilt copy or loss-aversion pressure (the app sells relaxation).
- Record copy, states, TalkBack text and the decision in `docs/design-system.md`.

**Rules**
- Stored on the phone. Decide whether it lives in `ledger/` (not backed up, like the allowance) or may be backed up (it's no photo data). A clock change can bend it: accepted risk, same as the allowance.
- Analytics event for streak length if Firebase lands (#12).

**Done when:** the streak counts days played, shows where specified, resets per the decided rule, survives app restarts, and is covered by unit tests (pure counter in `commonMain`, day boundaries, clock going backwards).

Effort: design ~1h; build human ~4h / CC ~20min.


---

## T22: Today's picture
Labels: P2, game, ui

**Why:** a "Today's picture" gives players something new every day, a reason to come back (day-1/day-7 retention targets, `docs/designs/gemmory.md`) and a soft taste of locked content. It works offline with no backend.

**Step 1: decide (design, before code)**
- Pick rule: one locked bundled picture a day chosen on the phone (for example, a date-seeded rotation over pictures the player hasn't unlocked or started), so it needs no server.
- What "today's" means: free to start today only, and once started it stays open forever (like `isLocked()`'s started rule), or unlocked permanently.
- Where it shows: a highlighted first tile or a card at the top of Pictures; copy, TalkBack label, what VIP sees (everything is unlocked for VIP, so maybe "Today's pick" with no unlock).
- Interaction with #33 (today's picture as the streak's daily goal?).
- Record copy, states and the decision in `docs/design-system.md` and `docs/designs/gemmory.md`.

**Rules**
- Picking runs in pure Kotlin (`commonMain`), testable with a fixed date.
- Local midnight like the allowance; changing the clock can show another picture: accepted risk.
- Needs enough locked pictures to rotate through.

**Done when:** a different picture is offered each day, starting it works without an ad, the rule is unit-tested across day boundaries, and the gallery shows it as specified.

Depends on #31 and #32. Effort: design ~1h; build human ~4h / CC ~20min.


---

## T23: Progress milestones and a peek at the finished picture
Labels: P2, game, ui

**Why:** on a Big canvas (~15k gems, hours of play) the only reward moments are a finished color and the finished picture. Players need small wins along the way, and a reason to keep going when the end is far.

**Candidates (decide in design first)**
- **Progress milestones:** a short sparkle on the top bar's progress at 25 / 50 / 75%, with a soft chime and haptic (both follow their Settings toggles; reduced motion: no sparkle, the chime stays).
- **Peek:** press and hold (or a button in the top bar) to see the finished picture, fading back on release. Motivation, and helps players see where a color goes.
- Possibly a "best session" or "gems today" line on the completion screen.

**Rules**
- The design system allows no other chrome on the play canvas: a peek control must live in the existing top bar or be a gesture that doesn't conflict with D23 (paint vs pan) and the pinch/zoom table.
- Milestones are derived from the placement count, so they change nothing in the save log or the time-lapse.
- TalkBack: milestones announced through the existing polite live region; peek reachable as a canvas action.
- Record motion, copy and the decision in `docs/design-system.md` (Play canvas, Motion).

**Done when:** the chosen milestones and peek work on a Big canvas, respect reduced motion and the Sound/Haptics toggles, and have unit tests for the milestone thresholds (including a canvas reopened past a milestone, which must not replay it).

Effort: design ~1h; build human ~4h / CC ~20min.


---

## T24: Share a progress time-lapse before the picture is finished
Labels: P2, timelapse, ui

**Why:** sharing is the growth loop (`utm_source=share`, ≥10% of finished photo canvases shared), but today a time-lapse exists only after a picture is finished, which on a Big canvas takes hours or days. Most players stop before that and never share. A progress share lets them post "look what I'm making" much earlier.

**Step 1: decide (design, before code)**
- Entry point: the top bar overflow on the play canvas (no new chrome on the canvas), and/or the gallery tile's long-press menu.
- Content: the same 9:16 time-lapse (#4) replaying the log so far and ending on the unfinished canvas, with a "47% done" line in the band; or a still before/after.
- Minimum progress before it's offered (for example 10%), so a blank canvas isn't shared.
- Watermark, title band and share text as for finished time-lapses; VIP without watermark.
- Ads: never before or during share (ad rules), and a progress share doesn't count as a finished canvas for the interstitial rule.
- Record copy, states and the decision in `docs/design-system.md` (Share) and `docs/designs/gemmory.md`.

**Rules**
- Reuses `EncodeManager` and the fallback chain; the encode is cancelled when the player places more, deletes or starts over (eng E4d).
- Encoded files stay in `cacheDir/timelapse/` (FileProvider `share_paths`); photo data never leaves the phone except through the share sheet the player opens.
- Analytics `share_tapped` / `share_completed` get a `progress` vs `finished` parameter once #12 lands.

**Done when:** a player can share a progress time-lapse from a half-done canvas, it ends on the current state with its percentage, and replay order matches the save log (unit test on the frame source).

Builds on #4 (time-lapse) and #18 (play canvas). Effort: design ~1h; build human ~4h / CC ~30min.

---

## T25: Inventory: earn, buy and keep bonus canvases, fills and other items
Labels: P2, ads, billing, game, ui

**Why:** founder request (2026-10-15). Today every extra is a one-off: a rewarded video buys one more photo canvas today (`DailyLedger.grantReward`), and #32 will add one video per locked picture. There is no place where a player holds things they earned or bought, so there is nothing to save up, nothing to come back for, and no way to buy a small boost without the full VIP. An inventory turns ads and small purchases into items players keep and spend when they want.

**Step 1: decide (design, before code)**
- **What items exist.** Candidates:
  - Bonus photo canvases: kept instead of expiring at midnight.
  - Fills: the #20 placement tools (region fill, color fill, bigger brush) as consumable charges.
  - Bigger canvases: a size above Big, or unlocking Big for a photo when the face rule or a tier limits it (check against the size rules in `docs/designs/gemmory.md` first).
  - Others to weigh: hints (Find), picture unlocks (#32), palette/tray cosmetics.
- **How players get them:** a rewarded video (cap? D15 grace), a one-time in-app pack (new Play products, consumable), daily login/streak (#33), milestones (#35), included with VIP (an amount per week, or unlimited).
- **Where it lives:** an inventory screen or sheet, and where its items are spent (Preview, the play canvas tray without new chrome on the canvas, the gallery). Copy, states and empty state go into `docs/design-system.md`, with a navigation-map entry.
- **Economy check:** how this interacts with the free daily canvas, VIP perks (the paywall copy must stay true, as in #32) and the ad caps and "no ads on the play canvas" rule.

**Rules**
- Stored on the device only, like the ledger (excluded from cloud backup and device transfer unless decided otherwise and recorded), with a codec and corruption handling like `FileStateStore`. A pure-Kotlin policy in commonMain with an injected clock, as with DailyLedger.
- Consumable purchases go through `BillingRepository` (acknowledge or consume, pending handling, restore rules); Play policy on consumables and loot boxes respected (no random rewards).
- Spending is idempotent (a double tap spends one item), like `consume(draftId)`.
- Any fill item keeps the #20 rules: a batch of ordinary placements, and the save log stays an ordered list of placed cell indices.

**Done when:** the item set, sources, prices and UI are decided and recorded; the inventory stores, earns and spends items with unit tests (ledger-style rows: earn, spend, double spend, midnight, corrupt file) and instrumented tests for the earn → hold → spend flow; and paywall/VIP copy matches what VIP gives.

Related: #20 (fill tools), #32 (locked pictures), #33 (streak), #35 (milestones), #8 (billing). Effort: design ~half a day; build depends on the chosen items.

---

## T26: Remove the hidden Settings telemetry check
Labels: P2, telemetry

**Why:** #12 added a hidden trigger so a Play-installed release build could prove that a forced crash and a forced encoder non-fatal reach the Crashlytics Console. It isn't in docs/design-system.md, and any player who taps Version 7 times can crash the app. Founder call during #12: ship it for the check, then remove it.

**Scope**
- Remove `telemetry/TelemetryCheck.kt` (TelemetryCheck, TapStreak, TelemetryCheckCrash) and its use in `GemmoryApp` (`TelemetryCheck.wrap` around `MediaCodecEncoder`)
- Remove the Version row's `pointerInput` tap streak and the "Telemetry check" ConfirmSheet from `settings/SettingsScreen.kt`
- Remove the tests that cover it (`EncodeManagerTest.theTelemetryCheckFailsOneFullHdAttemptAtConfigureThenDropsTo720`, `TelemetryTest.sevenQuickTapsOpenTheCheckAndSlowTapsStartOver`)

**Done when:** 7 taps on Version do nothing on a release build, and unit + instrumented tests are green.
Effort: human ~20 min / CC ~5 min

---

## T27: Remote pictures: add new pictures without a Play release, fetched once a day from GitHub
Labels: P2, persistence, game

**Why:** every new picture today means a new AAB and a Play review (`bundled/BundledPictures.kt`, frozen grids with pinned CRCs). The founder wants to publish pictures on their own schedule: the app checks once a day for new ones and downloads them. This also gives #34 (Today's picture) and #32 (locked pictures) a steady supply without app updates.

**Step 1: decide (before code)**
- **Hosting on GitHub.** Recommended: a separate public repo (e.g. `BrewingBytes/Gemmory-pictures`) served from `raw.githubusercontent.com` or GitHub Pages. It holds a `catalog.json` plus one grid file per picture. A separate repo keeps content pushes out of the app repo's protected `main` and CI.
- **What is downloaded: data, never code.** Only finished grids in the existing GMRY grid format, made by `./gradlew :shared:freezeBundledGrid`. Play's Device and Network Abuse policy forbids downloading executable code outside Play; data files are allowed.
- **Integrity.** Sign `catalog.json` (Ed25519), with the public key in the app and the private key outside every repo (never committed). For each picture the catalog lists id, title, ratio, size (quick/medium/big), width × height, color count, grid URL, SHA-256 and grid CRC, `locked`, `minAppVersion` and `addedAt`. The app refuses anything whose signature, hash or CRC doesn't match.
- **Grids are immutable.** Saves are bound to the grid CRC (GMRY, eng D-ledger), so a published grid never changes. A fix is a new id; the old one can be withdrawn.
- **Withdraw / kill switch.** A picture removed from the catalog disappears from the gallery unless the player has started it (same rule as `isLocked()`'s started rule).
- **Gallery.** Where remote pictures sit relative to bundled ones, and whether there's a "New" mark. Copy, TalkBack label and states go in `docs/design-system.md` → Gallery first. Ask the founder before any departure.
- **Backup.** Remote grids can be downloaded again. Choose whether to exclude `canvases/remote/*/grid` from backup and keep only progress, and what happens when a restored save's picture is gone from the catalog.

**Scope**
- **Fetch once a day:** when the app starts, if the last successful check was more than 24 h ago (or a WorkManager periodic job with a network constraint). Use conditional GET (ETag / If-Modified-Since). Never during play and no UI blocking; a failed check retries the next day.
- **Offline:** the cached catalog and already-downloaded pictures keep working offline. New pictures appear only after a successful check, and grids download only for pictures in the catalog.
- **Pure `commonMain`:** catalog parsing, signature + hash + CRC checks, `minAppVersion` filtering and the merge with bundled pictures, unit-tested. The network and files stay in `:androidApp` (no new HTTP dependency unless one is already on the classpath).
- **Storage:** e.g. `files/canvases/remote/<id>/`, with progress saved exactly like bundled pictures (`CanvasRepository`, `CanvasRef`). Update `StorageLayout` and `BackupRulesTest` to match the backup decision.
- **Telemetry:** `canvas_started`/`canvas_finished` get `source = remote`; catalog fetch and verify failures go to Crashlytics as non-fatals (no URLs with user data, since none exist).
- **Publishing tool:** a script or Gradle task that freezes the grid, computes the CRC and SHA-256, adds the catalog entry, signs the catalog and checks it against the app's verifier. Add a provenance row per picture (source, license or model, terms URL, date, like `tools/bundled/PROVENANCE.md`) and a content check: nothing above the current IARC rating (PEGI 3 / Everyone), nothing aimed at children (AGENTS.md: adults only).
- **Privacy and Play:**
  - GitHub (Microsoft) receives the device's IP address and user agent on each check, so update `docs/privacy-policy.md` ("Who receives data" currently says only Google) and `docs/play-compliance.md`.
  - Re-check the Data safety answers, though no new data type is expected: no identifiers, cookies or user data in the request.
  - Photos still never leave the phone.

**Done when:** a picture pushed to the content repo shows up in the gallery within a day without an app update, plays and saves like a bundled picture, and survives offline. A tampered catalog or grid is refused. The rules are unit-tested (signature, hash, CRC, minAppVersion, withdraw, 24 h cadence with a fixed clock), and the privacy policy names GitHub.

Related: #31 (bundled ramp stays in the APK so a new install has pictures offline), #32 (`locked` per remote picture), #34 (Today's picture can draw from remote pictures). Effort: design ~1h; build human ~1.5d / CC ~45min; publishing tool ~2h.

## T28: Play Games Services achievements (no leaderboards)
Labels: P2, game, ui

**Why:** Play Console suggests adding Play Games Services (card "Add Play Games Services to your game", 2026-10-16). Achievements show in the Play Games app and on the player's Play profile. That's a light retention and discovery lever that fits a calm solo game, and sign-in v2 is automatic, so it adds no login screen. Leaderboards and friends don't fit the product (no competitive scores), so they stay out.

**Step 1: decide (before code)**
- **Achievement list (~8-12).** For example: first canvas finished, first Quick / Medium / Big finished, first photo canvas finished, first time-lapse shared, 5 / 25 canvases finished, a 7-day streak (#33), every milestone of one picture (#35). Each needs a name, a description, whether it's hidden or incremental, and an icon (512×512, `docs/design-system.md` palette). The founder approves the list and copy first.
- **Where it shows.** A "Play Games achievements" row in Settings that opens Play Games' own achievements screen. No in-canvas UI: the canvas stays ad-free and quiet. Toasts use Play Games' own pop-up, or none (decide). Copy, TalkBack labels and states go in `docs/design-system.md` first; ask the founder before any departure.
- **Unlock rules.** Achievements unlock from game events only (completion, share, streak), never from purchases or ads. They're idempotent and work offline: the SDK queues unlocks.

**Scope**
- Play Console: create the Play Games Services project for `com.brewingbytes.gemmory`, link both OAuth clients (upload and app-signing SHA-1), add the achievements and the game ID resource. Testers go on the PGS testers list during closed testing.
- `:androidApp`: the `play-services-games-v2` SDK, `PlayGamesSdk.initialize` in `GemmoryApp`, automatic sign-in only (no sign-in button; if the player isn't signed in, unlocks are skipped silently), and an `Achievements` interface with a no-op in debug unless enabled with a `-P` switch, like `gemmory.fakeBilling`.
- Pure `commonMain`: which game events unlock which achievement, unit-tested with a fixed event sequence. `:androidApp` only maps them to SDK calls.
- Release build: R8 keep rules if the SDK needs them, and an R8 release smoke test on an emulator (dependency change).
- **Privacy and Play:**
  - Play Games collects the Play Games player ID and game activity, so update the "Who receives data" section of `docs/privacy-policy.md` and re-check the Data safety answers in `docs/play-compliance.md` (likely: App activity → Other actions, and a user ID).
  - Photos still never leave the phone: achievements carry only event ids.
  - Adults-only and target-audience answers are unchanged.

**Done when:** a closed-test player is signed in to Play Games automatically, unlocks the agreed achievements by playing, and sees them in Play Games. Nothing breaks when Play Games is missing or the player opted out. The unlock rules are unit-tested, the R8 release build is smoke-tested, and the privacy policy and Data safety answers cover Play Games.

Depends on #33 (daily streak) and #35 (milestones) for the achievements built on them; the first-finish ones can ship without them. Not before the closed test (founder, 2026-10-16). Effort: design ~1h (list + icons); build human ~1d / CC ~30min; Console setup ~1h.

## T29: 200%-font walkthrough fails at photo completion (ScreenSizesWalkthrough)
Labels: bug, P2

**Why:** `ScreenSizesWalkthrough.twoHundredPercentFontOnA360dpPhone` (`@DeviceCheck`) fails every time on `main` and on the #32 branch: 4 of 4 runs on gemmory_pixel at emulator load ~3-4, so it's not load (found during #32). That leaves the 200%-font photo-completion flow unverified. Either the app doesn't show a finished photo canvas reliably at 200% font, or the test waits too little or acts too early.

**What fails** (`androidTest/.../app/ScreenSizesWalkthrough.kt`, 360dp phone, `font_scale 2.0`):
- Line 210: after the last gem of the photo canvas `walk-1`, waiting 10 s for the "Finished picture" node (1 run on `main`).
- Line 212: after Done → My Photos → tap `tile-photo-walk-1`, waiting 10 s for `completion-still` (1 run on `main`, 2 on the #32 branch).
- In a #32-branch run, the `font2-completion` shot taken just before the failure shows the Finished screen correctly. The logcat said "interstitial blocked: TOO_SOON", so it isn't an interstitial covering the screen. A `font2-completion-still` shot from an earlier run exists on the device, so this step has passed before.

**Scope**
- Reproduce with a screenshot and a UI dump at the moment of the timeout, and find out whether it's the app or the test.
- If it's the app (a finished photo canvas doesn't reopen to the completion still, or the finished picture doesn't load, at 200% font): fix it, and add a non-`@DeviceCheck` test that would catch it.
- If it's the test: wait on the right condition (no fixed short timeouts for real-time picture loads) and keep the walkthrough's shots.
- Remove the 2026-10-16 (#32) entry about this failure from `docs/agents/lessons.md` once it passes.

**Done when:** the case passes 3 runs in a row on a quiet emulator with `-e deviceChecks true`, and the cause is written in the PR.

Out of scope: any other ScreenSizesWalkthrough layout changes. Effort: human ~1-2h / CC ~20min.

## T30: Prove 60 fps canvas panning on the low-end Test Lab phone
Labels: P2, renderer, ci

**Why:** #10's "Done when" asks for a Big canvas that pans and zooms at ~60 fps on the low-end Test Lab phone, and the design doc's T10 verify line says the same. #11 left it out on purpose (founder, 2026-10-16): `CanvasFrameTimingTest` (`@PerfTest`) only logs frame times and asserts nothing, so in CI it could never fail. Only emulator numbers exist today (1.4-2.2% missed frames on Big, design doc → #10 note).

**Scope**
- Pick the pass line for a low-end phone (for example: missed frames under 5% per phase, or a p90 frame time) and turn `CanvasFrameTimingTest`'s log into an assertion behind that line, or add a Macrobenchmark if that measures it better.
- Run it on the low-end MediaTek phone from `.github/workflows/testlab.yml`: either a second `--device` matrix in the same job or a by-hand run (Actions → Test Lab). Each extra phone is one of Spark's 5 physical-device runs a day.
- Record the measured numbers in the design doc next to the #10 note.

**Done when:** the low-end Test Lab phone has a recorded frame-timing result for a Big canvas, and a regression past the agreed line fails a run.

Out of scope: renderer changes, unless the numbers miss the line. Effort: human ~3h / CC ~30min.

## T31: Move the encoder smoke job off Firebase Test Lab before its 2027-09-30 shutdown
Labels: P2, ci

**Why:** Firebase Test Lab is deprecated and shuts down on 2027-09-30 (Firebase docs, checked 2026-10-16). The encoder smoke job from #11 (`.github/workflows/testlab.yml`, D7) stops working then. Google's replacement is the Developer Device Platform on Google Cloud (`gcloud beta device-run sessions submit instrumentation`), in Preview when #11 was built, with no published pricing or free tier.

**Scope**
- Re-check the Developer Device Platform once it's GA: pricing, a free tier, physical Pixel/Samsung/MediaTek phones, and whether the Workload Identity setup from `docs/play-compliance.md` carries over.
- Move the job to `gcloud … device-run` with the same phones (or the nearest ones) and the same tests, results link and video artifact.
- Update README → "Encoder smoke on Firebase Test Lab", `docs/play-compliance.md` → "Test Lab CI credentials" and D7's note in the design doc.

**Done when:** a release-candidate tag runs the encoder smoke on three chip families on the new service, before 2027-09-30. If it costs money, the founder approves the cost first.

Not before the service is GA, and no later than mid-2027. Effort: human ~3h / CC ~30min.

## T32: A bigger celebration when a picture is finished (confetti, fireworks or another idea)
Labels: P2, game, ui

**Why:** finishing a canvas, hours of play on a Big one, is the biggest moment in the game, and today it's the calm "lights down": a dim, one specular sweep, a chime, a 2s hold. The founder wants a bigger celebration on finish (2026-10-16, during #35). The design system currently rules it out ("Don't add confetti, coin bursts, glitter particles…, even for events"; "Motion confirms; it never celebrates loudly"), and the founder has said that rule can change for this.

**Candidates (decide in design first, with HTML mockups like #35)**
- **Gem confetti:** a short burst of square cushion-cut gems in the picture's own palette, falling over the dimmed room (not over the picture). On brand: the picture supplies the color.
- **Fireworks:** two or three bursts behind the picture, in the picture's colors, before the sweep.
- **Gem shower into the picture:** the remaining tray heaps fly up and "settle" into the picture as the sweep passes, then the room dims.
- **Bigger sweep:** keep lights down, but the sweep becomes a slow sparkle pass across every gem (a facet glint per gem, staggered), with a fuller chime.
- **Time-lapse preview:** the 15s build plays muted, small, behind "Finished" before Share appears (it's the thing players share).
- **Haptic crescendo:** a rising vibration pattern under any of the above.

**Rules**
- Reduced motion: no particles or flying gems; the dim and the chime stay (design D21).
- Sound and Haptics follow their Settings toggles. Consider a Settings toggle like #35's Milestones if the effect is big.
- Must not change the save log, the time-lapse or the share frame, and must not delay Share/Done by more than ~1s over today.
- The completion still (a finished picture reopened from the gallery) stays still.
- Ads stay where they are: an interstitial only after leaving the completion screen.
- Record the decision, motion and copy in `docs/design-system.md` (Completion, Motion, Do's and Don'ts, Decisions log), replacing the "no confetti" rule if it changes.

**Done when:** the chosen celebration plays on a fresh finish on Quick, Medium and Big canvases at 60 fps on the emulator, respects reduced motion and the toggles, and has a connected test that the completion still doesn't play it.

Effort: design ~1h; build human ~4h / CC ~30min.

## T33: Gallery: show every picture the player can open first, then the locked ones
Labels: P2, ui

**Why:** the Pictures tab is ordered by size first (#31: starter, every Quick, then Medium, then Big), with the free pictures first *within* each size (#32). So a free player scrolls past locked Quick tiles before reaching the free Medium ones, and past locked Medium tiles before the free Big ones. The founder wants every picture the player can open shown first, then the locked ones (2026-10-16, during #35).

**Scope**
- Order of the Pictures tab: the starter, then every **open** picture (free, unlocked by a video or today's grace, or started), then every **locked** one. Within each group keep the #31 difficulty ramp (Quick, then Medium, then Big).
- "Open" follows the same rule as a tap (`BundledPictures.FREE`, the `unlocks` file, a canvas with a gem placed, and VIP, for which nothing is locked, so VIP sees the plain #31 ramp).
- A picture unlocked by a video moves up into the open group the next time the gallery refreshes. Decide in design whether it moves at once (on return from the reward) or on the next visit, so a tile doesn't jump under the player's finger.
- Update `docs/design-system.md` → Layout (Gallery) and the decisions log, and `docs/designs/gemmory.md` → Locked pictures. Update `BundledPicturesTest` (it checks free pictures are listed first within their size).

**Done when:** a free player sees the starter and the 6 free pictures as the first 7 tiles, then the locked ones by size; a video unlock and VIP order correctly; unit tests cover the order for Free, Free with unlocks, a started locked picture, and VIP.

Out of scope: which pictures are free (#32). Effort: human ~1-2h / CC ~15min.

---

# Round 2: a reason to come back tonight (eng review, 2026-10-19)

Source: `docs/designs/gemmory-round2.md` → Recommended Approach (T35-T44, amended by the CEO review C1-C7, E1-E4, D14-D15) and "Eng Review (Phase 3)" (D1-D13, E1-E9, O1-O3, R1, required repairs). Build order (CEO C1): **1a** = T35, T36, T44; **1b** = T37, T38; **T39** is a launch item beside #74; **wave 2** (T40-T43) runs in parallel with 1b. One PR per issue. Every issue keeps the AGENTS.md rules: photos never leave the phone, no ads on the canvas, adults only, pure `commonMain`, UI follows `docs/design-system.md`.

Shared rules for round 2 (eng): the reminder runs in the app process and reads through `CanvasStore`'s single writer (it never writes a save); every date is the phone's local date from `DayClock`; tests use an injected clock; R1's regression contract applies to every issue that touches its code.

---

## T35: Evening reminder: one opt-in local notification a day, about something real
Labels: P1, game, ui, telemetry

**Why:** nothing reaches a player between evenings (no notification code at all on 0.5.0). The wedge of round 2: one gentle, opt-in reminder at the player's evening time that names something real: tonight's drop, Today's picture, a streak that really breaks at midnight, or a canvas almost finished. Plan: T35 in `docs/designs/gemmory-round2.md`; eng D2, D3, D5, D6, D8, D11, D12.

**Scope**
- **Prompt:** an in-app sheet after the first finished picture, never at first launch: title "Remind me in the evening?", a body naming the reminder time and every reason it can send, "a streak to keep" included (Phase 5 D10), primary "Yes, remind me", text button "Not now" (copy in `docs/design-system.md`, Evening reminder). It shows on the gallery after leaving the completion screen, under the one-interruption rule: at most one of the reminder ask, T39's review sheet and an interstitial per return, in that order. Yes → the system `POST_NOTIFICATIONS` dialog on Android 13+; on 12 and below Yes alone turns reminders on. Not now → asked once more after the 3rd finish, then never. Finishes count from the version that brings T35, so a player who already had finishes is asked at their next one (founder, 2026-10-20). A system denial (or Android's auto-deny or a dismissal, counted the same) → never again. Once reminders are on, one gallery line, once: "Reminders on, around 20:30. Change it in Settings."; nothing after a no (D12). A return that completes a collection or earns an event keepsake shows no ask, review or interstitial (D7). Channel "Evening reminder", default importance.
- **Settings:** a Reminders toggle and a time picker (17:00-22:00, 15-minute steps, default 20:30, copy "We'll remind you around 20:30."). Blocked by the system or channel off → toggle off + "Notifications are off for Gemmory" + Open settings. Turning it on without permission asks (13+) or opens system settings.
- **Scheduling (D8):** `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP)` at the chosen local time, no exact-alarm permission and no new dependency. A receiver does the work with `goAsync` (whole run ≤ 10 s). Rescheduled after every run, on boot, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, any Settings change and every app start (a force-stop clears alarms). Promise: best effort, usually within an hour on Android 12+, no delivery guarantee.
- **Background starts (D11):** the receivers are enabled only while reminders are on. `AdPolicy.onProcessStart` moves to the first foreground of a process, so a receiver-started process never ends a new player's ad-free first session. Billing, AdMob, Play Games and the achievements backfill wait for the first foreground.
- **Consent (D6):** `ConsentManager` is built first in `GemmoryApp.onCreate` and `analytics.consent(consent.consentMode.value)` runs before any component that logs; changes are collected as today.
- **Play-time state (D2):** a new "GMRT" v1 `play` file in each canvas directory: last gem time (millis) and first finished local day. Written on the writer thread with the save and on finish. Start over resets the last gem and keeps the first finished day; delete removes it with the directory. No migration; photo canvases stay out of backup with their directory. Pictures finished before T35 have no date.
- **Late-run rule:** reasons are computed for the scheduled local date. A run more than 2 h after the chosen time, or on a later date, posts nothing and logs `reminder_late` (minutes late).
- **Catalog check (D5):** when the remote check is due, the job starts or joins the gallery's guarded check (one job, `RemotePictures`), fetching the catalog plus today's grid only, cancelled after 8 s. Timeout or offline falls through.
- **Today's picture (D3):** the job makes the day's pick through the same synchronized `TodaysPictures.pick`, with candidates from one shared function moved out of `GalleryViewModel.todayCard`, so the notification and the Today card always name the same picture.
- **Reads (D12):** started canvases' saves + play files through `CanvasStore`; unstarted remote pictures from catalog metadata (no grid decode).
- **Reasons, first that applies wins** (pure `ReminderReasons` in `commonMain`, an ordered list of small predicates):
  1. Tonight's drop: a T36 drop whose `availableFrom` is today, stored and verified, not started (drop copy wins if it's also Today's picture).
  2. Today's picture, not started.
  3. Streak at risk: streak ≥ 2, nothing finished today, and it really breaks at midnight (`DailyStreak.shown(state, today + 1) == 0`).
  4. Almost finished: an unfinished canvas past 50% played in the last 7 days (most recent wins): "You're 82% through Lighthouse"; a photo canvas is only "your photo canvas".
  5. None: nothing is posted.
- **Skip rule:** skipped while the app is in the foreground, or if a gem was placed in the 3 h before the run. A skip suppresses reasons 1, 2 and 4; reason 3 still applies while nothing is finished today.
- **Tap:** drop or Today → that picture's canvas, no video, as the Today card opens it (founder, 2026-10-20: gallery pictures have no Preview screen); streak → the most recently played unfinished canvas, else the gallery; almost finished → that canvas, else the gallery. Stale (after midnight, withdrawn, no longer free) → the gallery, no error. Explicit, immutable `PendingIntent`; the id is re-checked against the catalog and store.
- **Large icon and copy:** the picture's flat preview (drop, Today, almost-finished bundled/remote); the app icon for a photo canvas or a streak. Copy names the picture ("Tonight: Lighthouse at Dusk"); calm, no urgency beyond the true streak fact. Exact titles and texts per reason, the small icon (a white square-gem outline, `patina` accent) and the channel description are in the design system's notification table (Phase 4; founder-approved in Phase 5 D21). Lock screen: `VISIBILITY_PUBLIC`, full text (D21). The design system's streak entry (#33) allows the streak fact in this opt-in note only (D10). Mystery handling arrives with T43: an unfinished mystery is "Tonight's mystery picture" / "A new picture, hidden until you finish it." with the frost as its large icon (D23).
- **Events:** `reminder_prompt_shown` (attempt), `reminder_prompt_result` (granted | declined_in_app | denied_system), `reminder_settings_changed` (on, minutes since midnight), `reminder_shown` (reason, only when posted), `reminder_late` (minutes), `reminder_opened` (reason); `canvas_started` gains `entry` = reminder.

**Docs this issue owns:** `docs/design-system.md` (reminder sheet, Settings rows and off state, notification copy and icon rules, navigation map, the #33 streak exception, the "Reminders on" line); `docs/privacy-policy.md` (notification events, reminder time stored on the phone); `docs/play-compliance.md` (Data safety: new app-activity events; `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`; no exact-alarm permission); `docs/designs/gemmory.md` architecture diagram entry.

**Tests:** `ReminderReasonsTest` (each reason in priority order, none, skip + streak exception, late run, forgiven-day streak, photo copy), `PlayFileCodecTest` (round trip, CRC, newer, malformed), `ReminderSchedulerTest` (next trigger across DST and time-zone change), `ReminderReceiverTest` (8 s cancel), R1 rows (`TodaysPictureTest`, `TodayCandidatesTest`, `CanvasRepositoryTest`, `CanvasStoreTest` pause/release read, `AdPolicyTest`, `MonetizationTest`, `TelemetryTest`, `BackupRulesTest`); connected `ReminderFlowTest` (alarm → post with a test clock, reboot reschedule, tap routes, receiver start keeps the first session, cold start with consent denied logs nothing).

**Done when:** a player who opts in gets at most one reminder a day around their chosen time naming a real picture or a true streak fact, the notification and the Today card agree, taps land where the table says, nothing posts when the app is open or a gem was placed in the last 3 h (except the streak case), a receiver start never ends the first session, consent is honored on a background start, and the docs above are updated.

Depends on: T36 for reason 1 (the rest can land first). Effort: human ~3d / CC ~2h.

---

## T36: Scheduled drops: one new picture every date, a Tomorrow teaser and a "New today" chip
Labels: P1, game, persistence, ui

**Why:** about 48 bundled pictures (42 locked) run dry for a daily player in ~6 weeks, and `pictures/v1/` has 0 published. A drop every date (CEO C4) gives the reminder something real to say. Plan: T36; eng D4, D9, D12, D13, outside voice #5, #6.

**Scope**
- **Catalog:** optional `availableFrom` (local date) per remote picture. Hidden until that date; on it, the New label and preferred as Today's picture. For a dated picture, `isNew` and `todayCandidates` count from `availableFrom`, not the fetch day; today's drop is preferred over other New pictures. Old clients never see dated pictures (T44 stamps `minAppVersion`).
- **Hidden means hidden:** `RemotePictures.listed()` (or the one wrapper every caller uses: `summaries`, `ensurePicture`, `pictureRef`, the backfill) leaves out future-dated pictures. A crafted or stale intent for tomorrow's drop opens the gallery.
- **Prefetch:** a fetch downloads and verifies (sha256 + crc32) future drops' grids and keeps them hidden. A drop not stored by its date stays hidden until it is; the reminder never names it.
- **Access (C2, D4, D9):** free to start on its `availableFrom` local date only; its first gem unlocks it for good (same path as Today's picture). After its date it follows its catalog `locked` flag (video, token or VIP). A drop that arrives after the day's Today pick is free beside that pick; the Today card shows the drop. Drops default to `locked = true` (T44 CSV).
- **Tomorrow teaser (E3, Phase 4 D5 = T2):** not a grid tile. A line at the bottom of the Today card under a hairline (56dp minimum, growing with its text at large font; Phase 5 D18): a 40dp square frost of tomorrow's stored and verified drop, "Tomorrow" (13sp `text-muted`, the card's label style, so the Today title leads; D13) over "A new picture opens at midnight" (13sp `bone`). With no Today card it stands alone in its own `felt-deep` card. No name, no countdown. Tap → an "Opens tomorrow" toast (16sp body, 4s, centred 16dp above New from photo, polite live region; D8), nothing opens. The #58 grid order doesn't change. The frost is the design system's one hidden-picture look (averaged into 6 blocks across, blurred, 45% `felt-deep`), drawn from the grid on the phone. Drops open at local midnight. Event: `teaser_tapped`. TalkBack: "Tomorrow's picture, opens tomorrow".
- **"New today" chip** on the Today card when it's a drop: a `felt` chip with the 12dp spark vector and "New today" (bone 13sp), replacing the "Today's picture" / "Today's pick" line; the tile keeps its ordinary New label; VIP sees it too. TalkBack folds it into the card: "Today's picture, new today: …".
- **Gallery refresh:** reload at local midnight while the gallery shows, and after a check that brings something new.
- **Large screens (D19):** the Today card spans the grid with its text column at most 480dp.
- **Summaries (D12):** unstarted remote tiles built from catalog metadata; a grid is decoded only for its thumbnail or Preview.
- **Integrity:** `issuedAt` anti-rollback and signatures unchanged. Moved clocks and public future art are accepted risks.
- **Events:** `canvas_started` / `canvas_finished` gain `entry` (gallery | today_card | reminder | collection | event) and `days_since_available` for drops; new `day_played` (entry) once per local day on the first gem, no ids (D13).

**Docs this issue owns:** `docs/design-system.md` (Tomorrow line, frost, "New today" chip and TalkBack labels, written in Phase 4; update if the build differs); `docs/privacy-policy.md` + `docs/play-compliance.md` (`day_played`, new params); `docs/designs/gemmory.md` (remote pictures section: dated drops).

**Tests:** `RemoteCatalogTest` (`availableFrom` parse, malformed refusal, 0.5.0 golden shows nothing new), `RemoteStateTest` (New/Today from `availableFrom`, undated unchanged, today's drop preferred), drop rules in `RemotePictureRules` tests (hidden before date, free on date only, date+1 locked, time-zone change), `TodaysPicturesTest` (morning pick + evening drop both free), crafted-id redirect, `TelemetryTest` (`day_played` once a day), `GalleryLoadTimingTest` at 365 remote entries on the A06 line; connected midnight-rollover and teaser tests.

**Done when:** a drop published weeks ahead appears on its date with New and the chip, is free that day only, the Today card shows tomorrow's frosted Tomorrow line, a crafted id can't open a future drop, the gallery refreshes at midnight, gallery load holds at 365 remote pictures, and the docs above are updated.

Depends on: none (1a). Ship this build to production before the first dated publish (deploy order, CEO Section 9). Effort: human ~2.5d / CC ~1.5h.

---

## T37: Themed collections with a keepsake picture and a token
Labels: P2, game, ui

**Why:** collections are the category's second return hook (trophies, "3 of 7 finished"). Plan: T37; eng D7, outside voice #2.

**Scope**
- **Catalog (D7):** a top-level `collections` list in the signed catalog: `{id, title, members: [picture ids], event?: true}`. Members may be remote or bundled ids; picture entries carry no collection field. Collection and event ids share one namespace, separate from picture ids. Old clients ignore the key.
- **Gallery (Phase 4 D6 = R1):** at most 2 rows above the grid, under the Today card: an event in its window first, then the newest collection not yet complete (a completed one stays 7 days, then leaves). Every collection is in an "All collections" sheet, opened by a text button right after the rows, before the grid (Phase 5 D3). Above 1.5× font scale only one row shows (D4). No rows and no link before the player's first finished picture (D5). A row: the name (16sp/600), "3 of 7 finished" (mono count), one line ("Finish all 7 for a keepsake picture."), then a sideways strip of 64dp member thumbnails with real progress and a check on finished ones. A thumbnail's tap is its tile's tap. Members not yet dropped (or dropped but not yet stored) count, and show as one recessed well, "2 more coming" (13sp, D14; above 1.5× font the well shows "+2" in mono and the words join the count line, D18). The keepsake is never shown before it's earned. No timer, progress bar or badge. A never-fetched phone shows no collection rows and no "All collections".
- **States (D6):** the design system's row states table: never fetched → nothing; offline with a cached verified catalog → rows as usual and completion still works; damaged member → its tile's "Can't open" mark and sheet; keepsake earned but its grid not on the phone → "Keepsake earned. It arrives next time you're online." (disk full: "…when there's space on this phone."), the token at once, the picture and glint on the next successful check; All collections sheet strips scroll sideways without dragging the sheet.
- **Large screens (D19):** rows span the grid, text column at most 480dp, the strip shows more without scrolling, rows stay stacked.
- **Membership:** a withdrawn member never started leaves the collection and its size; a started one stays. Finishes made before the collection existed count, including pictures finished before T35 (finished state, no date needed).
- **Access:** members keep their own rules (bundled lock, drop rules); finishing counts however the picture was opened.
- **Reward (C6):** finishing every member earns a collection-only picture (`rewardOf` = the collection id, hidden until earned) and 1 unlock token (`inventory_earned` source = collection; VIP: picture only). Decided only against a fetched, verified catalog.
- **Keepsake kept:** on earn, the reward's grid is copied into its canvas directory (existing create path) and its id goes into `PictureUnlocks` (backed up and transferred); an earned picture stays after the catalog withdraws it. `listed()` hides unearned rewards. It joins the end of the row's strip and, in the grid, the #58 order like any open picture, with New for 7 days (D25).
- **Reward return (D7):** the return that completes a collection shows no interstitial, reminder ask or review (they wait for the next eligible finish); the gallery scrolls the row into view.
- **Completion glint:** on the first gallery visit after completion, once the row is on screen, a `lamp` band (lamp's reward-moment meaning, D15) sweeps once across the row (900ms), then a 2dp `lamp` edge fades in and out (400ms). The line becomes "Keepsake added: <title>. 1 unlock token added." (VIP: no token). Reduced motion: no sweep or edge, the line just shows. TalkBack: the name line is a heading, "Seaside, 3 of 7 finished, 2 more coming"; on completion, polite "Seaside complete. Keepsake picture added."
- **Rules** live in one pure `CollectionRules` (with T38's events and `rewardOf`).
- **Events:** `canvas_*` gain `collection_id`; `collection_completed` (collection_id). Content ids are an explicit exception to the "no ids" rule in `Analytics.kt`.
- **T44 CSV:** `rewardOf` column and a collections sheet (id, title, members).

**Docs this issue owns:** `docs/design-system.md` (collection row, "2 more coming", glint, All collections sheet, states table, TalkBack, written in Phases 4-5; update if the build differs); `docs/privacy-policy.md` + `docs/play-compliance.md` (content ids in events); `docs/designs/gemmory.md` (catalog format).

**Tests:** `RemoteCatalogTest` (collections parse, unknown members, event flag), `CollectionRulesTest` (complete → picture + token, VIP picture only, withdrawn unstarted member leaves, started stays, bundled-only phone never completes, pre-T35 finish counts), keepsake survives withdrawal, `BackupRulesTest` row; connected `CollectionsFlowTest` (1× and 2× font on 360dp, no rows before the first finish, offline completion, pending keepsake, reward return without an interstitial).

**Done when:** a catalog collection shows its row with progress, completing it grants the keepsake and token once, the keepsake survives withdrawal and backup restore, and the docs above are updated.

Depends on: T36. Lands by the Assignment's date 8 (or the founder publishes plain drops instead). Effort: human ~2d / CC ~1h.

---

## T38: Weekly event: 7 drops in 7 days, finish 3 for an exclusive picture
Labels: P2, game, ui

**Why:** a monthly themed week built from drops and collections, with a reward earned by playing (VIP too, C3). Plan: T38; eng D7.

**Scope**
- An event is a collection with `event: true` whose 7 members drop on 7 consecutive dates; `event_id` = the collection id.
- **Window:** the first drop's `availableFrom` through the 6th day after it, inclusive, local dates.
- **Reward:** 3 of the 7 finished inside the window (by the `play` file's first finished day) unlocks the week's exclusive (`rewardOf` = event id) for good. VIP earns it the same way; unearned it stays hidden, so "Every picture unlocked" stays true. It's the event's keepsake: no extra T37 keepsake, but all 7 still grant C6's token (not VIP). Keepsake kept as in T37.
- **After the window:** the 7 stay under normal drop and lock rules; an unearned exclusive stays hidden. No timers on the canvas, no ads tied to it, no nag for a missed week.
- **Row copy** (design system, Collection and event rows): in the window "One new picture a day. Finish any 3 this week for a keepsake picture." and "4 more coming, one a day"; at 3 "Keepsake earned: <title>."; after the window an ordinary collection row ("Finish all 7 for an unlock token."; VIP "Finish all 7 to complete it."), with no "missed", "ended" or dates. The event row comes first among the 2 rows during its window, and is the one row shown above 1.5× font (T37, Phase 5 D4). A return that earns the exclusive is a reward return (no interstitial, ask or review; the glint plays once the row is on screen, D7); an exclusive whose grid isn't on the phone yet uses T37's pending-keepsake line (D6).
- **Events:** `canvas_*` gain `event_id`; `event_reward_unlocked` (event_id, finished_in_window).
- **T44 CSV:** the `event` flag.

**Docs this issue owns:** `docs/design-system.md` (event row copy, calm, no countdown or FOMO banner); `docs/privacy-policy.md` + `docs/play-compliance.md` (event ids); the C3 paywall copy (Phase 4 D7 = P2): the perk becomes "Every gallery picture unlocked", the rewarded grace text "Or get VIP: every gallery picture unlocked", and the success line "You're VIP. Every gallery picture is unlocked." (strings and their tests; already in `docs/design-system.md`).

**Tests:** `CollectionRulesTest` event rows (3 in window, finish at 23:59 last day counts, 00:01 next day doesn't, VIP earns the same way, token for all 7 not VIP, no double keepsake); connected event row test.

**Done when:** a 7-day event runs from the catalog alone, 3 finishes in the window reveal and unlock the exclusive for free and VIP players, nothing changes after the window, and the docs above are updated.

Depends on: T37. Effort: human ~1d / CC ~45min.

---

## T39: In-app review prompt after a finished picture (production only)
Labels: P2, game

**Why:** installs this round come from the store page and ratings; reviews on a closed track are private, so this is a launch item beside #74 (CEO C1). Plan: T39.

**Scope**
- Play In-App Review API after a finished picture, switched on by `features.review` in the signed catalog (one AAB goes from closed to production, so a build flag can't separate them). Old clients ignore the key.
- At most 3 calls per install (counter), attempt *n* at thresholds 3, 10, 25 finishes, at the first finish that is at or past the threshold, with the flag on, ≥ 14 days after the previous attempt, and not on the day of the first finish. One attempt at a time; a deferred attempt waits for the next eligible finish.
- Never blocks the finish screen; an API error counts as an attempt. Never on the canvas. Only on the gallery after Done/back from a fresh finish (never the completion screen, Share or the completion still), under the one-interruption rule: after T35's reminder ask, and when an attempt is due it takes that return instead of the interstitial (order confirmed by the founder, Phase 5 D22). Never on a return that completes a collection or earns an event keepsake; the attempt waits for the next eligible finish (D7).
- Event: `review_requested` (attempt).

**Docs this issue owns:** `docs/play-compliance.md` (the flag, how it's flipped at production); `docs/design-system.md` (One interruption per return, written in Phase 4; update if the build differs).

**Tests:** a pure gate table (thresholds, 14-day gap, first-finish day, flag off, already past a threshold when the flag flips); `TelemetryTest` row.

**Done when:** with the flag off nothing is called; with it on, the gate fires at most 3 times per install by the rules above, and the docs above are updated.

Depends on: production access (the 12 × 14 closed test and the D18 gate), beside #74. Effort: human ~4h / CC ~30min.

---

## T40: Gem redesign (founder: the current gems are ugly)
Labels: P2, renderer, ui

**Why:** the founder finds the flat cushions with a two-facet highlight ugly; this departs from the design system's cushion look with the founder's approval (office hours). Plan: T40.

**Scope**
- **Picked: the table cut** (Phase 4 D4, 2026-10-19; mockups `~/.gstack/projects/Gemmory/designs/t40-gem-redesign/index.html`). The whole cell, with no inset and no seam. A flat exact-color table inside four bevels: top 24% white, left 9% white, right 14% black, bottom 24% black. The bevel is 24% of the cell at 8-15dp and 20% from 16dp, never under 1.2dp. From 20dp, a 0.8dp catchlight at 38% white runs along the table's top edge. Flat below 8dp (below 8px off the canvas). The bevels replace D18's edges, and the Outline ring corner is 4%. One sprite per color per zoom bucket, as today. `GemRenderer` (Preview, tiles, completion, time-lapse), the confetti and the tray heaps draw the same gem. Spec: `docs/design-system.md`, Shapes and Play canvas.
- **Achievement icons (Phase 5 D17):** redraw the 12 Play Games achievement icons (#49) as a patina table cut in `tools/achievements/icons.html`, regenerate the PNGs; the founder re-uploads them in Play Console. No cushion gem is left.
- Must keep: no-gap tiling, the picture's exact colors, the 16dp placement floor, number legibility, the selected-color highlight modes.
- Must hold up in the time-lapse frames, the finish confetti and the preview; recognizability of photo canvases must not regress (founder + 3 testers, before/after on the converter goldens' canvases).
- Performance: the A06 frame-timing pass line (#54) stays green.

**Docs this issue owns:** `docs/design-system.md` (gem spec and decisions log written in Phase 4; update if the build differs).

**Tests:** existing renderer and frame-timing tests stay green; screenshot goldens updated deliberately; QA the hard-case strip (white, cream, light/mid/deep skin, near-black, mid violet placed beside their tints at 16 and 24dp) under all three Selected color styles; `CanvasFrameTimingTest` on the A06 at release.

**Done when:** the chosen gem ships on canvas, preview, time-lapse and confetti, the achievement icons are regenerated as table cuts, frame timing holds, recognizability doesn't regress, and the design system says so.

Depends on: nothing (picked 2026-10-19). Effort: design ~0.5d; build human ~1.5d / CC ~1h.

---

## T41: Better placing sounds
Labels: P2, game

**Why:** the founder finds the sounds thin (3 wavs today). Plan: T41.

**Scope**
- Sampled or carefully synthesized gem sounds, 3-5 variations per event on top of the random pitch, and a softer, layered tick when dragging. Events: a gem placed, a color done, a batch, a milestone, the finish.
- Sources CC0 or made in-house, provenance recorded like the pictures (a provenance file next to the sounds).
- The Sound toggle still governs all of it; `USAGE_GAME`, as today.

**Docs this issue owns:** `docs/design-system.md` (Haptics and sound section); sound provenance file.

**Tests:** `Feedback` keeps honoring the Sound toggle; no sound when off; variation selection is deterministic under a seeded random in a unit test.

**Done when:** every listed event has the new sounds with variations, provenance is recorded, the toggle governs them, and the AAB size delta is noted in the PR.

Depends on: sourced audio. Coordinate with T42 (both touch Settings and audio). Effort: human ~1d / CC ~45min plus sourcing.

---

## T42: Ambient music, on at 30%, silent in silent mode
Labels: P2, game, ui

**Why:** a calm loop makes the evening session feel good enough to repeat (CEO C5: on by default at 30%). Plan: T42; eng ringer rule.

**Scope**
- 2-4 calm CC0 or commissioned loops in the gallery and on the canvas, shipped compressed (Ogg/Opus, about 1 MB each [estimated]).
- Settings: a Music switch after Haptics and, while it's on, a Music volume slider row (steps of 10%, mono value), separate from Sound; on at 30% by default. **First music (Phase 5 D24):** it first plays when the first canvas opens, never on the cold-opened gallery. On the next gallery visit while it plays (after any sheet on that return closes), a one-time `felt-deep` chip above New from photo, "Music is playing" (16sp body, D14) + a text button "Turn off". It goes on tap, on a tile tap or after 8s (no timeout while TalkBack is on, D20), never shows again, and isn't shown when music didn't start. "Turn off" switches Music off, with a polite line. TalkBack "Music, on, switch" / "Music volume, 30 percent, slider". Spec: design system, Settings → Music.
- Silent mode: doesn't start while `AudioManager.ringerMode != RINGER_MODE_NORMAL`; pauses on `RINGER_MODE_CHANGED_ACTION`.
- Audio focus: ducks or pauses for other audio; doesn't start if other media is playing. Pauses during interstitials, rewarded ads and the time-lapse export; stops in the background; never part of the time-lapse audio.
- A loop that fails to decode turns music off for the session with a non-fatal.

**Docs this issue owns:** `docs/design-system.md` (Settings rows and the first-launch chip written in Phase 4; audio rules); music provenance file.

**Tests:** connected `MusicFlowTest` (silent cold open, 30% from the first canvas, chip without timeout under TalkBack, silent/vibrate → off, focus loss pauses, ad and export pause, background stops); decode-failure unit test.

**Done when:** music plays at 30% by default, never in silent/vibrate, yields to other audio, ads and export, stops in the background, the toggle and volume work, and the AAB size delta is noted.

Depends on: sourced audio; coordinate with T41. Effort: human ~1.5d / CC ~1h plus sourcing.

---

## T43: Mystery picture: a drop that stays hidden until it's finished
Labels: P2, game, ui

**Why:** a small novelty on drops (CEO C7: a catalog flag, never a player toggle). Plan: T43.

**Scope**
- A `mystery` flag on drops (T44 CSV column); old clients never see it (T44 stamps `minAppVersion`).
- **Hidden until finished (founder, Phase 5 D23: "all hidden"):** title, art, canvas tints, Peek and Share stay hidden until the picture is finished; there is no reveal at 50%. Everywhere: the frost (design system, Hidden pictures: averaged into 6 blocks across, blurred, 45% `felt-deep`; tiles carry a "Mystery" label with a gem-outline vector in New's place, with the real % under the tile) and a neutral name ("Today's mystery picture" in the app on its date, "Tonight's mystery picture" only in the reminder, "Mystery picture" after its date; D11) in the teaser, Today card, chip, collection rows, Preview, reminder icon and copy, and TalkBack. The real title appears at the finish (completion meta line, then everywhere).
- **Canvas (D9):** every unplaced cell is one neutral tint (`bone` mixed 20% toward `felt-deep`, number in `on-bone`), so the picture builds only from the gems placed; the tray and the Selected color style work as usual. The tints never come back: the finish celebration is the reveal.
- **Peek and Share (D23):** the Peek eye, its TalkBack "Peek at picture" action, the canvas Share progress button and the tile ⋮ "Share progress" are hidden (not dimmed) until the finish; it's shared only from the completion screen. Milestone banners are the ordinary ones ("Halfway there"). Preview line: "Hidden until you finish it." TalkBack tile: "Mystery picture, hidden until finished, 12% done". This replaces the earlier 50% reveal rules (a fill crossing 50%, share only after 50%).
- Whichever of T37 or T43 lands second applies the rule to collection rows; T43 applies it to the T35 reminder and the T36 teaser and chip.
- Event: `canvas_started` gains `mystery`.

**Docs this issue owns:** `docs/design-system.md` (mystery frost, names, neutral canvas tint, Peek/Share rule, TalkBack, written in Phases 4-5; update if the build differs).

**Tests:** a pure "hidden?" rule (unfinished → hidden, finished → shown, including a batch that places the last gem); renderer test for the neutral tint; connected check that no surface shows the art or title and no Peek/Share path exists before the finish, TalkBack labels neutral.

**Done when:** a mystery drop's art and title never show on any surface before it's finished, it can't be peeked or shared until then, the finish reveals it, and the docs above are updated.

Depends on: T36 (and T35 for the reminder copy). Effort: human ~1d / CC ~45min.

---

## T44: Picture pipeline: a ChatGPT sheet prompt, a splitter and a dated batch publish
Labels: P1, build, persistence

**Why:** a drop every date needs ~31 pictures a month (CEO C4); by hand that doesn't happen. Plan: T44 (CEO E1); eng D7, outside voice #12.

**Scope**
- **Prompt:** a checked-in ChatGPT image prompt (`tools/remote/prompts/`) for a 2×3 sheet of 6 pictures in one theme, calm palette, one aspect per sheet (1:1, 3:4 or 4:3).
- **Splitter:** finds the 6 tiles in a sheet, trims the gutters, crops each to the sheet's aspect and saves a source image; a sheet that doesn't split into 6 stops with a named error.
- **Batch:** a folder + CSV (title, size with aspect e.g. `big-1:1`, colors, availableFrom, locked default `true`) → converter freeze → content-check gate (a yes per picture; `-PcontentChecked`, README.md:142-146) → provenance rows (AI-generated, model, prompt file, date; still refuses real people, brands and copyrighted characters) → `publishRemotePicture` with dates. Catalog, grids and provenance in one PR or nothing.
- **Old-client guard (D14, D7):** stamps each picture's `minAppVersion` with the lowest versionCode that understands every hiding field it uses (`availableFrom`, `rewardOf`, `mystery`); collections never raise it. A publish test fails if one is too low. For a dated picture, `addedAt` = `availableFrom`.
- **Calendar warnings:** the batch warns when fewer than 14 days are scheduled ahead; a scheduled CI job on `pictures/v1/catalog.json` fails below 7 days, once the catalog has any `availableFrom`.
- Later columns ship with their issues: `rewardOf` + collections sheet (T37), `event` (T38), `mystery` (T43).
- The prompt, splitter and freeze can merge first; the dated publish merges with or after T36.

**Docs this issue owns:** README (how to run the pipeline), `tools/remote/PROVENANCE.md` (AI rows), `.github/workflows` (calendar check), `docs/agents/lessons.md` if the tooling teaches anything.

**Tests:** `RemotePictureToolTest` rows (stamped versions, a field above its version fails, collections don't raise it, `addedAt` = `availableFrom`); splitter tests (6 tiles, 5 tiles error, gutter trim); calendar-check unit test (< 14 warn, < 7 fail, no `availableFrom` → pass).

**Done when:** the founder turns 4 ChatGPT sheets into 24 dated, signed, provenance-recorded pictures in one PR, old clients see none of them early, and the calendar check runs daily.

Depends on: T36 for the dated publish. Effort: human ~2d / CC ~1h.
