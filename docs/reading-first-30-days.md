# Reading the first 30 days

How to read every success criterion of rounds 1-3 once Gemmory is in production: which event or report answers it, and how much the number can be trusted. The criteria themselves live in the design docs ([round 1](designs/gemmory.md) → Success Criteria, [round 2](designs/gemmory-round2.md), [streak](designs/streak-screen.md) O5, [round 3](designs/gemmory-round3.md), [ads](designs/ad-mediation.md)); the events and their params are listed in round 1's Analytics section. Built with T46 (launch funnel, #133).

## Before you read a number

- **Consented players only.** In the EEA, the UK and Switzerland, Analytics sees only the players who said yes in the consent form; everywhere else it's on by default. So every Firebase rate is a rate among consented players, and Europe is under-counted. Play Console reports (installs, ratings, store listing) count everyone.
- **The floor: 100.** A rate counts only once its denominator has at least **100 consented players** (for the first session: 100 `first_launch` events). Below that, write the count ("23 of 41"), not a percentage. Every percentage you write down carries its N.
- **Localized markets are one bucket.** Until each language has 100 players of its own, pool the 11 localized languages (de, es, pt-BR, fr, it, ro, pl, tr, hu, bg, ja) into one "localized" bucket and compare it with English. Never read a single small market on its own.
- **The denominator is `first_launch`, not `first_open`.** `first_launch` fires once, on a fresh install only, after the consent form has settled (T46). Firebase's automatic `first_open` fires again on a restored phone and isn't gated the same way. Use `first_open` only for install sources (below).
- **Production only.** Closed-test numbers are a handful of people (round 1 D18): informational.
- **Register the custom dimensions first.** Explorations can filter on an event param only once it's registered (Firebase console → Analytics → Custom definitions), and registration doesn't apply to the past. Before production, register these event-scoped dimensions: `source`, `size`, `kind`, `entry`, `days_since_available`, `collection_id`, `event_id`, `milestone`, `reason`, `result`, `saved_by`, `target`. Register `ms_since_first_launch` and `length` as custom metrics.

Where the numbers live: Firebase console → Analytics (Dashboard, Retention, User acquisition, and **Explore** for funnels and cohorts). Play Console → Statistics, Ratings and Store performance. Crashlytics for stability.

## Round 3: the first session and where installs come from

