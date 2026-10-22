# Play compliance and the production gate

What Gemmory declares in Play Console, Firebase and Google Cloud, why, and how the founder decides when production can open (#5 T5, design doc D10, D17, D18). The founder enters every Console answer by hand. Keep this file in step with the app: when an SDK, an event, a permission or a backup rule changes, update the matching row here, then [privacy-policy.md](privacy-policy.md), then the Console.

Facts the answers rest on (checked 2026-10-15 on 0.2.4 (6), Firebase BOM 34.19.0, Google Mobile Ads 25.5.0, UMP 4.0.0, ML Kit face detection 16.1.7, Play Billing 9.1.0, targetSdk 37; Play Games Services v2 22.1.0 added 2026-10-18, #49):

- Photos are processed only on the phone. Nothing in the app uploads them (AGENTS.md).
- No accounts, no login, no user IDs anywhere (`setUserId` is never called). Play Games (#49) signs the player in with their own Google Play Games profile; the app only reads the Play Games player ID to remember what Play Games confirmed, and never sends it to anyone else.
- Crashlytics is on for everyone in release builds (D17). Non-fatals: converter, ML Kit, encoder, billing, canvas save/load, FileStateStore. Paths and `content://` URIs are scrubbed before sending.
- Firebase Analytics starts off with every consent type denied. It turns on only when the UMP result grants `analytics_storage`. Outside GDPR regions UMP grants everything, so Analytics is on by default there. Events: design doc → Analytics.
- AdMob: rewarded + interstitial. The merged manifest declares `com.google.android.gms.permission.AD_ID`.
- Backup excludes photo canvases, the ledger (allowance counter, ad caps, entitlement cache) and Firebase's identifiers (Crashlytics UUID, Analytics app instance ID, their queues). The Firebase installation ID lives in `no_backup/`. Checked on a device with a local-transport backup (#5).

## Privacy policy

- **URL:** `https://github.com/BrewingBytes/Gemmory/blob/main/docs/privacy-policy.md` (also `PRIVACY_POLICY_URL` in `SettingsScreen.kt`). Public, no login, not a PDF. It changes the moment a PR touching it merges, so merge policy changes before the build that needs them ships.
- **Console:** Policy and programs → App content → Privacy policy → paste the URL.
- Before publishing it anywhere else (a website, GitHub Pages), ask the founder.

## Data safety

Play Console → Policy and programs → App content → Data safety.

### Data collection and security

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Crashlytics, Analytics, AdMob, Unity Ads (T34), ML Kit and Play Games Services send data off the phone. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | Firebase, ML Kit and Play Games Services use HTTPS, AdMob uses TLS (Google's SDK disclosures), Unity Ads encrypts all of it in transit (Unity's disclosure). |
| Account creation | **My app does not allow users to create an account** | No accounts or login. Play Games sign-in (#49) uses the player's Google Play Games profile, which Google runs; the app creates no account. Re-check the wording when the Console shows it. |
| Do you provide a way for users to request that their data is deleted? | **No** | Nothing we hold links data to a person. The policy explains uninstall/clear storage resets the IDs and the retention periods (founder call, #5). |
| Independent security review | Leave unchecked | Not done. |

### Data types

Declare only these. Everything else is **not collected**.

| Category → type | Collected | Shared | Processed ephemerally | Required or optional | Purposes (collected) | Purposes (shared) | Which SDK, and why |
|---|---|---|---|---|---|---|---|
| Location → **Approximate location** | Yes | Yes | No | Required | Advertising or marketing; Analytics; App functionality; Fraud prevention, security, and compliance | Advertising or marketing; Analytics; App functionality; Fraud prevention, security, and compliance | AdMob collects and shares the IP address, "which may be used to estimate the general location". Analytics derives a coarse region from the masked IP. Unity Ads collects and shares approximate location for all four purposes (T34). The ad SDKs run for everyone, so it can't be turned off. |
| Financial info → **Purchase history** | Yes | Yes | No | Required | Analytics; Advertising or marketing | Advertising or marketing; Analytics | Analytics logs Play purchase events (product, price, currency) automatically, plus our `vip_purchased` / `remove_ads_purchased`. Unity Ads' disclosure declares purchase history collected and shared for advertising and analytics (T34); the app passes Unity nothing about purchases, but the SDK's declaration is copied as is. Required because outside the EEA/UK/CH there is no opt-out. Payment details are Google Play's, not ours: not declared. |
| App activity → **App interactions** | Yes | Yes | No | Required | Analytics; Advertising or marketing; Fraud prevention, security, and compliance | Advertising or marketing; Analytics; Fraud prevention, security, and compliance | Analytics events (canvas started/finished, with a photo canvas's color choice and where it was opened from, a day played, the Tomorrow line tapped, the evening reminder's ask, settings, shown, late and opened events, share, rewarded, interstitial, time-lapse, daily streak length, today's picture started, fill tool used, inventory items earned and spent). AdMob collects and shares "app launch, taps, and video views". Unity Ads collects and shares page views and taps inside its ads only, not during play (T34). |
| App info and performance → **Crash logs** | Yes | No | No | Required | Analytics | — | Crashlytics stack traces, always on (D17). Play's "Analytics" purpose includes diagnosing and fixing crashes. |
| App info and performance → **Diagnostics** | Yes | Yes | No | Required | Analytics; App functionality; Advertising or marketing; Fraud prevention, security, and compliance | Advertising or marketing; Analytics; App functionality; Fraud prevention, security, and compliance | Crashlytics non-fatals and device state at crash time; ML Kit performance metrics and error codes; AdMob "performance of your app and the SDK" (shared); Unity Ads app diagnostics for app functionality and analytics (shared, T34); Play Games Services' own SDK analytics and diagnostics (#49). |
| Personal info → **User IDs** | Yes | Yes | No | Required | App functionality | App functionality | Unity Ads (T34) declares "personal identifiers" collected and shared for app functionality, for everyone who gets ads, so the row is now Required and Shared. Play Games Services (#49): the player's Play Games identity (player ID, gamertag, avatar) is collected by Google's SDK when they use Play Games (not shared); the app reads only the player ID and keeps it on the phone. |
| App activity → **Other actions** | Yes | No | No | Optional | App functionality | — | Play Games Services (#49): unlocked achievements and steps of incremental ones (gameplay progress, event ids only), stored by Google for the player's Play Games profile. Optional for the same reason. |
| Device or other IDs → **Device or other IDs** | Yes | Yes | No | Required | Analytics; App functionality; Advertising or marketing; Fraud prevention, security, and compliance | Advertising or marketing; Analytics; App functionality; Fraud prevention, security, and compliance | AdMob: advertising ID and app set ID (shared). Unity Ads: device identifiers for all four purposes (shared, T34). Analytics: app instance ID (+ advertising ID when ad storage is granted). Crashlytics installation UUID, Firebase installation ID, ML Kit per-installation ID. |

**Not declared, and why:**

- **Photos and videos:** the photo is read and converted on the phone and never transmitted. Play counts data as collected only when it leaves the device. Sharing the time-lapse is a user-initiated transfer to the app the player picks, which Play excludes from "shared".
- **Personal info (name, email, address, phone):** none. No accounts, `setUserId` never called. User IDs are declared only for Play Games (#49, above).
- **Financial info → payment info / credit card:** Google Play handles payment; the app never receives it.
- **Precise location, messages, contacts, calendar, audio, files, health, web browsing, in-app search, other user-generated content:** not collected. (Other actions is declared for Play Games achievements, #49.)
- **App info and performance → Other app performance data:** nothing beyond crash logs and diagnostics.

**Unity Ads (T34, #74), checked 2026-10-21: purposes and rows widened.** Source: Unity's "Google Play data safety section for Unity Ads" (docs.unity.com/en-us/grow/ads/privacy/google-data-safety, read 2026-10-21 for SDK 4.21.0). Unity serves ads only as a bidder inside AdMob mediation; it never sees a photo, canvas or title. Changes above: Approximate location, Diagnostics and Device or other IDs gain App functionality; Purchase history becomes Shared (Advertising or marketing); User IDs becomes Required and Shared. Not declared: Unity's "Other app activity" (app usage times) is collected only when Acquire Optimization is on in the Unity Monetization dashboard, which stays **off** (founder: keep it off, or this row must be added). Crash logs, precise location, name, email and the rest: Unity declares none. Unity offers its own deletion route (its privacy policy); "request deletion" stays **No** for our own data, and the privacy policy points to Unity's. Merged release manifest checked 2026-10-21: Unity adds no permission (its INTERNET, ACCESS_NETWORK_STATE, AD_ID and ACCESS_ADSERVICES_* are already there from AdMob), only its own ad activity, a non-foreground service and an initializer provider. **Founder:** submit the updated form in Play Console before the 0.8.1 build is promoted to closed.

**Remote pictures (#43), re-checked 2026-10-17: no change.** Once a day the app GETs a signed catalog and grid files from `raw.githubusercontent.com` (GitHub, Microsoft). The request carries no identifier, cookie, account or user content (plain `HttpURLConnection`, the platform's default user agent), and the app sends nothing in a body. GitHub sees the IP address and user agent that any download carries, and uses them only to serve the file. No new data type is collected: the app derives nothing from the request, and Approximate location is already declared for AdMob and Analytics. The privacy policy names GitHub under "Who receives data" and in its own "New pictures" section. The files are data (finished GMRY grids, signed), never code, which keeps the download within Play's Device and Network Abuse policy.

**Scheduled drops (T36, #77), re-checked 2026-10-19: no change.** `canvas_started` / `canvas_finished` gain `entry` (gallery | today_card, later reminder | collection | event) and, for a dated drop, `days_since_available` (a count); new `day_played` (entry; once per local day on the first gem, no ids) and `teaser_tapped` (no params). All are App interactions, already declared; no new data type, no picture or photo ids. Drops are downloaded ahead from the same GitHub folder (no new request kind) and kept hidden on the phone until their local date. The privacy policy's event list and its "New pictures" section name them.

**Mystery picture (T43, #84), re-checked 2026-10-20: no change.** `canvas_started` gains `mystery` (true, only for a mystery picture). An App interaction, already declared; no new data type, no ids. Mysteries come in the same signed catalog (no new request kind); the flag is kept in the canvas's `record` file on the phone. The privacy policy's event list names it.

**Collections (T37, #78), re-checked 2026-10-20: no change.** `canvas_started` / `canvas_finished` gain `collection_id` (the newest catalog collection listing the picture) and `entry` = collection; new `collection_completed` (collection_id); `inventory_earned` gains source = collection. A collection id is a content id: it names a set of pictures we publish, the same for every player, so it isn't a user or device identifier and isn't personal data (the one documented exception to "no ids" in `Analytics.kt`). Still App interactions, already declared; no picture, photo or user ids. Collections come in the same signed catalog from the same GitHub folder (no new request kind). Completions (`collections` preferences) are backed up like the unlocks, so a keepsake isn't earned twice after a restore. The privacy policy's event list names it.

**Weekly events (T38, #79), re-checked 2026-10-20: no change.** `canvas_started` / `canvas_finished` gain `event_id` (the newest weekly event listing the picture; its `collection_id` names the event too) and `entry` = event; new `event_reward_unlocked` (event_id, finished_in_window, a count). An event id is a content id like a collection id: the same for every player, not a user or device identifier, not personal data. Still App interactions, already declared; no picture, photo or user ids. Events come in the same signed catalog (no new request kind). The day an event's keepsake was earned joins the completions in the backed-up `collections` preferences. The VIP perk now reads "Every gallery picture unlocked": collection and event keepsakes are earned by playing, VIP too, and stay hidden until then, so the benefit stays accurate (Play subscriptions policy). The privacy policy's event list names it. **Founder:** if the Play Console subscription's benefit text says "Every picture unlocked", change it to "Every gallery picture unlocked".

**Evening reminder (T35, #76), re-checked 2026-10-20: no change.** New events `reminder_prompt_shown` (attempt 1 or 2), `reminder_prompt_result` (granted | declined_in_app | denied_system | blocked), `reminder_settings_changed` (on, minutes since midnight), `reminder_shown` (reason = drop | today | streak | almost_finished), `reminder_late` (minutes) and `reminder_opened` (reason); `canvas_started` / `canvas_finished` / `day_played` gain `entry` = reminder. All are App interactions, already declared; no ids, titles or photo data. The reminder is a local notification made on the phone, from the phone's own data (no push service, no server, no new SDK). Its evening check for new pictures is the same signed GitHub download as the gallery's, capped at 8 s. A reminder started in the background logs only after the saved consent is applied (eng D6). The privacy policy's event list and "What stays on your phone" name it.

**Streak screen and celebration (#101), re-checked 2026-10-21: no change.** New events `streak_celebration_shown` (length, milestone, saved_by) and `streak_screen_opened` (source): App interactions, already declared; no new data type, no ids. The streak's day log (`streak/history`: each day, how it counted, the first canvas id finished that day) stays on the phone and is backed up like the streak preferences: canvas ids only, never photo data. The privacy policy names both.

**Streak nudge (#102), re-checked 2026-10-22: no change.** New events `streak_nudge_shown` (broken = 1 for the repair variant, else 0; once a day) and `streak_nudge_tapped` (target = today | canvas | quick | repair | none); `streak_screen_opened` gains source = nudge and the canvas events' entry gains `streak_nudge`. App interactions, already declared; no new data type, no ids. The day the nudge last showed lives in the "streak" preferences. The evening reminder's streak reason now names the nudge's target, made on the phone as before. The privacy policy names them.

**Streak repair (#102), re-checked 2026-10-22: no change.** New events `streak_repair_offered` (length) and `streak_repair_completed` (vip = 1 for VIP's weekly repair, else 0); `rewarded_offered` / `rewarded_watched` / `rewarded_unavailable` / `grace_unlock_granted` gain reward_type = streak_repair. App interactions, already declared; no new data type, no ids. The last VIP and grace repair days live in the "streak" preferences, backed up like the streak. The privacy policy names them.

**Streak share card (#104), re-checked 2026-10-22: no change.** New event `streak_share_completed` (milestone = 7, 30, 50, 100 or 365) when an app is picked in the share sheet: App interactions, already declared; no new data type, no ids. The card is made on the phone from the streak number and bundled or remote pictures only, never a photo canvas, and leaves only through the player's own share, like the time-lapse. The privacy policy names it.

**Streak freezes (#102), re-checked 2026-10-22: no change.** New events `streak_freeze_earned` (paid = freeze | fills) and `streak_freeze_used` (count); `inventory_earned` / `inventory_spent` gain item = streak_freeze. App interactions, already declared; no new data type, no ids. Freezes live in the inventory file, backed up like the rest of the inventory. The privacy policy names them.

**More colors (#29), re-checked 2026-10-18: no change.** `canvas_started` and `photo_canvas_started` gain `colors` (fewer | standard | more, the player's choice, not the palette) and `reward_type` gains `colors`. Both are App interactions, already declared; no new data type. The privacy policy's event list names them (republish pending with the rest).

**Play Games achievements (#49), checked 2026-10-18: two new types, both optional.** Source: Google's Play Games Services data disclosure (developer.android.com/games/pgs/data-collection, latest SDK): it collects the Games account identity (gamertag, avatar) automatically, SDK analytics and diagnostics, and, depending on use, "unlocked achievements" as game service data, all over HTTPS; players delete it from their Play Games profile. The app uses achievements only (no leaderboards, friends, saved games or events), so the friends list is never requested. Declared as Personal info → User IDs and App activity → Other actions, collected, not shared (Google stores them for the player's own profile; nothing goes to a third party), Optional (only players who use Play Games), purpose App functionality. Diagnostics already covers the SDK's own diagnostics. Achievements carry event ids only: no photo, title or canvas data. No new Analytics event (founder, 2026-10-18), so App interactions is unchanged. Target audience (18+) and the ads answers don't change. The privacy policy has an "Achievements" section and names Play Games under "Who receives data". "Do you provide a way for users to request that their data is deleted?" stays **No** for our own data; Play Games data is deleted by the player in their Play Games profile, which the policy points to.

**"Shared" vs "service provider":** Crashlytics, Analytics (with sharing settings off, see Firebase Console below) and ML Kit process data for us, so they are not "shared". AdMob's disclosure says it "collects and shares" its four types, and Unity's marks every type it collects as shared, so every type either ad SDK touches is marked shared.

**Re-check this section when:** the app starts talking to a new server (GitHub for remote pictures #43 was checked, above), an SDK is added or updated (re-read its Play data disclosure page), Analytics is linked to AdMob or Google Ads, Google signals or data sharing is turned on, a user ID or account is added, or an event starts carrying new data.

## Other App content declarations

| Declaration | Answer | Why |
|---|---|---|
| **App access** | All functionality is available without any access restrictions | No login. Paid features are bought through Play; reviewers can open the paywall. |
| **Ads** | Yes, my app contains ads | AdMob rewarded + interstitial, with Unity Ads bidding through AdMob mediation (T34). Play shows the "Contains ads" label. |
| **Advertising ID** | Yes, uses advertising ID. Purposes: Advertising or marketing; Analytics; Fraud prevention, security, and compliance | The manifest declares AD_ID (merged from AdMob). Must match the Data safety purposes. |
| **Content rating** (IARC questionnaire) | Category: **Game**. Violence, fear, sexuality, language, controlled substances, crude humour, simulated gambling, real-money gambling, loot boxes / random paid items: **No**. Users interact or exchange content with other users: **No** (sharing goes out through the Android share sheet; there is no in-app communication). Shares the user's physical location with other users: **No**. Allows purchase of digital goods: **Yes**. Web browser or search engine: **No**. | Expected result: PEGI 3, ESRB Everyone, USK 0, IARC 3+, with an "In-App Purchases" notice. Bundled pictures are landscapes, animals, flowers, a couple and The Starry Night: no rated content. |
| **Target audience and content** | Age groups: **18 and over** only. Unintended appeal to children: **No** | AGENTS.md: adults only, not directed at children. Selecting 18+ keeps the app out of the Families policy and AdMob's child-directed treatment. The listing must not court children (no cartoon mascots, no "kids" wording). |
| **News app** | No | |
| **Government apps** | No | |
| **Financial features** | My app doesn't provide any financial features | Selling VIP isn't a financial feature. |
| **Health apps** | My app does not have any health features | |
| **Photo and video permissions** | Nothing to declare | The Photo Picker needs no media permission; the manifest has none. |
| **Notifications and alarms** (T35) | Nothing to declare | `POST_NOTIFICATIONS` (asked for on Android 13+ after the player says yes to the evening reminder) and `RECEIVE_BOOT_COMPLETED` (re-arms the reminder after a reboot) need no Play declaration. No `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`: the reminder uses an allow-while-idle alarm, best effort, so the exact-alarm declaration doesn't apply. |
| **Foreground service permissions** | Nothing to declare | The merged manifest has `FOREGROUND_SERVICE` from WorkManager (AdMob) but no `FOREGROUND_SERVICE_*` type permission. Declare only if Play asks. |
| **App category** (Store settings) | Game → **Puzzle** | Diamond painting is paint-by-numbers. |
| **Contact details** | Email andrei.serban@brewingbytes.com; website optional | Must be monitored. |

## Store listing check

Before submitting each listing change, check:

- [ ] Title "Gemmory: Photo Diamond Art" (26 of 30 characters). No "free", "best", "#1", ranking or price words in the title or icon.
- [ ] Short description (≤80) and full description: every claim is true of the current build. "Your photos never leave your phone" is true (ML Kit sends diagnostics only, never images). Don't promise features that aren't shipped (more colours). The streak gift wheel is free (never bought), so it needs no odds disclosure for paid random items, but its odds are shown in the app.
- [ ] The full description mentions ads and the VIP subscription honestly, with no fake urgency.
- [ ] Screenshots and the feature graphic show the real app (StoreScreenshots + the app's own time-lapse output). No device frames of other brands, no awards or ratings we don't have.
- [ ] Nothing aimed at children: no cartoon characters, no "for kids".
- [ ] Translations: none until the app is localized (close "Import translations with AI").
- [ ] Contact email and privacy policy URL filled in.

## Firebase Console settings (decided 2026-10-15)

Project `gemmory-3e776`.

1. **Analytics data retention: 2 months.** Firebase Console → Project settings → Integrations → Google Analytics → open the property in Google Analytics → Admin → Data collection and modification → Data retention → Event data retention **2 months**, "Reset user data on new activity" **off** → Save. Standard reports keep their aggregates; only user-level Explorations older than 2 months go.
2. **Google signals: off.** GA Admin → Data collection and modification → Data collection → Google signals data collection **off**.
3. **Data sharing: Technical support only.** GA Admin → Account settings → Data sharing settings: Google products & services **off**, Modeling contributions & business insights **off**, Technical support **on**, Recommendations for your business **off**. This keeps Analytics a service provider (Data safety: Analytics rows not shared).
4. **No ads links.** GA Admin → Product links: no AdMob, Google Ads or other ads links. Linking makes Analytics data usable for ads: re-do Data safety first.
5. **Data processing terms.** Firebase Console → Project settings → Privacy (or General → "Data privacy") → accept the Firebase **Data Processing and Security Terms**; GA Admin → Account settings → accept the **Data Processing Amendment**. Needed for GDPR processor terms.
6. **Crashlytics:** no settings to change. Crash data is kept 90 days by Google.

## Restrict the Android API key (Google Cloud Console)

`google-services.json` is committed (public repo), so the key must only work for our app.

1. Collect the SHA-1 fingerprints:
   - **App signing key:** Play Console → Test and release → Setup → **App signing** → "App signing key certificate" → SHA-1.
   - **Upload key:** same page, "Upload key certificate" → SHA-1. It should be `97:F7:D0:BA:23:7F:4C:58:03:B3:24:DA:7A:87:AF:B0:61:C7:0B:45` (read from the 0.2.4 AAB with `keytool -printcert -jarfile`).
   - **Debug key** (only needed for debug builds with `-Pgemmory.firebaseInDebug=true`): `2C:5B:E7:17:2E:EA:94:6B:38:FB:17:0B:81:28:3A:6E:90:7C:5D:39` (this Mac's `~/.android/debug.keystore`).
2. Google Cloud Console → select project **gemmory-3e776** → APIs & Services → **Credentials** → API keys → open **"Android key (auto created by Firebase)"** (it starts with `AIzaSyBB`, the `current_key` in `google-services.json`).
3. **Application restrictions** → **Android apps** → Add: package `com.brewingbytes.gemmory` + app-signing SHA-1; Add again: same package + upload SHA-1; Add again: same package + debug SHA-1 (optional).
4. **API restrictions:** leave the list Firebase set (it is already limited to Firebase APIs). Don't remove entries: Crashlytics and Firebase Installations stop working silently.
5. Save. Changes take up to 5 minutes.
6. **Check it worked** (same day): on `gemmory_play`, install the current build from Play, accept the consent form and open the app twice. Within a few minutes the version must show in Firebase Console → Crashlytics (crash-free statistics count its users) and in Analytics → Realtime. If not, `adb logcat | grep -i -E "firebase|installations|403"` shows `API_KEY_ANDROID_APP_BLOCKED` or `PERMISSION_DENIED`: re-check the SHA-1s (the Play build is signed with the app-signing key, not the upload key). To roll back, set Application restrictions to None.

## Test Lab CI credentials (Google Cloud Console)

The encoder smoke job (`.github/workflows/testlab.yml`, #11; README → "Encoder smoke on Firebase Test Lab") logs in to project `gemmory-3e776` with Workload Identity Federation: GitHub's short-lived OIDC token is exchanged for the service account below, and only workflows in `BrewingBytes/Gemmory` can do it. No key is created, so there is nothing to leak or rotate. Test Lab gets the debug APKs and the tests' synthetic canvases only, never a player's photo, so the Data safety answers don't change.

1. **APIs.** Google Cloud Console → project **gemmory-3e776** → APIs & Services → Library → search and **Enable** each: **Cloud Testing API**, **Cloud Tool Results API**, **IAM Service Account Credentials API**, **Security Token Service API**.
2. **Service account.** IAM & Admin → Service Accounts → **Create service account**: name `test-lab-ci` (ID `test-lab-ci`) → Create and continue → role **Basic → Editor** → Done. Don't open its Keys tab. Editor is the role Firebase's Test Lab CI guide names; the job also reads videos from Test Lab's results bucket with it.
3. **Pool.** IAM & Admin → **Workload Identity Federation** → Create pool: name `github`, pool ID `github` → Continue.
4. **Provider** (same wizard): provider **OpenID Connect (OIDC)**, name `github-actions`, provider ID `github-actions`, issuer URL `https://token.actions.githubusercontent.com`, audiences **Default audience** → Continue → attribute mapping: `google.subject` = `assertion.sub`, add `attribute.repository` = `assertion.repository` → **Add condition**: `assertion.repository == 'BrewingBytes/Gemmory'` → Save.
5. **Let the repo use the account.** Open the `github` pool → **Grant access** → "Grant access using service account impersonation" → service account `test-lab-ci` → principals: "Only identities matching the filter", attribute `repository`, value `BrewingBytes/Gemmory` → Save. Dismiss the "Configure your application" download: the job needs no config file.
6. **Provider name.** Pool `github` → provider `github-actions`: copy the resource name `projects/<project number>/locations/global/workloadIdentityPools/github/providers/github-actions` (the "Default audience" URL minus `https://iam.googleapis.com/`).
7. **GitHub secrets.** github.com/BrewingBytes/Gemmory → Settings → Secrets and variables → Actions → **New repository secret**, twice:
   - `GCP_WORKLOAD_IDENTITY_PROVIDER` = the resource name from step 6.
   - `GCP_TEST_LAB_SERVICE_ACCOUNT` = `test-lab-ci@gemmory-3e776.iam.gserviceaccount.com`.
8. **Test Lab once.** Firebase Console → project → Run (or DevOps & Engage) → **Test Lab**: open it once so the project is set up for it. Stay on Spark: its 5 physical-device runs a day cost nothing; Blaze would bill past 30 free minutes a day ($5 per device-hour).
9. **Check it worked:** GitHub → Actions → **Test Lab** → Run workflow → tick "Only print the physical-device catalog" → Run. It must go green with the device table in the run summary. `Permission 'iam.serviceAccounts.getAccessToken' denied` means step 5 is missing; `unauthorized_client` means the condition in step 4 doesn't match the repo name.

To revoke: delete the provider (or the `test-lab-ci` account); the secrets are then useless.

## Production gate (D18)

Production opens only when both checks pass. Everything else from the closed test (percentages, retention, share rate) is informational: about 6-10 consented people each, too few to decide anything.

Check on the build that will be promoted (filter by its version), over the last 7 days of the closed test:

1. **Crash-free users ≥ 99%.** Firebase Console → Crashlytics → filter the version → "Crash-free statistics" → crash-free users. With ~20 testers, one crashing tester is ~95%, so in practice the gate means no tester crashed on that build. Cross-check Play Console → Android vitals → user-perceived crash rate (it must not be flagged).
2. **No unrecovered save-load failures.** Firebase Console → Crashlytics → filter the version → Event type **Non-fatals** → search `CanvasStorageException`, then `RedactedException` (a report whose message named a file path is sent as a `RedactedException` whose message starts with `com.brewingbytes.gemmory.storage.CanvasStorageException:`; an unreadable save file does this). Any of them whose message contains **`: damaged,`** blocks production (the canvas couldn't be loaded and there was no good last save). These are informational, not blocking: `recovered from last good save` (the player lost at most 5 s), `write failed` (disk errors other than full), `won't overwrite a newer save`, and FileStateStore `IOException`s (settings/ledger, not canvas progress). A spike in any of them still needs a look before promoting.

Record each check (date, version, both numbers) in the PR or release notes that promotes the build.

## In-app review (T39)

The app asks Play for its in-app review sheet only while `features.review` is on in the signed remote catalog. The same AAB goes from the closed track to production, so a build flag can't tell the tracks apart, and reviews from a closed track are private anyway. The switch ships off.

**When:** once the production release is live (the D18 gate passed, beside #74). **How:** `./gradlew :shared:setRemoteFeature -Pfeature=review -Pon=true`, then a PR with `pictures/v1/` (README → Publishing remote pictures). Phones pick it up at their next catalog check, at most a day later. Turning it off is the same command with `-Pon=false`.

**What it does:** at most 3 attempts per install, at 3, 10 and 25 finished pictures, at least 14 days apart, never on the day of the player's first finish, only on the gallery after leaving a fresh finish's completion screen, and only when that return has no reminder ask and isn't a reward return (design system, One interruption per return). Play draws and rate-limits the sheet and never says whether it showed, so every attempt counts. Analytics: `review_requested` (attempt). The counter (attempts made, the day of the last, the player's first-finish day) is in the `review` preferences, which are backed up. Nothing about the player or their photos is sent: the sheet is Play's own.

## Real ads (T34)

Design: [docs/designs/ad-mediation.md](designs/ad-mediation.md). AdMob serves rewarded and interstitial ads, and Unity Ads bids in the same auction through AdMob mediation (adapter `com.google.ads.mediation:unity`). No app code picks between the two.

**IDs.** The three production AdMob IDs are committed in `gradle.properties`. Release builds use them (the app ID through `manifestPlaceholders`, the units through `BuildConfig`), and `:shared:validateReleaseAdIds` fails the release build on a missing, blank or test-publisher ID. The Unity Game ID and placement IDs are entered only in the AdMob console (each unit → bidding sources → Unity Ads).

**Consent.**
- GDPR: Unity Ads is a custom ad partner in the AdMob Privacy & messaging GDPR message. The adapter forwards UMP's `IABTCF_AddtlConsent` to Unity, and the app never writes `gdpr.consent`.
- US states: a published US-states message lists Unity Ads. The app writes Unity's `privacy.consent` from the GPP string UMP stores (`ads/UsPrivacy.kt`): false on any sale, sharing or targeted-ads opt-out, true with no US section, false and a non-fatal when the string doesn't decode. The write happens after each consent update (every launch, the first-ad form, Settings → Privacy choices) and always before `MobileAds.initialize`.

**Closed test (no friendly clicks).**
- The founder's phones are AdMob test devices (AdMob → Settings → Test devices).
- Unity test mode is **on** for the whole closed test (Unity dashboard → Monetization → Testing), because AdMob's test devices don't cover mediated Unity demand.
- Testers get a written "please don't tap ads" note.
- Check on the founder's phone: AdMob fills both units (test ads).
- Unity is already set up (Game ID `800406589`, placements `BP_Rewarded_Android` and `Interstitial_Android`, test mode forced on, not child-directed). Its two AdMob mediation groups wait for the production launch: while the app is in "needs review" (no public store listing to link), AdMob's mediation group picker lists no ad units (seen 2026-10-21).

**app-ads.txt.** `app-ads.txt` sits at the root of `https://brewingbytes.github.io/` (repo `BrewingBytes/brewingbytes.github.io`), with the AdMob line plus Unity's lines, and that site is the Play developer website. AdMob verifies it only after the app is linked to its public listing (production launch).

**Rollback.** If Crashlytics shows a crash or ANR cluster with Unity SDK frames, or Play vitals regress after the release, disable Unity Ads as a bidding source on both AdMob units. That needs no release, and AdMob demand keeps serving. The SDK removal then ships in the next version.

**Production launch** (tracked in #74's checklist):
- Link the AdMob app to the public listing and pass the readiness review.
- Create the two mediation groups (Rewarded and Interstitial, Android), each with Unity Ads as a bidding source mapped to Game ID `800406589` and its placement. Then check in Ad Inspector that Unity Ads (Bidding) fills both units.
- app-ads.txt shows "Verified".
- Turn Unity test mode off.
- Non-zero AdMob earnings within 7 days.
