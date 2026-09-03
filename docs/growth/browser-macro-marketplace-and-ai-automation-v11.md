# Browser Macro Marketplace & AI Automation V11

Date: 2026-09-04
Branch: fix/commercial-autotap-market-fit-v2

## 1. Decision

Add browser macro automation as a first-class candidate in the overseas opportunity pool, but do NOT build a generic clone of UI.Vision, Automa, Axiom, Bardeen, Browserflow, TaskMagic, or Browse AI.

The validated demand is not merely `record -> replay`. Current competitors already prove demand at meaningful scale:

- UI.Vision: ~200k Chrome users; record/replay, OCR, computer vision, AI assistant, local deterministic execution.
- Automa: ~200k Chrome users; visual block workflows, scheduling, open source, marketplace with ~4,450 shared workflows.
- Bardeen: ~200k Chrome users; AI automation, scrapers/templates, GTM workflows, multilingual.
- Axiom: ~100k Chrome users; no-code + code + AI, cloud/local execution, scheduling, sharing, 2FA.
- Browserflow: ~20k Chrome users with strong rating; simple no-code record/build model.
- Browse AI: 250+ prebuilt robots, monitoring, paid tiers.
- China precedent: Yingdao/影刀 already supports workflow sharing, versioning, marketplace publishing, paid/subscription distribution and reports 30k+ companies / 1000+ automation scenarios.

Conclusion: demand, payment and workflow reuse are validated. The opportunity is in reliability + maintained macro assets + creator economy, not basic recording.

## 2. Competitor strengths to absorb

### UI.Vision
Absorb:
- local deterministic playback
- open/local-first trust
- visual/OCR fallback
- macro remains editable after AI builds/fixes it
- AI used at build/repair time rather than every run

Do not compete on:
- generic free macro recorder
- generic OCR/image automation

### Automa
Absorb:
- easy visual blocks
- scheduling
- open workflow format/community
- marketplace/community distribution
- ability to turn workflows into reusable assets

Weakness/opportunity:
- marketplace has huge quantity but many visible items have very low engagement; quality, discoverability, maintenance and trust are not obviously solved.

### Axiom
Absorb:
- no-code + code escape hatch
- local and cloud execution choices
- 2FA/TOTP support
- run recordings/logs
- sharing/export

Weakness/opportunity:
- broad product complexity
- runtime pricing/cloud focus can be unnecessary for local repetitive tasks
- no obvious paid creator marketplace with maintained versions

### Bardeen
Absorb:
- use-case-first templates and pages
- strong GTM vertical positioning
- AI to create automations
- language/localization

Weakness/opportunity:
- pricing/credits had enough predictability concerns that Bardeen publicly emphasized transparency changes.
- product is increasingly GTM-focused, leaving non-sales verticals less central.

### Browserflow
Absorb:
- simple UX
- record/build directly in browser
- strong rating signal

### TaskMagic
Absorb:
- AI builds once, runtime stays cheap/deterministic
- plain-English setup

## 3. Proposed differentiated product

Working concept: `Nova Macro` / `Nova Web Macro` (name not final).

Core promise:

> Record once. AI cleans it up. Run locally. Repair when sites change. Buy and sell maintained workflows.

Not an autonomous agent on every run.

### V1 workflow
1. User presses Record.
2. Extension captures clicks, typing, navigation, waits, selected extractions.
3. User presses Stop.
4. User optionally presses `Optimize with AI`.
5. All Agnes-dependent UI globally disables until the request completes.
6. One batched Agnes call transforms raw recording into a cleaner deterministic macro:
   - remove redundant steps
   - parameterize values
   - suggest stable selectors
   - replace fixed sleeps with conditions where possible
   - identify loops/repeated patterns
   - add retry/timeout rules
   - identify secrets / fields that should become variables
   - generate a plain-language explanation
7. User reviews changes.
8. Runtime executes locally without Agnes.
9. On failure, capture failure context locally and offer `AI Repair`.
10. AI Repair is another explicit single Agnes request; user confirms patch.

This is ideal for the RPM constraint because ordinary runs use 0 Agnes calls.

## 4. Real moat candidates

### A. Reliability graph / locator stack
A recorded element should not be stored as one brittle XPath.
Store multiple locator signals:
- accessible role/name
- stable id/name attributes
- visible text
- relative anchors
- DOM hierarchy hints
- optional visual snapshot

Runtime tries deterministic fallbacks.

### B. Failure-to-fix dataset
With explicit opt-in and privacy-minimized telemetry, collect only failure signatures / macro version / site version-like signals, not arbitrary browsing history.
Goal: learn which locator strategies and waits survive changes.

### C. Maintained Macro marketplace
Do not sell a static JSON file as the main value.
Sell:
- versioned macro
- compatibility status
- update history
- success rate
- creator reputation
- required permissions/domains
- supported site/language/region
- changelog
- maintained updates

Buyer pays for continuing compatibility, not for knowing the click sequence.

### D. Creator network
A creator publishes a macro for a specific real business task.
The platform handles:
- discovery/SEO
- install/import
- permissions disclosure
- versioning
- updates
- ratings
- optional paid entitlement
- support boundary