| Criterion | How to read it |
|---|---|
| ≥ 80% of `first_launch` have `first_gem` | Explore → Funnel exploration, **closed** funnel: step 1 `first_launch`, step 2 `first_gem`. The step 2 completion rate is the number. N = step 1 users. |
| Median time to first gem under 60 s | The same funnel, with step 2 set to "Within 60 seconds" of step 1. The median is under 60 s when the within-60 s count is at least half of the plain step 2 count. For a closer look, the `ms_since_first_launch` metric's distribution in a free-form exploration. (The clock starts when a fresh install first comes to the screen; `first_launch` itself waits for consent, so the two can be a second or so apart.) |
| ≥ 50% finish their first canvas on day 0 | Closed funnel `first_launch` → `canvas_finished`, step 2 "Within 1 day". This is a 24-hour approximation of "the same local day". |
| Installs from shares (informational) | User acquisition → **First user source** = `share` (Firebase reads Play's install referrer for `first_open`). The medium splits it: `bundled`, `photo`, `streak`. The campaign: `timelapse`, `streak_card`. There's no custom event for this (eng O-E5). |
| Shared-picture opens (informational, once T50 ships) | `canvas_started` with `entry` = `share_link`, divided by first users whose first user source is `share` and whose medium is a picture share. |
| Non-English installs (informational) | Play Console → Statistics → installs by country or language: the localized bucket's share of all installs. Store conversion per language: Play Console → Store performance → Store listing acquisition, by language (visitors → acquirers). |

## Round 1: early production

| Criterion | How to read it |
|---|---|
| Day-1 retention ≥ 35%, day-7 ≥ 12% | Explore → Cohort exploration. Cohort inclusion: `first_launch`. Return criterion: any event, with `day_played` as a stricter second view ("came back and played"). Daily granularity; read day 1 and day 7. Firebase's Retention page uses `first_open` instead: use it only as a cross-check. |
| ≥ 10% of finished photo canvases get shared | `share_completed` with `source` = photo and `kind` = finished, divided by `canvas_finished` with `source` = photo (event counts). |
| Installs from `utm_source=share` (informational, D14) | The same as round 3's "installs from shares". |
| Rewarded-ad opt-in ≥ 20% of daily users | Per day: users with `rewarded_watched`, divided by active users. Average it over the 30 days. |
| Closed-test numbers (12 testers, 50% finish a Quick, 30% photo) | Informational only (D18). |
| Production gate (D18): crash-free users ≥ 99%, no unrecovered save-load failure | Crashlytics. How it's checked: [play-compliance.md](play-compliance.md) → Production gate. |

## Round 2: habits and content

| Criterion | How to read it |
|---|---|
| Reminder open rate ≥ 8% (switched off if under 3% after 30 days) | `reminder_opened` count divided by `reminder_shown` count, split by `reason` for interest. |
| D1 and D7 not below the pre-T35 level | The round 1 cohort, split by app version (installs on the T35 build and later, against earlier ones). Before/after is correlational: other changes shipped too. |
| Reminder opt-in ≥ 40% | `reminder_prompt_result` with `result` = granted, divided by `reminder_prompt_shown` (users). |
| Drops: ≥ 30% of daily players start that day's drop on its date | Per date: users with `canvas_started`, `days_since_available` = 0, divided by users with `day_played` that date. |
| Collections: ≥ 10% of starters complete within 30 days | Closed funnel: `canvas_started` with a `collection_id` → `collection_completed`, "Within 30 days". |
| Supply: the calendar stays ≥ 7 days ahead | The CI check (T44), not Analytics. |
| Weekly event: ≥ 15% of players active in the event week earn its keepsake | Users with `event_reward_unlocked` for that `event_id`, divided by users with `day_played` inside the event's window. |
| Store rating ≥ 4.3 after 50 ratings | Play Console → Ratings. |
| Feel: no regression in photo recognizability | By hand (founder plus 3 testers on the converter goldens); session length from Firebase is informational. |

## Streak (O5 targets and kill rules)

| Issue | Target | How to read it |
|---|---|---|
| A, celebration (#101) | Next-day return after a celebration beats next-day return after a plain finish | Two cohorts: inclusion `streak_celebration_shown`, against inclusion `canvas_finished` without a celebration that day. Return criterion `day_played` on day 1. **Kill:** 1-star reviews citing pressure, or a median session drop. |
| B, freezes and repair (#102) | Share of 7-day streaks kept alive by a freeze or a repair | `streak_day` with `length` ≥ 7 whose streak saw `streak_freeze_used` or `streak_repair_completed`, divided by all `streak_day` with `length` ≥ 7. Also read `streak_celebration_shown`'s `saved_by`. |
| C, widget (#103) | Widget adds per weekly active player | Users with `streak_widget_added`, divided by weekly active users. **Kill:** if it adds crashes or ANRs (Crashlytics, Play Console → Android vitals). |
| D, share card (#104) | Share rate at milestones | `streak_share_completed`, divided by `streak_celebration_shown` with `milestone` > 0. |

## Ads (production launch)

The production-launch checks (AdMob linked to the listing, app-ads.txt verified, non-zero earnings within 7 days) are a checklist, not a 30-day rate: [designs/ad-mediation.md](designs/ad-mediation.md) → Success Criteria and #74.

## Checking that the events arrive

A fresh install of the `perf` build built with `-Pgemmory.firebaseInPerf=true`, with DebugView on (README → debug build switches), should show, in order: `first_launch` → `canvas_started` → `first_gem` → `day_played` → `canvas_finished` → `share_tapped` → `share_completed`. A second launch, an upgrade or a restored phone shows neither `first_launch` nor `first_gem`. The T46 PR (#133) has the first run of this check.
