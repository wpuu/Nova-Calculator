# Nova Demand-Led Roadmap V3

Date: 2026-09-02
Status: strategy baseline; do not implement speculative features before validation
Primary market: overseas Android / Google Play
AI stance: Agnes is an internal research/production weapon by default, not an autonomous Accessibility executor.

## 1. Strategy reversal

Nova will no longer follow `existing feature -> find users` as its default product process.

Use:

`public demand signals -> cluster -> score -> identify timing node -> build minimum feature -> build acquisition asset -> validate -> expand`

AutoTap is currently the strongest validated acquisition wedge. Calculator/AI remains a second acquisition surface and cross-sell, not the reason to keep adding unrelated calculator features.

## 2. Current market signals (2026-09-02)

Evidence observed in current Google Play / public communities:

- True Developers Studio Auto Clicker: 100M+ downloads, ~826K reviews. Core expectations are multi-point taps, swipes, timer and import/export.
- Several competing AutoTap apps have 10M+ downloads; the category is unquestionably validated.
- Recent reviews repeatedly complain about excessive ads, opaque/expensive subscriptions, unreliable stopping, taps not registering, inaccessible saved layouts, and privacy concerns around Accessibility permission.
- A July 2026 review of a 10M+ competitor explicitly said they would happily pay a one-time USD 5-7 after several days of successful use.
- Public 2026 requests show a concrete unmet need for independent repeat intervals per node rather than one long chained timeline.
- New 2026 indie products are increasingly moving from blind timers to image/color/text conditions. This is an emerging demand, but Nova should keep it human-defined and deterministic.
- Long-running AFK use creates heat, runaway-click and recovery problems. Safety and session control are product demand, not merely engineering details.

## 3. Feature priority by demand

### P0 — ship-quality trust and reliability

- Reliable emergency stop and no runaway click state.
- Full-screen / landscape / display-mode recovery across OEMs.
- Saved profiles that restore correctly.
- Clear permission disclosure and no screen-content reading for basic AutoTap.
- Transparent one-time Pro offer; avoid aggressive ad/paywall patterns.

### P1 — proven expected capability

Only implement where current branch is missing the behavior:

- multi-point taps;
- long press with configurable duration;
- swipes;
- per-action delay / interval;
- repeat counts and stop timer;
- import/export / backup;
- Quick Settings / fast launch where policy-safe.

These are category table-stakes, not differentiation.

### P1.5 — high-value current gaps

- Independent clocks: each node can repeat on its own interval (e.g. 39s / 90s / 773s) without constructing a giant chain.
- Safety binding: optionally bind a profile to a chosen foreground app and pause/stop when the user leaves that app, using the narrowest feasible Android signal and no content capture.
- Maximum session duration and explicit cool-down / battery-temperature safety stop.
- Local diagnostics and OEM recovery guide rather than generic “restart phone” support.

### P2 — emerging 2026 demand

Human-defined visual triggers:

`user selects image/color condition -> user selects static action -> Nova waits -> deterministic action`

Start with local image/color matching. Do not launch with AI-generated autonomous screen control. OCR and branching come only after policy and retention evidence.

### P3 — moat instead of feature count

- Device/ROM compatibility knowledge base.
- Versioned automation templates.
- Template compatibility metadata by device/OS/app version.
- Self-help recovery rules generated from known device/OS failure patterns.

## 4. Timing arbitrage

### Android 17

Android 17 public developer documentation is already live. Build a compatibility test matrix and acquisition pages before mass adoption creates search spikes such as “auto clicker not working Android 17”.

### Android developer verification

On 2026-09-30 developer-verification protections begin in Brazil, Indonesia, Singapore and Thailand for participating stores; 2027 expands globally to certified Android devices. Verified, policy-clean distribution becomes an acquisition/trust advantage against shady sideload-only clickers.

Initial timing-market priority: Brazil + Indonesia, then Thailand; localize store assets and troubleshooting pages before the September 30 milestone.

### Play AI policy

July 2026 policy clarification makes app developers responsible for third-party AI integrations and their data handling. Keeping Agnes outside the end-user AutoTap execution path reduces product-policy complexity while preserving our internal AI advantage.

## 5. China -> overseas information arbitrage

China is not the first customer market for this roadmap. It is an early-warning and supply-side information source.

Use Chinese public sources to discover:

