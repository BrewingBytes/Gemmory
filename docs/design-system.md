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
| `text-muted` | 4.72 | 6.30 | **3.82** | never on `felt-raised`; use `bone` there |
| `lamp` (face hint text) | 5.65 | 7.55 | n/a | felt and felt-deep only |
| `patina-text` | 4.52 | 6.04 | **~3.7** | only on felt-deep, sheets or the dark felts |
| `fault` | **3.82** | 5.10 | **3.09** | only on felt-deep, sheets or the dark felts. On plain felt, offline copy is `bone` with a 6dp `fault` square marker before it |

QA checks every new string against this table.

**Canvas cell colors:** a placed gem is the exact grid color. An unplaced cell is that color mixed 78% toward `bone`, with its number in `on-bone`. A remaining cell of the selected color is its color mixed only 40% toward `bone`, with its number in `on-bone` or `bone`, whichever has more contrast. Gem colors are never re-tinted by the theme.

## Typography

Every face is OFL and verified in the google/fonts METADATA on 2026-10-13. **Bundle them as TTFs in `res/font`** (variable where available) rather than using GMS downloadable fonts, so first launch and offline play render correctly.

- **Bricolage Grotesque** (600, optical size axis) is the display face. It is only for screen titles (22sp), sheet titles (22sp), the completion title (34sp), the paywall title (30sp) and the time-lapse band title. It is warm and slightly crafty, unlike the gold bevels the shelf uses. Never use it for body text or buttons.
- **Figtree** (400 / 600) handles all UI and body: 16sp body at line-height 1.5, 16sp/600 buttons, 13sp captions.
- **Atkinson Hyperlegible Mono** (700, tabular figures) is for gem numbers on the canvas (0.46 × cell size), trough numbers and counts, and progress figures (`4,812 / 8,112`) and prices. It was designed for low-vision legibility, which matters for tiny cells and tired evening eyes. Use it only for figures, never for labels.

Scale: 34 / 30 / 22 / 16 / 13 sp. Adjacent levels differ by size *and* role, never by weight alone. Respect the system font scale. At 200% the canvas numbers do not scale (they follow cell size), but all chrome does.

## Layout

- Material 3 structure (top app bar, bottom sheets, system back) so Android feels native, with 16dp side gutters and a 4dp base grid.
- **Gallery:** a 2-column staggered grid (10dp gap) that honors 1:1 / 3:4 / 4:3 tiles. Tabs are "Pictures" and "My Photos". A "New from photo" primary button floats bottom-right. The first Pictures tile is the free starter picture (24×32, about 5 minutes; Phase 5 D13).
- **Play canvas:** a top app bar (back, title, mono progress), then a full-bleed canvas on `felt-deep`, then the tray bar at the bottom. No ads and no other chrome.
- **Size picker:** the face hint sits on top, followed by three option rows, each with a mini gem preview, gem count and color count.
- **Paywall:** a full screen with close visible immediately (top-left), plain perk lines, two plan rows, one primary button and a quiet "Just remove ads" row.
- Touch targets are at least 48dp, text buttons included. **Documented exception:** canvas cells can be placed from 16dp. The accessible alternatives are zoom up to 48dp cells, the Find button and the TalkBack canvas actions (Phase 5 D16).
- Sheets have a 16dp top radius, a grab handle and 20dp padding.

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
- **Font scale** (Phase 5 D20):
  - Trough width grows with the font scale, up to 72dp.
  - Above 1.5× the remaining count is hidden; TalkBack still reads it. The number never clips.

**Play canvas** (a single Compose `Canvas`, never one composable per cell).

| On-screen cell | Renders | Tap does |
|---|---|---|
| < 8dp | flat cushions; unplaced cells as pale tints, the selected color's in a stronger tint | zooms to 24dp cells centered on the tap |
| 8-15dp | + placed gems raised | zooms to 24dp cells centered on the tap |
| 16-19dp | + numbers on unplaced cells | places a gem; drag places along the path |
| ≥ 20dp | + facets, placement glint | places a gem |

