# Gemmory: issue drafts (from the eng review, 2026-10-12)

Source: `docs/designs/gemmory.md` → "Implementation Tasks" (T1-T12) and "Decision ledger (Eng Review)" (E1-E7, D1-D20).
Created on GitHub on 2026-10-13 as issues #1-#14 (T*n* = #*n*, https://github.com/BrewingBytes/Gemmory/issues). The issues are the live source; this file is the snapshot they were created from. Each section is one issue: the `##` line is the title, `Labels:` lists its labels, and the rest is the body.
Updated after the Phase 5 design review (2026-10-13, D3-D27): T2, T3, T4, T7, T8 and T10 gained UI scope, and T13 (app shell and screens) and T14 (accessibility and large screens) were added. UI specs and exact copy live in `docs/design-system.md`.
Later issues break the T*n* = #*n* mapping: T15 = #18 (plan step S6, which had no issue), T16 = #19 and T17 = #20 (both from playing #18 on the emulator), T18 = #29 (founder request during #19), T19-T24 = #31-#36 (from an app review after #19), T25 = #38 (founder request), T26 = #40 (follow-up of #12), T27 = #43 (founder request during #5).

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
