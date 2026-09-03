# Google Play + Chrome + Web Channel Strategy V9

Date: 2026-09-03

## Scope decision

This research phase is no longer limited to “publish Nova on Google Play”. The correct unit of analysis is the user demand plus the best carrier for acquisition and conversion:

- Google Play for mobile-native behavior and Android-only capabilities.
- Chrome Web Store for in-context browser decision support.
- Web tools/SEO for unlimited long-tail validation and lead generation.

Keep strategy comparison in the same conversation. Split into a new implementation conversation only after one Chrome product wins validation and is ready for independent code/release work.

## Carrier selection rule

Choose the carrier after the demand is known. Do not force every demand into Android.

### Google Play is strongest when the value requires

- Accessibility or device automation.
- Background/mobile usage.
- Camera/media/device sensors.
- Persistent Android workflow.
- Mobile-first discovery and repeated daily use.

Current examples: AutoTap, Easy Android Automation, mobile screen translation.

### Chrome is strongest when the user is already on the page where the decision happens

- 1688 / Alibaba sourcing pages.
- Amazon product or Seller Central pages.
- Shopify merchant pages.
- Other browser-based B2B admin portals.

The extension should add decision value directly to the current page rather than merely launch a website.

### Web is strongest when

- Demand still needs validation.
- The task can start from an upload/form/calculator.
- Search intent is the primary acquisition source.
- Hundreds of long-tail pages are useful.
- No install is required.

Grok 4.6 High should mainly scale web landing/tool pages, not create many near-identical extensions.

## Important Chrome Web Store 2026 constraints

1. Manifest V3 only. Remaining Manifest V2 extensions were removed from the store on 2026-08-31.
2. Chrome Web Store changed publication limits on 2026-08-20. Default publisher capacity is now two extension slots, with dynamic limits based on quality and usage. This makes extension spam structurally unattractive.
3. Extensions must have a narrow, understandable single purpose. A giant unrelated “Nova toolbox” extension is a policy and conversion risk.
4. Request only the minimum permissions necessary. Browser activity/content collection must be required for the disclosed user-facing purpose.
5. Payment can be external, but pricing/basic-paid functionality and sales terms must be disclosed clearly.
6. Publisher registration remains a one-time fee; 2026 Chrome documentation references the $5 developer registration fee.

## Portfolio implication

Do not create one extension per SEO keyword. Use this structure:

- 1–2 high-quality Chrome extensions maximum at first.
- Many Grok-generated web tools/landing pages.
- Shared Nova Gateway, entitlement, analytics and Agnes key pool.
- Each extension has one coherent purpose and routes users into the same backend/account system.

## Current carrier matrix

| Opportunity | Play | Chrome | Web | Preferred first carrier |
| --- | --- | --- | --- | --- |
| AutoTap | Excellent | N/A | Weak | Play |
| Easy Android Automation | Excellent | Weak | Validation only | Play |
| Mobile Screen Translate | Excellent | Website-only subset | Good validation | Web -> Play |
| China Deal Inspector | Weak | Excellent | Excellent validation | Web -> Chrome |
| FBA Recovery Second Opinion | Weak | Medium | Excellent | Web first; Chrome only if page-context proves valuable |
| Chargeback Evidence | Weak | Medium | Medium | Shopify/native integration before Chrome |
| IEEPA / Customs refund screener | Weak | Low initially | Excellent | Web |
| Amazon sourcing / arbitrage decision support | Weak | Excellent | Good | Chrome + Web |

## Chrome market proof relevant to Nova strategy

The browser decision-support model is already proven at large scale:

- Keepa: millions of Chrome users by adding price history directly to Amazon pages.
- Helium 10: roughly one million Chrome users for seller research/profit analysis.
- SellerAmp SAS: roughly one hundred thousand Chrome users for one-click sourcing and profitability analysis.
- Official 1688 Purchasing Assistant: roughly two hundred thousand Chrome users.
- Official 1688 AlphaShop / AIBUY: tens of thousands of Chrome users.

The winning pattern is not “AI extension”. It is “user is making a transaction decision on a webpage; extension supplies missing history, cost, risk or comparison at that exact moment.”

## China Deal Inspector implication

Chrome is a much better long-term carrier than Android for this demand because the user is already on 1688/Alibaba/Amazon when the decision occurs.

However, competition is real:

- Official 1688 extensions already cover image search, trends, sourcing and AI-assisted procurement.
- New third-party 1688 Supplier Intelligence already provides trust score, English reports and supplier comparison but currently has almost no installed base.

Therefore Nova must not compete on “translate 1688” or generic supplier scoring.

Preferred differentiator:

**Deal economics and decision layer**

- True minimum order cash requirement.
- Tiered price normalization.
- Packaging/sample/tooling/deposit signals.
- Supplier A/B/C comparison.
- Landed-cost estimate.
- Target-market margin.
- Historical quote and supplier memory.
- Questions still requiring confirmation before payment.

The extension reads the current page locally, then the user deliberately clicks one Analyze action. That action uses the global Agnes single-flight system.

## Agnes single-flight remains universal

- One user-triggered Agnes request at a time across all modules.
- Once one request starts, all other Agnes-dependent controls are disabled.
- Local deterministic functions remain available.
- Multiple Agnes account keys are a server-side pool only.
- Per-key scheduling stays below the 20 RPM provider ceiling; prefer rotation/queue rather than front-end concurrency.
- Batch all page context and user intent into one request whenever possible.

## Conversation/repository separation rule

Strategy research stays together while comparing carriers.

Open a new dedicated conversation when a winner reaches implementation, for example:

- `Nova Android / Google Play` remains Android execution.
- `Nova China Deal Inspector Chrome` becomes a separate Chrome implementation thread.
- `Customs Refund Radar` becomes a separate web/lead-gen thread.

Do not mix unrelated production code into the Android app just because the opportunity was discovered here.
