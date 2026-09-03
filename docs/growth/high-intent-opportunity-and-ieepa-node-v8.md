# High-Intent Opportunity & IEEPA Node V8

Date: 2026-09-03

## Executive decision

The prior three candidates remain useful but their differentiation is eroding quickly:

- Chargeback evidence tooling is becoming crowded. New Shopify apps launched in mid/late 2026 already offer AI evidence assembly, deadline tracking, one-click review and even explicit human-review / missing-evidence workflows.
- FBA reimbursement auditing is also becoming crowded. ReimburseOps, BeanHawk and other tools now explicitly audit sourcing-cost gaps, under-reimbursements and reimbursement windows.
- 1688 supplier intelligence remains strategically relevant, but a near-direct Chrome competitor appeared in August 2026 with supplier scoring, compare, English reports and paid tiers.

Therefore we should not rely on “better UI” or “AI summaries” as the moat.

A new high-priority time-window opportunity is IEEPA tariff-refund screening and referral.

## Why IEEPA is unusually attractive now

CBP launched CAPE in ACE on 2026-04-20 to process IEEPA refunds. Subsequent 2026 updates expanded support for reconciliation-flagged entries and changed handling of warehouse entries/withdrawals. Eligibility is not a single static deadline; entries can age out of particular CAPE handling paths, so timing matters.

The business characteristics are strong:

1. The user is already owed or may be owed money.
2. The trigger is external and urgent: Supreme Court/CIT/CBP process changes, liquidation dates and CAPE phases.
3. The target customer is identifiable: U.S. importers of record and their brokers/forwarders/accountants.
4. The user does not need to be educated that money matters.
5. Licensed brokers/recovery firms already perform the filing work.
6. Multiple current programs explicitly offer referral/partner compensation.
7. Most of the screening can be deterministic; Agnes should be optional and normally 0-1 call per qualified case.

## Product concept: IEEPA Refund Screener

This is not a customs broker, law firm, government service or filing agent.

Primary promise:

> Upload or enter your import data and see which entries appear to require action, which CAPE path may apply, and what to ask your customs broker next.

### V1 inputs

- importer-of-record confirmation
- entry number(s)
- entry type
- entry / summary date
- liquidation date/status if available
- reconciliation flag/status
- warehouse / withdrawal type
- IEEPA Chapter 99 indicator if available
- carrier / broker identity
- optional ACE export CSV

### V1 outputs

- likely in-scope / needs review / likely out-of-scope classification
- current CAPE-path note based on published CBP rules
- aging / urgency signal
- reconciliation or warehouse handling note
- missing-data checklist
- broker-ready CSV / action list
- explicit source links and date of rule snapshot
- CTA: send to a licensed recovery/broker partner

No legal conclusion, no guaranteed refund amount, no request for banking credentials.

## Acquisition funnel

### Channel 1: high-intent search

Grok should create real tools/pages only for concrete questions such as:

- IEEPA refund eligibility checker
- CAPE declaration rejected
- IEEPA refund 80 day liquidation window
- CAPE reconciliation entry
- CAPE warehouse entry rejected
- entry type not allowed CAPE
- UPS IEEPA refund importer of record
- DHL IEEPA refund importer of record
- IEEPA refund importer vs broker
- CAPE Phase 2 reconciliation
- IEEPA refund ACH setup checklist
- IEEPA refund CSV format
- IEEPA refund for China imports
- IEEPA refund for Mexico imports
- IEEPA refund for Canada imports

Each page must contain an actual calculator/checker or current rule table, not a title-swapped article.

### Channel 2: partner-led acquisition

Instead of finding every importer ourselves, target people who already have importer relationships:

- customs brokers
- freight forwarders
- trade accountants
- bookkeepers serving importers
- supply-chain consultants
- ecommerce agencies with importer clients

Partner pitch:

> Give clients a free screening tool; qualified cases are routed to a licensed filing/recovery partner. You keep the client relationship; the recovery partner handles the regulated work.

