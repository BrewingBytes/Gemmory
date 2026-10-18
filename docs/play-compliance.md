# Play compliance and the production gate

What Gemmory declares in Play Console, Firebase and Google Cloud, why, and how the founder decides when production can open (#5 T5, design doc D10, D17, D18). The founder enters every Console answer by hand. Keep this file in step with the app: when an SDK, an event, a permission or a backup rule changes, update the matching row here, then [privacy-policy.md](privacy-policy.md), then the Console.

Facts the answers rest on (checked 2026-10-15 on 0.2.4 (6), Firebase BOM 34.19.0, Google Mobile Ads 25.5.0, UMP 4.0.0, ML Kit face detection 16.1.7, Play Billing 9.1.0, targetSdk 37):

- Photos are processed only on the phone. Nothing in the app uploads them (AGENTS.md).
- No accounts, no login, no user IDs anywhere (`setUserId` is never called).
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
| Does your app collect or share any of the required user data types? | **Yes** | Crashlytics, Analytics, AdMob and ML Kit send data off the phone. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | Firebase and ML Kit use HTTPS, AdMob uses TLS (Google's SDK disclosures). |
| Account creation | **My app does not allow users to create an account** | No accounts or login. |
| Do you provide a way for users to request that their data is deleted? | **No** | Nothing we hold links data to a person. The policy explains uninstall/clear storage resets the IDs and the retention periods (founder call, #5). |
| Independent security review | Leave unchecked | Not done. |

### Data types

Declare only these. Everything else is **not collected**.

| Category → type | Collected | Shared | Processed ephemerally | Required or optional | Purposes (collected) | Purposes (shared) | Which SDK, and why |
|---|---|---|---|---|---|---|---|
| Location → **Approximate location** | Yes | Yes | No | Required | Advertising or marketing; Analytics; Fraud prevention, security, and compliance | Advertising or marketing; Fraud prevention, security, and compliance | AdMob collects and shares the IP address, "which may be used to estimate the general location". Analytics derives a coarse region from the masked IP. AdMob runs for everyone, so it can't be turned off. |
| Financial info → **Purchase history** | Yes | No | No | Required | Analytics | — | Analytics logs Play purchase events (product, price, currency) automatically, plus our `vip_purchased` / `remove_ads_purchased`. Required because outside the EEA/UK/CH there is no opt-out. Payment details are Google Play's, not ours: not declared. |
| App activity → **App interactions** | Yes | Yes | No | Required | Analytics; Advertising or marketing; Fraud prevention, security, and compliance | Advertising or marketing; Analytics; Fraud prevention, security, and compliance | Analytics events (canvas started/finished, with a photo canvas's color choice, share, rewarded, interstitial, time-lapse, daily streak length, today's picture started, fill tool used, inventory items earned and spent). AdMob collects and shares "app launch, taps, and video views". |
| App info and performance → **Crash logs** | Yes | No | No | Required | Analytics | — | Crashlytics stack traces, always on (D17). Play's "Analytics" purpose includes diagnosing and fixing crashes. |
| App info and performance → **Diagnostics** | Yes | Yes | No | Required | Analytics; Advertising or marketing; Fraud prevention, security, and compliance | Advertising or marketing; Analytics; Fraud prevention, security, and compliance | Crashlytics non-fatals and device state at crash time; ML Kit performance metrics and error codes; AdMob "performance of your app and the SDK" (shared). |
| Device or other IDs → **Device or other IDs** | Yes | Yes | No | Required | Analytics; Advertising or marketing; Fraud prevention, security, and compliance | Advertising or marketing; Analytics; Fraud prevention, security, and compliance | AdMob: advertising ID and app set ID (shared). Analytics: app instance ID (+ advertising ID when ad storage is granted). Crashlytics installation UUID, Firebase installation ID, ML Kit per-installation ID. |

**Not declared, and why:**

- **Photos and videos:** the photo is read and converted on the phone and never transmitted. Play counts data as collected only when it leaves the device. Sharing the time-lapse is a user-initiated transfer to the app the player picks, which Play excludes from "shared".
- **Personal info (name, email, user IDs, address, phone):** none. No accounts, `setUserId` never called.
- **Financial info → payment info / credit card:** Google Play handles payment; the app never receives it.
- **Precise location, messages, contacts, calendar, audio, files, health, web browsing, in-app search, other user-generated content, other actions:** not collected.
- **App info and performance → Other app performance data:** nothing beyond crash logs and diagnostics.

**Remote pictures (#43), re-checked 2026-10-17: no change.** Once a day the app GETs a signed catalog and grid files from `raw.githubusercontent.com` (GitHub, Microsoft). The request carries no identifier, cookie, account or user content (plain `HttpURLConnection`, the platform's default user agent), and the app sends nothing in a body. GitHub sees the IP address and user agent that any download carries, and uses them only to serve the file. No new data type is collected: the app derives nothing from the request, and Approximate location is already declared for AdMob and Analytics. The privacy policy names GitHub under "Who receives data" and in its own "New pictures" section. The files are data (finished GMRY grids, signed), never code, which keeps the download within Play's Device and Network Abuse policy.

**More colors (#29), re-checked 2026-10-18: no change.** `canvas_started` and `photo_canvas_started` gain `colors` (fewer | standard | more, the player's choice, not the palette) and `reward_type` gains `colors`. Both are App interactions, already declared; no new data type. The privacy policy's event list names them (republish pending with the rest).

**"Shared" vs "service provider":** Crashlytics, Analytics (with sharing settings off, see Firebase Console below) and ML Kit process data for us, so they are not "shared". AdMob's disclosure says it "collects and shares" its four types, so every type AdMob touches is marked shared.

**Re-check this section when:** the app starts talking to a new server (GitHub for remote pictures #43 was checked, above), an SDK is added or updated (re-read its Play data disclosure page), Analytics is linked to AdMob or Google Ads, Google signals or data sharing is turned on, a user ID or account is added, or an event starts carrying new data.

## Other App content declarations

| Declaration | Answer | Why |
|---|---|---|
| **App access** | All functionality is available without any access restrictions | No login. Paid features are bought through Play; reviewers can open the paywall. |
| **Ads** | Yes, my app contains ads | AdMob rewarded + interstitial. Play shows the "Contains ads" label. |
| **Advertising ID** | Yes, uses advertising ID. Purposes: Advertising or marketing; Analytics; Fraud prevention, security, and compliance | The manifest declares AD_ID (merged from AdMob). Must match the Data safety purposes. |
| **Content rating** (IARC questionnaire) | Category: **Game**. Violence, fear, sexuality, language, controlled substances, crude humour, simulated gambling, real-money gambling, loot boxes / random paid items: **No**. Users interact or exchange content with other users: **No** (sharing goes out through the Android share sheet; there is no in-app communication). Shares the user's physical location with other users: **No**. Allows purchase of digital goods: **Yes**. Web browser or search engine: **No**. | Expected result: PEGI 3, ESRB Everyone, USK 0, IARC 3+, with an "In-App Purchases" notice. Bundled pictures are landscapes, animals, flowers, a couple and The Starry Night: no rated content. |
| **Target audience and content** | Age groups: **18 and over** only. Unintended appeal to children: **No** | AGENTS.md: adults only, not directed at children. Selecting 18+ keeps the app out of the Families policy and AdMob's child-directed treatment. The listing must not court children (no cartoon mascots, no "kids" wording). |
| **News app** | No | |
| **Government apps** | No | |
| **Financial features** | My app doesn't provide any financial features | Selling VIP isn't a financial feature. |
| **Health apps** | My app does not have any health features | |
| **Photo and video permissions** | Nothing to declare | The Photo Picker needs no media permission; the manifest has none. |
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
