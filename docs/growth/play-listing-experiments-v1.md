# Nova — Google Play Acquisition Experiments V1

Date: 2026-08-30

## Purpose

The first commercial validation is not “do users like Nova?” It is:

1. Which search intent can acquire users?
2. Does the installed product deliver the promise of that listing?
3. Which intent produces repeat AutoTap use?
4. Which intent produces Pro Lifetime purchases?

Google Play currently permits Custom Store Listings targeted by Play Search keywords. A custom listing may customize app name, icon, descriptions and graphic assets. App name must stay within 30 characters; short description within 80 characters.

## Default listing

### Working name
`Nova Calculator AI`

Use the default listing mainly for brand/general calculator traffic. Do not force AutoTap keywords unnaturally into every field.

## Experiment A — generic Auto Clicker

**Keyword bundle**
- auto clicker
- automatic clicker

**Custom name**
`Nova AutoTap: Auto Clicker`

**Short description**
`Auto tap with precise timing, saved setups and fast volume-key stop.`

**First screenshot message**
`Fast Auto Tap. Instant Hardware Stop.`

**Product promise**
- reliable tapping
- simple setup
- safe stop

## Experiment B — Auto Tap / Auto Tapper

**Keyword bundle**
- auto tap
- auto tapper
- automatic tap

**Custom name**
`Nova AutoTap: Auto Tapper`

**Short description**
`Reliable auto taps across full-screen and landscape apps with saved positions.`

**First screenshot message**
`Set It Once. Tap Again Anytime.`

**Product promise**
- saved target positions
- orientation/full-screen reliability

## Experiment C — Volume Key Control

**Keyword bundle**
- volume key auto clicker
- volume button auto clicker
- volume key auto tap

**Custom name**
`Nova AutoTap: Volume Clicker`

**Short description**
`Start and stop auto taps with volume keys while your screen stays usable.`

**First screenshot message**
`Volume Up Starts. Volume Down Stops.`

**Second screenshot message**
`Stop Instantly Without Hunting for a Floating Button.`

**Product promise**
- hardware start/stop
- Volume Down is the safety stop
- running overlays do not steal touches

## Experiment D — No Root

**Keyword bundle**
- auto clicker without root
- no root auto clicker
- auto tap without root

**Custom name**
`Nova AutoTap: No Root Clicker`

**Short description**
`No root needed. Place targets, set timing, then use volume keys to control taps.`

**First screenshot message**
`Auto Tap Without Root.`

**Product promise**
- Accessibility-based user-defined automation
- clear permission explanation

## Experiment E — Multiple Points

**Keyword bundle**
- multi point auto clicker
- multiple point auto clicker
- two point auto clicker

**Custom name**
`Nova AutoTap: Multi Clicker`

**Short description**
`Set multiple tap points, tune the timing and save your setup for later.`

**First screenshot message**
`Place Targets. Set Timing. Save the Setup.`

**Product promise**
- multiple targets
- repeatable profiles

**Release dependency**
Do not activate this listing until the marketed target/profile functionality exists in the shipping build.

## Experiment F — Press and Hold

**Keyword bundle**
- press and hold auto clicker
- long press auto clicker
- auto tap hold duration

**Custom name**
`Nova AutoTap: Tap and Hold`

**Short description**
`Set precise tap and hold duration with simple on-screen targets.`

**First screenshot message**
`Precise Tap and Hold Timing.`

**Product promise**
- per-action hold duration
- understandable millisecond controls

**Release dependency**
Do not activate this listing until the shipping implementation matches the claim.

## Store Listing Experiment rules

Do not change multiple concepts at once. Run sequential experiments against meaningful traffic.

Priority variables:

1. icon
2. first screenshot / hero claim
3. short description

Do not use price/deal/ranking claims in title or listing graphics. Do not keyword-stuff descriptions.

## Conversion interpretation

Treat results as a funnel rather than only installs.

For each Custom Store Listing record:

- listing visitors
- installers
- AutoTap settings opened
- accessibility setup completed
- first overlay success
- first configured targets
- first successful Volume+ start
- successful Volume− stop
- second AutoTap session
- D1 / D7 return
- Pro paywall view
- Pro verified purchase

### Diagnostic rules

**High listing conversion + low AutoTap activation**
The listing attracted the wrong user or the installed product/brand does not match the promise.

**High AutoTap activation + low first-run success**
Do not buy traffic. Fix onboarding, permissions, overlay stability and full-screen behavior.

**High repeat usage + low Pro conversion**
Test Pro packaging and price before adding unrelated features.

**Strong generic Auto Clicker traffic, weak calculator traffic**
Consider a later brand/package-positioning decision based on data. Do not prematurely clone near-identical apps.

## External SEO V1

Only after a Play URL exists, publish one focused page for each long-tail intent:

- volume-button-auto-clicker-android
- auto-clicker-without-root-android
- two-point-auto-clicker
- press-and-hold-auto-clicker
- auto-clicker-full-screen-landscape
- safe-stop-auto-clicker
- auto-clicker-saved-profiles

Each page must:

- solve one intent
- explain the relevant Nova feature accurately
- include screenshots/gifs only from the real shipping build
- deep-link to the closest Custom Store Listing
- avoid game-specific cheating or anti-cheat bypass claims

Grok 4.6 can later generate page variants from one controlled template. Human/product rules remain authoritative.

## Agnes 2.5 Flash acquisition experiment

Do not lead the first Play listing with generic “AI.”

Once the deterministic AutoTap funnel works, create a separate listing/landing-page experiment around:

`Describe What to Tap. Review It. Run It.`

Agnes workflow:

screenshot + user instruction -> suggested targets/timing -> visual review -> explicit user confirmation -> static script.

Measure whether this improves:

- setup completion
- time to first successful run
- repeat use
- Pro conversion

If it does not improve those metrics, do not keep AI merely as a marketing label.
