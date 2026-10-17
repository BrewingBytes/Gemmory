# Gemmory privacy policy

Effective date: 17 October 2026

Gemmory: Photo Diamond Art ("Gemmory", "the app") is made by BrewingBytes ("we", "us"). This policy explains what data the app handles, why, who receives it and how long it is kept. Contact: andrei.serban@brewingbytes.com.

## The short version

- **Your photos never leave your phone.** We have no servers for your pictures, and the app never uploads them.
- There are no accounts. We don't ask for your name, email or phone number, and we never attach a user ID to anything the app sends.
- The app sends Google three kinds of data: **crash reports** (always, to keep the app working), **usage statistics** (only where you allow them) and the **data Google AdMob uses to show ads**.
- Purchases go through Google Play. We never see your payment details.
- Once a day the app downloads the list of new pictures from **GitHub** (Microsoft). That request carries nothing about you.

## Your photos

When you make a photo canvas, you pick a photo with Android's photo picker. The app crops it, turns it into a gem grid and stores the result in the app's private storage on your phone. All of this happens on the phone.

- Photos, grids and your progress on them are **not** included in Android cloud backups or device-to-device transfers.
- Face detection, which suggests a canvas size when a photo has faces in it, uses Google ML Kit's on-device model. **The photo is not sent anywhere.** ML Kit sends Google diagnostic and usage information about the face detection feature itself, never the image: device model and OS version, app package and version, a per-installation identifier, timing, image size and format, and error codes. Google uses it to measure and improve ML Kit and does not pass it to third parties.
- You can delete a photo canvas from the gallery, delete all of them in Settings → Delete all photo canvases, or uninstall the app. Each of these removes the data from your phone.

## Sharing

When you tap Share, the app makes the time-lapse video (or a before/after picture) on your phone and hands it to the Android share sheet. It goes only where you choose to send it. The share text includes a Google Play link to the app. The app counts that a share happened (see "Usage statistics"), never what was shared or to whom.

## New pictures (GitHub)

We publish new built-in pictures without an app update. About once a day, while you're in the gallery, the app downloads a signed list of pictures and any new picture files from GitHub (`raw.githubusercontent.com`), which is run by Microsoft. Nothing is downloaded while you play.

- **What GitHub receives:** what every web request carries: your phone's IP address and a user agent naming the Android version and device model. The app adds no identifier, cookie, account or anything about you or your photos, and sends nothing to GitHub.
- **What it's used for:** only to deliver the files. GitHub's handling of requests is covered by the GitHub General Privacy Statement.
- The pictures are images with no code. The app checks every file's signature and checksum and refuses anything that doesn't match.

## Crash reports (Firebase Crashlytics)

The app uses Google's Firebase Crashlytics to find and fix crashes and failures, for everyone who uses the app. We do this on the basis of our **legitimate interest** in keeping the app working and your saved progress safe (GDPR Art. 6(1)(f)). Crash reports are kept to the minimum needed for that:

- **What is sent:** when the app crashes, or when something important fails without a crash (making a canvas from a photo, face detection, making a time-lapse, a purchase step, or reading or saving your progress), a report with the error and the code location where it happened, the app version, the device model, OS version, free memory and disk space, whether the app was in the foreground, and a few technical details about the failure (for example which video encoder failed and how long a time-lapse took to make).
- **If usage statistics are on** (next section), the last few of those app events before a crash are attached to its report.
- **The identifiers:** a random Crashlytics installation ID, created when the app is installed, used only to count how many installs a problem affects, and a random Firebase installation ID that Firebase services use to tell installs apart. Neither is linked to you, your Google account or your advertising ID, and neither is included in backups, so a new phone gets new ones.
- **What is never sent:** photos, pictures or grids, your progress, names, email addresses or any user ID. Before a report leaves the phone, the app removes file paths and photo addresses (`content://` links) from error messages.
- **How long:** Google keeps crash reports for 90 days, then deletes them.

Because crash reports carry nothing that identifies you, there is no in-app switch for them. If you don't want them sent, uninstalling the app stops them, and you can write to us to object.

## Usage statistics (Google Analytics for Firebase)

With your permission, the app uses Google Analytics for Firebase to learn how the app is used, for example whether people finish pictures and whether sharing works, so we can decide what to improve.