Current market evidence shows explicit referral/partner programs already exist among IEEPA recovery firms and freight forwarders.

### Channel 3: narrow B2B outbound

Only after we have a real checker and destination partner.

Use lawful public/business data to identify likely importers, then limited business outreach focused on the specific refund event. No mass spam, no fake government branding, no requests for ACE passwords or banking details.

The message should drive to the free checker rather than directly asking for a sale.

### Channel 4: time-node content

CBP guidance is evolving. Every new CBP CAPE message creates a temporary search spike.

Examples:

- reconciliation support deployed
- warehouse-entry handling changed
- new phase announced
- new entry types supported
- new refund report / error code

This is where Grok has leverage: quickly produce accurate update pages and route them to the checker.

## Conversion

The free result must create immediate value before asking for contact details.

Example:

> 412 entries uploaded
> 263 appear to match the current deterministic screen
> 31 need reconciliation review
> 18 warehouse entries need a different path
> 100 missing required fields
> 42 entries are near an aging threshold

Then:

> Export action list
> or
> Send to a licensed recovery partner

Our monetization can be referral / partner compensation from an approved recovery provider. The filing work stays with the licensed/authorized party.

## Why this is harder to bypass

A static article is bypassable. The durable layer is:

- continuously updated CBP rule mapping
- entry-level screening
- phase/status classification
- aging alerts
- structured CSV preparation
- partner routing
- later expansion to other customs refunds / duty recovery events

IEEPA itself is a time-limited wedge, not the permanent company.

Long-term product direction:

> Customs Refund Radar

Potential later categories may include other carrier/customs overpayments and refund programs, but each must be independently verified before implementation.

## Competitor lesson

The current IEEPA market already has contingency recovery firms, flat-fee recovery programs, lead-generation sites and partner programs. Therefore we should not compete by claiming to “recover refunds better.”

Our differentiated entry point is:

> self-serve, transparent, source-cited screening before the importer gives a recovery firm access or signs a contingency agreement.

This positions us upstream of multiple recovery providers rather than as another provider.

## Agnes global single-flight hard rule

All Agnes-backed modules share one global UI lock per client session.

When any Agnes request begins:

1. the initiating control enters loading state;
2. every other Agnes-backed control across all modules is disabled/greyed;
3. deterministic/local tools remain usable;
4. no second Agnes request can be initiated by that client until completion/failure/timeout;
5. identical payloads should be hash-deduplicated and cached where safe.

Server-side key pool:

- multiple Agnes keys/accounts may be configured;
- keys are selected server-side only;
- each key has its own rolling RPM accounting;
- normal scheduling target <=12 RPM per key;
- routing threshold <=15 RPM per key;
- if all keys are busy, queue rather than burst;
- never expose provider/model/key identity to the client.

For IEEPA V1, Agnes should normally be unnecessary. Use it only for ambiguous document interpretation or summarizing a complex broker/CBP notice, ideally one structured call per qualified case.

## Revised opportunity ranking

1. IEEPA / customs-refund screener + referral: temporary but very high-intent node; immediate validation priority.
2. China Deal Inspector: strategic long-term information-gap opportunity, but direct competition has appeared; needs narrower differentiation around true landed-cost / transaction decision / supplier-history data.
3. FBA second-opinion audit: real demand but fast commoditization; keep as content/referral experiment, not primary build yet.
4. Chargeback evidence cockpit: proven demand but 2026 Shopify App Store is filling rapidly with near-identical AI evidence products; downgrade unless a new niche or distribution advantage is found.

## Next validation before code

For IEEPA:

- verify 10-20 current high-intent queries and current SERP competition;
- select 2-3 real referral/recovery partners whose terms permit the proposed traffic source;
- define deterministic eligibility/routing rules from CBP sources;
- Grok-build one checker plus 5-10 high-intent supporting tool pages;
- instrument: landing -> checker start -> completed screen -> qualified -> partner click -> partner conversion where tracking is available;
- only after conversion evidence, build a broader dashboard or importer-history product.
