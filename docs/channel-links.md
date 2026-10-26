# Channel links

One tagged Play Store link per place Gemmory is posted, so every install from it lands under a named channel. Play hands a link's `referrer` to Firebase (the install's **first user source** and **campaign**) and to Play Console's store-listing acquisition report. How to read the numbers: [reading-first-30-days.md](reading-first-30-days.md) → Round 4, channel readout. Built with T55 (#156); plan `docs/designs/gemmory-round4.md` → T55.

## The links

| Channel | `utm_source` | `utm_campaign` | Link |
|---|---|---|---|
| TikTok bio | `tiktok` | `bio` | `https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory&referrer=utm_source%3Dtiktok%26utm_campaign%3Dbio` |
| Instagram bio | `instagram` | `bio` | `https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory&referrer=utm_source%3Dinstagram%26utm_campaign%3Dbio` |
| YouTube channel (About and Shorts description) | `youtube` | `bio` | `https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory&referrer=utm_source%3Dyoutube%26utm_campaign%3Dbio` |

Add a row here before you post somewhere new, with the rule below. A link is never reused for another community: two places on one link read as one channel.

## Making a new link

1. **`utm_source` is the platform**, lowercase: `reddit`, `facebook`, `tiktok`, `instagram`, `youtube`. A new platform gets its own lowercase name.
2. **`utm_campaign` is the community, or one post in it:**
   - reddit: `r-<subreddit>` (`r-diamondpainting`)
   - Facebook group: `fb-<group-name>`, the group's name lowercased with spaces as hyphens (`fb-diamond-painting-addicts`)
   - a clip platform's bio: `bio`
   - one post, read on its own: the community plus `-MMDD`, the post's date (`r-diamondpainting-1024` for a post on 24 October). A second post the same day adds `-2`.
3. Only `a-z`, `0-9` and `-`, at most 100 characters (the app drops anything else, `ReferrerParse`).
4. Put the two into the link, encoded once (`=` is `%3D`, `&` is `%26`):

   `https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory&referrer=utm_source%3D<platform>%26utm_campaign%3D<campaign>`

Worked example, a post in r/diamondpainting on 24 October:

`https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory&referrer=utm_source%3Dreddit%26utm_campaign%3Dr-diamondpainting-1024`

To check a new link: open it on a phone without Gemmory, install from the store page, and after a day look for its campaign in Play Console's store-listing acquisition report (Firebase shows it as the install's first user source and campaign once analytics consent is given).

## Reserved values: never on a channel link

The app acts on some referrer values, so a channel link carries `utm_source` and `utm_campaign` and nothing else.

| Never | Why |
|---|---|
| `utm_source=share` | Share attribution: the links in players' shares (T50, T53, `ShareLink`). A channel link on it would count as a player's share. |
| any `utm_content` | T50 reads it as a catalog picture id, from any source, and opens that picture as a fresh install's first canvas (`ShareLanding.pictureId`). Channel links give up landing on a picture by choice (round 4 outside voice, item 14). An id that names no picture opens nothing, and the gallery shows as usual (`ShareLandingTest.theIdIsUtmContentWhateverTheSource`, `SharedLandingTest.aHiddenIdOpensNothingOnceTheCheckHasEnded`, `ScheduledDropsFlowTest`). |
| `utm_medium` | Share links use it for `bundled`, `photo` and `streak`. |

## Clips: most installs arrive untagged

Feed posts (the clip itself) carry no link, and TikTok, Instagram and YouTube open bio links in in-app browsers that often drop Play's referrer. So most installs a clip drives arrive as `google-play / organic`. They're read through the organic bucket, split by T56's "where did you find us" answer (`video`), not through the bio links alone. The readout also has a row for players' own feed-cut shares (`share_completed` with `cut` = `feed`).