- Max zoom is 48dp cells, and min zoom fits the whole canvas.
- Placement needs at least 16dp cells, so a gem is never placed where its number can't be read.
- **Selected-color tint** (founder decision 2026-10-13, replaces the Phase 5 D17 ring): the remaining cells of the selected color use their own gem color mixed 40% toward `bone` instead of 78%, at every zoom. Their number is `on-bone` or `bone`, whichever reads better.
  - It reads as a preview of the gem and stays visible at fit zoom, where the ring wasn't drawn.
  - It's weak for light colors (cream, skin tones), whose stronger tint lands near the other pale tints. #19 lets the player switch to the D17 outline ring (1.5dp `on-bone` outer + 1dp `lamp` inner, from 8dp) or to dimming the other cells.
  - QA it on dark, white and skin-tone colors.
- **Placed vs unplaced** (Phase 5 D18): from 8dp up, a placed gem gets a 1dp bottom-right edge at 18% black and a 1dp top-left edge at 12% white. Unplaced cells stay flat.
  - This makes white, cream and skin-tone gems readable, where the tint vs gem contrast is only 1.1-1.2:1.
  - Bake the edges into the per-color sprite (T10).
- **Gestures** (Phase 5 D23):
  - **One finger, 16dp and up:** a drag that starts on an unplaced cell of the selected color paints. Cells of other colors along the path are skipped silently, with no wrong-tap haptic during a drag. A drag that starts anywhere else pans.
  - **One finger, under 16dp:** a drag pans.
  - **Two fingers:** always pan and zoom.
  - **Tap a placed gem:** nothing happens.
  - **Double-tap:** zooms to 24dp cells, or back to fit when already zoomed in.
  - **Long-press a cell:** selects its color.
- **First-time hints** (Phase 5 D12): three inline bone labels on a `felt-deep` chip over the canvas.
  - Each shows once ever and fades after its action or after 6s.
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
- **Damaged canvas** (Phase 5 D9): when the save or grid is refused and there's no last good file, the tile keeps its last thumbnail with a `felt-deep` label "Can't open".
  - Tapping it opens a sheet: "This canvas's file is damaged, so it can't be opened. You can delete it, or start the picture over."
  - The primary button is Delete for a photo canvas, Start over for a bundled picture. Keep is a text button.
- **My Photos empty** (Phase 5 D10):
  - Display title "Your photos, in gems".
  - Body: "Turn a photo of your own into a canvas you can finish. It never leaves this phone."
  - An inline primary "New from photo". The floating button is hidden on this empty tab.
- The "New from photo" floating button shows on both tabs. The grid gets 88dp of bottom padding so the button never covers a tile's %.
- **Allowance note** (Phase 5 D5): when today's free photo canvas is used, one muted line sits beside the floating button. The line has the same copy as the Preview allowance line.

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
| Free one used, extras left | "Today's free one is used. Watch a video for another (2 left today)." | primary becomes "Watch a video" (rewarded states) |
| Bonus earned from a video | "Uses a bonus canvas from a video." | Start |
| All 3 extras used | "That's all the photo canvases for today. More at midnight, or get VIP for unlimited." | disabled; text "Get VIP" |
| VIP | no line | Start |

**Completion: "lights down", the one authored moment.**
1. The chrome slides away over 250ms and the screen dims ~30%.
2. One raking specular sweep crosses the picture over 2.4s.
3. One low chime and the finish haptic play.
4. The picture holds still for 2s.
5. "Finished" (display-lg), the meta line (title · gems · time), then Share (primary) and Done (quiet) fade in.

Reduced motion skips the sweep. The interstitial (if allowed) can appear only after leaving this screen.

A finished picture reopened from the gallery shows the **completion still**:
- the picture at rest with no sweep;
- the "Finished" title and meta line;
- Share (primary), Done (quiet) and a "Play again" text button. "Play again" asks the Start over confirm.

