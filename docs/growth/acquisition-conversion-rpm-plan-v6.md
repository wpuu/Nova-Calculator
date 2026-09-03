# Nova / Opportunity Factory — Acquisition, Conversion & Agnes RPM Plan V6

Date: 2026-09-03

## Core correction

A demand is not a business until the full path is explicit:

`where the customer already appears -> how we intercept -> why they trust us -> what immediate result they get -> what action produces revenue -> why they do not bypass us`

Agnes is assumed permanently free for planning. The only hard runtime constraint is RPM <= 20. Design for <= 12 RPM steady-state and <= 15 RPM burst ceiling to leave safety margin.

## Agnes usage rule

Agnes must not sit in high-frequency loops.

- Static calculators, policy windows, fee formulas, keyword routing, eligibility gates: local deterministic code / cached data.
- Agnes only at high-intent moments where one call can materially improve a conversion or a high-value case.
- Batch all text/images belonging to one case into one request where possible.
- Deduplicate identical inputs by content hash.
- Cache normalized results.
- Global token bucket: steady 12 RPM, hard 15 RPM; queue excess requests.
- No per-message, per-screen-frame, per-keystroke, per-scroll AI calls.

Target calls per conversion path:

| Path | Agnes calls before conversion |
|---|---:|
| Flight compensation lead | 0-1 |
| FBA reimbursement lead | 0-1 |
| TikTok/Shopify dispute pack | 1-3 per case |
| China sourcing product review | 1-2 per product |
| Screen translation realtime | too frequent; deprioritized |

## Ranking now includes customer acquisition feasibility

### 1. TikTok Shop / Shopify chargeback evidence assistant

Why it matters:
- Seller receives a real chargeback notification: intent is immediate, not hypothetical.
- TikTok US normally gives 7 calendar days to appeal and requires transaction status, product description, and proof of receipt; optional evidence includes order emails, tracking, product images, refund/cancel data, and customer messages.
- Shopify App Store already proves merchants install tools that detect disputes, gather evidence and build responses.

Acquisition channels:
1. Shopify App Store: strongest borrowed-distribution channel for Shopify version. Merchant searches `chargeback`, `dispute`, `fraud`, `recovery` while already having the problem.
2. Google long-tail SEO: `chargeback evidence template`, `Shopify chargeback response`, `TikTok Shop chargeback appeal`, reason-code/problem pages.
3. Chrome Web Store for TikTok Shop Seller Center helper: distribution surface without needing a standalone consumer app.
4. Seller communities/content only as secondary discovery, not manual cold outreach.

Conversion path:
`seller has active dispute -> opens app/page -> imports or uploads order evidence -> deterministic checklist instantly shows missing proof -> one Agnes case synthesis -> preview evidence pack -> install/submit/upgrade/partner action`

Why conversion can work:
- deadline and money loss already exist;
- product shows missing evidence before asking for payment/action;
- value is tied to a specific case, not a vague promise.

Do not do:
- fabricate evidence;
- alter tracking/customer records;
- promise a win;
- automate deceptive disputes.

Development difficulty:
- Web/manual upload MVP: 4/10.
- Shopify integrated version: 6.5/10.
- TikTok Chrome helper: 5.5/10.

Status: highest product-build candidate because acquisition channel and monetizable event are both native to the workflow.

### 2. Amazon FBA reimbursement lead engine

Important correction:
Amazon already proactively reimburses many fulfillment-center lost/damaged and customer-return cases. Generic `Amazon owes you money` is too broad. Remaining acquisition wedges must focus on exceptions and errors:
- missed automatic reimbursement;
- removal claims;
- sourcing-cost valuation errors;
- expiring claim windows;
- reimbursement reversals / unmatched events.

Borrowed execution:
GETIDA has an affiliate program: 90-day cookie, revenue sharing when referred sellers recover money, and a performance-based service. PartnerStack currently describes 10% of recovery fee for the first 12 months plus a signup activation incentive.

Acquisition channels:
1. SEO for concrete exception queries, not generic reimbursement keywords.
2. Free deterministic claim-window checker.
3. Free sourcing-cost reimbursement checker.
4. Seller-facing browser tool/report that flags candidate anomalies but sends execution to approved reimbursement partner.
5. Amazon seller creator/newsletter partnerships after traffic exists.

Conversion path:
`seller searches a specific reimbursement anomaly -> free checker gives a concrete candidate amount/window -> CTA: run full audit with partner -> affiliate tracking -> partner executes recovery`

Agnes:
- 0 calls for simple windows/formulas.
- max 1 call when seller uploads a complex report and we need classification/explanation.

Weakness:
- crowded SEO;
- Amazon is automating more reimbursements, so broad opportunity may shrink.

Status: strong affiliate experiment, not highest-priority proprietary product.

