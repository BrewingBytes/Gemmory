# TODOS

## Monetization

### Gallery banner ad (v1.1)
- **What:** Small AdMob banner on the gallery screen for Free users.
- **Why:** Always-on ad revenue, even from players who never finish a picture.
- **Pros:** Extra ARPDAU at negligible build cost.
- **Cons:** Clutters the most-seen screen. Adds one more ad unit and consent surface.
- **Context:** Deferred at the CEO review on 2026-10-12 (CUT-1 / D2) so testers and store screenshots see a clean gallery. Revisit once production ARPDAU and D7 retention data exist. Remove-ads and VIP must hide it.
- **Effort estimate:** human S / CC S
- **Priority:** P3
- **Depends on / blocked by:** MVP in production with 30 days of ad data.

### Server-side purchase verification
- **What:** A small backend that verifies Play purchases through the Play Developer API (`purchases.subscriptionsv2`, `purchases.products`) and listens to Real-time Developer Notifications (RTDN) for renewals, refunds and revocations. The app trusts the server's entitlement instead of deriving it on the phone.
- **Why:** On-device checks (MVP, eng review E6/D5) can be spoofed on rooted phones, and refunds go unnoticed while the phone is offline.
- **Pros:** Fraud and refund resistance; accurate entitlement across devices; a base for cloud save later.
- **Cons:** First backend for the project (hosting, auth, a privacy policy update). Ongoing ops cost.
- **Context:** Accepted at the CEO and eng reviews on 2026-10-12: on-device only for the MVP, "revisit if refund or fraud signals appear". Start from `shared/policy/Entitlements`, which stays as the pure derivation, with the server becoming its input.
- **Effort estimate:** human M / CC S
- **Priority:** P3
- **Depends on / blocked by:** MVP live; Play Console showing meaningful refund rate or fraud signals.

## CI

### Encoder smoke after Test Lab shuts down
- **What:** Before Firebase Test Lab shuts down on 2027-09-30, take the `v*` tag trigger out of `.github/workflows/testlab.yml` so release-candidate tags don't fail, then run `TimelapseEncoderSmokeTest` and `EncodeCancelTest` by hand on a real phone (`adb shell am instrument -w -e class …`) for each release, and say so in the release PR.
- **Why:** The tag job (#11, D7) and the A06 frame timing (#54) run on Test Lab, which stops working then. The move to Google Cloud's Developer Device Platform (#55) was closed as not planned: it has no free tier and the founder doesn't want a paid device service.
- **Pros:** Tags stay green; the encoder still gets a real-phone check before each release.
- **Cons:** One phone by hand instead of three chip families; the A06 frame-timing line is no longer checked automatically.
- **Context:** #55 closed 2026-10-22 (founder). Setup and quota today: README → "Encoder smoke on Firebase Test Lab" and "Frame timing on Firebase Test Lab".
- **Effort estimate:** human S / CC S
- **Priority:** P2
- **Depends on / blocked by:** Pick it up when a free device-test service appears (then re-open the move in #55's place), or by mid-2027 at the latest.

## Platform

### Cloud save through Play Games Saved Games (round 3, D3)
- **What:** Sync non-photo progress (bundled and remote canvases, unlocks, inventory, the streak) between a player's devices through Play Games Services v2 Saved Games (snapshots), with merge rules that can never duplicate an inventory item or a streak day. Photo canvases never leave the phone.
- **Why:** The category's top complaint is "no cloud sync"; Android Auto Backup already restores the same data on a new phone, but not live between two devices or when backup is off or stale.
- **Pros:** Two-device players keep one progress; a restore that doesn't depend on Google backup.
- **Cons:** Merge rules for a placement log, inventory and streak; Saved Games turned on in the Play Console; privacy policy and Data safety updates; no effect on installs or the first session.
- **Context:** Offered in round 2 (not taken up), raised again by the founder in round 3 ("cloud save could be added through play games? if not then defer") and deferred at the round-3 CEO review on 2026-10-22 (`docs/designs/gemmory-round3.md`). Start from `PlayGamesBackend` and the backed-up stores listed in `BackupRulesTest`.
- **Effort estimate:** human M-L / CC M
- **Priority:** P3
- **Depends on / blocked by:** Store reviews or support asks mentioning a lost save or a second device.

## Localization

### More languages (round 3)
- **What:** Add the next app and store-listing languages after the round-3 set (de, es, pt-BR, fr, it, ro, pl, tr, hu, bg, ja). Candidates: Indonesian, Korean, Vietnamese, Dutch, Czech.
- **Why:** Play search ranks listings per language; each new one opens a market's searches.
- **Pros:** Cheap once strings live in resources (T48) and the round-3 tooling exists (frames, translated titles, glyph test).
- **Cons:** Every monthly picture batch needs titles in one more language; a script our fonts lack needs a bundled font (as bg and ja did).
- **Context:** The founder added languages through the round-3 CEO review on 2026-10-22 and noted "we could add more languages later". Pick by Play Console's per-country store visits and installs.
- **Effort estimate:** human S per language / CC S
- **Priority:** P3
- **Depends on / blocked by:** T48 and T49 shipped; about 30 days of production data per country.

### Revisit the bundled Japanese font (round 3)
- **What:** Drop the bundled Zen Kaku Gothic New and let Android draw Japanese in the phone's own Noto CJK font.
- **Why:** The font is most of round 3's install-size growth (about 2.9 MB compressed for every player), while Android already falls back glyph by glyph to the system's Noto fonts, so Japanese never shows empty boxes without it.
- **Pros:** A smaller install for everyone.
- **Cons:** Japanese text no longer matches the bundled design-system fonts exactly (founder's reason for bundling, round-3 CEO C11).
- **Context:** Raised by the round-3 eng outside voice on 2026-10-22 (`docs/designs/gemmory-round3.md`, Eng Review → Outside voice, #1); the founder kept the font and asked for this TODO. GlyphCheck's font list and the design system's typography entry change with it.
- **Effort estimate:** human S / CC S
- **Priority:** P3
- **Depends on / blocked by:** Play Console showing install size hurting installs (store listing to install conversion, or size warnings on low-end phones).

## Game

### Home-screen widget (round 2, E2)
- **What:** A Jetpack Glance widget showing tonight's drop or Today's picture and the current canvas progress; a tap opens that picture's Preview. On-phone state only, never a photo canvas.
- **Why:** A pull that needs no notification permission, for players who decline reminders.
- **Pros:** Reaches the decliners; the art changes with each drop.
- **Cons:** Few casual players add widgets; one more surface to design and keep fresh.
- **Context:** Deferred at the round-2 CEO review on 2026-10-19 (E2, `docs/designs/gemmory-round2.md`), so T35's reminder data comes first.
- **Effort estimate:** human M / CC S
- **Priority:** P3
- **Depends on / blocked by:** T35 live in production; reminder opt-in under 40% of prompts shown.

### "Gems today" / "best session" line on the completion screen
- **What:** A mono line under the completion meta line, such as "2,140 gems today" or "Best session: 1 h 12 min".
- **Why:** One more small win on long canvases, alongside the #35 milestones.
- **Pros:** Cheap to show; motivating for players who come back daily.
- **Cons:** Needs a new per-day counter (no photo data, may be backed up). It crowds the lights-down meta line, and a daily count edges toward a streak mechanic the calm design avoids.
- **Context:** Left out of #35 by the founder on 2026-10-16 (milestones and peek shipped instead).
- **Effort estimate:** human S / CC S
- **Priority:** P3
- **Depends on / blocked by:** closed-test feedback asking for session stats, or D7 retention data showing long canvases are abandoned.
