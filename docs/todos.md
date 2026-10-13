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
