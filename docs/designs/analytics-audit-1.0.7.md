# Analytics audit for 1.0.7 (Firebase / GA4)

Made by a Sonnet agent on 2026-11-27, read-only. Sources: `telemetry/Analytics.kt`, the call sites,
`docs/reading-first-30-days.md`, `docs/designs/ad-spots.md`, `docs/play-compliance.md` (Data safety), `AGENTS.md`.
None of it is decided yet. Today there are about 44 weekly consented players, so everything here reads as counts.
No event may carry user content or photos.

## 1. What the Firebase UI can't answer today

1. **Where players drop inside a picture.** The only events are `canvas_started` and `canvas_finished`
   (CanvasStore.kt:127, :185), with no progress in between.
2. **Session flow and play time.** Play time is computed locally in `onStopped`/`onClosed` (CanvasStore.kt ~190)
   but never sent.
3. **Time to finish and number of sittings.** `canvas_finished` carries no duration, no sittings count, no days
   since start, and no gem count.
4. **Which sizes and shapes players pick.** `size()` (Analytics.kt:256) drops the aspect ratio. Photo starts log
   `entry` hard-coded to `gallery` (CanvasStore.kt:231). The setup screens have no abandon event.
5. **How much the fill tool is used.** `fill_tool_used` fires once per canvas. Its `tier` only says vip or free,
   so Remove-ads players are hidden among free players.
6. **Coming back to an unfinished picture.** There is no `canvas_resumed` event.
7. **The fills economy.** `inventory_spent` has no amount, no reason and no balance.
8. **Take-up of the reward spots.** `rewarded_offered` fires when the sheet opens (Rewarded.kt:140).
   `interstitial_shown` has no params.
9. **Streaks.** There is no `streak_broken` event.
10. **Player segments.** No user properties are set anywhere.

## 2. Proposed changes

| Pri | Change | Name and params | Fires at | GA4 registration |
|---|---|---|---|---|
| P1 | Add `placement` to the three `rewarded_*` events | `placement`: completion_bonus, daily_gift, double_wheel, rewarded_interstitial, locked_picture, photo_canvas, colors, inventory_fills, streak_repair | `AnalyticsEvent.reward()`, derived from `RewardType` | dimension `placement` |
| P1 | `rewarded_offer_seen` (already decided, CEO D4 / design D20) | `placement`, `tier` | offer ≥50% visible, once per moment | reuses `placement` and `tier` |
| P1 | Enrich `rewarded_watched` | `reward_amount` (Long) | Rewarded.kt:224 | metric `reward_amount` |
| P1 | Enrich `interstitial_shown` | `placement` (interstitial or rewarded_interstitial), `skipped`, `finish_index` | Interstitials.kt:48 | dimension `skipped`, metric `finish_index` |
| P1 | Enrich `inventory_spent` | `amount`, `reason` (tool_brush, tool_region, tool_color, unlock, start_photo), `balance_after` | PlayViewModel.kt:90, Rewarded.kt:292 | dimension `reason`, metric `balance_after` |
| P1 | Enrich `canvas_finished` | `play_ms`, `sittings`, `days_open`, `gems` (cell count) | CanvasStore.kt:185 | metrics for each |
| P1 | Add the aspect ratio | `ratio` (3:4, 1:1, 9:16) next to `size` | Analytics.kt:256 | dimension `ratio` |
| P2 | `canvas_progress` | `source`, `size`, `pct` (25, 50 or 75), `play_ms`; once per threshold per canvas | PlayViewModel | dimension `pct` |
| P2 | `canvas_resumed` | `source`, `size`, `pct_done`, `days_since_last` | CanvasStore.open | metric `days_since_last` |
| P2 | Fix the photo start's `entry` | the real `opened_from` | CanvasStore.kt:231 | none |
| P2 | `canvas_abandoned_setup` | `source`, `size`, `step` (size_picker, colors, preview) | Preview back or close | dimension `step` |
| P2 | Enrich `fill_tool_used` | `uses`; `tier` as free, remove_ads, vip or earned | CanvasStore.kt:193 | metric `uses` |
| P2 | `streak_broken` | `length`, `repairable` | PlayStreak.kt ~92 | reuses `length` |
| P3 | `canvas_picked` | `source`, `category` (a fixed set, never a title), `size` | gallery tile tap | dimension `category` |
| P3 | `settings_changed` | `setting`, `value` | Settings | dimension `setting` |
| P3 | Enrich `timelapse_made` | `play_ms` | EncodeManager.kt:219 | reuses `play_ms` |