### 3. Flight compensation affiliate funnel

AirHelp currently offers 15% commission on successful claims, roughly EUR70-EUR75 average affiliate payout, 30-day cookie, and says partnerships can convert up to 45% in embedded customer journeys.

But acquisition is the bottleneck:
- generic `flight delay compensation` SEO is mature and competitive;
- AirHelp affiliate terms prohibit brand bidding, unsolicited commercial email, doorway/incentive/automated traffic, and direct affiliate-link PPC.

Therefore Grok must not mass-produce thin doorway pages.

Best acquisition wedges:
1. Useful disruption checker/calculator pages by regulation and problem type.
2. Airline/airport/route pages only where each page contains unique current rules, eligibility logic and a real calculator.
3. Travel tools: `delay > eligibility -> claim` rather than generic articles.
4. Post-disruption content integrations with travel sites/newsletters if audience later exists.

Conversion path:
`traveler searches immediately after disruption -> enters flight/date -> deterministic eligibility screen -> if ambiguous, one Agnes explanation -> show estimated claim range + why -> AirHelp referral`

Agnes:
0-1 call per lead.

Status: excellent unit economics and low engineering; weaker zero-to-one acquisition because SERP competition and affiliate acceptance matter.

### 4. China sourcing intelligence / lead routing

Opportunity is not translation. Competitors already translate/import 1688 listings and Shopify apps exist.

Acquisition wedges must be decision problems:
- `1688 landed cost calculator`
- `1688 vs Alibaba price`
- `1688 supplier check`
- `1688 hidden fees`
- `1688 MOQ meaning`
- `1688 agent fee`
- `China sourcing margin calculator`

Borrowed distribution:
1. Google/Bing SEO.
2. Shopify App Store via a narrow sourcing/margin app after web validation.
3. Chrome Web Store helper on 1688 pages.
4. Referral/lead-routing to sourcing agents, freight forwarders, inspectors, or procurement services once partner economics are verified.

Conversion path:
`buyer pastes link/photo -> local parser/calculator gets obvious fields -> 1 Agnes call normalizes Chinese commercial terms -> user receives landed-cost/risk card -> next action: compare suppliers / request sourcing / install extension / partner referral`

Anti-bypass:
Do not sell a one-time translation. Retain value in supplier history, price changes, landed-cost history, comparison, evidence/risk checks and execution referrals.

Development difficulty:
- Web MVP: 3.5/10.
- Chrome helper: 5/10.
- Shopify integration: 6/10.

Status: strongest China-to-overseas information-gap candidate; customer acquisition still needs SEO/tool proof before deeper build.

## Acquisition scoring

| Candidate | Existing intent | Borrowed traffic surface | Conversion urgency | RPM fit | Build difficulty | Current rank |
|---|---:|---:|---:|---:|---:|---:|
| Chargeback evidence | 9 | 9 (Shopify/CWS/Search) | 10 | 10 | 6 | 1 |
| FBA reimbursement affiliate | 8 | 7 (Search/partner) | 8 | 10 | 3 | 2 |
| Flight compensation affiliate | 10 | 6 (Search crowded) | 10 | 10 | 2 | 3 |
| China sourcing intelligence | 8 | 8 (Search/Shopify/CWS) | 7 | 9 | 4 | 4 |
| Screen translator | 10 | 8 (Play/Search) | 5 | 3 | 7 | deprioritize |
| AutoTap | 10 | 9 (Play) | 4 | 10 | already built | traffic asset |

## Grok 4.6 High rule

Grok pages are created only after defining the exact funnel.

Each page must have:
1. one high-intent problem;
2. a real deterministic tool/checker;
3. one clear result;
4. one conversion action;
5. source/claim boundaries;
6. attribution tag to identify which page produced the lead.

Do not create thousands of thin pages. Start with 5-10 pages per candidate, measure impressions -> tool start -> result -> CTA click -> downstream conversion, then expand only winning clusters.

## First validation sequence

1. Build no new Android feature.
2. Create 5-10 real-tool pages for FBA exception cases.
3. Create 5-10 real-tool pages for China sourcing decision cases.
4. Create one chargeback evidence-pack web MVP and evaluate Shopify App Store / Chrome Web Store packaging.
5. Apply to GETIDA/AirHelp affiliate only when a compliant owned site with actual tools exists.
6. Keep Agnes globally throttled <=15 RPM and normally <=12 RPM.
7. Only after actual tool-start and CTA data should Codex build deeper integrations.

## Kill rule

A project is rejected even if demand is large when we cannot answer all four:
- where the customer already appears;
- what exact query/event brings them to us;
- what they receive before leaving;
- what concrete action generates revenue.

Demand without acquisition and conversion is not a validated opportunity.