Creators become an acquisition channel because they promote their own workflows.

### E. Vertical starter packs
Generic macros have weak willingness to pay.
Seed marketplace with workflows that have clear ROI:
- e-commerce back-office report export / reconciliation
- marketplace product research and monitoring
- repetitive CRM/vendor portal operations
- invoice/report download and file organization
- approved data entry workflows
- QA/regression and internal admin tasks

Avoid building the marketplace around generic “fill this form” macros.

## 5. Marketplace economics / bypass risk

Static one-time macro files are easy to copy and bypass.
A stronger structure is:

`buyer -> maintained workflow -> Nova runtime/versioning/update service -> creator`

Reasons to stay inside Nova:
- one-click updates
- compatibility status
- automatic migration between macro versions
- verified creator reputation
- failure reports
- AI repair context
- saved variables/secrets
- audit history

A buyer can still recreate a macro manually, but bypassing means accepting maintenance work.

## 6. Acquisition

Do not rely on the Chrome Web Store generic keyword `browser automation`; competitors are mature.

Main acquisition engine:

### Template SEO
Every high-quality workflow gets a real page:
- `automate X without API`
- `export X report automatically`
- `copy X to Google Sheets`
- `bulk update X safely`
- `download monthly reports from X`

The page explains the task, limitations, permissions and expected outputs, then CTA installs Nova and imports the workflow.

### Creator-led distribution
Template sellers post their workflow in the exact niche community where the task exists.
Nova earns from the transaction/update layer rather than doing all promotion itself.

### Chrome Web Store
One flagship extension only, not dozens of variants.
Store listing sells the engine: record, optimize, replay, repair, install workflows.

### Cross-sell from existing opportunity pages
IEEPA / sourcing / reseller / seller tools that eventually require repetitive browser actions can offer a Nova Macro workflow where appropriate.

## 7. China -> overseas information advantage

China already has mature RPA culture around Yingdao/影刀 and other automation tools, especially e-commerce operations. Useful ideas to export:
- application/workflow marketplace
- paid workflow distribution
- version control / rollback
- team sharing
- standardized reusable subflows
- e-commerce operation template libraries

Do not export high-maintenance anti-risk/anti-detection shop-farm patterns. Instead export the productive workflow architecture to legitimate overseas seller/admin operations.

## 8. Gray-edge opportunity classes

Allowed research/possible marketplace categories:
- price/listing monitoring
- authorized repetitive portal operations
- own-account report export
- marketplace research
- appointment-slot intelligence without automated booking
- public/authorized data extraction with rate limits

High-risk categories to exclude from official marketplace:
- CAPTCHA bypass / queue bypass
- credential stuffing
- fake account creation
- automated ad clicking
- bulk spam / unsolicited messages
- fake reviews/engagement
- payment confirmation or fraud workflows
- anti-ban / anti-detection packages

The product can be general-purpose, but the official marketplace should not become a catalog of platform-abuse macros.

## 9. Chrome policy architecture constraint

Manifest V3 remote-hosted-code policy is a major design constraint.
Chrome explicitly restricts remotely fetched executable logic and even warns against interpreters that execute complex remotely fetched commands.

Therefore:
- no remote arbitrary-JS macro store in the extension
- executable primitives must be bundled/reviewable
- marketplace format should be constrained/declarative or imported through a policy-compatible user-driven mechanism
- arbitrary advanced code should be handled only through an architecture specifically reviewed against Chrome's User Scripts / Debugger API rules or via a separate local desktop runner
- before marketplace implementation, perform a dedicated Chrome Web Store policy design review

This is P0, not a later compliance detail.

## 10. Engineering difficulty

- basic recorder + playback: 5/10
- robust selector/fallback engine: 7/10
- AI post-record optimization: 5/10 because Agnes is one-shot and existing gateway patterns can be reused
- failure capture + AI repair: 6.5/10
- marketplace, creator accounts, versioning, payments: 8/10
- cloud execution / proxies / fleet: 9/10 and should not be V1

Recommended initial scope stays local-first.

## 11. Current opportunity score

Browser Macro Engine alone: 7.0/10 (validated but crowded)

Browser Macro + AI optimization alone: 7.2/10 (already copied by strong competitors)

Maintained Workflow Marketplace + local deterministic runtime + AI repair: 8.6/10 candidate, but marketplace willingness-to-pay and Chrome policy architecture still require validation.

## 12. Next validation gates

Before major coding:
1. Mine top complaints/reviews/issues from UI.Vision, Automa, Axiom, Browserflow and TaskMagic.
2. Identify 20 repetitive browser tasks with direct business ROI and recurring breakage.
3. Rank which tasks have communities/search intent and creator supply.
4. Test 5-10 workflow landing pages with Grok before building marketplace.
5. Validate whether users prefer:
   - build their own macro
   - buy a maintained macro
   - pay a creator for customization
6. Design Chrome MV3-compliant workflow import/runtime architecture.
7. Only then decide whether to open a separate Nova Macro implementation conversation/repository.