## 3. User properties (set only after consent; never `setUserId`)

| Property | Values | Why |
|---|---|---|
| `tier` | free, remove_ads, vip | splits every funnel by plan |
| `install_cohort` | YYYY-MM | cohort comparisons |
| `finished_count_bucket` | 0, 1-2, 3-9, 10+ | engagement segment |
| `photo_player` | true or false | has the player ever made a photo canvas |
| `entitlement_source` | family or own | if the field exists on Android |

These would be set in `androidApp/.../telemetry/FirebaseAnalyticsLogger.kt`.

## 4. Explorations to build in GA4

1. **Picture funnel:** started, progress 25/50/75, finished, share completed. Break down by size, ratio and source;
   segment by tier.
2. **Reward take-up:** offer seen, offered, watched, by placement. `rewarded_unavailable` by reason. Revenue per
   spot comes from AdMob by ad unit name.
3. **Effort to finish:** `play_ms`, `sittings` and `days_open` by size, plus `days_since_last` from
   `canvas_resumed`.
4. **Retention cohorts:** `first_launch` then `day_played`, by tier and install cohort.
5. **Purchase funnel:** paywall shown, purchase started, purchased, with cancelled as the exit. Ad revenue by
   format and source.

Mark `canvas_finished`, `vip_purchased` and `remove_ads_purchased` as key events.

## 5. Redundant or wrong today

- `photo_canvas_started` duplicates `canvas_started` with `source=photo`, so summing starts double-counts.
- `tier` on `todays_picture_started` and `fill_tool_used` labels Remove-ads players as free.
- `size` merges every aspect ratio into one value.
- `rewarded_offered` fires when the sheet opens, while ad-spots.md describes it as "on tap". Check this before
  building the 1.0.7 funnel.
- `interstitial_shown` has no params, so the two formats can't be compared.
- `inventory_spent` assumes an amount of 1.
- Restored installs carry no flag on `first_gem` and `canvas_started`.
- Once iOS ships, always filter by Platform.
- Never log `ad_impression` ourselves; it would double the revenue.

## Codex review (2026-11-27) and the founder's decision

Codex checked the audit against the code. The gaps are real, but some claims needed correcting:
- `placement` and `rewarded_offer_seen` are already decided, and the approved value is `daily_chest`, not
  `daily_gift`.
- Both photo start events fire on purpose, and summing two different event names is what double-counts.
- `gallery` also covers starts from Preview.
- The fill event fires once per tool per session, not once per canvas.
- A `skipped` flag on `interstitial_shown` can't measure a skipped intro, because that event records a real show.
- A single `reward_amount` mixes items unless it is paired with an item param.
- At about 44 weekly players, splits by tier × size × ratio aren't readable.

**Founder (D22): the narrow set plus three play stats in 1.0.7.**
1. Approved ad measurement:
   - `placement` on `rewarded_offered` / `rewarded_watched` / `rewarded_unavailable`.
   - `rewarded_offer_seen`, logged once per offer moment when the offer is fully faded in and at least 50%
     visible.
   - Documented meanings: `rewarded_offered` = the offer tapped or opened (legacy meaning kept), `rewarded_watched`
     = reward earned, `rewarded_unavailable` = no video at tap.
   - GA custom definitions registered and checked in DebugView before release.
2. `canvas_finished` gains `play_ms`, `sittings` and `days_open`, as metrics.
3. A `tier` user property: free, remove_ads or vip. It is user-scoped, set only after consent, never with
   `setUserId`.
4. `inventory_spent` gains `amount`, alongside its existing item param.

Everything else waits until the cohorts reach 100. Check the Data safety wording against the expanded collection;
no new identifier is added.