- Xiaomi/HyperOS, Huawei, OPPO, vivo, Honor ROM-specific Accessibility failures and recovery patterns;
- recurring automation pain from mobile-game and utility communities;
- new Chinese mobile titles / mechanics that may later expand globally;
- automation UI patterns and feature expectations appearing in domestic utility apps before foreign communities describe them clearly.

Agnes clusters Chinese and English signals into device / problem / requested outcome / willingness-to-pay / policy-risk records. The user never needs to know or interact with Agnes.

The commercializable information gap is not “translate a Chinese article.” It is converting fragmented knowledge into a Nova compatibility rule, verified template, troubleshooting page and product behavior.

## 6. Grok 4.6 High page factory

Grok is used as a free one-shot page factory, not the source of truth.

Pipeline:

1. Demand radar finds a repeated/high-intent problem.
2. Agnes deduplicates, classifies and produces a structured brief.
3. Product rules decide whether the problem is legal/policy-safe and worth targeting.
4. Grok generates a complete landing page from one of a small set of fixed templates.
5. Agnes checks the generated copy against the structured facts and flags unsupported claims.
6. Publish static pages and route each CTA to the closest Play Custom Store Listing.
7. Expand only pages that receive impressions/clicks/installs.

Initial page families:

- android-17-auto-clicker
- auto-clicker-not-working-after-update
- samsung-oneui-auto-clicker-accessibility
- hyperos-auto-clicker-accessibility
- auto-clicker-independent-timers
- safe-auto-clicker-no-screen-reading
- auto-clicker-one-time-purchase
- volume-key-auto-clicker
- landscape-fullscreen-auto-clicker
- auto-clicker-overheating-long-session

## 7. A/B intermediary models that are harder to bypass

### Compatibility Network

A = users whose clicker fails on a device/OS combination.
B = fragmented OEM/community knowledge and test feedback.
Nova = detection, verified recovery logic, compatibility profile and continuous updates.

The product sells execution and maintenance, not a one-time piece of information.

### Versioned Template Network

A = users wanting a repeatable outcome without building a macro.
B = template authors / internally produced safe templates / community-tested setups.
Nova = template schema, validation, compatibility checks, version updates and execution.

Raw coordinates are easy to copy; a maintained template with conditions, device scaling and version compatibility is harder to replace.

### Problem-search arbitrage

A = users searching for a concrete failure or repetitive task.
B = fragmented forum/review/support answers.
Nova = useful page + verified fix + app execution.

This borrows traffic from search engines and Play rather than buying traffic.

## 8. Gray / legal-edge opportunity boundaries

Pursue cautiously:

- idle/incremental game repetitive grinding where the feature remains generic and user-controlled;
- repetitive UI work, QA, demos, internal operations and accessibility-related strain reduction;
- game-specific compatibility content only when it does not promise cheating, anti-ban or policy bypass.

Do not productize:

- anti-detection / anti-ban humanization;
- ad clicking or reward-app farming;
- account creation/spam/auto-like/auto-follow systems;
- ticket/drop抢购 automation or automated bidding;
- captcha bypass, credential entry, payment confirmation or financial transaction automation;
- autonomous AI agents that decide where/when to click without a human-defined static rule.

These have materially worse Play, fraud, platform-ToS or legal risk than their likely incremental revenue.

## 9. Product allocation until evidence changes

- 70% AutoTap reliability + high-intent demanded features.
- 20% acquisition/demand engine + compatibility intelligence.
- 10% calculator/AI maintenance and second-surface experiments.

Do not delete calculator AI. Do not let it consume the roadmap until its acquisition/retention data beats AutoTap.

## 10. Demand score before implementation

Every proposed feature receives a 0-5 score for:

- observed frequency;
- recency / upcoming timing node;
- user pain;
- payment evidence;
- search/acquisition intent;
- implementation cost (reverse scored);
- Play/policy/legal risk (reverse scored);
- support burden (reverse scored);
- defensibility / non-bypass value;
- benefit from our free Agnes/Grok resources.

A feature should normally require a strong total score and at least two independent public demand signals before native implementation.

## 11. Current recommended next features

Before broad release, validate current P0 physical-device stability.

Then, demand order is:

1. independent per-node timers;
2. long-press duration / swipes / multi-point only where the shipping branch lacks them;
3. max-session + safe stop / thermal guard;
4. device/OEM self-diagnostic and compatibility profiles;
5. local human-defined image/color triggers;
6. template/versioning layer;
7. template marketplace only after repeat usage proves users want to reuse/share setups.

No autonomous Agnes control of Accessibility is on this roadmap.