**Share progress sheet.**
- Title "Making your video", the line "About 15 seconds of sparkle. Keep the app open.", a 6dp bone progress bar, a mono %, and a Cancel text button.
- A drop to 720p is silent.
- **PNG fallback:** the sheet title becomes "Here's a picture instead", with the body "Video isn't supported on this phone, so here's a before/after picture instead." It shows both thumbnails and a primary "Share picture" button.
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
| Online no-fill, grace available | "No video right now. This one's on us today." | primary "Unlock picture" / "Start canvas"; text "Or get VIP: every picture unlocked" |
| Online no-fill, grace used | "No video right now. Try again in a bit." | quiet "Try again"; text "Get VIP: no videos needed" |
| Offline | "You're offline, connect to watch a video" (in `fault`) | quiet "Try again"; text "Get VIP: no videos needed". No grace. |
| Reward granted (Phase 5 D11) | locked picture: the sheet shows "Unlocked" for 600ms, then the canvas opens. Extra canvas: back to Preview with "Uses a bonus canvas from a video." | none |
| Video closed early (Phase 5 D11) | back to the sheet; muted line "Watch to the end to unlock." No grace, no penalty | the primary button is enabled again |

**Paywall.**
- "Gemmory VIP" title, then four perk lines with patina square bullets: Every picture unlocked, Unlimited photo canvases, No ads ever, No watermark on your videos.
- Yearly (pre-selected, lamp outline, shows the per-week equivalent) and Weekly plan rows. Prices are in mono.
- One primary "Start VIP" button, then a "Just remove ads" one-time row, then the renewal fine print.
- No countdowns, no fake strike-through prices, no delayed close button. Prices come from Play Billing `ProductDetails`, never hard-coded.
- **States** (Phase 5 D6):

| State | What the user sees |
|---|---|
| Prices loading | plan rows as `felt-raised` placeholders; Start VIP disabled |
| Prices fail / offline | "Prices couldn't load. Connect and try again." + quiet "Try again" |
| Purchase pending | "Payment pending. VIP starts when Google Play confirms it." |
| User cancels | back to the paywall, no message |
| Purchase error | "Google Play couldn't finish the purchase. You weren't charged." |
| Success | the paywall closes; one gallery line: "You're VIP. Every picture is unlocked." (or "Ads are off for good." for remove-ads) |
| Already owns remove-ads | the "Just remove ads" row is hidden |
| Already VIP | the paywall isn't reachable; "VIP" and the Membership row open Play's subscription page |

**Settings.**
- Sound, Haptics, Membership (shows `VIP · yearly` / `Ads removed` / `Free`), Manage subscription (VIP only, opens Play), Restore purchases, Privacy choices (UMP, shown only when UMP says privacy options are required), Delete all photo canvases, Privacy policy, and Version (mono).
- **VIP not confirmed** (Phase 5 D7): after the 7-day offline fallback (eng D5), Membership reads "VIP · connect to Google Play to confirm". The gallery shows one line, once: "We couldn't confirm VIP for a week. Connect to the internet to restore it."
- **Delete all photo canvases** (Phase 5 D27):
  - It's a plain bone row, not a red button.
  - Confirm: "Delete all photo canvases? Every photo, grid and its progress is removed from this phone. Pictures and purchases stay." Buttons: Delete (primary), Keep (text).
  - It cancels any running encode first (eng E4d).
- Restore purchases is a quiet button. While running it shows "Checking Google Play…". Results appear inline: "Purchases restored: VIP (yearly)", "No purchases found on this account", or "Couldn't reach Google Play. Try again."

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
  - **Wrong tap:** announces "Not color 7". This is an announcement only, with no state change (eng D4).
  - **Color finished:** announces "Color 7 done. Color 8 selected."
  - **Trough:** "Color 7, 312 left, selected" or "Color 7, done".
  - **Gallery tile:** "Sunflowers, 42% done" / "Sunflowers, done" / "Sunflowers, locked, watch a video to unlock", with its ⋮ actions as custom actions.
  - Every icon button has a label: Settings, Find, close.
  - **Sheets:** focus moves to the sheet title on open and returns to the trigger on close.
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
- Under reduced motion, movement becomes fades (Phase 5 D21):
  - The wrong-tap trough nudge becomes the haptic plus a 120ms lamp-outline blink.
  - Sheets, chrome and screen transitions become 150ms fades.
  - The trough lift is instant.
  - The Find cell pulse is skipped.
  - The lights-down dim (a fade) stays.
- Haptics also follow the system touch-feedback setting.

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
