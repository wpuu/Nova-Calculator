# Nova AutoTap — Market Validation & Acquisition V1

Date: 2026-08-30

## Decision

Nova's first commercial acquisition wedge is **AutoTap**, not generic calculator and not Photo Math.

The first release should win on a narrow combination users can understand immediately:

> **Volume+ Start · Volume− Instant Stop · Touch-through while running · Full-screen reliable · Saved setup · Precise timing**

Calculator remains the base utility. Agnes 2.5 Flash is a P1 differentiator for reducing setup friction, not the runtime click engine.

## Demand evidence

### Large existing market

Current Google Play examples show that automatic tapping is a large, established Android utility category:

- True Developers Studio — Auto Clicker - Automatic tap: 100M+ downloads.
- Simple Design — Auto Clicker: Auto Tapper: 10M+ downloads, hundreds of thousands of reviews.
- gc auto clicker — Auto Click - Automatic Clicker: 10M+ downloads, hundreds of thousands of reviews.
- Bright Prospect — Auto Clicker-Auto Tap & Swipe: 10M+ downloads, tens of thousands of reviews.

This is not a market we need to invent. The product problem is differentiation and conversion.

### Direct willingness-to-pay signal

A verified Google Play review dated 2026-07-31 for Simple Design's Auto Tapper says the user would happily pay a **one-time USD 5–7** after trying the app for several days.

Use this as the initial price anchor, not as a universal market average.

Initial Pro Lifetime tests:

- USD 5.99
- USD 6.99 — default first test
- USD 7.99

### Repeated pain signals

Current/recent reviews and product descriptions repeatedly reveal these jobs and pain points:

1. **Safe stop** — users need to stop an automation immediately when something goes wrong.
2. **Low obstruction** — floating panels and targets should not block the underlying app while running.
3. **Precise timing** — interval and press/hold duration matter.
4. **Saved scripts/profiles** — users expect repeatable setups.
5. **Simple configuration** — ease of setup is repeatedly praised.
6. **Low advertising interruption** — excessive ads generate explicit negative reviews.
7. **Orientation/full-screen reliability** — overlay/layout stability is a product-quality differentiator.

### Volume keys are a current demand signal

This is no longer only a historical niche:

- Auto Clicker - Fast Tap recently added volume-key pause control.
- AutoClicker 2026 MacroRecorder lists volume keys as gesture triggers.
- Historical Volume Key Auto Clicker reached substantial install volume before becoming unavailable; its removal reason is not treated as evidence of a policy violation.

Therefore Nova should not claim that volume-key control is unique. The differentiation is the **complete hardware-control experience**, especially `Volume− Instant Stop`.

## Product scope driven by demand

### Free

- 2 click targets
- Volume+ start
- Volume− stop
- interval and duration
- 1 saved profile
- draggable target/status placement while idle
- targets/status pass through touch while running
- no aggressive interstitial advertising

### Pro Lifetime

- more click targets
- multiple profiles
- import/export
- advanced timing presets
- optional AI Setup Assistant quota
- future convenience features only when tied to measured demand

### AI Plus

Keep the infrastructure, but do not make subscription the first revenue thesis. Promote it only after Photo Math / AI explanation produces meaningful repeated use.

## Agnes 2.5 Flash — recommended integration

### AI Setup Assistant

The highest-leverage initial use of Agnes is **configuration assistance**:

1. User supplies a screenshot or capture.
2. User states the desired repetitive action in natural language.
3. Agnes proposes target regions, order, interval, and hold duration.
4. Nova renders an explicit visual preview.
5. User confirms or edits every target/parameter.
6. Nova converts the confirmed plan into a deterministic static script.
7. Runtime executes only the confirmed static script.

Do **not** make Agnes continuously observe the screen and autonomously decide the next action. That creates a materially different policy and safety profile from user-defined deterministic automation.

### Photo Math

Secondary experiment:

- screenshot/photo -> Agnes multimodal interpretation
- deterministic calculations -> existing math engine verifies when possible
- Agnes -> natural-language explanation and steps
- measure repeat usage before increasing development priority

## Acquisition plan

### Channel 1 — Google Play organic search

Use one APK and Custom Store Listings before considering separate apps.

First six search-intent listings:

1. `auto clicker`
2. `auto tap` / `auto tapper`
3. `volume key auto clicker`
4. `auto clicker without root`
5. `multi point auto clicker`
6. `press and hold auto clicker`

Do not promise features that are not present in the current release.

### Hero-message experiments

Test one dominant promise per listing instead of mixing every feature into the first screenshot:

- `Stop Instantly with Volume Down`
- `Auto Tap Without Blocking Your Screen`
- `Reliable Full-Screen Auto Tap`
- `Set Once. Save It. Run Again.`
- `Precise Tap & Hold Timing`

### Channel 2 — Store Listing Experiments

Only vary a few high-impact elements per experiment:

- icon
- first screenshot / hero message
- short description

Primary decision metric: store-listing visitor -> install.

### Channel 3 — Programmatic SEO

After Play production/closed-testing URL exists, publish focused landing pages and deep-link to the relevant listing.

Initial long-tail pages:

- volume button auto clicker android
- auto clicker without root android
- two point auto clicker
- press and hold auto clicker
- auto clicker full screen landscape
- safe stop auto clicker
- auto clicker with saved profiles

Grok 4.6 can generate page variants at very low production cost; templates should be centrally controlled so claims remain accurate.

### Channel 4 — Paid app campaigns

Do not buy installs before organic listing conversion and first-run activation are measurable.

Use small paid tests only after:

- listing conversion is known
- AutoTap activation is instrumented
- first successful run is instrumented
- purchase event is instrumented

Then optimize on downstream actions instead of raw installs.

## Validation funnel

Track at minimum:

1. Store listing visit
2. Install
3. AutoTap settings opened
4. Accessibility disclosure accepted
5. Accessibility permission completed
6. Overlay displayed successfully
7. First profile/targets configured
8. First successful Volume+ run
9. Volume− successful stop
10. Second-day return
11. Seventh-day return
12. Paywall viewed
13. Pro purchase started
14. Pro purchase verified by server
15. Purchase restored successfully

## Internal decision thresholds

These are **Nova experiment rules**, not Google Play industry benchmarks:

- If a keyword listing brings installs but AutoTap activation is low, store promise and installed product are mismatched.
- If AutoTap activation is high but first successful run is low, fix onboarding/permission/stability before buying traffic.
- If repeat use is healthy but Pro conversion is weak, test packaging/price before adding unrelated features.
- If a feature cannot be tied to acquisition, activation, retention, conversion, support reduction, or compliance, it does not enter P0.

## Immediate engineering priorities

1. API 30+ current WindowMetrics; legacy realMetrics fallback.
2. DisplayManager listener for display/mode changes.
3. Make reticles and status indicator non-touchable during active clicking.
4. Replace 1 px micro-swipe with a stationary tap.
5. Verify Volume− stops during pending gesture/watchdog conditions.
6. Add Profiles.
7. Complete Play Accessibility disclosure and testing.
8. Ship the six acquisition listings.

## Current non-goals

- multiple near-identical Play apps
- runtime autonomous AI clicking
- game-specific cheating claims
- aggressive ad monetization before retention is understood
- large Photo Math investment before AutoTap funnel data exists
