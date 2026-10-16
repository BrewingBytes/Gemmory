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
  gem: 18%
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
- Square cushion-cut gems tile with no gaps, so the picture always reads.
- Progress you can feel (shrinking gem heaps) instead of percentages.
- Completion dims the screen rather than exploding.

Preview reference: `~/.gstack/projects/Gemmory/designs/design-system-20261013/preview.html` (approved 2026-10-13).

## Colors

**Strategy:** Restrained. Neutrals come from one sage-slate felt, and color is rare and meaningful. The picture supplies the saturation.

**Light or dark:** neither. The felt is **mid-tone** on purpose. Gem graders judge stones on neutral grey because colors read true there. A mid-tone also stays comfortable in a dim bedroom without the "near-black + glow" look. One theme ships. Under system dark mode the three felt tokens drop one step (`felt-dark`, `felt-deep-dark`, `felt-raised-dark`), and every other token stays the same.

Token roles:
- `felt` is the app ground, with a subtle grain at about 9% noise. `felt-deep` is the canvas surround, sheets, the tray bar and selected tabs. `felt-raised` is quiet buttons, toggles at rest and closed trough flaps.
- `bone` is primary text and the **only** primary-button fill (5.7:1 on felt). `on-bone` is text on bone. Bone is never a ground.
- `text-muted` is for secondary text (4.7:1 on felt, 6.3:1 on felt-deep).
- `lamp` marks "this one": the selected trough outline, the selected size or plan option, and the face hint text. It is never used for buttons.
- `patina` fills VIP and success states (switch-on track, perk bullets). `patina-text` is VIP wording in text (4.5:1 on felt).
- `fault` covers offline / no-video wording only (5.1:1 on felt-deep). **It is never used for a wrong-color tap.**
- `scrim` at 55% sits behind sheets. `frame-text` is text on the darkened 9:16 band.

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

Scale: 34 / 30 / 22 / 16 / 13 sp. Adjacent levels differ by size *and* role, never by weight alone. Respect the system font scale. At 200% the canvas numbers do not scale (they follow cell size), but all chrome does.

## Layout

