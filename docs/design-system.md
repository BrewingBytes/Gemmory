---
# gstack: design-md-format=spec
name: Gemmory
description: A matte grey-green felt sorting tray under one warm lamp; quiet, tactile, mid-tone, where the only saturated color is the picture.
colors:
  felt: "#4F5A52"
  felt-deep: "#3E4741"
  felt-raised: "#5C685F"
  felt-dark: "#39423C"
  felt-deep-dark: "#2E3530"
  felt-raised-dark: "#47524A"
  bone: "#E9E4D8"
  on-bone: "#2B322D"
  text-muted: "#CDD3C9"
  lamp: "#F4E3B1"
  patina: "#7FB3A0"
  patina-text: "#ACD6C6"
  fault: "#E8B09E"
  scrim: "#141815"
  frame-text: "#F2EEE4"
typography:
  display:
    fontFamily: Bricolage Grotesque
    fontWeight: 600
    fontSize: 22px
    letterSpacing: -0.01em
  display-lg:
    fontFamily: Bricolage Grotesque
    fontWeight: 600
    fontSize: 34px
    letterSpacing: -0.02em
  body:
    fontFamily: Figtree
    fontSize: 16px
    lineHeight: 1.5
  button:
    fontFamily: Figtree
    fontWeight: 600
    fontSize: 16px
  label:
    fontFamily: Figtree
    fontSize: 13px
    letterSpacing: 0em
  mono:
    fontFamily: Atkinson Hyperlegible Mono
    fontWeight: 700
    fontFeature: tnum
rounded:
  sm: 4px
  md: 6px
  lg: 12px
  sheet: 16px
  gem: 0%
spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  2xl: 48px
components:
  button-primary:
    backgroundColor: "{colors.bone}"
    textColor: "{colors.on-bone}"
    rounded: "{rounded.md}"
  button-quiet:
    backgroundColor: "{colors.felt-raised}"
    textColor: "{colors.bone}"
    rounded: "{rounded.md}"
  sheet:
    backgroundColor: "{colors.felt-deep}"
    rounded: "{rounded.sheet}"
  option-selected:
    borderColor: "{colors.lamp}"
    rounded: "{rounded.lg}"
  palette-trough:
    backgroundColor: "{colors.felt}"
    rounded: "{rounded.md}"
  canvas-surround:
    backgroundColor: "{colors.felt-deep}"
---

# Gemmory

## Overview

**Creative North Star:** "The Sorting Tray." Gemmory looks like a jeweler's grey-green felt tray under one bedside lamp: matte, mid-tone, tactile. It serves the founder's anchor, *"the calmest thing on my phone."* The shell carries no saturated color, so the player's photo is the only vivid thing on screen and reads as *their* photo.

**Product context:** Android (Kotlin, Jetpack Compose, Material 3 base) diamond-painting game for adults (not for children) who already play color-by-number / diamond apps 30-60 min a day, often in the evening. The differentiator is an own-photo canvas that stays recognizable plus a 9:16 sparkle time-lapse. See `docs/designs/gemmory.md`.

**Mode per surface:**
- Gallery, crop, size picker, preview: Operate (calm, fast decisions).
- Play canvas: Experience (immersive; chrome stays out of the way).
- Completion and the 9:16 time-lapse: Persuade (the one authored moment; it is also what strangers see).
- Paywall, rewarded states, settings: Operate (honest, plain-spoken).

