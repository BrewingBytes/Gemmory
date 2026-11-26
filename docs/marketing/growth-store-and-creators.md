# Growth without ads: the store page (#1) and creators (#4)

Written 2026-11-26. Numbers are from Play Console → Grow → overview, the last 28 days.

## Where we are

| Metric (28 days) | Value |
|---|---|
| Times Play showed Gemmory (impressions) | 31 |
| Installs | 23 |
| First opens | 23 |
| Monthly active devices | 23 |
| Devices still playing after 7 days | 5 |

Play barely shows the app yet: 31 impressions, and the 23 installs came mostly from people who already knew it.
That changes both plans:

- **Store listing experiments can't finish at this volume.** An A/B test needs a few hundred store visitors per
  variant. Start them once the store page gets about 1,000 visitors a month (Grow → store listing report).
- **Ranking comes from installs per day.** Play moves an app up search and the charts when installs come in steadily
  and players keep it. So the fastest way to "more impressions" is outside traffic, which is what #4 brings.
- **Day-7 retention (5 of 23) is worth watching.** At these numbers it's noise, but if it holds at about 20% once real
  traffic arrives, that's the next thing to work on, before spending on anything.

## #1 Store page

### a. Get found for the genre's biggest search terms (do now)

"Color by number" and "paint by number" are much bigger searches than "diamond painting". Today they appear only
once, in the last section of the long description. The title is full at 30 characters, so the short description
is the place:

| | Short description (80 max) |
|---|---|
| Now | A diamond painting game for your own photos. Calm gem art, one tap at a time. |
| **Proposed** | Diamond painting & color by number from your own photos. Calm gem art, one tap. (79) |
| Alternative | Paint by number with gems: turn your own photos into calm diamond painting art. (79) |

Also add one line near the top of the long description: *"Like color by number and paint by number, but with
sparkling gems."*

Each language uses its own term for this, so translate the idea, not the words:
- de: "Malen nach Zahlen"
- fr: "coloriage par numéro"
- es: "colorear por números"
- pt-BR: "pintar por números"
- it: "colorare con i numeri"
- pl: "malowanie po numerach"
- ro: "colorat după numere"
- ja: "数字で塗り絵"
- hu: "számfestő"
- tr: "sayılarla boyama"
- bg: "оцветяване по номера"

The listing text lives in `tools/store/listing/<lang>/short.txt` and `full.txt`, and `ListingTest` checks it. So
this is a small content PR plus a re-upload in Play Console (Store presence → Main store listing).

### b. Add a preview video (do now)

A listing video plays at the top of the store page and makes it look alive. Use ad 4 (Perfect fill, the mandala,
no AI shots, so no AI label needed) or ad 1 (your pet in gems):
1. Upload it to the Gemmory YouTube channel as public or unlisted, with ads off and no age restriction.
2. Paste the link in Main store listing → Video.

### c. Watch the search terms (from mid-December)

Grow → overview → Acquisition → the "Search terms" breakdown fills in once there are a few dozen search installs. Each
month, move any term that brings installs into the short description of that language.

### d. Experiments (later, when ~1,000 store visitors a month)

Test in this order, one at a time, 50/50, for at least 7 days:
1. **Icon:** the current one vs a close-up of a gem-filled pet face.
2. **First screenshot:** "Your photo → gem painting" before/after vs the gallery.
3. **Short description:** whichever of the two above lost.

## #4 Creators

### Who

The goal is **micro-creators with 5k–50k followers** whose audience already does diamond painting, cozy crafts or
cozy games. Big channels cost money and their audience doesn't pick out one app. Small ones answer DMs, and their
viewers trust them.

Where to find them:
- [Feedspot: 25 diamond painting YouTubers](https://videos.feedspot.com/diamond_painting_youtube_channels/): skip the
  big ones (DiamondXpres, ~300k) and start with the 5k–50k tier, such as Michelle's Diamond Painting (~20k) and Diamond
  Paint with Caitlin (~7k). Check the counts on YouTube; the list may be out of date.
- [SponsorRadar: Diamond Art Club's sponsored creators](https://sponsorradar.com/brands/diamond-art-club): these
  creators already take craft sponsorships, but most are big (90k–440k). Use them as a guide to the style, not as
  targets.
- TikTok, Instagram and Lemon8: search #diamondpainting, #diamondpaintingasmr, #diamondart and #cozygames. Pick
  accounts that post at least weekly and whose comments are real conversations.
- Romanian creators: you can write to them in your own language, and the RO market is pooled anyway.

Make a list of 20. Expect about 1 in 5 to say yes.

### What we offer

- **Free VIP.** Play Console → Monetize → Promo codes. Know the catch: for a subscription, a promo code is a
  **free trial of up to 90 days**, and Play asks for a payment method and auto-renews afterwards. Say so up front so
  nobody is surprised.
- **Simpler option:** a promo code for the one-time **Remove ads** purchase. It's free forever with no card needed;
  pair it with "I'll extend VIP for you any time".
- **Their own photo as the hook:** ask them to turn their pet, or their latest physical diamond painting, into a
  canvas and post the time-lapse the app makes. That's the "your own photos" angle, and it shows the product better
  than anything we could film.
- **No money at first.** If someone's post brings installs, a small paid follow-up ($50–150) is worth it.

### Tracking

Each creator gets their own link, following the rule in `docs/channel-links.md`:
- `utm_source` is the platform.
- `utm_campaign` is `creator-<handle>`.
- Add a row to that file before sending the link.

Example: `https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory&referrer=utm_source%3Dtiktok%26utm_campaign%3Dcreator-diamondbycaitlin`

Rules for creators' posts:
- They must mark the post as sponsored (#ad, or the platform's paid-partnership toggle) even when they're only given
  a free code. That's FTC and EU rules.
- If they use any of our AI-made shots, the AI label applies (EU AI Act Art. 50). Their own footage needs none.

### First message (DM or email, edit per person)

> Hi {name}! I love your {specific video/post}, especially {one real detail}.
>
> I'm Andrei, and I make Gemmory, a small diamond painting game for Android where you turn your own photos into a
> gem canvas and get a sparkle time-lapse at the end. I'm a solo developer, no big studio.
>
> Would you like to try it? I'd send you a code to unlock everything. No obligation to post. If you like it, I'd love
> to see {their pet / their latest kit} turned into gems.
>
> Here's what it looks like: {link to the ad 1 video}

Then follow up once after 5 days, and never again.

## Next steps

1. Pick the short description, and I'll make the listing PR with all 13 languages.
2. Upload the preview video to YouTube and paste it into the listing.
3. Choose the offer (VIP trial or Remove ads code). I'll draft the promo-code setup; you create the codes.
4. Build the 20-creator list. I can do the first pass from the sources above, and you check who feels right.