- **When it is on:** in the European Economic Area, the UK and Switzerland, only if you agree in the consent form shown when the app first starts (purpose "Store and/or access information on a device"). Until then, and if you say no, the app sends nothing to Google Analytics. Elsewhere it is on by default. You can change your choice at any time in Settings → Privacy choices (where it is shown).
- **The events the app records:** a picture started or finished (bundled picture or photo, and its size), a photo canvas started, Share tapped and an app picked in the share sheet, a time-lapse made (its size, whether it worked and how long it took), the first picture finished on a new day (how many days in a row you've finished one), today's picture started (its size, and whether you're VIP), a fill tool used for the first time on a picture (which tool, the picture's size, and whether you're VIP), an inventory item earned (fills, a photo canvas or an unlock token, how many, and whether it came from a video or the streak gift) or spent, a rewarded video offered, watched or unavailable, a free unlock granted when no video was available, a full-screen ad shown, and VIP or Remove ads bought. Events carry kinds, sizes and counts only: no picture names, no photo data, no IDs of yours.
- **What Google Analytics adds by itself:** a random app instance ID for this installation (not linked to you, and not included in backups), app opens, sessions and screen views, your device model, OS, app version, language and country, an approximate region derived from your IP address (the IP address itself is not stored), and Google Play purchase events with the product, price and currency.
- **Advertising ID:** Google Analytics reads your device's advertising ID only when ad storage is allowed: in the EEA, the UK and Switzerland only if you agreed to it in the consent form; elsewhere by default. We have **not** turned on Google signals and have not linked Analytics to any advertising account, so Analytics data is not used to personalise ads.
- **How long:** user-level data is kept for 2 months. Reports made from it are aggregated counts that don't identify anyone.
- **Sharing:** we have turned off sharing Analytics data with Google for its own products and services. Google processes it for us.

## Ads (Google AdMob)

Gemmory shows ads from Google AdMob: a short video you choose to watch (to unlock a picture or get another photo canvas) and, occasionally, a full-screen ad after you finish a picture. Ads are never shown while you are placing gems. VIP and Remove ads turn the full-screen ads off.

To show and measure ads and to prevent ad fraud, Google may collect:

- your device's advertising ID, app set ID and other device identifiers;
- your IP address, which may be used to estimate your general location;
- how you interact with ads (for example taps and video views) and app launches;
- diagnostic information about the app and the ads software.

In the EEA, the UK and Switzerland, the app asks for your consent through Google's consent form before requesting ads, and Google shows non-personalised or limited ads when you don't consent. You can change your choice at any time in Settings → Privacy choices. You can also reset or delete your advertising ID in your Android settings.

Google uses this data as an independent controller, as described in Google's privacy policy: https://policies.google.com/privacy and in "How Google uses information from sites or apps that use our services": https://policies.google.com/technologies/partner-sites

## Purchases (Google Play)

VIP (weekly or yearly) and "Remove ads" are sold through Google Play Billing. Google Play handles payment, and we never receive your card or payment details, your name or your email. The app asks Google Play which purchases your account holds, and keeps that answer on your phone so VIP works offline for a while. That record is not included in backups or device transfers. You can manage or cancel VIP in Google Play. Google Play's handling of your purchase is covered by Google's privacy policy.

## What stays on your phone

We don't run servers that store your data. What the app keeps (your canvases, progress, settings, the daily photo canvas counter, ad limits and your purchase status) stays on your phone.

If Android backup is on, Android may include some of it in your own Google backup and restore it on a new phone: your settings, your progress on the built-in pictures (including the ones downloaded from GitHub), your inventory (fills, kept photo canvases and unlock tokens), and your consent choice. **Never included:** photo canvases and their progress, the daily photo canvas counter, ad limits, your purchase status, the downloaded list of new pictures (it's fetched again), and the Crashlytics and Analytics identifiers.

## Who receives data

Google, as described above: Firebase Crashlytics and Google Analytics for Firebase process data for us; AdMob and ML Kit as described in their sections; Google Play for purchases. And GitHub (Microsoft), which receives your IP address and user agent when the app downloads new pictures (see "New pictures"). We don't sell your data, and we don't give it to anyone else. Google and GitHub may process data outside your country, including in the United States, with the safeguards described in their privacy policies and data processing terms. All of it is sent encrypted (HTTPS/TLS).

## Your choices and rights

- **Consent:** Settings → Privacy choices (in the EEA, the UK and Switzerland) changes your ads and Analytics choice at any time. Withdrawing consent doesn't affect what was collected before.
- **Advertising ID:** reset or delete it in your Android settings.
- **Deleting data on your phone:** delete a canvas, use Settings → Delete all photo canvases, clear the app's storage in Android settings, or uninstall the app.
- **Data held by Google:** because we never attach anything that identifies you, we can't look up, copy or delete the crash reports or statistics of one person. Uninstalling the app or clearing its storage resets the identifiers, so new data can't be linked to old data, and the old data is deleted after the retention periods above (90 days for crash reports, 2 months for user-level statistics).
- Depending on where you live, you may have rights to access, correct or delete personal data, to object to processing based on legitimate interest, and to complain to your data protection authority. Write to us and we will answer within one month.

## Children

Gemmory is meant for adults (18+) and is not directed at children. We don't knowingly collect data from children. If you believe a child is using the app, contact us.

## Changes

If this policy changes, we will update this page and its effective date. The app will be updated to describe any new kind of data use before it starts.

## Contact

BrewingBytes, andrei.serban@brewingbytes.com