**Reference sites (observed 2026-10-13):**
- [Play "diamond painting" search](https://play.google.com/store/search?q=diamond%20painting&c=apps): a loud shelf of rainbow pixels, neon purple and navy, gold-bevel logos, glitter.
- [Happy Color](https://play.google.com/store/apps/details?id=com.pixel.art.coloring.color.number): a calm benchmark. A plain shell lets the art carry the color, and its top review praises not being bombarded with ads.
- [Dotterly](https://apps.apple.com/au/app/dotterly-diamond-painting/id6446167461): the photo-import competitor. A 1-star review reads "used a photo and you can't even recognise it."

**Key characteristics:**
- An exhale in the first three seconds: nothing shouts, glows or glitters at rest.
- Felt grain, bone type, one warm lamplight color for "this one."
- Square table-cut gems tile with no gaps, so the picture always reads (T40, #81). Each is a flat table in the exact color with four bevels, like a real square drill seen from above.
- Progress you can feel (shrinking gem heaps) instead of percentages.
- Completion dims the room and lets the picture celebrate: its gems glint, then gem confetti in its own colors falls through the dim (#57). The picture is never covered.

Preview reference: `~/.gstack/projects/Gemmory/designs/design-system-20261013/preview.html` (approved 2026-10-13).

## Colors

**Strategy:** Restrained. Neutrals come from one sage-slate felt, and color is rare and meaningful. The picture supplies the saturation.

**Light or dark:** neither. The felt is **mid-tone** on purpose. Gem graders judge stones on neutral grey because colors read true there. A mid-tone also stays comfortable in a dim bedroom without the "near-black + glow" look. One theme ships. Under system dark mode the three felt tokens drop one step (`felt-dark`, `felt-deep-dark`, `felt-raised-dark`), and every other token stays the same.

Token roles:
- `felt` is the app ground, with a subtle grain at about 9% noise. `felt-deep` is the canvas surround, sheets, the tray bar and selected tabs. `felt-raised` is quiet buttons, toggles at rest and closed trough flaps.
- `bone` is primary text and the **only** primary-button fill (5.7:1 on felt). `on-bone` is text on bone. Bone is never a ground.
- `text-muted` is for secondary text (4.7:1 on felt, 6.3:1 on felt-deep).
- `lamp` marks "this one": the selected trough outline, the selected size or plan option, and the face hint text. Its only other use is the game's reward moments: the milestone banner's edges (#35) and a collection's completion glint (T37; plan-design-review D15). It is never used for buttons.
- `patina` fills VIP and success states (switch-on track, perk bullets). `patina-text` is VIP wording in text (4.5:1 on felt).
- `fault` covers offline / no-video wording only (5.1:1 on felt-deep). **It is never used for a wrong-color tap.**
- `scrim` at 55% sits behind sheets. `frame-text` is text on the darkened 9:16 band.
- **Streak flame** (#101, G1): `flame` `#F5A623` (days 1-29), `flame-30` `#F08A2C` (30+), `flame-100` `#E0563B` (100+), core `#FCE3A6`; before today's finish, `ember` `#7E8880` with core `#A3ACA4`. A flat shape, never a glow and never text. It is the one saturated color outside a picture, and only the streak uses it (decision log).

**Valid grounds for text tokens** (WCAG AA 4.5:1 for small text, measured 2026-10-13; Phase 5 D15):

| Token | felt | felt-deep / sheets | felt-raised | Rule |
|---|---|---|---|---|
| `bone` | 5.68 | 7.58 | 4.60 | valid everywhere |
| `text-muted` | 4.72 (flat) / **4.09** (grained) | 6.30 | **3.82** | only on felt-deep, sheets, the dark felts or the completion's dimmed room; on the grained felt ground and on `felt-raised` use `bone` (#14) |
| `lamp` (face hint text) | 5.65 | 7.55 | n/a | felt and felt-deep only |
| `patina-text` | 4.52 | 6.04 | **~3.7** | only on felt-deep, sheets or the dark felts |
| `fault` | **3.82** | 5.10 | **3.09** | only on felt-deep, sheets or the dark felts. On plain felt, offline copy is `bone` with a 6dp `fault` square marker before it |

QA checks every new string against this table. The ground's 9% grain counts: the Accessibility Test Framework samples the grained felt at about #59635C, which is why `text-muted` moved off plain felt in #14. `AccessibilityChecksTest` runs the framework (the Accessibility Scanner's engine) on every main screen and sheet.

**Canvas cell colors:** a placed gem is the exact grid color. An unplaced cell is that color mixed 78% toward `bone`, with its number in `on-bone`. How the selected color's remaining cells stand out is the player's **Selected color** setting (#19, see Play canvas): by default they are their color mixed only 40% toward `bone`, with their number in `on-bone` or `bone`, whichever has more contrast. Under "Dim the rest", every other unplaced cell is its pale tint mixed a further 35% toward `felt-deep` (#3E4741, also under system dark mode), number again in `on-bone` or `bone`, whichever has more contrast. Gem colors are never re-tinted by the theme.

## Typography

Every face is OFL and verified in the google/fonts METADATA on 2026-10-13. **Bundle them as TTFs in `res/font`** (variable where available) rather than using GMS downloadable fonts, so first launch and offline play render correctly.

- **Bricolage Grotesque** (600, optical size axis) is the display face. It is only for screen titles (22sp), sheet titles (22sp), the completion title (34sp), the paywall title (30sp) and the time-lapse band title. It is warm and slightly crafty, unlike the gold bevels the shelf uses. Never use it for body text or buttons.
- **Figtree** (400 / 600) handles all UI and body: 16sp body at line-height 1.5, 16sp/600 buttons, 13sp captions.
- **Atkinson Hyperlegible Mono** (700, tabular figures) is for gem numbers on the canvas (0.46 × cell size), trough numbers and counts, and progress figures (`4,812 / 8,112`) and prices. It was designed for low-vision legibility, which matters for tiny cells and tired evening eyes. Use it only for figures, never for labels.

- **`streak-hero`** (#101, G8): Atkinson Hyperlegible Mono 700, tabular, for the streak number only: 88sp on the celebration, 384px on the 1080px share card (#104, the mockup's proportion), 56sp on the streak screen, at most 64sp above 1.5x font.

- **Other scripts (T49, #136, round 3 C11):** Figtree and Bricolage Grotesque have no Cyrillic or Japanese, so those languages get bundled faces of their own (OFL, `res/font`), picked by the app's language:
  - **Bulgarian (Cyrillic):** **Onest** (variable, 400 / 600) for body and UI in Figtree's roles, **Geologica** (variable, 600) for titles in Bricolage's roles. Onest has Figtree's round, friendly forms; Geologica keeps Bricolage's slightly crafted edge.
  - **Japanese:** **Zen Kaku Gothic New** (Regular 400, Bold 700) for both roles: body and UI at 400 (labels and buttons at 700, since it has no 600), titles at 700. Warm and a little crafted like Bricolage; 6,682 kanji (JIS levels 1-2), so picture titles draw in it. Japanese body line height is 1.6 (denser glyphs), everything else follows the scale.
  - **Numbers** stay in Atkinson Hyperlegible Mono in every language (digits are Latin in all eleven).
  - Every other language of the round (de, es, pt-BR, fr, it, ro, pl, tr, hu) is covered by Figtree and Bricolage. Romanian is written with ș / ț (comma below) only; the cedilla forms ş / ţ aren't in the fonts and are normalized away.
  - A character our face can't draw is caught by GlyphCheck (app strings fail the build; a batch title only warns and falls back to the phone's font).

Scale: 34 / 30 / 22 / 16 / 13 sp. Adjacent levels differ by size *and* role, never by weight alone. Respect the system font scale. At 200% the canvas numbers do not scale (they follow cell size), but all chrome does.

## Layout

- Material 3 structure (top app bar, bottom sheets, system back) so Android feels native, with 16dp side gutters and a 4dp base grid.
- **Gallery:** a 2-column staggered grid (10dp gap) that honors 1:1 / 3:4 / 4:3 tiles. Tabs are "Pictures" and "My Photos", with the daily play streak chip (#33; a button to the streak screen, #101) at the end of their row. On Pictures, the Today's picture card (#34, with the Tomorrow line from T36; both hidden until the player's first finished picture, T45 DR4, though the day's free pick is still made) sits above the grid, spans its width and scrolls with it; at most two collection or event rows (T37/T38) follow it, then the "All collections" text button, then the grid. A "New from photo" primary button floats bottom-right. The first Pictures tile is the free starter picture (24×32, about 5 minutes; Phase 5 D13). After it come every picture the player can open (free, unlocked by a video or today's grace, started, today's free picture (#34) or today's drop (T36); all of them for VIP), then every locked one (#58). Each group is a difficulty ramp: every Quick picture, then Medium, then Big (#31). A remote picture (#43) sits in its size's run after the bundled ones, oldest published first, in the open or locked group like any picture; new ones appear when a check that brings them finishes, or on the next Gallery visit, and a gallery left showing reloads at local midnight for the day's drop (T36). The order is set each time the Gallery shows, so a picture unlocked by a video moves up on the next visit, never under the player's finger; its locked label goes at once.
- **Finished section** (#89): each tab ends with its finished canvases, after the locked group on Pictures and after the unfinished photo canvases on My Photos, so a tab shows what's left to play first. A full-width quiet heading on the felt, 8dp below the last tile: "Finished" in the label style (`text-muted`), " · ", the count in mono, and a 20dp chevron (`expand_more`, `text-muted`) that turns up when open (150ms, instant under reduced motion). No card and no divider. At least 48dp tall, its text at most 480dp wide (D19); at 200% font the count wraps under "Finished". Collapsed by default; a tap expands it in place, and the state is remembered per tab (Settings preferences, backed up). No heading when nothing in the tab is finished. Inside: the same tiles in the same staggered grid, newest finish first (the T35 `play` file's first finished day); canvases finished before T35 have no date and go after the dated ones, and a same-day tie or an undated pair goes newest created first. A tile there opens the completion still as before. A tile moves into the section on the next Gallery visit after its finish, never under the finger (as #58); Play again or Start over makes it unfinished, so it goes back to its group on the next visit. The Today card, the Tomorrow line and the collection/event rows (T37/T38) don't change, and a finished picture still counts in its collection's row.
- **Play canvas:** a top app bar (back, title, the Peek eye, Share progress from the first gem (#36), mono progress), then a full-bleed canvas on `felt-deep`, then the tray bar at the bottom (for VIP it also holds the placement tools, #20). No ads and no other chrome; the milestone banner (#35) is a 3s moment over the canvas, not chrome.
- **Size picker:** the face hint sits on top, followed by three option rows, each with a mini gem preview, gem count and color count (the Standard count; the color choice is on Preview, #29).
- **Paywall:** a full screen with close visible immediately (top-left), plain perk lines, two plan rows, one primary button and a quiet "Just remove ads" row.
- Touch targets are at least 48dp, text buttons included. **Documented exception:** canvas cells can be placed from 16dp. The accessible alternatives are zoom up to 48dp cells, the Find button and the TalkBack canvas actions (Phase 5 D16).
- Sheets have a 16dp top radius, a grab handle and 20dp padding. Their content scrolls, so nothing clips at 200% font. The grab handle is a 32×4dp bar in a 48dp square labelled "Drag handle" (Material puts expand and close actions on it).

**Navigation map** (Phase 5 D3). System back always goes one step up.

```
first launch ─► UMP consent (EEA only, first launch, before any ad request; never over a later launch)
            │   (T45, #132: nothing else before the first gem. First-run order, audited 2026-10-22: consent (EEA) ─► Gallery
            │    with the starter first, the streak chip at 0 and the Inventory bag (no Today card, Tomorrow line, collection
            │    rows or "All collections" until a picture is finished; no "VIP" until the 2nd session) ─► starter canvas.
            │    Nothing opens by itself on that path: the music chip needs a canvas opened first (D24) and shows on the gallery
            │    only; the reminder ask, Play's review sheet, the interstitial and the streak celebration only follow a finish;
            │    the gift wheel waits 5 days; rewarded sheets open only on a tap. Play Games' turn: nothing of it before a
            │    picture is finished. In the session of the first finish it starts on the next gallery visit that isn't the
            │    finish's return, with no reminder ask, review flow or rewarded sheet up and no reminder or widget tap opening
            │    over it; a progress share alone (Work in Progress) doesn't count as a finish; from then on it starts with the app (the
            │    first foreground), as early as allowed (founder), and so does it at once for an upgrader with a finish or an
            │    achievement. Its sign-in may show its one-time UI (welcome pop-up, or Google's profile sheet) over whatever
            │    shows then: the gallery in the E3 spike (2026-10-22), or a canvas after a cold reminder tap or a quick tap,
            │    which the founder accepted, since Play Games v2 can't pin its pop-ups to a view)
            ├─► share link with a listable picture id (T50, #137: fresh install only) ─► Gallery ─► that canvas, once,
            │    free to start (entry share_link); an unknown, hidden or crafted id ─► Gallery only
            └─► Gallery  [title left · Inventory bag (no VIP, #38) · quiet "VIP" text action from the 2nd session on · Settings icon right]
                 ├─ tab Pictures (default) │ tab My Photos
                 ├─ "New from photo" ─► Photo Picker ─► Crop ─► Size picker ─► Preview ─Start─► Canvas
                 ├─ tile in progress ─► Canvas (resumed)
                 ├─ tile done ─► Completion still (Share primary, Done quiet, "Play again" text)
                 ├─ "Finished · n" heading (end of each tab, #89) ─► expands or collapses in place; its tiles ─► Completion still
                 ├─ tile locked ─► Rewarded sheet ─► (reward, or an unlock token #38) Canvas
                 ├─ Inventory bag ─► Inventory sheet ─┬─ "Watch a video: +5 fills" ─► Rewarded sheet (fills) ─► back to the gallery
                 │                                    └─ "Spin for your 5-day gift" ─► Gift wheel sheet ─► back to the gallery
                 ├─ Today's picture card (or its tile) ─► Canvas, no video (#34)
                 ├─ Tomorrow line ─► "Opens tomorrow" toast, nothing opens (T36)
                 ├─ collection / event row thumbnail ─► as its gallery tile (T37/T38)
                 ├─ "All collections" (after the rows) ─► All collections sheet ─► thumbnail ─► as its tile
                 ├─ back from a fresh finish ─► at most one of: reminder ask sheet (T35) · Play review sheet (T39) · interstitial
                 │    (on a new streak day the celebration comes first; the one interruption runs when the gallery shows after it, #101 G6)
                 ├─ streak chip (#101) ─► Your streak ─back─► Gallery
                 ├─ streak nudge (#102) ─► its canvas (entry streak_nudge, as its tile) · broken or no target ─► Your streak
                 ├─ (home screen) streak widget (#103) ─► Your streak over the Gallery (a canvas open is saved and closed) ─back─► Gallery
                 ├─ tile damaged ─► "Can't open" sheet (see Gallery tile)
                 ├─ tile ⋮ menu: photo canvas "Rename" / "Delete" · bundled "Start over" · first "Share progress" once a gem is placed (#36)
                 ├─ "VIP" ─► Paywall
                 └─ Settings ─► Membership row ─► Paywall (Free) or Play subscription page (VIP)
                    Settings ─► Achievements row (#49, only where Play Games runs) ─► Play Games' own achievements screen
Canvas ─finish─► Completion ─Share─► progress sheet ─► Android share sheet ─► back to Completion
Canvas / tile ⋮ ─Share progress─► progress sheet ─► Android share sheet ─► back where it started (#36)
Completion ─Done/back─► Gallery ─► interstitial check (Ad rules), never before or during Share
Completion ─Done/back, a new streak day (#101)─► Streak celebration ─Continue/back─► Gallery ─► the return's one interruption
                                                                    └─ "See your streak" ─► Your streak ─back─► Gallery ─► the same
Streak celebration on a milestone ─Share─► Android share sheet (the share card, #104) ─► back to the celebration
Gallery after a cold start with today's celebration unshown (G9) ─► Streak celebration if the finish was < 30 min ago, else the chip's flame lights up once
Reminder notification (T35) ─tap─► drop or Today's picture: its canvas, no video (as the Today card) · streak: the nudge's target (#102), else Gallery
                                   · almost finished: that canvas, else Gallery · stale: Gallery, no message
```

- Long-press on a tile opens the same ⋮ menu.
- **Delete confirm:** "Delete this photo canvas? The photo, grid and progress are removed from this phone." Buttons: Delete (primary), Keep (text).
- **Start over confirm:** "Start this picture over? Your progress on it is cleared." Buttons: Start over (primary), Keep (text).

**Large screens** (Phase 5 D22). On 600dp and wider, Android 16 ignores orientation locks, so these layouts are required:
- Under 600dp: portrait-first, as designed. Landscape is allowed but not optimized.
- 600dp and wider: the gallery has 3 columns (4 at 840dp and wider). Sheets are at most 560dp wide and centered. The paywall content column is at most 480dp.
- The Today card (with its Tomorrow line) and the collection or event rows still span the grid; their text column is at most 480dp, a row's strip shows more thumbnails without scrolling, and rows stay stacked, never side by side (plan-design-review D19).
- Canvas at 600dp and wider in landscape: the tray becomes a vertical 2-column rail on the right, with the same troughs, and the canvas fills the rest.
- Canvas, zoom and selection state survive rotation and fold changes.

## Elevation & Depth

Depth means **recess and raise**, not glow:
- Troughs are recessed with an inset shadow (0 3dp 6dp, 45% black).
- Gallery tiles sit on the felt with a short cast shadow (0 2dp 3dp).
- Sheets lift with a 0 -8dp 20dp shadow over a 55% scrim.
- Buttons have a 1dp darker "stitch" inset and a 1dp bottom shadow.

There are no zero-offset colored halos and no frosted glass anywhere. The frost (Hidden pictures) is not a surface: it's how a picture that isn't available yet is drawn (plan-design-review D16).

## Shapes

- Radii: `sm` 4dp for thumbnails and labels, `md` 6dp for buttons and troughs, `lg` 12dp for option rows and plan rows, `sheet` 16dp for the top of bottom sheets.
- **Gems are square table cuts** (T40, #81, founder pick 2026-10-19; mockups `~/.gstack/projects/Gemmory/designs/t40-gem-redesign/`). They replace the 18% cushion and its two-facet highlight.
  - **Shape:** the whole cell, square corners, no inset and no seam. Neighbouring bevels meet in a soft V in their own colors, so there are no grout lines and no dark gaps.
  - **Table:** a flat square in the exact grid color, centered, inset from the cell edge by the bevel width.
  - **Bevels:** four trapezoids from the table to the cell edge, as overlays on the exact color: top 24% white, left 9% white, right 14% black, bottom 24% black. The lit and shaded halves cancel at mid tones, so the gem averages to its true color when zoomed out; at the ends a white overlay can't lighten white or a black one darken black, so a white gem averages up to ~18 levels darker and a black one up to ~15 lighter (about 7%; `TableCutTest`). The bevel width is 24% of the cell from 8 to 15dp and 20% from 16dp, never under 1.2dp.
  - **Catchlight:** from 20dp, a 0.8dp line at 38% white along the table's top edge.
  - **Below 8dp** (and below 8px off the canvas) a gem is a flat square in its exact color.
  - One baked sprite per color per zoom bucket, as before (T10). The Outline ring's corner radius is 4% of the cell.
  - The 12 Play Games achievement icons (#49, `tools/achievements/icons.html`) are redrawn as a patina table cut in T40 and re-uploaded in Play Console (plan-design-review D17), and so are the launcher icon and the Play Store icon (`tools/store/icon.py`, founder 2026-10-20), so no cushion gem is left.
- Buttons are rectangles, never pills.
- A nested inner radius equals the outer radius minus the gap.

## Components

**Primary button.** Bone fill with on-bone text and a stitch inset. Pressed state moves it down 1dp. Disabled is 55% opacity. Focus-visible gets a 2dp lamp outline. Use one per screen.

**Quiet button.** `felt-raised` fill with bone text, for secondary actions such as Done, Try again and Restore.

**Text button.** Bone, underlined, 48dp minimum height (Phase 5 D16). It wraps to a centered block inside sheets.

**Palette tray (sorting tray).**
- One trough per color, 52dp wide. The well is 44dp tall and recessed.
- A heap of up to 9 mini table-cut gems (9dp, 24% bevels, no catchlight) shows the remaining count (one gem per ~3.5 cells, minimum 1 while any cells remain). The trough number is in mono, with the remaining count in muted mono below it.
- **Selected:** the trough lifts 3dp and gets a 2dp lamp outline.
- **Finished:** a `felt-raised` flap closes over the well, the number is struck through, the count is hidden, and the trough moves to the end of the tray.
- The tray scrolls horizontally, and the selected trough auto-scrolls into view. A More colors photo canvas has up to 60 troughs (#29), about 3,100dp of tray; numbers stay at two digits.
- **Selection** (Phase 5 D25):
  - On open, the first unfinished color in tray order is selected.
  - When a color finishes, the next unfinished trough is auto-selected and scrolled into view.
  - On resume, the last zoom, pan and selected color are restored. They are stored with the canvas record, not in the GMRY placement log.
- **Find** (Phase 5 D24): when the selected color has 20 or fewer cells left, a 48dp quiet "Find" button appears at the tray's left end.
  - Each tap pans and zooms (24dp cells) to the next remaining cell and pulses the cell once for 400ms. Under reduced motion the cell doesn't pulse.
  - It is hidden at all other times. It shares its logic with the TalkBack "Find next gem" action.
  - It is an icon button (magnifier) labelled "Find". In the large-screen rail it is pinned full width at the top, above the scrolling troughs.
- **Placement tools** (#20, founder picks 2026-10-17): unlimited for VIP; anyone else uses fills from the inventory (#38), one per Fill region or Fill color use, or per Brush stroke (finger down to up), spent only when the use placed something:
  - **Tool well:** a 48dp quiet icon button after Find (and Fill), before the troughs, showing the current tool's icon: Tap (one gem), Brush 3×3 (a 3×3 of gems) or Fill region (a patch with a gem at its heart). While the tool isn't Tap, a 6dp `lamp` square sits in its top-right corner. Tapping it swaps the color troughs for three tool troughs in the same row (Tap, Brush 3×3, Fill region: `felt` wells, 64dp tall at least, icon over a 13sp label that wraps to 2 lines at large font, with the icon hidden above 1.5× font scale as the count is (D20), laid over the color troughs at their exact size so the tray never changes height, the chosen one lifted 3dp with the lamp outline); picking one, or tapping the well again, swaps the color troughs back. The tool stays until changed or the canvas is left; every canvas opens on Tap. In the large-screen rail the well is pinned full width under Find and the tool troughs stack.
  - **Fill color:** while the selected color has 20 or fewer cells left (when Find shows), a 48dp quiet button "Fill 14" (its icon and the count) sits after Find. A tap places all of them, nearest the middle of the view first, which always finishes the color.
  - **Without VIP** (#38): the well shows while the player holds fills, with the count in mono 11sp on a `felt` tab at its bottom-right corner ("Tool: Tap, 5 fills"); above 1.5× font the count sits beside the icon instead and the well widens, so it never covers the icon. When they run out on a canvas it stays, dimmed to 55%, and the tool goes back to Tap; tapping it still opens the tool troughs, with Brush and Fill region dimmed and read as "Brush 3×3, no fills left". Fill color hides at 0. Nothing on the canvas says where fills come from (no nag, no ad). With no fills on opening, there's no well.
  - If VIP lapses on an open canvas, the well follows the fills held, and the tool goes back to Tap if there are none.
- **Font scale** (Phase 5 D20):
  - Trough width grows with the font scale, up to 72dp.
  - Above 1.5× the remaining count is hidden; TalkBack still reads it. The number never clips.

**Play canvas** (a single Compose `Canvas`, never one composable per cell).

| On-screen cell | Renders | Tap does |
|---|---|---|
| < 8dp | flat squares; unplaced cells as pale tints, the selected color shown per the Selected color setting (Outline uses the stronger tint here) | zooms to 24dp cells centered on the tap |
| 8-15dp | + placed gems as table cuts (bevels 24% of the cell) | zooms to 24dp cells centered on the tap |
| 16-19dp | + numbers on unplaced cells (bevels 20%) | places a gem; drag places along the path |
| ≥ 20dp | + the table's catchlight, placement glint | places a gem |

- Max zoom is 48dp cells, and min zoom fits the whole canvas.
- Placement needs at least 16dp cells, so a gem is never placed where its number can't be read.
- **Selected color** (founder decision 2026-10-13 for the default, #19 for the choice): how the remaining cells of the selected color stand out is a Settings choice with three styles. Placed gems look the same in all three.
  - **Stronger tint** (default): the selected color's remaining cells use their gem color mixed 40% toward `bone` instead of 78%, at every zoom; their number is `on-bone` or `bone`, whichever reads better. It previews the gem and shows at fit zoom. It's weak for light colors (cream, skin tones), whose stronger tint lands near the other pale tints.
  - **Outline** (the Phase 5 D17 two-tone ring): from 8dp, each remaining cell of the selected color keeps its pale tint and gets a 1.5dp `on-bone` outer ring 0.6dp inside the cell edge plus a 1dp `lamp` inner ring, with a 4% corner radius (T40). Below 8dp, where a ring can't be drawn, it falls back to the stronger tint, so the selection still shows at fit zoom. The rings sit inside the cell and the number is centered in the opening, so they never cover it (at 16dp the opening is ~9.8dp, the widest number "36" ~9dp). The `on-bone` ring is the dark partner the lamp ring needs: a lamp ring alone on pale tints measured 1.0-1.7:1 (D17).
  - **Dim the rest**: the selected color's remaining cells keep their pale tint; every other unplaced cell is darkened 35% toward `felt-deep`, at every zoom. Numbers on dimmed cells are `on-bone` or `bone`, whichever reads better: at worst 3.9:1 (pale tints of near-black), above the stronger tint's own worst case (3.2:1, a mid violet), at least 5.5:1 on cream and skin tones.
  - The style applies at once, including on a canvas that's already open, and changes nothing else: not the TalkBack canvas node, not the log, not the time-lapse (which never shows a selection).
  - QA each style on a dark, a cream and a skin-tone color, at fit, 8, 16 and 24dp, and under reduced motion.
- **Placed vs unplaced** (Phase 5 D18, T40): from 8dp up, a placed gem has its four bevels and an unplaced cell stays flat (pale tint, a 0.5dp seam 8% darker on its right and bottom, and its number from 16dp).
  - The bevels replace D18's 1dp edges. Their 24% shaded bottom bevel is what makes white, cream and skin-tone gems readable beside their tints, where the tint vs gem contrast is only 1.1-1.2:1.
  - The bevels are baked into the per-color sprite (T10).
  - QA the gem on a white, a cream, a light, mid and deep skin tone, a near-black and a mid violet, placed beside their own tints at 16 and 24dp (the mockup's hard-case strip).
- **Gestures** (Phase 5 D23):
  - **One finger, 16dp and up:** a drag that starts on an unplaced cell of the selected color paints. Cells of other colors along the path are skipped silently, with no wrong-tap haptic during a drag. A drag that starts anywhere else pans.
  - **One finger, under 16dp:** a drag pans.
  - **Two fingers:** always pan and zoom.
  - **Tap a placed gem:** nothing happens.
  - **Double-tap:** zooms to 24dp cells, or back to fit when already zoomed in. Taps never wait for a possible second tap: a double-tap counts only when neither tap places a gem (each lands on a placed gem, another color or outside the canvas). A quick second tap that would place (an unplaced cell of the selected color, at 16dp and up) is a normal tap and places its gem, so a corrective tap right after a miss never zooms out. Under 16dp the first tap already zooms in, so the second adds nothing.
  - **Long-press a cell:** selects its color.
  - **With a tool** (#20): a tap that would place a gem (16dp and up, an unplaced cell of the selected color) places the tool's batch instead: Brush 3×3 the selected color's unplaced cells in the 3×3 around it, Fill region the whole patch of the selected color 4-connected to it (through gems of that color already placed). Any other tap does what a plain tap does, so a wrong color is still rejected with no state change. A brush drag stamps the 3×3 around every cell the finger crosses (the drag still starts only on a placeable cell); with Fill region a drag paints single gems as with Tap. Long-press, two fingers and double-tap don't change.
- **A batch** (#20): placed at once (the save log gets its cells in the tool's order: Fill region ring by ring outward from the tap, the brush and Fill color nearest first), then revealed on the canvas as a sweep: each ring shows 16ms after the one before, squeezed so the sweep never passes 600ms, and each gem glints as its ring shows (from 20dp). Cells not yet reached keep their selected-color look. A brush stamp is one ring, so it shows at once like a drag. Milestone banners, "Color 7 done" and the finish wait until the sweep ends; the time-lapse replays the same order as a ripple.
- **First-time hints** (Phase 5 D12): three inline bone labels on a `felt-deep` chip over the canvas.
  - Each shows once ever and fades after its action or after 6s. One shows at a time: a tap-to-zoom shows hint 3 first, then hint 2.
  - The "shown" flags hold no photo data and may be backed up and transferred.
  - There is no dimming overlay and no Next button. TalkBack reads them.
  1. First canvas open: "Pinch or tap to zoom in until numbers appear"
  2. First time cells reach 16dp: "Pick a color below, then tap or drag its numbers"
  3. First tap-to-zoom: "Zoomed in so you can see the numbers"
- **Save failing** (Phase 5 D9): if a save fails because storage is full, a quiet line appears under the top bar: "Progress isn't saving: phone storage is full." It stays until a write succeeds.
- **Progress milestones** (#35, founder picks 2026-10-16): at 25, 50 and 75% of the canvas's gems, on every size.
  - Derived from the placement count only: nothing goes in the save log, the save format or the time-lapse. A milestone plays only when a placement in this session crosses it (the gem that reaches 25% rounded up: the 3,780th of 15,120), so a canvas reopened past one never replays it. The gem that finishes the picture is the completion's moment, not a milestone; on a tiny canvas where one gem crosses two, the higher one plays.
  - **Banner ("Breaking News", mockup B3):** a full-width `felt-deep` band across the middle of the canvas, with 2dp `lamp` edges top and bottom and an 8dp shadow. At its start a `bone` tag with the percentage in mono 20sp `on-bone` ("50%"); beside it the headline in display 22sp `bone` and the count in mono 12sp `text-muted` ("7,560 / 15,120 gems"). Motion is in Motion below.
  - Copy: 25% "A quarter done", 50% "Halfway there", 75% "Three quarters done".
  - Touches pass through the band, so painting never stops, and TalkBack doesn't focus it.
  - Chime and haptic as it starts (Haptics and sound). A **Milestones** toggle in Settings turns all of it off: no banner, sound, haptic or announcement.
- **Peek** (#35, founder picks 2026-10-16): a 48dp eye icon button labelled "Peek" in the top bar, between the title and the progress (the title ellipsizes to make room).
  - Holding it shows the **whole finished picture**, fitted (whole pixels per cell, centered) over the canvas on `felt-deep`; releasing fades back. A tap (released within 250ms) shows it for 1.5s.
  - Fades are 150ms each way. A touch on the shown picture hides it and places nothing.
  - No canvas gesture changes: long-press still selects a color and two fingers still pan and zoom (D23).
- **Share progress** (#36): a 48dp share icon button (Material's three-node "share", `bone`) labelled "Share progress", between the Peek eye and the progress. It shows from the first gem placed until the last one; on a blank canvas the top bar is unchanged. A tap opens the Share progress sheet over the canvas (see Share progress sheet → Progress share).
- **Top bar at large font** (Phase 5 D20): the title ellipsizes to 1 line. Above 1.5× font scale, progress shows as a mono "59%".
- **Wrong-color tap (eng D4, confirmed 2026-10-13):** rejected with **no state change**. Nothing goes in the log, no counter moves, no penalty applies. Feedback is one soft low haptic and the selected trough nudging 2dp sideways over 150ms. No red, no buzz, no toast.

**Haptics and sound.**

| Event | Haptic | Sound |
|---|---|---|
| Gem placed | `HapticFeedbackConstants.CLOCK_TICK`; drag throttled to ≤ 1 per 40ms | a tap: a glass "tink" over a soft knock on the tray, 1 of 5 takes, ±3 semitone random pitch, 35% volume. A drag: a softer, shorter, darker tick, 1 of 3 takes, ±1.5 semitones, 22% |
| Wrong color | `TEXT_HANDLE_MOVE` (lighter than the tick) | none |
| Color finished | `CONFIRM` (API 30+), else `CONTEXT_CLICK` | felt-flap thump and a small rising run of glass pings, 1 of 3 takes, 60% |
| Picture finished (#57) | a crescendo: 18 `PRIMITIVE_TICK`s that speed up and swell across the 2.4s sweep, then `PRIMITIVE_CLICK` + `QUICK_FALL` on the chime (no primitives: an amplitude waveform with the same shape; neither: the 400ms soft vibration on the chime). Reduced motion: the 400ms soft vibration on the chime | the low chime (1 of 3 takes, 70%) with its fifth (×1.5, 35%) and octave (×2, 25%) on top, all from the same take |
| A tool's batch (#20) | a light sweep: `PRIMITIVE_TICK`s every 60ms across the reveal, scale 0.3 → 0.6 (no primitives: 12ms amplitude pulses with the same shape; neither: one `CLOCK_TICK`). A brush stamp or reduced motion: one `CLOCK_TICK`, as for a gem. Color finished adds its `CONFIRM` when the sweep ends | a placing click 5 times across the sweep, each a semitone higher and its own take (a stamp or reduced motion: one click; a brush stamp in a drag: the drag tick). Color finished adds its sound at the end |
| Progress milestone (#35) | `CONFIRM` (API 30+), else `CONTEXT_CLICK` | a short bell a fifth above the finish chime, 1 of 3 voicings (plain, with a third, with an octave), 40% |

Sound and Haptics each get a Settings toggle. Both default to on. Each event picks a random take, never the same one twice in a row (T41, `SoundCues`). Every sound is synthesized in-house by `tools/make_sounds.py` (`tools/sounds/PROVENANCE.md`) and played with `USAGE_GAME`; the Sound toggle governs all of them.

**Music** (T42, #83). Six calm CC0 tracks from OpenGameArt (`tools/sounds/PROVENANCE.md`), Ogg Opus 48 kbps, loudness-matched to −20 LUFS, about 8 MB in all. They play in random order, never the same one twice in a row; the 34s Heavenly Loop plays three times in a row each time it comes up. Each start and resume fades in over 1s. Music has its own switch and volume (Settings → Music), apart from Sound. Once the first canvas has opened (D24) it plays on every screen of the app while all of these hold, and pauses (or doesn't start) otherwise:
- Music is on, and the ringer is on normal: silent or vibrate pauses it (`RINGER_MODE_CHANGED_ACTION`).
- Gemmory has audio focus (`AUDIOFOCUS_GAIN`, `USAGE_GAME` / `CONTENT_TYPE_MUSIC`): it doesn't start while other media plays, the system ducks it under a navigation prompt or notification, a transient loss (a call) pauses it until the gain, and a permanent loss (another app's media) waits for Gemmory's next foreground.
- No full-screen ad (interstitial or rewarded) and no time-lapse or progress-share export is running.
- The app is on screen: leaving it stops the music, and the next foreground starts a new track.
- No track has failed to decode this session: one that does turns music off until the next process start, with a non-fatal.

The volume keys move the music while it plays. The time-lapse is video only, so music is never part of it. The rules live in pure `Music` (shared, `MusicTest`); `MusicController` feeds it.

**Gallery tile.**
- A tile shows its real progress: placed gems in color, unplaced cells as pale tints, with the title and a mono % (or "Done") underneath.
- A remote picture never opened (T36, eng D12) draws its art when its tile first scrolls into view; until then (about a frame) the tile is plain `felt-deep` with its labels, title and %. Founder approved 2026-10-19.
- Locked pictures stay fully visible, with a small `felt-deep` label at the bottom-left (Phase 5 D14). Never put a padlock over the art or dim it.
  - The label is a 12dp play-triangle **vector** in bone plus "Watch to unlock" in Figtree 13sp bone (7.58:1), with `sm` radius and 4/8dp padding.
  - Never use the ▶ text glyph, which some fonts draw as an emoji.
  - The whole tile is the tap target. Labels disappear without animation when VIP starts.
- Every tile has a ⋮ menu, also opened by long-press (see Navigation map):
  - photo canvases: Rename, Delete;
  - bundled pictures: Start over;
  - first, on an unfinished tile with at least one gem placed: Share progress (#36). It opens the Share progress sheet over the gallery.
- **New** (#43): a remote picture that arrived on this phone in the last 7 days and hasn't been opened carries a `felt-deep` label at the tile's **top-left** (so it never meets "Watch to unlock" at the bottom-left): a 12dp four-point spark **vector** in bone plus "New" in Figtree 13sp bone, `sm` radius, 4/8dp padding, the same shape as the locked label. One line at any font scale (it ellipsizes rather than wraps). It goes, without animation, the first time the tile is tapped (whether it opens the canvas or the locked sheet) or after 7 days. Nothing is "New" on a phone's first catalog sync, so a new install isn't covered in labels. No `lamp`: lamp stays "the selected one".
- Remote pictures otherwise look and behave exactly like bundled ones: same tile, ⋮ menu (Start over), locked label, rewarded sheet and token. Nothing tells the player a picture came from the network, and there's no loading or offline state in the gallery: a picture shows only once its grid is downloaded and verified. A remote picture withdrawn from the catalog disappears unless it's been started.
- Photo canvas titles default to "My photo · 13 Oct" (Phase 5 D26).
- **Rename sheet** (#13): title "Rename", a one-line text field (at most 60 characters) with the current title, primary "Save" (disabled while blank) and "Keep" as a text button.
- **Damaged canvas** (Phase 5 D9): when the save or grid is refused and there's no last good file, the tile keeps its last thumbnail with a `felt-deep` label "Can't open".
  - Tapping it opens a sheet: "This canvas's file is damaged, so it can't be opened. You can delete it, or start the picture over."
  - The primary button is Delete for a photo canvas, Start over for a bundled picture. Keep is a text button.
- **My Photos empty** (Phase 5 D10):
  - Display title "Your photos, in gems".
  - Body: "Turn a photo of your own into a canvas you can finish. It never leaves this phone."
  - An inline primary "New from photo". The floating button is hidden on this empty tab.
- The "New from photo" floating button shows on both tabs. The grid gets 88dp of bottom padding so the button never covers a tile's %.
- **Allowance note** (Phase 5 D5, layout #14): when today's free photo canvas is used, a `bone` line on a `felt-deep` chip (`sm` radius, 12/8dp padding) sits beside the floating button, right-aligned with it, at most 280dp wide. When less than 160dp is left beside the button (200% font on a phone), the chip goes on its own line above the button. The grid's bottom padding follows their height. The line has the same copy as the Preview allowance line.

**Today's picture** (#34).
- One picture a day (bundled or remote, #43), picked on the phone: for a free player a locked picture they haven't unlocked or started, for VIP a picture they haven't started. When a remote picture still marked "New" is among them, the pick is from those (#43). The pick is date-seeded, kept for the whole local day (starting it doesn't move it), never the starter, and not yesterday's while another is left. It changes at local midnight, like the allowance; a moved clock or time zone can show another one (accepted risk).
- A `felt-deep` card (`lg` radius, 12dp padding) above the Pictures grid, spanning its width and scrolling with it (only on Pictures). Left: a 72dp-wide thumbnail at the picture's ratio, the finished picture until a gem is placed, then its real progress. Right, stacked: "Today's picture" (13sp `text-muted`), the title (Figtree 16sp/600 `bone`, up to 2 lines), a 13sp `text-muted` line, then a quiet button.
  - Free, not started: "Quick · free today", button "Start". Started: "Quick · 12% done" (the % in mono), "Continue". Finished: "Quick · done", no button (the card opens the completion still).
  - VIP: "Today's pick", "Medium", "Start" / "Continue". Nothing to unlock, so no "free".
  - The whole card opens the picture, like its tile. A free player's pick opens straight to the canvas, never the rewarded sheet. Its tile in the grid is an open picture today: no "Watch to unlock" label, and it sits with the open pictures in its size (#58 order otherwise unchanged).
  - No `lamp` on the card (lamp stays "the selected one"), and the quiet button keeps "New from photo" the only primary.
- What it grants (founder, 2026-10-17): free to start today; its first gem unlocks it for good, in the same unlocks a video writes, so Start over and a reinstall keep it. With no gem placed by midnight, it locks again. The grant is decided when its canvas opens, so a first gem placed after midnight on a canvas opened before it still unlocks it for good.
- Stored in the "today" preferences (a day and a picture id). No ad, no server.
- **"New today" chip** (T36): when Today's picture is that date's drop, the 13sp "Today's picture" / "Today's pick" line is replaced by a `felt` chip (`sm` radius, 3/8dp padding): the 12dp four-point spark vector and "New today" in Figtree 13sp `bone` (5.68:1 on felt). One line at any font scale (it ellipsizes). Its tile in the grid keeps the ordinary New label. VIP sees it too. A mystery drop (T43) keeps the chip.
- Nothing to pick (everything unlocked or started): no card. The streak (#33) doesn't change: finishing any picture counts the day.

**Tomorrow line** (T36, founder pick T2 2026-10-19; mockup `t40-gem-redesign/ui.html`).
- When tomorrow's drop is stored and verified, a row (56dp minimum, growing with its text at large font; the 40dp frost stays; plan-design-review D18) sits at the bottom of the Today card, under a 1dp hairline at 12% `bone`: a 40dp square frost of the picture (`sm` radius, see Hidden pictures), "Tomorrow" (13sp `text-muted`, the card's "Today's picture" label style) over "A new picture opens at midnight" (13sp `bone`), so the Today title stays the strongest line above the grid (plan-design-review D13). There is no name, no countdown and no "evening": a drop opens at local midnight.
- With no Today card, the line stands alone in its own `felt-deep` card (`lg` radius, 12dp padding) in the card's place.
- A tap shows a `felt-deep` toast "Opens tomorrow" (Figtree 16sp body, `bone`, `sm` radius, 12/8dp padding, 4s, a polite live region; plan-design-review D8), centred on the screen 16dp above the New from photo button, and opens nothing. A music chip still showing is dismissed first. Event `teaser_tapped`.
- It isn't in the grid, so the #58 order doesn't change. It goes at local midnight (the gallery reloads), when its picture becomes today's drop.
- TalkBack: one node, "Tomorrow's picture, opens tomorrow", button.

**Hidden pictures: the frost** (T36 teaser, T43 mystery). One look for any picture the player may not see yet: the finished picture averaged into 6 blocks across (rows by its aspect), scaled back up with a blur of 1/14 of its width, then covered with `felt-deep` at 45%. Its mood and colors show; its subject doesn't. It's drawn from the grid on the phone, never shipped as an image.

**Collection and event rows** (T37/T38, founder pick R1 2026-10-19).
- **Row:** above the grid, under the Today card, 16dp side gutters. The name (Figtree 16sp/600 `bone`), then "3 of 7 finished" (the count in mono 13sp, the words Figtree 13sp, `bone`), then one 13sp `bone` line, then a strip of member thumbnails that scrolls sideways: 64dp squares (center-cropped, `sm` radius, real progress like a tile), 8dp apart. A finished member gets a 16dp `felt-deep` square with a 10dp `bone` check at its top-right. Members keep their own labels (New, Watch to unlock) shrunk to the corner mark only. A thumbnail's tap is its tile's tap.
- **Not yet dropped:** one recessed well (64dp, `felt-deep`, the trough's inset shadow) at the strip's end: "2 more coming" (13sp `bone`, centered, wraps; plan-design-review D14). Events in their window: "4 more coming, one a day". Above 1.5× font scale (D18) the well shows only the count in mono 13sp ("+2") and the words join the count line ("3 of 7 finished · 2 more coming", an event's "· 4 more coming, one a day"); TalkBack is unchanged.
- **Lines:**
  - Collection in progress: "Finish all 7 for a keepsake picture."
  - Event in its window: "One new picture a day. Finish any 3 this week for a keepsake picture." At 3: "Keepsake earned: Golden Field." While its picture is on the way, the pending keepsake lines below. All 7 finished: "Complete. 1 unlock token added." (VIP: "Complete."); the keepsake came at the 3rd, so it isn't named again.
  - Event after its window: an ordinary collection row, "Finish all 7 for an unlock token." (VIP: "Finish all 7 to complete it."). No "missed", "ended" or dates.
  - Complete: "Keepsake added: Harbour Lights. 1 unlock token added." (VIP: no token).
  - A collection the catalog gives no keepsake (founder 2026-10-20): "Finish all 7 for an unlock token." (VIP: "Finish all 7 to complete it."), then "Complete. 1 unlock token added." (VIP: "Complete.").
- **Keepsake:** never shown before it's earned (C3). Once earned it joins the end of the row's strip; in the grid it follows the #58 order like any open picture (its size's run in the open group) with the New label for 7 days (plan-design-review D25).
- **Completion glint:** on the first gallery visit after the last member (or the event's 3rd) is finished, once the row is scrolled into view with nothing over it (D7, see One interruption per return), a `lamp` band (55%) sweeps once across the row over 900ms (ease-in-out), then a 2dp `lamp` edge fades in and out over 400ms. `lamp` here means "this moment", as on the milestone banner (#35). Reduced motion: no sweep and no edge; the line just shows.
- **How many:** at most two rows: the event during its window first (after a reward return's pinned row, D7), then the newest other collection not yet complete ("newest" is the catalog's list order, the last listed; founder 2026-10-20). Above 1.5× font scale only the first of them shows (plan-design-review D4: at 200% two rows push the grid off the first screen); the other is in All collections. A completed collection stays as one of the two for 7 days, then leaves. Rows are only shown once the catalog has been fetched and verified, and only after the player's first finished picture (plan-design-review D5: a new player's first screen keeps the starter in view); until then there are no rows and no All collections link.
- **All collections:** a text button "All collections" right after the last row, before the grid (plan-design-review D3: under a grid that grows by a drop a day it would sink out of reach). It shows when a fetched catalog has any collection the rows don't show, or any complete one. It opens a sheet titled "Collections" with every row, newest first, complete ones last. Each row there is the same component.
- **TalkBack:** the name line is a heading, "Seaside, 3 of 7 finished, 2 more coming"; the line is read after it; each thumbnail is one node read like its tile ("Beach Huts, done"); the well is folded into the heading. On completion, polite: "Seaside complete. Keepsake picture added." On an event's 3rd finish in its window, polite: "Golden Week: keepsake picture added." ("Golden Week: keepsake earned." while the picture is pending; founder 2026-10-20).
- No timer, progress bar, percentage or badge count anywhere on a row.
- **States** (plan-design-review D6):

| State | What the player sees |
|---|---|
| Catalog never fetched | no rows, no All collections link |
| Offline, cached verified catalog | rows as usual; the cached catalog counts as fetched, so a 7th finish offline still completes |
| Member dropped, grid not stored yet | it stays in the "more coming" well, never a blank thumbnail |
| Member damaged | its last thumbnail with a small × as its corner mark ("Can't open" doesn't fit 64dp; TalkBack still reads "can't open", founder 2026-10-20); a tap opens the tile's Can't open sheet |
| Keepsake earned, grid not on the phone | line "Keepsake earned. It arrives next time you're online."; the token is added at once; the picture (and the glint) come with the next successful check |
| Keepsake earned, copy failed (disk full) | line "Keepsake earned. It arrives when there's space on this phone."; retried on each gallery visit |
| All collections sheet | the same row component; strips scroll sideways inside the vertically scrolling sheet, and a sideways drag never drags the sheet. No empty state: the link shows only when there's something to list |

**Mystery picture** (T43). Everything about it stays hidden until it's **finished** (founder, plan-design-review D23; this replaces Phase 4's reveal at 50%):
- Every surface shows the frost instead of the art until the finish: tile (its "Can't open" thumbnail too), Today card, Tomorrow line, collection thumbnails, notification large icon. Tiles carry a top-left `felt-deep` label with a 12dp gem-outline vector (the reminder's small icon) and "Mystery" (the New label's shape; it takes New's place), with the real % under the tile as usual; a collection thumbnail shows the gem outline alone as its corner mark. There's no picture Preview screen: a tile or the Today card opens the canvas (founder, 2026-10-20: the Phase 5 "Preview" line is dropped).
- Names (plan-design-review D11): "Today's mystery picture" on its date in the app (Today card, tile, TalkBack), "Tonight's mystery picture" only in the evening reminder, "Mystery picture" after its date. The real title appears only at the finish: on the completion screen's meta line, then everywhere.
- Canvas (plan-design-review D9): every unplaced cell is one neutral tint, `bone` mixed 20% toward `felt-deep`, with its number in `on-bone`, instead of its own pale tint, so the picture builds only from the gems placed. Placed gems, numbers, the tray and the Selected color style work as usual (the selected color's cells use their stronger tint or ring; Dim the rest dims the neutral tint). The tints never come back: the last gem finishes the picture.
- The Peek eye, its TalkBack "Peek at picture" action, the canvas Share progress button and the tile ⋮ "Share progress" are hidden (not dimmed) on a mystery until it's finished. It's shared only from the completion screen (D23).
- Milestone banners are the ordinary ones ("Halfway there"); nothing is revealed at 50%. The finish celebration is the reveal: the chrome slides away over a picture that has never been shown whole.
- TalkBack: tile "Mystery picture, hidden until finished, 12% done"; Today card "Today's mystery picture, new today, hidden until finished, Quick, free to start today"; the canvas top bar's title reads the neutral name, and the canvas has no "Peek at picture" action.
- Which pictures: a dated drop flagged `mystery` in the catalog (the batch CSV's `mystery` column), stamped for versionCode 16, so older apps never list it. The canvas keeps the flag in its `record` file, so a mystery the catalog withdraws stays hidden until it's finished. `canvas_started` carries `mystery=true`.

**Daily play streak** (#33; Duolingo-style since #101, founder D1, `docs/designs/streak-screen.md`, mockup `docs/designs/mockups/streak-screen/ui.html`). This reverses the 2026-10-17 calm rules (no flame, no in-app streak moment) on purpose; the copy rules below still hold.
- **A day** counts once the player finishes a picture that day, any picture, bundled or their own photo (local date, the allowance's clock). Placing gems without finishing, or reopening a finished picture, doesn't count. A clock moved back changes nothing.
- **Covers:** a missed day is covered, in order, by the weekly day off (one in any 7 days, rolling), the VIP weekly freeze (VIP only, one in any 7 days), then a streak freeze held (#102). Covered days aren't counted. A gap with a day nothing covers starts over at the next day played.
- **The flame** (G1): flat, two shapes (outer and core). Grey `ember` until today's picture is finished, lit after, in the length's color. It flickers (1.6s loop) at 40dp and up; on the chip, the week row, the calendar and under reduced motion it's still. It ignites (800ms) on the celebration.
- **Chip** (G18): from day 1, a 48dp `felt-deep` button (`sm` radius, 8/10dp padding) at the end of the tabs row: the 16dp flame, the count in mono 15sp and "›", all `bone`. Hidden while there's no streak (0 or broken), back once a new one starts; while a broken streak can still be repaired it shows a grey flame "0 ›" (#102, G4; TalkBack "0 days in a row, your 12 days can still be restored, button, opens your streak"). It opens Your streak. TalkBack, one node: "6 days in a row, not done today, button, opens your streak". At 200% it wraps under the tabs.
- **Nudge** (#102, G2-G4; mockup `streak-b.html` section 4): on the Pictures tab, while the streak is 2+ and nothing is finished today, a line in the Today card under a 1dp hairline (12% `bone`), above the Tomorrow line; with no Today card it's its own `felt-deep` card in that slot, the Tomorrow line under it. The 20dp grey flame, "Finish a picture today to keep your 6 days" (`body`, `bone`, the number in mono), the hint in `label` `text-muted`, and "›"; at least 56dp, wrapping at large font (above 1.5x the collection rows already drop to one, so the nudge takes the 2nd row's place). The hint names the target: Today's picture if it's Quick and unfinished ("Today's picture is Quick"), else the unfinished canvas closest to done, any age ("Harbour is 82% done"; "Your photo canvas is 82% done"; "Your mystery picture is 82% done"), else a Quick picture a tap opens ("Beach Huts is Quick"); no hint when there's none, and the tap then opens Your streak. A missed day a finish covers adds a muted "Yesterday is covered when you finish" ("Tuesday is…", several: "Your missed days are covered when you finish"). The tap opens the target's canvas like its tile (entry `streak_nudge`). Broken, in the repair window: "Your 12 days can still be restored" / "See your streak" → Your streak; gone with the window. Gone once a picture is finished today. TalkBack: one node, a button, its lines joined ("Finish a picture today to keep your 6 days. Today's picture is Quick"). Hidden by the O5 switch.
- **Widget** (#103, G17; mockup `streak-c.html`): one 2×2 home-screen widget (Glance), not resizable. A fixed `felt` ground with the launcher's corner radius (16dp before Android 12), never dynamic color, so it reads the same on any wallpaper. The days in a row top left in Atkinson Hyperlegible Mono 700 at 44dp, drawn as a bitmap (a launcher draws a widget's text in the system font), sized in dp and shrunk to fit beside the flame, so a 4-digit streak never outgrows the tile; the flame (32dp, still) top right, grey until today's picture is finished and lit after in the length's color; one line at the bottom in the system font (Roboto on a Pixel) 14sp `text-muted`, at most two lines with font scale: "Not yet today" / "Done today" / "Start a streak" (0: never played, broken, or in the repair window; the repair lives on Your streak). A 2×2 under 140dp wide gets 32dp, 24dp, 12sp and 10dp padding. The line in the system font is the one departure from the type scale (Figtree can't reach a launcher). One TalkBack node: "Gemmory streak, 6 days, done today" / "…, not done today" / "Gemmory streak, start a streak". The whole tile opens Your streak over the Gallery. It updates on every streak change, just after local midnight (within about 10 minutes, later while the phone dozes), and on a clock or time-zone change. Never a picture or a photo: a home screen is public. Picker: "Streak", "Your streak and whether today's picture is done", a fixed preview (6, lit, "Done today").
- **Celebration** (E3, G6-G11, G15): full screen on `felt-deep-dark` after leaving a fresh completion on a new streak day, from day 1. The flame (96dp, 112dp on milestones) ignites, the `streak-hero` number rolls up from the day before (600ms from 500ms), then the title and line in the copy table below, the week row (the locale's week; covered days in their marks, today in the `lamp` outline), and on every 5th day for players without VIP "Your 5-day gift spin is in the bag." Primary "Continue" (back does the same) and a text "See your streak"; on milestones the text button is "Share", which makes the milestone share card and opens the Android share sheet over the celebration (#104, G10; back returns to it). No auto-advance. Milestones 7, 30, 50, 100, 365 get the bigger flame and a 2dp `lamp` edge that brightens and fades once (1.4s). Sound: the milestone bell on ordinary days, the finish chime on milestones, with the confirm haptic; silent and still when Settings → Milestones is off (G15). TalkBack: the words are one node and one polite announcement ("5 days in a row. Your day off on Tuesday kept it going."); the roll-up isn't read digit by digit. The moment scrolls at 200% font; the buttons stay at the bottom.

| Case | Title | Line |
|---|---|---|
| Day 1 | "You started a streak" | "Finish a picture tomorrow to make it 2." |
| Ordinary | "n days in a row" | "Finish one tomorrow to make it n+1." |
| Kept by the day off | "n days in a row" | "Your day off on Tuesday kept it going." |
| Kept by a freeze (#102) | "n days in a row" | "A streak freeze kept it going on Tuesday. 1 left." (two: "2 streak freezes kept it going. None left.") |
| Kept by the VIP freeze | "n days in a row" | "Your VIP freeze kept it going." |
| Several covers | "n days in a row" | "Your day off and a streak freeze kept it going." |
| Milestone 7 / 30 / 50 / 100 / 365 | "A week of gems" / "A month of gems" / "50 days of gems" / "100 days of gems" / "A year of gems" | "n days in a row." |

- **Your streak** (the streak screen): top app bar "Your streak". The hero (72dp flame, the number in `streak-hero` 56sp, "days in a row", "Best: n" when there was one; stacked at 200%). A `felt-deep` section with today's status: "Done for today" with a small lit flame, or "Finish a picture today to keep it" (never started or broken: "Finish any picture to start a streak.") with a quiet "Today's picture" button when there is one to finish; a missed day awaiting cover adds "Yesterday is covered by your day off when you finish today." (G13). The covers while a streak stands: "Day off ready" / "Day off used Saturday" + "Ready again next Saturday", then for VIP the same for "VIP freeze", for everyone else "Streak freezes 1" + "Covers a missed day. Hold up to 2." with the freeze mark (#102; VIP never spends held freezes, so it doesn't list them). "Next gift spin at n days in a row" (not VIP). Then the month calendar.
- **Calendar** (G16, the cell table): 7 columns in the locale's week, max 420dp, cells 1:1 with a 2dp radius on `felt` for past days. A finished day shows the first picture finished that day, center-cropped (photo canvases included: it's in-app); a finished day whose canvas is gone, or a kept day before the log existed (O7), a small still lit flame; day off a 2dp `bone` outline; a freeze or VIP freeze `patina` with a `felt-deep` cross; repaired (#102) `bone` with a dashed `on-bone` stitch; awaiting cover a dashed `text-muted` outline; today a 2dp `lamp` outline; other past days the date in mono 11sp `text-muted` (hidden at 200% in picture cells); future days the date only. Months go back to the first month with history and never ahead; 48dp arrows "Previous month" / "Next month". Cells aren't tappable; each is one TalkBack node ("Tuesday 14, finished Lighthouse", "…, day off", "Today, not finished yet").
- **Copy rules** (G7): true facts and weekday names only; no countdowns, hours left, "!", guilt, "don't lose" or "last chance". Never on the play canvas or the completion screen. The opt-in evening reminder (T35) may state the streak fact, only on a night it really breaks at midnight with every cover counted.
- **One switch** (O5): a build flag turns the celebration (and #102's nudge) off; the chip and the screen stay.
- Every 5th day in a row earns a gift wheel spin for players without VIP (#38, see Inventory); the Inventory sheet and Your streak show the way to the next one.
- Stored in the "streak" preferences and the day log `streak/history` ("GMRS": each day, how it counted and its first canvas id), both backed up (no photo data; a restore can't add days past the stored streak).

**Evening reminder** (T35; mockup `t40-gem-redesign/ui.html`).
- **The ask** (a sheet on the gallery, after leaving the completion screen of the 1st finished picture; again after the 3rd if "Not now"; never at first launch, never over the completion screen, Share or the canvas). Finishes count from the version that brought T35, so a player who already had finishes is asked at their next one (founder, 2026-10-20):
  - Title "Remind me in the evening?" (display 22sp).
  - Body "One quiet note a day around 20:30, only when there's something to play: a new picture, today's picture, a streak to keep, or one you're close to finishing." The time is the current setting (D10: the body names every reason the reminder can send).
  - Muted line "Change the time or turn it off in Settings." (13sp `text-muted`, valid on sheets).
  - Primary "Yes, remind me"; text button "Not now". On Android 13+ the system permission dialog follows Yes. Swipe, back or the scrim count as "Not now". If Gemmory's notifications or the "Evening reminder" channel are already off (Android 10-12, or switched off on 13+), Yes turns reminders on and opens the app's system notification page, as the Settings switch does; no "Reminders on" line then, and Settings shows the off line until they're allowed (founder, 2026-10-20).
  - **After** (plan-design-review D12): once reminders are on (granted, or Yes on Android 12 and below), one gallery line, once, in the paywall success line's style (`bone`, a polite live region): "Reminders on, around 20:30. Change it in Settings." After a system denial or "Not now": no message.
- **Notification** (channel "Evening reminder", default importance, description "One note a day about something new to play or a picture you're close to finishing."). Title / text by reason:

| Reason | Title | Text |
|---|---|---|
| Tonight's drop | "Tonight: Lighthouse" | "A new picture, free to start today." |
| Today's picture | "Today's picture: Beach Huts" | "A Quick picture, free to start today." (VIP: "Today's pick: Beach Huts" / "A Quick picture, ready when you are.") |
| Streak (#102, G5) | "Your 6-day streak ends at midnight" | "Finish any picture tonight to keep it. Lighthouse is Quick." (the nudge's target: "Harbour is 82% done." / "Your photo canvas is 82% done." / "Your mystery picture is 82% done."; none without one) |
| Almost finished | "You're 82% through Venice" | "Pick up where you left off." (photo canvas: "You're 82% through your photo canvas") |
| Mystery drop (T43, until finished) | "Tonight's mystery picture" | "A new picture, hidden until you finish it." |
| Mystery as Today's picture or almost finished (T43, after its date) | "Today's picture: Mystery picture" / "You're 82% through your mystery picture" | as their rows above |

  - **Priority:** the first reason that applies: a streak that really ends at midnight (day off, VIP freeze and freezes held all counted; first since #102, G5), tonight's drop, Today's picture, almost finished. On any other night it's drop → Today → almost finished, as before. A streak still applies while the player is playing; the others are skipped then.
  - **Small icon:** a white square-gem outline vector (an outer square and its table); accent color `patina`.
  - **Large icon:** the picture drawn flat (whole pixels per cell, center-cropped square) for a drop, Today's picture or an almost-finished bundled or remote picture; the app icon for a streak and for any photo canvas (never the photo); the frost for a mystery picture until it's finished.
  - No action buttons, no progress bar, no big-picture style. The % in the title is rounded down.
  - **Lock screen** (plan-design-review D21, founder approved the ask and notification copy above): `VISIBILITY_PUBLIC`, full text, since it never carries a photo; the player's own Android lock-screen setting still applies.
- **Copy rules:** name the picture; state only true facts; never "don't lose", "last chance", "hurry", "you're missing", a countdown or an exclamation mark.

**One interruption per return to the gallery** (T35, T39 and the ad rules; order confirmed by the founder, plan-design-review D22). Leaving a completion screen after a fresh finish shows at most one of these, first that applies:
1. The reminder ask (1st or 3rd finish only).
2. Play's in-app review sheet (T39: attempts at 3, 10, 25 finishes, at least 14 days apart, `features.review` on, not on the day of the first finish). It's Play's sheet, drawn and rate-limited by Play.
3. An interstitial, under the existing caps.

A skipped ask or review attempt waits for the next eligible finish. None of them appears over the canvas, the completion screen, Share or the completion still.

**The streak celebration is part of the exit, not one of these** (#101, G6, founder O4 "the ad should still show after the day"): on a new streak day it shows first, and the one interruption runs when the gallery next shows, after Continue or after Your streak closes, never between them. A rotation keeps it waiting.

**A reward return shows none of them** (plan-design-review D7): the return that completes a collection or earns an event's keepsake has no interstitial, and the ask or review waits for the next eligible finish. The gallery scrolls that row into view (it takes a row slot for this visit, at any font size) and its completion glint plays once the row is on screen.

**Inventory** (#38, founder picks 2026-10-17; mockups `~/.gstack/projects/Gemmory/designs/t25-inventory/`). Not for VIP, who has everything unlimited.
- **Bag (E1):** a 48dp icon button in the gallery top bar, before "VIP", labelled "Inventory". While a gift spin waits, a 7dp `bone` square (2dp radius) sits at its top-right and it reads "Inventory, gift spin waiting".
- **Inventory sheet:** title "Inventory"; four rows, each a 44dp `felt` well with the item's icon, the count in mono 18sp, then the name (body) and a muted line: Fills "One fills a region, the rest of a color, or a brush stroke." · Photo canvases "For a photo after today's free one. Kept until you use them." · Unlock tokens "Opens any locked picture for good." · Streak freezes "Covers a missed day after your day off. Hold up to 2." (#102; the freeze mark: a square with a cross; shown at 0 too, since the wheel is the only way to earn one). Each row is one TalkBack node ("Fills: 5. One fills…"). Then primary "Watch a video: +5 fills" (the fills rewarded sheet), and the muted line "Next gift spin at 5 days in a row" over five 6dp steps, `bone` for the days so far. While spins wait, a primary "Spin for your 5-day gift" ("Spin for your gift (2 waiting)") comes first and the video becomes a quiet button.
- **Gift wheel (W1):** a sheet "Your gift" with a 220dp wheel of five flat segments sized by their odds, clockwise from the top: 5 fills 45% (`patina`), 10 fills 20% (`patina-text`), 1 photo canvas 15% (`bone`), 1 unlock token 10% (`text-muted`), Streak freeze 10% (`felt` with a `bone` cross, the freeze mark; #102, E7), `felt-deep` seams and hub, a `bone` pointer at the top; the odds in words below ("5 fills 45%" …). With 2 freezes held, the freeze slice reads and pays "5 fills" (it keeps its cross); its label changes only between spins. "Spin": the prize is rolled and added first, then the wheel turns 5 times and lands on it over 3.2s easing out, a gem tick (haptic + click) as each segment passes the pointer and the milestone chime on landing; "5 fills added to your inventory." ("A streak freeze added to your inventory.") in a polite live region; then "Spin" again while more wait, else a quiet "Done". TalkBack reads the wheel as "Gift wheel. 5 fills 45%, …, Streak freeze 10%".
- Stored on the phone, backed up like the unlocks.

**Photo flow states** (Phase 5 D4).

| Step | State | What the user sees |
|---|---|---|
| Photo Picker | cancelled | back to the gallery, no message |
| Decode | fails (corrupt, unsupported, cloud-only) | inline on the gallery: "This photo couldn't be opened. Try another one." |
| Crop | default | ratio chips 1:1 / 3:4 / 4:3 (selected chip has a lamp outline). Default ratio comes from the photo's orientation (portrait → 3:4, landscape → 4:3, square → 1:1). Pinch and drag inside the frame; no rotation. Primary "Next" |
| Size picker | face check running | the size rows are enabled; a muted line reads "Checking for faces…". Pre-selection and the face hint apply when the check ends (≤ 1s; on failure it counts as "no face") |
| Preview | generating | "Setting the gems…" with a 2dp indeterminate line where the preview will appear; Start is disabled |
| Preview | another color choice converting (#29) | the last preview stays at 35% under a 2dp indeterminate line ("Updating gem preview" to TalkBack); Start waits for it |
| Preview | converter fails | "Couldn't make this canvas. Try Medium or another photo." with a quiet "Back" |
| Preview | faces disabled (`faceMinSize` = Disabled) | hint "Faces aren't supported yet"; Start is replaced by primary "Choose another photo" |

**Size option.**
- Selected: 2dp lamp inset. Disabled for face photos below `faceMinSize`: 50% opacity, not tappable, with the reason in the hint.
- Face hint copy follows the faceMinSize table: "Faces look best at Medium or Big" / "Faces need Big" / "Faces aren't supported yet".

**Preview + Start.**
- Shows a preview of the whole canvas (table-cut gems where a cell is at least 8px, flat below; T40), then the Colors row, then "Medium · 8,112 gems · 24 colors" and the allowance line ("Uses today's free photo canvas.").
- Start is single-flight: it shows a pressed state and ignores repeat taps.
- **Colors row** (#29, founder pick P1 from `~/.gstack/projects/Gemmory/designs/t18-more-colors/placement.html`): a 13sp `bone` label "Colors", then three troughs side by side, Fewer / Standard / More: `felt-deep`, 8dp radius, 52dp tall at least (they grow with the font, the name wraps to 2 lines), the name in Figtree 13sp `bone` over the count in mono 13sp `text-muted`. The chosen one lifts 3dp with a 2dp `lamp` outline, as a tray trough does. While More needs a video (no VIP, not unlocked for this photo), it carries a 10dp `bone` play mark in its top-right corner.
  - Counts per size (caps; the real palette can be smaller): Quick 12 / 16 / 24, Medium 16 / 24 / 40, Big 24 / 36 / 60. Standard is the cap from before #29, and the size picker shows it.
  - A tap converts that size at those colors once (≈0.3 s on a phone) and swaps the preview; the size line shows the real count. The choice stays for the whole photo draft (size changes and back-and-forth keep it). Standard is the default.
  - Fewer and Standard are free. More is VIP, or one rewarded video for this photo: the colors video, or a canvas video watched on this Preview (one video gets the canvas and More, so nobody watches two).
  - The row is hidden when faces aren't supported (Start is replaced); it stays when a conversion fails, so the player can switch back.
- **Primary and line with More** (#29):

| Situation | Line | Primary |
|---|---|---|
| More, no VIP, not unlocked, a free or kept canvas left | "More colors: watch a video, or get VIP." | "Watch a video for more colors" (the colors rewarded sheet); then Start |
| More, not unlocked, today's free one used | "One video gets this canvas with more colors." | "Watch a video" (the canvas rewarded sheet); its reward also unlocks More |
| More, unlocked or VIP | the allowance line below | as below |
- **Allowance line states** (Phase 5 D5):

| Situation | Line | Start |
|---|---|---|
| Free one available | "Uses today's free photo canvas." | Start |
| Free one used | "Today's free one is used. Watch a video for another." | primary becomes "Watch a video" (rewarded states) |
| Kept canvas in the inventory (#38) | "Uses a kept photo canvas (2 left)." | Start |
| Bonus earned before the #38 update (that day only) | "Uses a bonus canvas from a video." | Start |
| VIP | no line | Start |

**Completion: "lights down" and the finish celebration, the one authored moment** (#57).
1. The chrome slides away over 250ms and the screen dims ~30%.
2. **Sparkle sweep** (2.4s): every gem catches the light in turn along the diagonal from top-left to bottom-right (a `#FFFAEB` flash at up to 55%, each tile lit for 9% of the sweep, staggered by up to 5% so the wave twinkles), about one in seven with a small 4-point facet star, over the old raking band at 14%. A gem smaller than 4dp at fit (Big canvases) glints in square tiles of gems at least 4dp across. The finish haptic crescendo rises under it.
3. The chime and the crescendo's peak land as the sweep reaches the far corner.
4. **Gem confetti** (2s, the hold): square table-cut gems (4-9dp, drawn with the ≥ 20dp sprite so the lit top bevel and shaded bottom bevel show as they tumble) in the picture's own colors, picked in proportion to its gem counts. 60% spill out from under the picture's bottom edge into the room, 40% rain from above the screen; they tumble under gravity, pass **behind** the picture (never over it), and all fade out over the last 400ms. 180 on a phone, up to 360 on a wide screen.
5. "Finished" (display-lg), the meta line (title · gems · time), then Share (primary) and Done (quiet) fade in, at the same 5.05s as before the celebration.

Reduced motion skips the sparkle, the crescendo and the confetti: the dim, the chime, the 400ms soft haptic and the 2s hold remain. There is no Settings toggle for the celebration; Sound and Haptics follow their own toggles. The interstitial (if allowed) can appear only after leaving this screen.

**Share and the meta line** (#13, #4): Share is the primary button; it opens the Share progress sheet below. A killed encode's message ("The video stopped when the app closed. Tap Share to try again.") is a muted line under Share for that visit. The lights-down dim is on the room around the picture; the picture itself stays lit. The meta line's time is active play time ("5 min", "1 h 12 min").

A finished picture reopened from the gallery shows the **completion still**:
- the picture at rest with no sparkle, confetti, chime or haptic;
- the "Finished" title and meta line;
- Share (primary), Done (quiet) and a "Play again" text button. "Play again" asks the Start over confirm.

**Share progress sheet.**
- Title "Making your video", the line "About 15 seconds of sparkle. Keep the app open.", a 6dp bone progress bar, a mono %, and a Cancel text button.
- A drop to 720p is silent.
- **PNG fallback:** the sheet title becomes "Here's a picture instead", with the body "Video isn't supported on this phone, so here's a before/after picture instead." It shows both thumbnails (labelled "Before" and "After" in muted 13sp) and a primary "Share picture" button. The PNG itself is 1080×1920: "Before" (the cropped photo, or the empty tint grid for a bundled picture) above "After", on the same band with the same title, app line and watermark row as the video.
- Dismissing the sheet while it runs is a Cancel. The failure and disk-full messages keep the sheet's "Making your video" title; swipe, back or the scrim closes them.
- **Progress share** (#36, from the canvas's Share progress or a tile's ⋮): the same sheet, states and copy, with two differences in the PNG fallback: the body is "Video isn't supported on this phone, so here's a picture of your progress instead." and the second label is "So far" (on the sheet and in the PNG). Cancel, or a dismissed share sheet, goes back to the canvas or the gallery with no message. A process killed mid-encode shows nothing later: tapping Share progress again starts over.
- **Edge states** (Phase 5 D8):
  - **Background:** encoding continues while the process lives. There is no foreground service.
  - **Process killed mid-encode:** on the next visit to that completion screen: "The video stopped when the app closed. Tap Share to try again."
  - **Video and picture fallback both fail:** "Couldn't make the video. Try again." with a quiet "Try again".
  - **Disk full:** "Not enough space on this phone to make the video."
  - **Cancel, or the share sheet is dismissed:** back to completion with no message.

**Rewarded states** (locked picture, extra photo canvas, fills or More colors; the streak repair draws the same states inline, see Repair panel below):

| State | Copy | Actions |
|---|---|---|
| Loading (≤ 5s) | primary button disabled: "Loading video…", plus a 2dp indeterminate line | none |
| Video ready (#7) | sheet title "Watch to unlock" / "Another photo canvas" / "More fills" (#38) / "More colors" (#29); body "Watch a short video to unlock this picture for good." / "Watch a short video for one more photo canvas. It's kept until you use it." / "Watch a short video for 5 fills. Each fills a region, the rest of a color, or a brush stroke." / "Watch a short video for more colors on this photo canvas." | primary "Watch video". A locked picture with unlock tokens held also gets a quiet "Use an unlock token · 1 left" under it (#38), in every state but while the video shows; one tap shows "Unlocked" and opens it, as a video does |
| Online no-fill, grace available | "No video right now. This one's on us today." (fills: "These are on us today.") | primary "Unlock picture" / "Start canvas" / "Get 5 fills" / "Use more colors" (#29); text "Or get VIP: every gallery picture unlocked" / "Or get VIP: unlimited photo canvases" / "Or get VIP: unlimited fills" / "Or get VIP: more colors on every photo" |
| Online no-fill, grace used | "No video right now. Try again in a bit." | quiet "Try again"; text "Get VIP: no videos needed" |
| Offline | "You're offline, connect to watch a video" (in `fault`) | quiet "Try again"; text "Get VIP: no videos needed". No grace. |
| Reward granted (Phase 5 D11) | locked picture: the sheet shows "Unlocked" for 600ms, then the canvas opens. Extra canvas: back to Preview with "Uses a kept photo canvas (1 left)." (and More unlocked for this photo, #29). More colors (#29): "More colors unlocked" for 600ms, then back to Preview with Start | none |
| Video closed early (Phase 5 D11) | back to the sheet; muted line "Watch to the end to unlock." No grace, no penalty | the primary button is enabled again |

**Repair panel** (#102, Streak B PR 2; mockup `streak-b.html` section 3): the rewarded states drawn inline as the first `felt-deep` section of Your streak while a broken streak of 2+ days can come back (within 2 days of the day it ended). Never the sheet, never on the canvas or the completion screen. Title "Bring back your 12 days" (`display`). Under every state the muted line "You can do this until Thursday." (the day it ended + 2).

| State | Copy | Actions |
|---|---|---|
| Ready | "Your streak ended on Tuesday. Watch a short video to restore it." | primary "Watch a video": one tap loads the video and shows it as soon as it's there (the screen preloads while an offer stands) |
| VIP, weekly repair ready | "Your streak ended on Tuesday. Your weekly VIP repair can bring it back." | primary "Bring it back", no video |
| VIP, weekly repair used | the Ready panel plus muted "VIP repair used Monday · ready again next Monday" | as Ready |
| Loading (≤ 5s) | the Ready line | primary disabled "Loading video…", 2dp indeterminate line |
| Online no-fill, grace allowed | "No video right now. This one's on us today." | primary "Bring it back" (tapped, like every grace) |
| Online no-fill, grace used today or a grace repair in the last 30 days | "No video right now. Try again in a bit." | quiet "Try again" |
| Offline | "You're offline. Connect to watch a video." (`fault` on `felt-deep`) | quiet "Try again". No grace |
| Video closed early | the Ready line plus muted "Watch to the end to bring it back." | primary enabled again |
| Restored | the panel goes; the hero number rolls 0 → n over 600ms (shown at once under reduced motion); "Streak back: 12 days" under the hero, announced politely; the status section adds "Monday and Tuesday repaired"; the missed days show the repaired stitch | none |

Video repairs have no limit; VIP gets one free repair every 7 days; a grace repair at most once in 30 days (founder, 2026-10-22). The celebration on day 1 of a new streak while the old one can come back says "Your 12 days can still be restored" in place of "See your streak" and opens Your streak.

**Paywall.**
- "Gemmory VIP" title, then five perk lines with patina square bullets: Every gallery picture unlocked (T38/C3: collection and event keepsakes are earned by playing, for VIP too), Unlimited photo canvases, No ads ever, No watermark on your videos, Brush and fill tools (#20).
- Yearly (pre-selected, lamp outline, shows the per-week equivalent) and Weekly plan rows. Prices are in mono. Rows read "Yearly · $X / year ($Y a week)" and "Weekly · $X / week"; $Y is the yearly price over 52 weeks, rounded half up in the currency's own decimals. Base plans only, no trials or offers.
- One primary "Start VIP" button, then a "Just remove ads" one-time row, then the renewal fine print.
- No countdowns, no fake strike-through prices, no delayed close button. Prices come from Play Billing `ProductDetails`, never hard-coded.
- **States** (Phase 5 D6):

| State | What the user sees |
|---|---|
| Prices loading | plan rows as `felt-raised` placeholders; Start VIP disabled |
| Prices fail / offline | "Prices couldn't load. Connect and try again." + quiet "Try again" |
| Purchase pending | "Payment pending. VIP starts when Google Play confirms it." (remove-ads: "Payment pending. Ads turn off when Google Play confirms it.") |
| User cancels | back to the paywall, no message |
| Purchase error | "Google Play couldn't finish the purchase. You weren't charged." |
| Success | the paywall closes; one gallery line: "You're VIP. Every gallery picture is unlocked." (or "Ads are off for good." for remove-ads) |
| Already owns remove-ads | the "Just remove ads" row is hidden |
| Already VIP | the paywall isn't reachable; "VIP" and the Membership row open Play's subscription page |

**Settings.**
- Sound, Haptics, Music and Music volume (T42), Milestones (#35, a toggle, on by default; stored with Sound and Haptics), Reminders and Reminder time (T35), Selected color, Achievements (#49, only where Play Games can run), Membership (shows `VIP · yearly` / `Ads removed` / `Free`), Manage subscription (VIP only, opens Play), Restore purchases, Privacy choices (UMP, shown only when UMP says privacy options are required), Delete all photo canvases, Privacy policy, and Version (mono).
- **Selected color** (#19): a row right after Haptics, built like Membership: "Selected color" at the start, the current style's name at the end (both `bone` body). Tapping it opens the **Selected color sheet**:
  - Title "Selected color" (display 22sp), then three radio rows, each at least 56dp tall: a 20dp radio mark at the start, the option name in `bone` body, and a one-line description under it in `text-muted` 13sp (valid on sheets).
  - Options and descriptions:
    - "Stronger tint" / "Its cells turn a deeper shade" (selected by default)
    - "Outline" / "A ring around its cells when zoomed in"
    - "Dim the rest" / "Every other color steps back"
  - Radio mark: unselected is a 1.5dp `bone` ring; selected is a 2dp `lamp` ring with an 8dp `lamp` dot (`lamp` = "this is the selected one"). The whole row is the target.
  - Tapping an option saves it and closes the sheet. Swipe, back or the scrim close it without a change. Focus goes to the title on open and back to the row on close, like every sheet.
  - TalkBack: the row reads "Selected color, Stronger tint"; the three sheet rows form one radio group, each one node: "Stronger tint, Its cells turn a deeper shade, selected" (selected state from the radio semantics).
  - Stored with Sound and Haptics (no photo data, may be backed up).
- **Music** (T42): a switch row after Haptics, on by default. While it's on, a **Music volume** row follows: the label, a Material slider (4dp `felt-raised` track, `bone` active track and 20dp `bone` thumb) in steps of 10%, and the value in mono 13sp ("30%"), from 10% to 100%, default 30%. Off hides the volume row. TalkBack: "Music, on, switch"; "Music volume, 30 percent, slider".
  - **First music** (plan-design-review D24): the first music plays at 30% when the first canvas opens, never on the cold-opened gallery, and not at all while the ringer is silent or vibrate. From then on it plays in the gallery and on the canvas as usual. On the next gallery visit while it plays, a one-time `felt-deep` chip sits above the New from photo button (after any sheet on that return closes) (right-aligned with it, `sm` radius): "Music is playing" (Figtree 16sp body, `bone`, as the allowance chip; D14) + a text button "Turn off" (48dp). It goes when tapped, when a tile is tapped, or after 8s, and never shows again. "Turn off" switches Music off, with a polite line "Music off. Turn it back on in Settings." Not shown if music didn't start. Volume keys move the music while it plays.
  - **With TalkBack on** (touch exploration; plan-design-review D20): the chip has no 8s timeout and stays until it's used or a tile is tapped. (D24 already starts music only at the first canvas, the rule D20 first set for TalkBack.)
- **Reminders** (T35): a switch row after Milestones, "Reminders" with the 13sp line "Around 20:30" under it while on (`bone`). While on, a **Reminder time** row follows, built like Selected color: "Reminder time" at the start, "20:30" at the end.
  - **Reminder time sheet:** title "Reminder time", the line "We'll remind you around 20:30." (16sp `text-muted`, updates as the choice moves), then a scrolling radio list from 17:00 to 22:00 in 15-minute steps (21 rows, 56dp, the Selected color sheet's radio and lamp mark), opened scrolled to the current one. Times follow the phone's 12/24-hour setting ("8:30 PM"). A tap saves and closes; swipe, back or the scrim close without a change.
  - **Notifications off** (blocked by the system, or the channel turned off): the switch shows off, the time row hides, and under the switch: "Notifications are off for Gemmory" in `bone` with the 6dp `fault` square before it (the plain-felt problem-line rule), then a quiet "Open settings" (the app's system notification page). Turning the switch on asks for permission (13+) or opens that page, whichever Android allows. A phone that was never asked (Android 13+ before the first Yes) isn't "off": no line until the player wants reminders or has answered Android's dialog.
  - TalkBack: "Reminders, on, switch. Around 20:30"; "Reminder time, 20:30, button"; off: "Reminders, off, switch. Notifications are off for Gemmory."
- **Achievements** (#49): a row right after Selected color, built like it: "Achievements" at the start, "Play Games" at the end (both `bone` body, 56dp). It shows only where Play Games can run (Google Play services present and the build has a Play Games project id) and once Play Games has started in this run of the app (T45: from the first finish on); otherwise there's no row at all, and no other way in.
  - Tapping it opens Play Games' own achievements screen. If the player isn't signed in to Play Games, the tap is the one sign-in they can ask for (Play Games' own sheet), then the screen opens.
  - While the tap runs, the end text reads "Checking…" and the row ignores more taps.
  - A declined or closed sign-in shows nothing. Anything else that fails (offline, Play Games unreachable, no screen to open) adds one line under the row for this visit only: "Couldn't reach Play Games. Try again." in `bone` with the 6dp `fault` square before it (offline copy on plain felt), in a polite live region.
  - TalkBack: "Achievements, Play Games, button" (or "Achievements, Checking…" while it runs); the failure line is read out when it appears.
  - Play Games' own first-run sheet: a player with no Play Games profile sees Google's "Create a Play Games profile" sheet once, from its automatic sign-in. Until T45 that was at the first app start (seen 2026-10-18 on a Play Store emulator, over the consent form); since T45 it's at Play Games' turn, after the first finish (over the gallery in the spike). It's Google's, not ours, and can't be turned off without dropping Play Games; after it, sign-in is silent.
  - No achievement UI anywhere else: nothing on the gallery, canvas or completion screen. Play Games shows its own "Achievement unlocked" pop-up (top of the screen, a few seconds, its design, not ours) when an unlock reaches it, and the app sends unlocks only while the Gallery or Settings shows, so it never appears over the canvas or the completion screen.
- **VIP not confirmed** (Phase 5 D7): after the 7-day offline fallback (eng D5), Membership reads "VIP · connect to Google Play to confirm". The gallery shows one line, once: "We couldn't confirm VIP for a week. Connect to the internet to restore it."
- **Delete all photo canvases** (Phase 5 D27):
  - It's a plain bone row, not a red button.
  - Confirm: "Delete all photo canvases? Every photo, grid and its progress is removed from this phone. Pictures and purchases stay." Buttons: Delete (primary), Keep (text).
  - It cancels any running encode first (eng E4d).
- Restore purchases is a quiet button. While running it shows "Checking Google Play…". Results appear inline, in a polite live region, for that visit only: "Purchases restored: VIP (yearly)" / "Purchases restored: VIP (weekly)" / "Purchases restored: Ads removed", "No purchases found on this account", or "Couldn't reach Google Play. Try again."

**9:16 time-lapse frame** (1080×1920).
- The canvas spans the full 1080px width, top-aligned with a 36px margin.
- The band behind it is the finished canvas scaled to cover, blurred to ~40px radius at 1080 and darkened to 45% brightness.
- Band text: the title in Bricolage Grotesque 600 at 60px, then "Gemmory · Photo Diamond Art" in Figtree at 36px, both in `frame-text` and left-aligned with 48px margins.
- The non-VIP watermark is "Made with Gemmory", Figtree 600 at 30px, 70% opacity, bottom-right. It keeps its own bottom row with a 25% `scrim` band under it, so a title never collides with it.
- The title is at most 2 lines, then an ellipsis.
- **Progress share** (#36): a line under the title, "47% done" (rounded down; "Just started" under 1%; Figtree 600 at 36px, `frame-text`), then the app line. The build replays the save log so far and the hold shows the canvas as it stands (unplaced cells as pale tints); the band is still the finished picture, blurred. Timing, watermark and share text are the finished time-lapse's.
- **Timing** (Phase 5 D26): 15s in total.
  - 1s intro: photo canvases show the cropped source photo crossfading to the empty tint grid; bundled pictures show the tint grid only.
  - 12s build.
  - A silent 2s hold of the finished picture to end.

**Milestone share card** (#104, G14, mockup `docs/designs/mockups/streak-screen/streak-d.html`), 1080×1920 PNG, the v3 card at ×4.
- `felt-deep` with the felt's 9% grain. From the top (224px margin): the lit flame in the milestone's tier color (440px), the `streak-hero` number in `bone` (Atkinson Hyperlegible Mono 700, tabular, 384px), the milestone title in Bricolage Grotesque 600 at 104px (the celebration's: "A week of gems", "A month of gems", "50 days of gems", "100 days of gems", "A year of gems"), then the milestone week and "Gemmory" in Bricolage 600 at 64px, `frame-text`, 96px from the bottom. 80px side margins.
- The week: the celebration's week (the locale's, G12) under its weekday letters (Figtree 48px, `text-muted`), seven 8px-radius cells 24px apart. A finished day shows a bundled or remote picture finished that day (the day's logged first finish when it is one), drawn finished from its grid and center-cropped, on `felt`. A day finished only on photo canvases, a day kept by the day off, a freeze or a repair, and a picture whose grid is gone show the lit flame (88px) on `felt`. Days after the milestone are empty outlines (4px `bone` at 25%). The milestone day has no outline (founder, 2026-10-22). No dates.
- **Never a photo canvas, its pixels or its colors:** only bundled and remote grids are handed to the renderer, the day rules drop photo canvases, and the renderer refuses one handed over as a picture (AGENTS.md).
- Only from the milestone celebration's Share; shared with "Made with Gemmory: <Play link>" (`utm_medium=streak`, `utm_campaign=streak_card`). The file is `cacheDir/timelapse/streak-<n>.png` (the share path, never backed up). No VIP difference.

## Accessibility

- **Contrast:** follow the valid-grounds table in Colors. New UI color needs a decision-log entry.
- **TalkBack** (Phase 5 D19):
  - **Canvas:** one node: "Canvas. 4,812 of 8,112 placed. Color 7 selected, 312 left." Custom actions: Place next gem, Find next gem, Zoom in, Zoom out, Whole picture, Peek at picture; for VIP (#20) also Fill region at next gem, and Fill the rest of color 7 while Fill color shows (both after Place next gem).
    - **Place next gem** places the cell Find last showed while it's still empty, else the next remaining cell of the selected color in reading order, through the same rule as a tap (the log and time-lapse can't tell). It announces the count left ("311 left"; founder call #14), or "Color 7 done. Color 8 selected." when the color finishes.
    - **Fill region at next gem** (#20) fills the region holding the cell Place next gem would place, and announces "214 placed. 98 left" (or "Color 7 done. Color 8 selected."). **Fill the rest of color 7** is the Fill button; it always ends on "Color 7 done. Color 8 selected." There's no brush action: Place next gem covers single gems. A batch's announcement is spoken when its sweep ends.
    - **Find next gem** is the Find button's logic. Find and Place next gem share one cursor, which starts over at the top when the selected color changes.
    - **Peek at picture** (#35) shows the peek until the same action, now labelled **Back to canvas**, hides it. They announce "Showing the finished picture" / "Back to canvas". The eye button itself reads "Peek, button"; a TalkBack double-tap shows the picture for 1.5s.
    - **Zoom in / Zoom out** step through fit → 8 → 16 → 24 → 48dp, the levels of the zoom table. **Whole picture** goes back to fit.
    - Announcements go through one polite live region (not `announceForAccessibility`), cleared 3s after it speaks; the same words twice in a row are still spoken twice.
  - **Wrong tap:** announces "Not color 7". This is an announcement only, with no state change (eng D4).
  - **Color finished:** announces "Color 7 done. Color 8 selected."
  - **Milestone** (#35): announces the headline ("Halfway there"). After Place next gem it follows the count ("311 left. Halfway there"); when the same gem finishes a color, one line: "Color 7 done. Color 8 selected. Halfway there."
  - **Trough:** "Color 7, 312 left, selected" or "Color 7, done". The description is "Color 7, 312 left" and "selected" comes from the selected state, so TalkBack doesn't say it twice.
  - **Colors row on Preview** (#29): "Colors" is a heading; the troughs are one radio group, each one node: "Standard, 36 colors, selected" / "More, 60 colors, needs a video or VIP".
  - **Gallery tile:** "Sunflowers, 42% done" / "Sunflowers, done" / "Sunflowers, locked, watch a video to unlock" ("…, can't open" when damaged; a remote picture marked New reads "Fox in Snow, new, 0% done" / "Fox in Snow, new, locked, watch a video to unlock", #43), with its ⋮ actions as custom actions ("Share progress" first when offered, #36). The tile is one node: its title, % and ⋮ button are folded into it, so it reads only that line.
  - **Today's picture card** (#34): one node, "Today's picture: Beach Huts, Quick, free to start today" / "Today's pick: Beach Huts, Quick" (VIP) / "Today's picture: Beach Huts, 12% done" / "…, done", with the click labelled "Start", "Continue" or "Open". With the T36 chip: "Today's picture, new today: Lighthouse, …". The Tomorrow line is its own node, "Tomorrow's picture, opens tomorrow".
  - **Finished heading** (#89): one node, "Finished, 12 pictures, collapsed, button" ("photo canvases" on My Photos; "1 picture"), with "collapsed" / "expanded" as its state. When it expands, focus stays on the heading and the tiles follow it in reading order.
  - **Round 2** (T35-T43): collection and event rows, the mystery picture, Music, Reminders and the reminder ask carry their labels in their own entries above.
  - Every icon button has a label: Settings, Find, close, Share progress, the tool well ("Tool: Fill region", #20; "Tool: Tap, 5 fills" without VIP, #38), Inventory ("Inventory, gift spin waiting" while one waits, #38). The Fill button reads "Fill the rest of color 7"; tool troughs read "Tap" / "Brush 3×3" / "Fill region", with "selected" from the selected state.
  - **Sheets:** focus moves to the sheet title on open and returns to the trigger on close. TalkBack moves its focus on its own when a sheet's window closes, so the trigger keeps asking for it for 1.2s after.
- **Font scale:** see the tray and top bar rules. Sheets, paywall rows and size options wrap, with no fixed heights. QA at 200% on a 360dp-wide phone.
- **Touch targets:** 48dp, with the documented canvas exception.
- **Reduced motion:** see Motion.

## Do's and Don'ts

- **Do** keep the picture the only saturated thing on screen. New UI color needs a decision-log entry.
- **Do** use `lamp` for two meanings only: "this is the selected one", and the game's own reward moments (the milestone banner's edges, #35; a collection's completion glint, T37; the streak's today cell and milestone edge, #101). Nothing else is lamp (plan-design-review D15).
- **Do** keep the streak flame the only saturated UI color (#101): flat, on the streak's surfaces only, never as text, a button or a glow.
- **Do** show progress as the art itself (tiles, tray heaps). Use numbers only in mono, small.
- **Do** design the empty, loading, offline and error state of every component (see Rewarded states and Restore).
- **Do** keep calm copy plain and specific: "You're offline, connect to watch a video", not "Oops!".
- **Don't** add coin bursts, glitter particles, gold bevels, purple, neon or glow halos, even for events. The one exception is the finish celebration's gem confetti (#57): table-cut gems in the picture's own colors, behind the picture, on a fresh finish only. No other screen gets confetti.
- **Don't** signal a wrong tap with red, shake, buzz or a counter.
- **Don't** add timers, countdowns, "ends in", "last chance" or loss copy to drops, events, collections or reminders (T35-T38). A missed week or drop is just gone, with no nag.
- **Don't** put a padlock over locked art or blur it; locked pictures are shown in full. The one blurred picture is one that isn't available yet (tomorrow's drop, an unfinished mystery), drawn as the frost; never a locked one (D16).
- **Don't** use pill buttons, cards inside cards, emoji, or gradient buttons.
- **Don't** put ads, ad banners or VIP nags on the play canvas. (The milestone banner, #35, is the game's own 3s moment, not an ad.)
- **Don't** let anything outside the app's control land on the canvas or the completion screen: Play Games achievements are sent only while the Gallery or Settings shows (#49), Play Games starts only after the first finish (T45), and there is no sign-in prompt of ours anywhere. Play Games' one-time sign-in UI (welcome pop-up, profile sheet) is the accepted exception: it lands on whatever shows when its sign-in completes.

## Motion

- **Approach:** intentional and slow. Motion confirms; it celebrates only at the finish (#57), briefly at a milestone (#35), and on a new streak day (#101).
- **Streak** (#101): the flame flickers in a 1.6s loop at 40dp and up (taller-narrower and back, its core on a 1.1s beat) and ignites over 800ms (scale 0.25 → 1.14 → 1, grey to its color by 55%); the celebration's number rolls up over 600ms (ease-out) from 500ms; a milestone's `lamp` edge brightens and fades over 1.4s once. The flame is drawn in an offscreen layer, so its flicker doesn't re-tessellate the screen (#115).
- **Easing:** enter ease-out (`FastOutSlowIn` decelerate), exit ease-in, move ease-in-out.
- **Duration:** micro 80ms (placement glint), short 150-250ms (trough lift and flap, sheet), medium 250-400ms (screen transitions, chrome slide), long 2.4s (the completion's sparkle sweep only) and 2s (its confetti, during the hold).
- **Placement glint:** a 180ms specular pass across the placed gem at 20dp and up. No particles.
- **Off the canvas** (T40): the Preview, gallery tiles, completion screen, time-lapse frames, tray heaps and the confetti draw the same table-cut gem wherever a cell is at least 8px (the time-lapse's Big cells are ~8.9px), and flat squares below that.
- **The one authored moment:** completion "lights down" with the finish celebration (see Components): the sparkle sweep is linear over 2.4s; the confetti falls under a constant 520dp/s² gravity with each gem spinning and tumbling, and fades out over its last 400ms. It plays only on a fresh finish, once (a rotation mid-sequence doesn't replay it), and never on the completion still.
- **Milestone banner** (#35, a founder-approved exception to "never celebrates loudly"): the `bone` tag slides in from the left over 300ms (ease-out), the headline and count wipe open beside it from 300 to 660ms (`FastOutSlowIn`), the band holds and fades out over its last 300ms: 3s in all. No particles, glow or bounce. Under reduced motion the band fades in over 150ms, holds, and fades out over 150ms (2.5s in all) at its real duration; the chime and haptic stay.
- **Peek:** a 150ms fade in and out; under reduced motion it switches at once.
- **Gift wheel** (#38): 5 turns over 3.2s, easing out (cubic 0.1, 0.6, 0.2, 1), landing on the prize's middle. Under reduced motion it doesn't turn: the result line and the chime come at once.
- **A tool's sweep** (#20): rings outward at 16ms each, at most 600ms, each gem's glint as its ring shows. Under reduced motion the batch shows at once with one tick and one click, like a tap; its cues don't wait.
- Reduced motion (`ANIMATOR_DURATION_SCALE == 0` or the accessibility setting) skips the glint, the sparkle sweep, the haptic crescendo and the confetti. The dim, the chime, the soft finish haptic and the 2s hold remain.
- That setting also makes Compose finish every animation at once, so the reduced-motion fades and the blink run at their real duration on purpose (#14).
- Under reduced motion, movement becomes fades (Phase 5 D21):
  - The wrong-tap trough nudge becomes the haptic plus a 120ms lamp-outline blink.
  - Sheets, chrome and screen transitions become 150ms fades. Sheets and their scrim fade in; a sheet that closes leaves at once, as it does without reduced motion. Screens have no transition, so they switch at once.
  - The trough lift is instant.
  - The Find cell pulse is skipped.
  - The milestone banner fades instead of sliding and wiping; the peek switches at once.
  - A tool's batch (#20) shows at once, with no sweep and no glint.
  - The gift wheel (#38) doesn't turn; its result shows at once.
  - A collection's completion glint (T37) doesn't sweep or flash; its line just shows.
  - The "Opens tomorrow" toast and the first-music chip fade in over 150ms, like every chip.
  - The lights-down dim (a fade) stays.
  - The finish celebration (#57) drops its sparkle, haptic crescendo and confetti; the dim, the chime and the soft finish haptic stay.
  - The streak flame (#101) doesn't flicker or ignite; the celebration's number shows at once (no roll-up), the week row is already filled and the milestone edge doesn't show. Its sound and haptic stay (unless Milestones is off).
- Haptics also follow the system touch-feedback setting.
- The Selected color styles have no motion of their own; switching style redraws the canvas at once, with or without reduced motion.

## Decisions Log
| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-10-13 | Initial design system "The Sorting Tray" created | /design-consultation from docs/designs/gemmory.md + store research (Play shelf, Happy Color, Dotterly); anchor "the calmest thing on my phone" |
| 2026-10-13 | Mid-tone felt, one theme (deepened one step under system dark) | Colors read true on neutral grey; calm in dim rooms; avoids cream and near-black-glow defaults |
| 2026-10-13 | Square cushion-cut gems as the default; round deferred to an optional setting | No gaps, so photos and faces stay recognizable (risk #1); round leaves ~21% dark gaps |
| 2026-10-13 | Wrong-color tap rejected with no state change; soft haptic + 2dp trough nudge | Confirms eng D4 (placement log = ordered cell indices); calm, no penalty |
| 2026-10-13 | Palette bar as a sorting tray with shrinking heaps | Progress you feel without percentages |
| 2026-10-13 | "Lights down" completion, no confetti; time-lapse ends on a 2s still hold | Calm anchor; stillness stands out on loud feeds |
| 2026-10-13 | Fonts bundled: Bricolage Grotesque / Figtree / Atkinson Hyperlegible Mono (OFL) | Verified on Google Fonts; offline first launch |
| 2026-10-13 | `fault` #E8B09E, `patina-text` #ACD6C6 split from `patina` fill | WCAG AA for small text on felt and sheets |
| 2026-10-13 | Outside voices: Codex unavailable (gpt-6.1-sol unsupported on ChatGPT login); Claude subagent converged on the same direction | Independent input recorded |
| 2026-10-13 | Phase 5 design plan review (/plan-design-review): D3-D27 applied above (nav map, all missing states, gestures, two-tone ring, raised placed gems, Find, TalkBack, font scale, reduced motion, large screens, valid-ground contrast rule, 48dp text buttons, starter picture, time-lapse intro/timing) | Ledger in docs/designs/gemmory.md "Design Review (Phase 5)" |
| 2026-10-13 | Selected color shown as a stronger own tint (40% toward bone) at every zoom; replaces the D17 two-tone ring | Founder call while playing #18 on the emulator: the outline looked busy. Chosen from five HTML mockups (ring, dim the rest, lamp fill, stronger own tint, dim + tint). Weak on light colors, so #19 adds a player setting with the ring and dimming as options |
| 2026-10-13 | Starter picture = The Starry Night (van Gogh, public domain), cypress-and-swirl crop; Share disabled with "Video sharing is coming soon." until #4; Rename sheet copy; face photos at faceMinSize = Medium disable Quick | Founder answers during #13. Provenance in tools/bundled/PROVENANCE.md |
| 2026-10-14 | No daily cap on extra photo canvases: every rewarded video gives one more, so the "All 3 extras used" row and the "(N left today)" count are gone | Founder call during #7 ("we don't need a max video ad unlock, you can watch as many"). Grace stays 1/day per reward type |
| 2026-10-14 | Rewarded sheet "video ready" copy: locked picture "Watch to unlock" / "Watch a short video to unlock this picture for good."; extra canvas "Another photo canvas" / "Watch a short video for one more photo canvas today."; primary "Watch video". Extra-canvas grace text "Or get VIP: unlimited photo canvases". Interstitial only after a fresh finish (not the completion still). A UMP form that couldn't show on the first launch shows before the first rewarded ad instead | Founder answers during #7 (the Rewarded states table had no ready-state copy) |
| 2026-10-14 | Share enabled (#4): progress sheet, PNG fallback with "Before"/"After" labels, failure and disk-full messages inside the sheet, killed-encode line under Share | Implements the Share progress sheet and D8 edge states; the labels and in-sheet messages fill details the spec left open |
| 2026-10-14 | Double-tap never delays a placing tap: it zooms only when neither tap places (a second tap that would place is a normal tap). Coach-hint flags may be backed up. Find in the large-screen rail is pinned at the top | Founder answers during #10. Standard double-tap detection would delay every placement by ~300ms |
| 2026-10-14 | `text-muted` only on felt-deep, sheets and the dark felts: on the grained felt ground it measures 4.09:1, so the unselected tab, tile %, My Photos empty body, Settings version, top-bar progress, "Checking for faces…" and Preview's lines are `bone`. Allowance note on a `felt-deep` chip beside the button, stacked above it when narrow. "Place next gem" announces "311 left". Accessibility Scanner signal = the Accessibility Test Framework in instrumented tests (no Play Store on the emulator). Reduced-motion sheet fades run at their real 150ms | Founder answers during #14. The note floated over tile art and broke at 200% font |
| 2026-10-15 | Billing (#8): the copy for the plan rows, "Just remove ads · $X once", the fine print "VIP renews automatically until you cancel. Cancel anytime in Google Play.", remove-ads pending, and Restore's weekly / Ads removed lines. Membership is a row with its value at the end; tapping it opens the paywall (Free, Ads removed) or Play's subscription page (VIP, and VIP not confirmed). Manage subscription also shows for VIP not confirmed. "Prices couldn't load" uses bone with the `fault` square on felt. Loading placeholders read "Loading prices" to TalkBack. Start VIP is disabled while a VIP payment is pending, the remove-ads row while its own is. The gallery line and the Restore result are polite live regions | Founder answers during #8 for the copy; the rest filled details the spec left open, confirmed by the founder on PR #28 (VIP not confirmed handling, "Loading prices") |
| 2026-10-15 | Selected color setting (#19): Stronger tint (default), Outline (the D17 ring, from 8dp; stronger tint below 8dp so fit zoom still shows the selection) and Dim the rest (others 35% toward `felt-deep`, numbers on-bone/bone by contrast). A Settings row after Haptics opens a sheet with three radio rows and one-line descriptions; a pick saves and closes. Applies at once on an open canvas; no new entry point on the canvas | Founder call during #18 that the tint is weak on light colors; issue #19. Founder answers during #19: row + sheet over an inline group, Outline falls back to the stronger tint below 8dp, keep 35% dimming (numbers at worst 3.9:1), no Settings entry on the canvas |
| 2026-10-16 | Gallery order after the starter: every Quick picture, then Medium, then Big (#31) | Closes the ~20× jump from the 768-gem starter to 15k-gem Big pictures, so a new player can finish a Quick canvas without a photo conversion (closed-test success criterion "≥50% finish at least 1 Quick canvas"). Order taken from the #31 scope |
| 2026-10-16 | Locked pictures (#32): only the starter and 2 Quick, 2 Medium, 2 Big are free, and the rest show the D14 label. Free pictures come first within each size | Founder call during #32 ("leave only starter + 2 quick + 2 medium + 2 big unlocked"). The free ones first, so a free player finds what they can play without scrolling past locked tiles. The list and the reinstall answer are in docs/designs/gemmory.md, Locked pictures |
| 2026-10-16 | Pictures tab (#58): the starter, then every picture the player can open, then the locked ones, each group Quick, Medium, Big in catalog order. Replaces #32's free-first-within-size order. A video unlock or VIP moves tiles on the next Gallery visit, not at once | Founder asked during #35: a free player scrolled past locked Quick tiles to reach the free Medium and Big ones. Founder picks during #58: next visit over at once (after an unlock the canvas opens, so the tile has moved by the time the player is back; a VIP purchase doesn't reshuffle the grid in front of them), and catalog order inside the open group (VIP sees the plain #31 ramp). "Open" is the tap's rule (`isLocked`) |
| 2026-10-16 | Progress milestones and peek (#35): 25/50/75% on every size, each a "Breaking News" banner across the middle of the canvas (tag snaps in, headline wipes open, 3s; `lamp` edges), the finish chime ×1.5 at 40% and the `CONFIRM` haptic, with a Settings toggle to turn milestones off. Peek is a top-bar eye: hold to see the whole finished picture, or tap for 1.5s; TalkBack action "Peek at picture" / "Back to canvas". No "best session" / "gems today" line | Founder picks from the HTML mockups (`~/.gstack/projects/Gemmory/designs/t23-milestones-peek/`) during #35. The banner departs from "no other chrome" and "Motion confirms; it never celebrates loudly" on purpose: on a Big canvas the only reward moments were a finished color and the finish. `lamp` on the banner's edges is a second meaning ("this moment"), kept as mocked. Long-press is taken by color select (D23), so the peek can't be a canvas hold. The stats line is in docs/todos.md |
| 2026-10-16 | Finish celebration (#57): on a fresh finish the sweep becomes a sparkle that glints every gem (or 4dp tile of gems) in turn over the same 2.4s, a haptic crescendo rises under it and peaks with the chime (now with its fifth and octave), then gem confetti in the picture's own colors falls through the dimmed room during the 2s hold, behind the picture. Share still appears at 5.05s. Reduced motion keeps the dim, the chime, the soft haptic and the hold. No Settings toggle. The completion still doesn't celebrate | Founder pick (recommended D + A + F) from the HTML mockups (`~/.gstack/projects/Gemmory/designs/t32-finish-celebration/`) during #57, over fireworks (hidden behind a 3:4 picture, the genre's loud look), a gem shower (the tray is empty at the finish, and it covers the picture) and a time-lapse preview (the real 15s video would delay Share 10s+). Replaces the 2026-10-13 "no confetti" rule for this one moment; the colors come from the picture, so the shell stays unsaturated. No toggle: reduced motion removes all the motion, and Sound and Haptics have their own |
| 2026-10-17 | Share progress (#36): a share icon in the play canvas's top bar (between the Peek eye and the progress) and "Share progress" first in an unfinished tile's ⋮ menu, both from the first gem. It shares the same 9:16 time-lapse, replaying the save log so far and ending on the canvas as it stands, with a "47% done" line under the title ("Just started" under 1%); the PNG fallback reads "Before" / "So far". Same sheet, encoder, fallback chain, watermark and share text; no ad before, during or after it, and it isn't a finished canvas for the interstitial rule. Analytics `share_tapped` / `share_completed` carry `kind` = `progress` \| `finished` | Picked by the agent from its own recommendations (`~/.gstack/projects/Gemmory/designs/t24-progress-share/`, with emulator frame sheets) while the founder was away; the founder confirmed the entry point and content in review (2026-10-17) and chose "any progress" over the proposed 10% minimum, so it's offered from the first gem. Rejected: a ⋮ overflow on the canvas (a menu of one item, and "Share" is hidden in it) and a still before/so-far picture (no sparkle; it's already the video's fallback). The band stays the finished picture blurred, so it doesn't change as gems are added |
| 2026-10-17 | Daily play streak (#33): a day counts when a picture is finished that day, any picture (local date); one missed day a week is forgiven; a `felt-deep` chip "4 days in a row" at the end of the gallery's tabs row from 2 days on; a broken streak is simply not shown. No rewards, no reminders, nothing on the canvas or the completion screen. Backed up with the settings | Picked by the agent from the calmest options in the issue while the founder was away; in review the founder raised the bar from one gem to a finished picture and confirmed the rest (2026-10-17): rewards would tie it to the ad rules and tiers, and guilt or loss copy goes against a relaxing app. The completion screen stays the one authored moment. Screenshots at 1× and 2× font in `~/.gstack/projects/Gemmory/designs/t21-daily-streak/` |
| 2026-10-17 | Today's picture (#34): one bundled picture a day picked on the phone (date-seeded, kept for the day; a locked one not unlocked or started for a free player, any not started for VIP), shown as a `felt-deep` card above the Pictures grid with the finished picture, "Today's picture" / "Today's pick", the title, "Quick · free today" and a quiet Start. It opens without a video; the first gem unlocks it for good. Its tile joins the open pictures for the day. No streak link, no ad. Analytics `todays_picture_started` | Founder picks from the HTML mockups (`~/.gstack/projects/Gemmory/designs/t22-todays-picture/`, with emulator shots at 1× and 2× font): the card (B) over a lamp-outlined 2nd tile (A, recommended) and a first tile (C, breaks D13); unlock on the first gem over the E4c started rule alone (a Start over would have locked it again) and over unlocking on sight; VIP gets "Today's pick"; any finished picture already counts the streak. The card is a new component, so the mockup's lamp kicker became `text-muted` and its bone Start a quiet button, keeping lamp and the primary fill to their one meaning each |
| 2026-10-17 | Placement tools (#20): Brush 3×3, Fill region and Fill color ("Fill N" beside Find while ≤ 20 left), VIP only until #38 adds earned charges. Picker: a tool well in the tray (P1) that swaps the troughs for tool troughs; a 6dp `lamp` corner on the well while a tool other than Tap is on. A batch is placed at once and revealed as a ring sweep (16ms a ring, ≤ 600ms) with a light `PRIMITIVE_TICK` sweep and 5 rising clicks; its cues wait for the sweep. TalkBack: "Fill region at next gem", "Fill the rest of color 7". Paywall gains a 5th perk | Founder picks from the HTML mockups (`~/.gstack/projects/Gemmory/designs/t17-placement-tools/`) during #20, over a segmented row above the tray (P2: ~44dp of chrome on every canvas) and a long-press on the trough (P3: hidden). VIP-only keeps the founder's 2026-10-15 power-up call without a video on the canvas (no ads there) and without a VIP nag: free players see nothing. The well's lamp corner is the "selected" meaning (a tool is selected). Tools work from 16dp like a tap, so nothing is placed where its number can't be read |
| 2026-10-17 | Inventory (#38): fills (one per tool use or brush stroke), kept photo canvases and unlock tokens for players without VIP, in a bag (E1) in the gallery top bar opening an Inventory sheet; a rewarded video there gives 5 fills (never on the canvas), the extra-canvas video's canvas is now kept, and every 5th streak day earns a spin of a flat gift wheel (W1: 5 fills 50%, 10 fills 20%, 1 photo canvas 20%, 1 unlock token 10%, odds on the wheel and in words). The tool well (#20) shows for free players holding fills, with the count, and dims at 0 with no copy. Unlock tokens are a quiet button on the locked picture's sheet. VIP has no inventory | Founder picks from the HTML mockups during #38, over a "5 fills" chip (E2: one item of three, cramped at 2×) and a tabs-row chip (E3), and over a gem-ring wheel (W2). The wheel and the streak gift reverse #33's "no reward" and #38's "no random rewards" on purpose (founder); the gift is free, so no paid loot box. The wheel uses shell tokens only (`patina`, `patina-text`, `bone`, `text-muted`), no new color; `lamp` stays "selected". Its tick and chime reuse the gem click and the milestone chime |
| 2026-10-17 | Remote pictures (#43): pictures published to `pictures/v1/` in the repo (signed catalog, GMRY grids) join the Pictures tab in their size's run after the bundled ones, oldest first, open or locked like any picture (A). A `felt-deep` "New" label with a spark vector at the tile's top-left marks one that arrived in the last 7 days until it's tapped; nothing is new on the first sync. Today's picture prefers a new remote picture. No other visual difference, no loading or offline state | Founder picks from the HTML mockups (`~/.gstack/projects/Gemmory/designs/t27-remote-pictures/`): A over a "New" group right after the starter (B: puts locked tiles before the free ones again, which #58 removed) and a horizontal "New pictures" strip (C: a new component, two scroll directions, ~170dp more before the grid). The label reuses D14's shape and bone on felt-deep (7.58:1); top-left so both labels fit on one tile at 2× font |
| 2026-10-18 | More colors (#29): a Colors row of three troughs on Preview (Fewer / Standard / More), counts Quick 12/16/24, Medium 16/24/40, Big 24/36/60; the size picker is unchanged. More is VIP or one rewarded video per photo (the colors video, or a canvas video watched on that Preview); Fewer and Standard are free. The chosen trough lifts 3dp with the lamp outline like a tray trough; More carries a play mark while it needs a video. Bundled pictures keep their frozen grids | Founder picks from the mockups (`~/.gstack/projects/Gemmory/designs/t18-more-colors/`, real converter output) during #29: P1 over count chips on each size row (P2: chosen blind on 56dp previews, chips under 48dp) and a slider (P3: steps look alike, ~36 TalkBack stops). The video rule is the founder's 2026-10-15 call on the issue; one video for both keeps a free player from watching two for one canvas. Re-freezing a bundled picture would fail every started save's grid CRC ("Can't open"), so richer pictures come as remote pictures (#43) |
| 2026-10-18 | Play Games achievements (#49): 12 achievements, none hidden (first finish; a Quick, Medium and Big finish; a photo canvas finished; a finished and a progress share; 5 and 25 finishes; 5 photo canvases; 7- and 30-day streaks), each icon a patina cushion-cut gem with bone facets and one bone mark on `felt-deep`, with no text or numbers (founder, following Google's icon guidance: one icon serves every language). One Settings row after Selected color opens Play Games' screen (end text "Play Games", "Checking…" while a tap runs, one failure line). Sign-in is Play Games' automatic one, with nothing shown when it fails or is declined. Unlocks are sent only while the Gallery or Settings shows, so Play Games' own pop-up never covers the canvas or the completion screen. Nothing on the completion screen | Founder picks (all recommended) from the HTML mockup (`~/.gstack/projects/Gemmory/designs/t28-achievements/`) during #49, over a completion-screen action (it competes with Share in the one authored moment) and no in-app entry. Patina on the icons is its "success" meaning; they live in Play Games, not in the app's shell. Milestones, More colors, Today's picture and the tools were left out: one can be turned off, the others lean on VIP, videos or fills. "Checking…" was added in the build, after the emulator showed a tap could wait with no feedback; founder approved it (2026-10-18) |
| 2026-10-19 | Gem redesign (T40, #81): square **table cut** replaces the 18% cushion and its two-facet highlight. Whole cell, no inset or seam; a flat exact-color table inside four bevels (top 24% white, left 9% white, right 14% black, bottom 24% black; 24% of the cell wide at 8-15dp, 20% from 16dp); a 0.8dp catchlight from 20dp; flat below 8dp. The bevels replace D18's edges. Same gem in the Preview, tiles, time-lapse, tray heaps and confetti wherever a cell is at least 8px. Outline ring corner 4% | Founder pick (A, recommended) from HTML mockups on real frozen grids (beach-huts, couple; `~/.gstack/projects/Gemmory/designs/t40-gem-redesign/index.html`) at fit, 10, 16, 24 and 48dp under all three Selected color styles, with a light-color hard-case strip. Picked over Sockets (outside voice: flat placed gems, depth in empty cells; a finished canvas reads as pixel art), Glazed tile (the reflection repeats on every cell) and Smalti (4 sprites per color, noise on faces; the outside voice proposed the same as "Zellige"). The balanced bevels keep the picture's average color exact; one sprite per color per bucket, as before. Research 2026-10-19: Diamond Painting ASMR and Art by Number draw round domed drills with gaps, Diamond Paint a glitter scanline; real square drills are a table with bevels |
| 2026-10-19 | Round 2 UI (T35-T43): reminder ask sheet, notification copy table and icon rules, Settings Music/Music volume and Reminders/Reminder time (sheet, off state); one interruption per return to the gallery (reminder ask, then Play review, then interstitial); "New today" chip; Tomorrow teaser as a line in the Today card (T2); the frost as the one hidden-picture look; collection and event rows (at most 2 above the grid + "All collections" sheet, R1), "2 more coming" well, lamp completion glint; event copy with no countdown; mystery names, Peek and Share hidden before 50%, banner "Halfway: revealed"; first-launch "Music is playing · Turn off" chip; paywall, grace and success copy say "every gallery picture" (P2) | Founder picks T2, R1, P2 (all recommended) from `t40-gem-redesign/ui.html` during the round-2 design consultation. T2 over a frosted grid tile (T1: breaks the #58 order and puts a locked-looking tile after the starter). R1 over every row stacked (the grid sinks screens down within weeks) and a Collections tab (hidden from the main tab). P2: keepsakes stay hidden until earned for VIP too (C3), so "every gallery picture" is the exact promise. The rest (copy, interruption order, music chip, mystery Peek/Share rule) are the agent's recommended spec, shown in the mockup page; lamp on the glint reuses the milestone banner's "this moment" meaning |
| 2026-10-19 | Round 2 design plan review (/plan-design-review, D3-D25): All collections link after the rows (D3); one row above 1.5× font (D4); no rows before the first finish (D5); row and sheet states, incl. pending keepsake (D6); a reward return has no interruption and the glint waits until the row is on screen (D7); "Opens tomorrow" toast 16sp, 4s, fixed spot (D8); mystery canvas in one neutral tint (D9); streak fact allowed in the opt-in reminder only, ask body names it (D10); "Today's mystery picture" in the app (D11); "Reminders on" line after Yes (D12); Tomorrow line in the label style (D13); well 13sp, music chip 16sp (D14); lamp's two meanings written into the rule (D15); frost exception to the blur and glass rules (D16); achievement icons redrawn as table cuts in T40 (D17); 200% font: Tomorrow line grows, well shows "+2" (D18); large screens: text ≤480dp, rows stacked (D19); music and chip under TalkBack (D20); reminder copy approved, lock screen public (D21); interruption order confirmed (D22); a mystery stays hidden until finished, title, art, tints, Peek and Share all included, shared only from the completion screen (D23, founder); first music at the first canvas open (D24); keepsake follows the #58 order in the grid (D25) | Founder answers, all recommended except D23, where the founder went further: hidden until finished, not until 50%. Outside voice: Codex unavailable, a native Claude subagent (15 findings) raised D5, D7, D10, D11, D13, D14, D15, D16 and D24. Ledger in `docs/designs/gemmory-round2.md`, "Design Review (Phase 5)" |
| 2026-10-20 | Reminder ask while notifications are off (T35 review): Yes turns reminders on and opens the app's system notification page, with no "Reminders on" line; Analytics `reminder_prompt_result` = `blocked` | Founder pick (recommended) from three: open the page like the Settings switch, skip the ask while blocked (players with notifications off never hear of reminders), or a new hint line (new copy, goes nowhere). Before this, Yes on Android 10-12 with notifications off claimed "Reminders on" while nothing could post |
| 2026-10-20 | Evening reminder build (T35, #76): a drop or Today's picture reminder opens its canvas directly (no video, entry = reminder), like the Today card; the ask counts finishes from the version that brought T35; the Settings off line shows only once the player wants reminders or answered Android's dialog | Founder answers during #76 (both recommended): gallery pictures have no Preview screen (Preview is the photo flow's), so "its Preview" became the canvas the Today card already opens; counting finishes from the first launch would never ask players who already had three. The off-line rule is the agent's: on Android 13+ every fresh install has notifications blocked until asked, and a fault line there would read as a problem the player never had |
| 2026-10-20 | Gallery Finished section (#89): a collapsed "Finished · n" heading at the end of each tab (Pictures after the locked group, My Photos after the unfinished canvases), newest finish first from the T35 `play` file, undated after dated, ties newest created first; expand state per tab, backed up; tiles move on the next visit; a plain label-style row with a new `expand_more` chevron, no card or divider | Founder request 2026-10-20 ("hide completed photos or have a special tab for them"): over a third tab (crowds the tabs row and streak chip at 2× font) and a hide switch (hidden canvases get forgotten). Founder picks during #89 (all recommended): newest-created tie-break, the chevron, no hairline |
| 2026-10-20 | Collections built (T37, #78): "newest collection" is the catalog's list order; a collection flagged `event` stays hidden until T38 gives it its window; a collection with no keepsake earns the token alone ("Finish all 7 for an unlock token.", then "Complete. 1 unlock token added."); a damaged member's corner mark is a small × | Founder answers during the #78 plan and PR #93 review: the catalog format has no date for collections, and the publishing tool appends; a T37-only build must never read an event as a 7-of-7 collection; the batch tool allows a collection without a keepsake; "Can't open" doesn't fit a 64dp thumbnail |
| 2026-10-20 | Weekly event built (T38, #79): the event row in its window comes after a reward return's pinned row (D7) and before the newest other collection; its one glint plays at the 3rd finish (or at completion if never earned); TalkBack on the 3rd: "Golden Week: keepsake picture added." / "…: keepsake earned." while pending; a completed event reads "Complete. 1 unlock token added." (no keepsake named again); the paywall, grace and success lines say "every gallery picture" (P2) | Founder answers during the #79 plan: the design system gave the event's place and lines but no announcement for an earn that isn't a completion; D7 already lets a reward return take a row slot at any font size; the event's exclusive is earned at the 3rd, not by completing all 7 |
| 2026-10-20 | Gem redesign built (T40, #81): one shared drawing (`TableCut`) for the canvas sprites, the offline renderer (a cell's px stands in for its dp), the confetti (the ≥ 20dp proportions) and the tray heaps (9dp, 24%, no catchlight). An unplaced cell is a flat square tint with its 0.5dp seam on the right and bottom; the Outline ring keeps its 0.6dp inset with a 4% corner. A sprite's bevel band comes from its zoom bucket's features (24% from 8dp, 20% with numbers from 16dp), not its pixel size. The balance sentence is corrected: the bevels cancel at mid tones, a white gem averages up to ~18 levels darker. The launcher and Play Store icons are redrawn as table cuts too | Founder approved the icons and the in-app look from the before/after screenshots (beach-huts, couple at fit-48dp, the hard-case strip under all three styles). The launcher icon was added to the scope by the founder. The balance correction came from `TableCutTest`: a white overlay can't lighten white |
| 2026-10-20 | Mystery picture built (T43, #84): no picture Preview screen exists, so the Phase 5 "Preview: the frost, Hidden until you finish it." line is dropped; the neutral name also replaces the title in the canvas top bar and the Today card's TalkBack; after its date a mystery reminder says "Today's picture: Mystery picture" or "You're 82% through your mystery picture"; the frost's `felt-deep` cover is baked into the notification icon (no theme there); a withdrawn mystery stays hidden by its canvas record. Stamped min app 16, with the versionCode bump left to the next release PR | Founder, 2026-10-20: drop the Preview line (recommended) and bump versionCode in the release PR. The other rows are the agent's reading of D11 and D23 where the spec named no surface |
| 2026-10-21 | Placing sounds built (T41, #82): every canvas sound replaced by in-house synthesis (`tools/make_sounds.py`, no third-party audio): 5 takes of a glass "tink" over a knock for a placed gem and a batch, 3 softer drag ticks (±1.5 semitones), 3 color-done thumps with a rising glass run, 3 milestone bell voicings recorded a fifth up, 3 finish-chime metals (fifth and octave still layered). A random take per event, never the same twice in a row; the wheel spin keeps the tap voice | Founder approved every event by ear from a before/after listening page (2026-10-21) over sourcing CC0 samples: the existing sounds were already synthesized here, so this keeps one provenance story and nothing to license. Takes and pitch live in pure `SoundCues` so the toggle and the variation are unit-tested |
| 2026-10-21 | Ambient music built (T42, #83): six CC0 tracks instead of 2-4 (Heavenly Loop × 3, Contemplation, Vaporware, Lifewave 2k, Synthwave 421k, The Budding of Consciousness), random order with no repeat, 1s fade-in; volume slider 10%-100%; music plays on every screen once the first canvas has opened, not only the gallery and canvas; the chip's "Music off" line is a 4s polite toast where the chip was | Founder asked the agent to find the loops, then picked "all" from a listening page of six CC0 OpenGameArt candidates (2026-10-21) and "random rotation". About 8 MB of audio in the AAB. Gates and rotation live in pure `Music` so every rule is unit-tested |
| 2026-10-21 | In-app review built (T39, #80): Play's sheet takes the return's one interruption after the reminder ask and before the interstitial, as specified; while Play's flow runs the one-time "Music is playing" chip waits, as it does for the ask; nothing of ours is drawn around Play's sheet. Switched on by `features.review` in the signed catalog at production | The spec named the order (D22) and the reward-return rule (D7) but not the music chip, which T42 added later; waiting keeps two things from landing on one return. The switch is the CEO C1 / eng gate: one AAB serves both tracks |
| 2026-10-21 | Streak (#101, Streak A): the Duolingo-style streak replaces the calm #33 chip. A literal flat flame in three new UI colors (`flame` `#F5A623`, `flame-30` `#F08A2C`, `flame-100` `#E0563B`, grey `ember` `#7E8880` before today's finish) and one new type style, `streak-hero` (mono 700, 88/56sp, at most 64sp above 1.5x font). A celebration on every new streak day before the gallery, Your streak with a month calendar, and the chip as a button from day 1. The week row and calendar follow the locale's first day of the week. Until the share card (#104), a milestone's text button is "See your streak" | Founder D1 "full Duolingo" and G1 (flame over the recommended streak gem), the CEO, eng and design review ledgers in `docs/designs/streak-screen.md`. The flame is the one saturated color outside a picture, kept to the streak's own surfaces. G12 names "Monday-Sunday" as "the locale's calendar week", so a US phone starts on Sunday. The celebration is part of the exit and the ad still follows it (O4) |
| 2026-10-22 | Streak repair (#102, Streak B PR 2): the repair panel is the rewarded states drawn inline on Your streak (one tap loads and shows the video), with "Bring it back" for the grace and VIP's weekly repair; the restored number rolls 0 → n over 600ms. Video repairs unlimited, VIP one free repair a week, a grace repair once in 30 days | Founder picks during #102 from the Streak B mockup section 3 (approved 2026-10-22), over E8/O1's "once in 30 days" for every repair: "we want the ads revenue" |
| 2026-10-22 | Streak nudge and streak-first reminder (#102, Streak B PR 3): the nudge line in the Today card (its own card without one) points at Today's picture if Quick, else the canvas closest to done, else a Quick picture, and opens it; the broken variant and the chip's grey "0 ›" stay for the 2-day repair window; the evening reminder's streak reason goes first on a night it really ends and names the same target | Founder-approved spec G2-G5 and the Streak B mockup sections 4-5 (2026-10-22). With no target the line has no hint and opens Your streak; the awaiting line uses the mockup's short form; above 1.5x the existing one-row rule makes room |
| 2026-10-22 | Streak freezes (#102, Streak B PR 1): a 4th inventory item, held up to 2, from a 5th gift-wheel slice (odds 45/20/15/10/10, E7). The slice is `felt` with a `bone` cross (the freeze mark from the calendar); at 2 held it reads and pays 5 fills. A freeze covers a missed day after the day off and is spent only after the day is saved; VIP never spends held freezes. Your streak lists them for players without VIP; the celebration says how many are left | Founder picks during #102 from the Streak B mockup (`docs/designs/mockups/streak-screen/streak-b.html`, approved 2026-10-22): `felt` + cross over `patina` + cross (two patina slices side by side) and a plain `felt-deep-dark` slice (weak against the seams). No new color |
| 2026-10-22 | Milestone share card (#104, Streak D): the 9:16 card above, on `felt-deep` with grain; pictures for finished days, the lit flame for photo-only and kept days, empty outlines after the milestone, no outline on the milestone day; entry only from the milestone celebration's Share | Founder approvals on the Streak D mockup (`docs/designs/mockups/streak-screen/streak-d.html`), 2026-10-22: celebration only (no Share on Your streak), option A (no milestone-day outline), the number at the v3 card's proportion (384px) rather than G8's 88sp. No new color or type style |
| 2026-10-22 | Streak widget (#103, G17): one 2×2 Glance widget on fixed `felt`, the number a bitmap in the design-system mono, sized in dp; the line Glance's text in the system font, following font scale; a compact layout under 140dp; picker preview, label "Streak" and description from the `streak-c.html` mockup | Founder picks 2026-10-22: first a TextView naming the font, but on the launcher it drew in Roboto, so a bitmap number over system fonts for both or bitmaps for both (the line keeps font scale); the `streak-c.html` mockup sections (launcher grid, tiers, 110dp and 200% font, picker, TalkBack) approved ("seems ok") |
| 2026-10-22 | Fonts for other scripts (round 3, T49 #136, CEO C11): Bulgarian uses Onest (body, UI) and Geologica 600 (titles); Japanese uses Zen Kaku Gothic New 400 / 700 for both, body line height 1.6; numbers stay Atkinson Hyperlegible Mono everywhere; Romanian ș / ț only. Navigation map gains the round-3 first launch (T45 #132: nothing before the first gem, Play Games after the first finish) and the share-link landing (T50 #137) | Founder picks (recommended) from the preview `~/.gstack/projects/Gemmory/designs/t49-fonts/` (round-3 /design-consultation). Bundling over the phone's fonts is the founder's C11 call for a consistent look; Zen Kaku over Noto Sans JP for warmth and half the size (2.9 MB vs 5.9 MB compressed), M PLUS 2 for fewer kanji, BIZ UDPGothic for size. The 1.6 Japanese line height and 700 for Japanese labels (no 600 weight) are agent defaults, open to override |
| 2026-10-22 | Round 3 plan-design-review (T45 #132, T47 #134, T49 #136, T50 #137): **first launch** shows no Today card or Tomorrow line until the first finish (like collection rows), unless the player came from a share link: leaving the shared picture lands in the normal gallery with everything. **Share-link landing**: the shared picture's canvas opens with a one-time toast "Shared with you: <title>" (16sp, 4 s, polite live region; a mystery reads "a mystery picture"), Big pictures too; if the picture is only ready after the gallery has drawn, no jump: a toast "Shared with you: <title> · Open" for 8 s. Backed out with no gem it stays free until local midnight in the open group, no lock label. **Long text**: body, sheets, toasts and hints wrap freely; buttons and sheet actions wrap to 2 lines and grow, never ellipsize; the tile's "Watch to unlock" label wraps to 2 lines, and above 1.5× font is the play icon only (full text for TalkBack); tile and Today titles 2 lines then ellipsis; top bar, chips, tabs and Settings end text 1 line with ellipsis (tabs scroll at 200%); hyphenation for de and hu, phrase line breaking for ja. **Settings**: a "Language" row (end text the language's own name, e.g. "Deutsch") on Android 13+ opens the system per-app language page; TalkBack "Language, Deutsch, button". **Play Games fallback** (if T45's spike forces it): the Achievements row's end text reads "Sign in" until signed in. The 9:16 frame title uses the per-language fonts and the sender's language; the streak calendar's week start follows the phone's region. **Clips** (debug): a plain list on felt, three order chips and a format choice, one render at a time with "Rendering… 45%", "Saved 4 files" with the folder, an error line under the row, "No pictures on this phone yet" when empty | Founder picks (1A, 2A, 3A, 5A, 8A, all recommended; plus: after leaving the first shared picture the player is in the normal game) and agent fixes from the round-3 /plan-design-review and its outside voice (Claude subagent). Ledger in `docs/designs/gemmory-round3.md` → Design Review (Phase 5) |
| 2026-10-22 | First run clear (T45, #132): a fresh install shows only the EEA consent form before the starter; the Today card and Tomorrow line wait for the first finish (DR4); Play Games starts only after the first finish (in that session at a gallery visit that isn't the finish's return, then with the app at each start), so its profile sheet can't land before the first gem; the Achievements row hides until then; no build has `PlayGamesInitProvider`. The first-run order is in the navigation map | Round-3 CEO O2, eng E3/O-E9 and design review DR4. The audit (navigation map, 2026-10-22) found nothing else that opens by itself before the first gem. The E3 spike (0.8.2, Play Store emulator) saw Play Games' one-time UI only over the gallery, so the gate ships, not the Settings-only fallback; the founder then asked for Play Games as soon as the app starts once past the first finish (over a 3 s idle-gallery wait and over a gallery-only start), accepting that its one-time UI can land on a canvas (a cold reminder tap, or a tap in its few-second sign-in), since v2 has no public way to pin pop-ups |