- Material 3 structure (top app bar, bottom sheets, system back) so Android feels native, with 16dp side gutters and a 4dp base grid.
- **Gallery:** a 2-column staggered grid (10dp gap) that honors 1:1 / 3:4 / 4:3 tiles. Tabs are "Pictures" and "My Photos". A "New from photo" primary button floats bottom-right. The first Pictures tile is the free starter picture (24×32, about 5 minutes; Phase 5 D13). The other bundled pictures follow as a difficulty ramp: every Quick picture, then Medium, then Big (#31).
- **Play canvas:** a top app bar (back, title, mono progress), then a full-bleed canvas on `felt-deep`, then the tray bar at the bottom. No ads and no other chrome.
- **Size picker:** the face hint sits on top, followed by three option rows, each with a mini gem preview, gem count and color count.
- **Paywall:** a full screen with close visible immediately (top-left), plain perk lines, two plan rows, one primary button and a quiet "Just remove ads" row.
- Touch targets are at least 48dp, text buttons included. **Documented exception:** canvas cells can be placed from 16dp. The accessible alternatives are zoom up to 48dp cells, the Find button and the TalkBack canvas actions (Phase 5 D16).
- Sheets have a 16dp top radius, a grab handle and 20dp padding. Their content scrolls, so nothing clips at 200% font. The grab handle is a 32×4dp bar in a 48dp square labelled "Drag handle" (Material puts expand and close actions on it).

**Navigation map** (Phase 5 D3). System back always goes one step up.

```
first launch ─► UMP consent (EEA only, first launch, before any ad request; never over a later launch)
            └─► Gallery  [title left · Settings icon right · quiet "VIP" text action from the 2nd session on]
                 ├─ tab Pictures (default) │ tab My Photos
                 ├─ "New from photo" ─► Photo Picker ─► Crop ─► Size picker ─► Preview ─Start─► Canvas
                 ├─ tile in progress ─► Canvas (resumed)
                 ├─ tile done ─► Completion still (Share primary, Done quiet, "Play again" text)
                 ├─ tile locked ─► Rewarded sheet ─► (reward) Canvas
                 ├─ tile damaged ─► "Can't open" sheet (see Gallery tile)
                 ├─ tile ⋮ menu: photo canvas "Rename" / "Delete" · bundled "Start over"
                 ├─ "VIP" ─► Paywall
                 └─ Settings ─► Membership row ─► Paywall (Free) or Play subscription page (VIP)
Canvas ─finish─► Completion ─Share─► progress sheet ─► Android share sheet ─► back to Completion
Completion ─Done/back─► Gallery ─► interstitial check (Ad rules), never before or during Share
```

- Long-press on a tile opens the same ⋮ menu.
- **Delete confirm:** "Delete this photo canvas? The photo, grid and progress are removed from this phone." Buttons: Delete (primary), Keep (text).
- **Start over confirm:** "Start this picture over? Your progress on it is cleared." Buttons: Start over (primary), Keep (text).

**Large screens** (Phase 5 D22). On 600dp and wider, Android 16 ignores orientation locks, so these layouts are required:
- Under 600dp: portrait-first, as designed. Landscape is allowed but not optimized.
- 600dp and wider: the gallery has 3 columns (4 at 840dp and wider). Sheets are at most 560dp wide and centered. The paywall content column is at most 480dp.
- Canvas at 600dp and wider in landscape: the tray becomes a vertical 2-column rail on the right, with the same troughs, and the canvas fills the rest.
- Canvas, zoom and selection state survive rotation and fold changes.

## Elevation & Depth

Depth means **recess and raise**, not glow:
- Troughs are recessed with an inset shadow (0 3dp 6dp, 45% black).
- Gallery tiles sit on the felt with a short cast shadow (0 2dp 3dp).
- Sheets lift with a 0 -8dp 20dp shadow over a 55% scrim.
- Buttons have a 1dp darker "stitch" inset and a 1dp bottom shadow.

There are no zero-offset colored halos and no frosted glass anywhere.

## Shapes

- Radii: `sm` 4dp for thumbnails and labels, `md` 6dp for buttons and troughs, `lg` 12dp for option rows and plan rows, `sheet` 16dp for the top of bottom sheets.
- **Gems are square cushion-cuts:** an 18% corner radius and a 0.6dp inset between gems so the grid reads without dark gaps. At 20dp and up, each placed gem gets a two-facet highlight: top-left triangle at 20% white, bottom-right at 14% black.
- Buttons are rectangles, never pills.
- A nested inner radius equals the outer radius minus the gap.

## Components

**Primary button.** Bone fill with on-bone text and a stitch inset. Pressed state moves it down 1dp. Disabled is 55% opacity. Focus-visible gets a 2dp lamp outline. Use one per screen.

**Quiet button.** `felt-raised` fill with bone text, for secondary actions such as Done, Try again and Restore.

**Text button.** Bone, underlined, 48dp minimum height (Phase 5 D16). It wraps to a centered block inside sheets.

**Palette tray (sorting tray).**
- One trough per color, 52dp wide. The well is 44dp tall and recessed.
- A heap of up to 9 mini gems shows the remaining count (one gem per ~3.5 cells, minimum 1 while any cells remain). The trough number is in mono, with the remaining count in muted mono below it.
- **Selected:** the trough lifts 3dp and gets a 2dp lamp outline.
- **Finished:** a `felt-raised` flap closes over the well, the number is struck through, the count is hidden, and the trough moves to the end of the tray.
- The tray scrolls horizontally, and the selected trough auto-scrolls into view.
- **Selection** (Phase 5 D25):
  - On open, the first unfinished color in tray order is selected.
  - When a color finishes, the next unfinished trough is auto-selected and scrolled into view.
  - On resume, the last zoom, pan and selected color are restored. They are stored with the canvas record, not in the GMRY placement log.
- **Find** (Phase 5 D24): when the selected color has 20 or fewer cells left, a 48dp quiet "Find" button appears at the tray's left end.
  - Each tap pans and zooms (24dp cells) to the next remaining cell and pulses the cell once for 400ms. Under reduced motion the cell doesn't pulse.
  - It is hidden at all other times. It shares its logic with the TalkBack "Find next gem" action.
  - It is an icon button (magnifier) labelled "Find". In the large-screen rail it is pinned full width at the top, above the scrolling troughs.
- **Font scale** (Phase 5 D20):
  - Trough width grows with the font scale, up to 72dp.
  - Above 1.5× the remaining count is hidden; TalkBack still reads it. The number never clips.

**Play canvas** (a single Compose `Canvas`, never one composable per cell).

| On-screen cell | Renders | Tap does |
|---|---|---|
| < 8dp | flat cushions; unplaced cells as pale tints, the selected color shown per the Selected color setting (Outline uses the stronger tint here) | zooms to 24dp cells centered on the tap |
| 8-15dp | + placed gems raised | zooms to 24dp cells centered on the tap |
| 16-19dp | + numbers on unplaced cells | places a gem; drag places along the path |
| ≥ 20dp | + facets, placement glint | places a gem |

- Max zoom is 48dp cells, and min zoom fits the whole canvas.
- Placement needs at least 16dp cells, so a gem is never placed where its number can't be read.
- **Selected color** (founder decision 2026-10-13 for the default, #19 for the choice): how the remaining cells of the selected color stand out is a Settings choice with three styles. Placed gems look the same in all three.
  - **Stronger tint** (default): the selected color's remaining cells use their gem color mixed 40% toward `bone` instead of 78%, at every zoom; their number is `on-bone` or `bone`, whichever reads better. It previews the gem and shows at fit zoom. It's weak for light colors (cream, skin tones), whose stronger tint lands near the other pale tints.
  - **Outline** (the Phase 5 D17 two-tone ring): from 8dp, each remaining cell of the selected color keeps its pale tint and gets a 1.5dp `on-bone` outer ring along the cushion edge plus a 1dp `lamp` inner ring, following the 18% corner radius. Below 8dp, where a ring can't be drawn, it falls back to the stronger tint, so the selection still shows at fit zoom. The rings sit inside the cushion's edge and the number is centered in the opening, so they never cover it (at 16dp the opening is ~9.8dp, the widest number "36" ~9dp).
  - **Dim the rest**: the selected color's remaining cells keep their pale tint; every other unplaced cell is darkened 35% toward `felt-deep`, at every zoom. Numbers on dimmed cells are `on-bone` or `bone`, whichever reads better: at worst 3.9:1 (pale tints of near-black), above the stronger tint's own worst case (3.2:1, a mid violet), at least 5.5:1 on cream and skin tones.
  - The style applies at once, including on a canvas that's already open, and changes nothing else: not the TalkBack canvas node, not the log, not the time-lapse (which never shows a selection).
  - QA each style on a dark, a cream and a skin-tone color, at fit, 8, 16 and 24dp, and under reduced motion.
- **Placed vs unplaced** (Phase 5 D18): from 8dp up, a placed gem gets a 1dp bottom-right edge at 18% black and a 1dp top-left edge at 12% white. Unplaced cells stay flat.
  - This makes white, cream and skin-tone gems readable, where the tint vs gem contrast is only 1.1-1.2:1.
  - Bake the edges into the per-color sprite (T10).
- **Gestures** (Phase 5 D23):
  - **One finger, 16dp and up:** a drag that starts on an unplaced cell of the selected color paints. Cells of other colors along the path are skipped silently, with no wrong-tap haptic during a drag. A drag that starts anywhere else pans.
  - **One finger, under 16dp:** a drag pans.
  - **Two fingers:** always pan and zoom.
  - **Tap a placed gem:** nothing happens.
  - **Double-tap:** zooms to 24dp cells, or back to fit when already zoomed in. Taps never wait for a possible second tap: a double-tap counts only when neither tap places a gem (each lands on a placed gem, another color or outside the canvas). A quick second tap that would place (an unplaced cell of the selected color, at 16dp and up) is a normal tap and places its gem, so a corrective tap right after a miss never zooms out. Under 16dp the first tap already zooms in, so the second adds nothing.
  - **Long-press a cell:** selects its color.
- **First-time hints** (Phase 5 D12): three inline bone labels on a `felt-deep` chip over the canvas.
  - Each shows once ever and fades after its action or after 6s. One shows at a time: a tap-to-zoom shows hint 3 first, then hint 2.
  - The "shown" flags hold no photo data and may be backed up and transferred.
  - There is no dimming overlay and no Next button. TalkBack reads them.
  1. First canvas open: "Pinch or tap to zoom in until numbers appear"
  2. First time cells reach 16dp: "Pick a color below, then tap or drag its numbers"
  3. First tap-to-zoom: "Zoomed in so you can see the numbers"
- **Save failing** (Phase 5 D9): if a save fails because storage is full, a quiet line appears under the top bar: "Progress isn't saving: phone storage is full." It stays until a write succeeds.
- **Top bar at large font** (Phase 5 D20): the title ellipsizes to 1 line. Above 1.5× font scale, progress shows as a mono "59%".
- **Wrong-color tap (eng D4, confirmed 2026-10-13):** rejected with **no state change**. Nothing goes in the log, no counter moves, no penalty applies. Feedback is one soft low haptic and the selected trough nudging 2dp sideways over 150ms. No red, no buzz, no toast.

**Haptics and sound.**

| Event | Haptic | Sound |
|---|---|---|
| Gem placed | `HapticFeedbackConstants.CLOCK_TICK`; drag throttled to ≤ 1 per 40ms | soft click, ±3 semitone random pitch |
| Wrong color | `TEXT_HANDLE_MOVE` (lighter than the tick) | none |
| Color finished | `CONFIRM` (API 30+), else `CONTEXT_CLICK` | felt-flap thump |
| Picture finished | one 400ms soft `VibrationEffect` composition | one low chime |

Sound and Haptics each get a Settings toggle. Both default to on.

**Gallery tile.**
- A tile shows its real progress: placed gems in color, unplaced cells as pale tints, with the title and a mono % (or "Done") underneath.
- Locked pictures stay fully visible, with a small `felt-deep` label at the bottom-left (Phase 5 D14). Never put a padlock over the art or dim it.
  - The label is a 12dp play-triangle **vector** in bone plus "Watch to unlock" in Figtree 13sp bone (7.58:1), with `sm` radius and 4/8dp padding.
  - Never use the ▶ text glyph, which some fonts draw as an emoji.
  - The whole tile is the tap target. Labels disappear without animation when VIP starts.
- Every tile has a ⋮ menu, also opened by long-press (see Navigation map):
  - photo canvases: Rename, Delete;
  - bundled pictures: Start over.
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

**Photo flow states** (Phase 5 D4).

| Step | State | What the user sees |
|---|---|---|
| Photo Picker | cancelled | back to the gallery, no message |
| Decode | fails (corrupt, unsupported, cloud-only) | inline on the gallery: "This photo couldn't be opened. Try another one." |
| Crop | default | ratio chips 1:1 / 3:4 / 4:3 (selected chip has a lamp outline). Default ratio comes from the photo's orientation (portrait → 3:4, landscape → 4:3, square → 1:1). Pinch and drag inside the frame; no rotation. Primary "Next" |
| Size picker | face check running | the size rows are enabled; a muted line reads "Checking for faces…". Pre-selection and the face hint apply when the check ends (≤ 1s; on failure it counts as "no face") |
| Preview | generating | "Setting the gems…" with a 2dp indeterminate line where the preview will appear; Start is disabled |
| Preview | converter fails | "Couldn't make this canvas. Try Medium or another photo." with a quiet "Back" |
| Preview | faces disabled (`faceMinSize` = Disabled) | hint "Faces aren't supported yet"; Start is replaced by primary "Choose another photo" |

**Size option.**
- Selected: 2dp lamp inset. Disabled for face photos below `faceMinSize`: 50% opacity, not tappable, with the reason in the hint.
- Face hint copy follows the faceMinSize table: "Faces look best at Medium or Big" / "Faces need Big" / "Faces aren't supported yet".

**Preview + Start.**
- Shows a flat-cushion preview of the whole canvas, with "Medium · 8,112 gems · 24 colors" and the allowance line ("Uses today's free photo canvas.").
- Start is single-flight: it shows a pressed state and ignores repeat taps.
- **Allowance line states** (Phase 5 D5):

| Situation | Line | Start |
|---|---|---|
| Free one available | "Uses today's free photo canvas." | Start |
| Free one used | "Today's free one is used. Watch a video for another." | primary becomes "Watch a video" (rewarded states) |
| Bonus earned from a video | "Uses a bonus canvas from a video." | Start |
| VIP | no line | Start |

**Completion: "lights down", the one authored moment.**
1. The chrome slides away over 250ms and the screen dims ~30%.
2. One raking specular sweep crosses the picture over 2.4s.
3. One low chime and the finish haptic play.
4. The picture holds still for 2s.
5. "Finished" (display-lg), the meta line (title · gems · time), then Share (primary) and Done (quiet) fade in.

Reduced motion skips the sweep. The interstitial (if allowed) can appear only after leaving this screen.

**Share and the meta line** (#13, #4): Share is the primary button; it opens the Share progress sheet below. A killed encode's message ("The video stopped when the app closed. Tap Share to try again.") is a muted line under Share for that visit. The lights-down dim is on the room around the picture; the picture itself stays lit. The meta line's time is active play time ("5 min", "1 h 12 min").

A finished picture reopened from the gallery shows the **completion still**:
- the picture at rest with no sweep;
- the "Finished" title and meta line;
- Share (primary), Done (quiet) and a "Play again" text button. "Play again" asks the Start over confirm.

**Share progress sheet.**
- Title "Making your video", the line "About 15 seconds of sparkle. Keep the app open.", a 6dp bone progress bar, a mono %, and a Cancel text button.
- A drop to 720p is silent.
- **PNG fallback:** the sheet title becomes "Here's a picture instead", with the body "Video isn't supported on this phone, so here's a before/after picture instead." It shows both thumbnails (labelled "Before" and "After" in muted 13sp) and a primary "Share picture" button. The PNG itself is 1080×1920: "Before" (the cropped photo, or the empty tint grid for a bundled picture) above "After", on the same band with the same title, app line and watermark row as the video.
- Dismissing the sheet while it runs is a Cancel. The failure and disk-full messages keep the sheet's "Making your video" title; swipe, back or the scrim closes them.
- **Edge states** (Phase 5 D8):
  - **Background:** encoding continues while the process lives. There is no foreground service.
  - **Process killed mid-encode:** on the next visit to that completion screen: "The video stopped when the app closed. Tap Share to try again."
  - **Video and picture fallback both fail:** "Couldn't make the video. Try again." with a quiet "Try again".
  - **Disk full:** "Not enough space on this phone to make the video."
  - **Cancel, or the share sheet is dismissed:** back to completion with no message.

**Rewarded states** (locked picture or extra photo canvas):

| State | Copy | Actions |
|---|---|---|
| Loading (≤ 5s) | primary button disabled: "Loading video…", plus a 2dp indeterminate line | none |
| Video ready (#7) | sheet title "Watch to unlock" / "Another photo canvas"; body "Watch a short video to unlock this picture for good." / "Watch a short video for one more photo canvas today." | primary "Watch video" |
| Online no-fill, grace available | "No video right now. This one's on us today." | primary "Unlock picture" / "Start canvas"; text "Or get VIP: every picture unlocked" / "Or get VIP: unlimited photo canvases" |
| Online no-fill, grace used | "No video right now. Try again in a bit." | quiet "Try again"; text "Get VIP: no videos needed" |
| Offline | "You're offline, connect to watch a video" (in `fault`) | quiet "Try again"; text "Get VIP: no videos needed". No grace. |
| Reward granted (Phase 5 D11) | locked picture: the sheet shows "Unlocked" for 600ms, then the canvas opens. Extra canvas: back to Preview with "Uses a bonus canvas from a video." | none |
| Video closed early (Phase 5 D11) | back to the sheet; muted line "Watch to the end to unlock." No grace, no penalty | the primary button is enabled again |

**Paywall.**
- "Gemmory VIP" title, then four perk lines with patina square bullets: Every picture unlocked, Unlimited photo canvases, No ads ever, No watermark on your videos.
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
| Success | the paywall closes; one gallery line: "You're VIP. Every picture is unlocked." (or "Ads are off for good." for remove-ads) |
| Already owns remove-ads | the "Just remove ads" row is hidden |
| Already VIP | the paywall isn't reachable; "VIP" and the Membership row open Play's subscription page |

**Settings.**
- Sound, Haptics, Selected color, Membership (shows `VIP · yearly` / `Ads removed` / `Free`), Manage subscription (VIP only, opens Play), Restore purchases, Privacy choices (UMP, shown only when UMP says privacy options are required), Delete all photo canvases, Privacy policy, and Version (mono).
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
- **Timing** (Phase 5 D26): 15s in total.
  - 1s intro: photo canvases show the cropped source photo crossfading to the empty tint grid; bundled pictures show the tint grid only.
  - 12s build.
  - A silent 2s hold of the finished picture to end.

## Accessibility

- **Contrast:** follow the valid-grounds table in Colors. New UI color needs a decision-log entry.
- **TalkBack** (Phase 5 D19):
  - **Canvas:** one node: "Canvas. 4,812 of 8,112 placed. Color 7 selected, 312 left." Custom actions: Place next gem, Find next gem, Zoom in, Zoom out, Whole picture.
    - **Place next gem** places the cell Find last showed while it's still empty, else the next remaining cell of the selected color in reading order, through the same rule as a tap (the log and time-lapse can't tell). It announces the count left ("311 left"; founder call #14), or "Color 7 done. Color 8 selected." when the color finishes.
    - **Find next gem** is the Find button's logic. Find and Place next gem share one cursor, which starts over at the top when the selected color changes.
    - **Zoom in / Zoom out** step through fit → 8 → 16 → 24 → 48dp, the levels of the zoom table. **Whole picture** goes back to fit.
    - Announcements go through one polite live region (not `announceForAccessibility`), cleared 3s after it speaks; the same words twice in a row are still spoken twice.
  - **Wrong tap:** announces "Not color 7". This is an announcement only, with no state change (eng D4).
  - **Color finished:** announces "Color 7 done. Color 8 selected."
  - **Trough:** "Color 7, 312 left, selected" or "Color 7, done". The description is "Color 7, 312 left" and "selected" comes from the selected state, so TalkBack doesn't say it twice.
  - **Gallery tile:** "Sunflowers, 42% done" / "Sunflowers, done" / "Sunflowers, locked, watch a video to unlock" ("…, can't open" when damaged), with its ⋮ actions as custom actions. The tile is one node: its title, % and ⋮ button are folded into it, so it reads only that line.
  - Every icon button has a label: Settings, Find, close.
  - **Sheets:** focus moves to the sheet title on open and returns to the trigger on close. TalkBack moves its focus on its own when a sheet's window closes, so the trigger keeps asking for it for 1.2s after.
- **Font scale:** see the tray and top bar rules. Sheets, paywall rows and size options wrap, with no fixed heights. QA at 200% on a 360dp-wide phone.
- **Touch targets:** 48dp, with the documented canvas exception.
- **Reduced motion:** see Motion.

## Do's and Don'ts

- **Do** keep the picture the only saturated thing on screen. New UI color needs a decision-log entry.
- **Do** use `lamp` for exactly one meaning: "this is the selected one".
- **Do** show progress as the art itself (tiles, tray heaps). Use numbers only in mono, small.
- **Do** design the empty, loading, offline and error state of every component (see Rewarded states and Restore).
- **Do** keep calm copy plain and specific: "You're offline, connect to watch a video", not "Oops!".
- **Don't** add confetti, coin bursts, glitter particles, gold bevels, purple, neon or glow halos, even for events.
- **Don't** signal a wrong tap with red, shake, buzz or a counter.
- **Don't** put a padlock over locked art or blur it; locked pictures are shown in full.
- **Don't** use pill buttons, cards inside cards, emoji, or gradient buttons.
- **Don't** put ads, banners or VIP nags on the play canvas.

## Motion

- **Approach:** intentional and slow. Motion confirms; it never celebrates loudly.
- **Easing:** enter ease-out (`FastOutSlowIn` decelerate), exit ease-in, move ease-in-out.
- **Duration:** micro 80ms (placement glint), short 150-250ms (trough lift and flap, sheet), medium 250-400ms (screen transitions, chrome slide), long 2.4s (completion sweep only).
- **Placement glint:** a 180ms specular pass across the placed gem at 20dp and up. No particles.
- **The one authored moment:** completion "lights down" (see Components).
- Reduced motion (`ANIMATOR_DURATION_SCALE == 0` or the accessibility setting) skips the glint and the sweep. The 2s hold remains.
- That setting also makes Compose finish every animation at once, so the reduced-motion fades and the blink run at their real duration on purpose (#14).
- Under reduced motion, movement becomes fades (Phase 5 D21):
  - The wrong-tap trough nudge becomes the haptic plus a 120ms lamp-outline blink.
  - Sheets, chrome and screen transitions become 150ms fades. Sheets and their scrim fade in; a sheet that closes leaves at once, as it does without reduced motion. Screens have no transition, so they switch at once.
  - The trough lift is instant.
  - The Find cell pulse is skipped.
  - The lights-down dim (a fade) stays.
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
