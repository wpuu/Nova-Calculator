# Nova Commercial Product Plan V1

Date: 2026-08-29
Status: planning baseline for `commercial/nova-ai-v1`

## 1. Branch model

- `self-use/full-feature-v1`: frozen full-feature personal build baseline, created from commit `dc8334d7f352e52c0825db65c35392c49c0942d8`.
- `commercial/nova-ai-v1`: commercial product line, initially forked from the same baseline.
- Existing `main` and PR #1 are not merged or rewritten by this plan.

The self-use branch may retain the existing secret-code photo/video/audio/evidence features. The commercial branch must not ship those hidden/stealth capture features.

## 2. Planning assumption for AI

For product planning, treat `agnes-2.5-flash` inference as permanently free. If this assumption changes in the future, replace the provider or revise the economics then.

Even under the free-inference assumption:

- never embed provider API keys in the Android APK;
- call AI through a Nova server-side gateway;
- keep an `AiProvider` abstraction so Agnes can be replaced without changing the Android product;
- enforce per-user rate limits to prevent abuse and protect service reliability.

## 3. Product positioning

Primary product: **Nova Calculator AI**

Positioning:

> A fast daily/scientific calculator with an AI math assistant built directly around the calculation workflow.

Do not position Nova as another broad homework platform competing head-on with Photomath, Gauth, Mathway or Symbolab.

Core user promise:

1. calculate normally without AI;
2. when a calculation is confusing, tap AI and ask what it means, why it works, or how to set it up;
3. use natural language for calculations that are annoying to translate into formulas;
4. retain exact local calculation as the source of numerical truth whenever possible.

Secondary tools retained in the commercial product:

- AutoTap / auto-clicker, visibly disclosed and user-controlled;
- Underwater Camera, visibly disclosed as a separate explicit tool.

The commercial product must not describe itself as a disguised/stealth calculator.

## 4. Market conclusion

Current market evidence shows two large validated demand pools:

### Calculator demand

- ClevCalc: 100M+ Google Play downloads.
- Calculator Plus with History: 50M+ downloads.
- HiPER Scientific Calculator: 10M+ Google Play downloads and claims 50M+ global downloads.
- Original Calculator++: 1M+ Google Play downloads.

### Math-assistance demand

- Photomath: 100M+ Google Play downloads, 3M+ reviews.
- Gauth: 100M+ downloads, ~2M reviews.
- Mathway: 50M+ downloads.
- Symbolab: 10M+ downloads.

Conclusion: demand does not need to be invented. Nova should enter through the calculator utility market and use AI to raise retention and monetization, rather than attempting to recreate a full education ecosystem.

## 5. User needs to prioritize

### Need A — explain the current calculation

User has already entered an expression and received a result but does not understand it.

UX:

`result -> Explain with AI`

AI should explain:

- what the expression means;
- why the result is correct;
- step-by-step transformation;
- common mistakes;
- an optional simpler explanation.

This is the highest-priority AI feature because it fits the existing calculator workflow with almost zero friction.

### Need B — natural-language calculation

Examples:

- "8536 with a 15% discount, then add 13% tax"
- "split 186.40 between 5 people and add a 20% tip"
- "250000 loan at 3.4% for 5 years, monthly payment"
- "convert 17.5 miles per gallon to L/100km"

Preferred architecture:

`user language -> Agnes intent/formula extraction -> structured expression -> local calculator/verified computation -> Agnes explanation`

Do not rely on the LLM alone for arithmetic when the local engine can calculate it exactly.

### Need C — math word problems

AI converts the question into variables/formulas, explains assumptions, then uses the exact engine where possible.

Initial scope:

- arithmetic and percentages;
- algebra;
- ratios;
- finance-style everyday math;
- unit conversions;
- basic statistics;
- supported scientific functions.

Do not try to become a full K-12 curriculum platform in V1.

### Need D — error explanation

When the parser reports invalid syntax or an impossible operation, offer:

`Why is this wrong?`

AI explains the error and suggests a corrected expression without silently changing the user's calculation.

### Need E — reusable smart formulas

Let users save a calculation as a named template, for example:

- VAT / GST
- discount + tax
- margin / markup
- compound interest
- loan payment
- tip split
- fuel consumption
- currency/unit calculation

Later, AI can generate template parameters from a natural-language request.

## 6. AI features deliberately deferred

Do not make these V1 requirements:

- general-purpose ChatGPT-style open chat;
- full school subject tutoring;
- live camera homework solving;
- autonomous AI control of Accessibility;
- AI reading the screen and deciding where to click;
- AI-generated arbitrary automation that executes without explicit user confirmation;
- social/community features;
- teacher marketplace;
- large question banks.

Image math can later use Android Photo Picker first, avoiding a new camera permission in the calculator flow.

## 7. Accuracy architecture

Nova's differentiation should be **AI explanation + deterministic calculation**, not "LLM gives an answer".

Recommended flow:

1. User enters expression or natural-language problem.
2. Agnes extracts intent, variables and candidate expression in structured JSON.
3. Nova validates the structure.
4. Local JSCL/calculator engine computes exact supported math.
5. Nova returns exact result to Agnes as context.
6. Agnes explains the verified result.
7. UI clearly distinguishes exact calculator output from AI-generated explanation.

For unsupported symbolic/proof problems, show that the answer is AI-generated and allow the user to retry/check.

## 8. Commercial feature split

### Free

- basic + scientific calculator;
- history;
- conversions already present in the product;
- limited saved formulas;
- small daily AI allowance;
- basic AutoTap;
- Underwater Camera basic mode;
- ads may be used, but keep them unobtrusive.

### Pro Lifetime

- no ads;
- unlimited local saved formulas/templates;
- advanced calculator themes;
- advanced AutoTap local features;
- import/export local configurations;
- additional local convenience features.

### AI Plus

Assuming Agnes inference remains free, price AI for product value rather than inference cost.

Candidate benefits:

- much higher daily AI allowance;
- detailed step-by-step explanations;
- follow-up questions about the current calculation;
- natural-language calculation;
- formula builder;
- error explanation;
- advanced word-problem mode;
- future image input through explicit Photo Picker.

Never expose a truly unbounded endpoint. Use generous fair-use quotas to prevent automated abuse.

## 9. Commercial privacy/compliance split

### Remove from commercial branch

- calculator secret-code photo trigger;
- calculator secret-code video start/stop;
- calculator secret-code audio start/stop;
- hidden CameraX preview in normal CalculatorActivity;
- hidden recording indicators/state;
- AudioRecorderManager used for stealth recording;
- VideoRecorderManager paths used for stealth recording;
- evidence/vault export UI and terminology;
- secret setup UI for capture codes;
- stealth/hidden/disguised wording;
- camera/microphone permission requests from the calculator/settings startup flow.

### Retain, but make explicit

`UnderwaterCameraActivity` may remain as a visible tool because its user intent is explicit.

Requirements:

- visible entry named Underwater Camera;
- explain why camera permission is needed before requesting it;
- request microphone only when the user enables/starts video with audio;
- never initialize its camera while the user is only using the calculator;
- keep photo/video storage visible and understandable;
- no hidden background recording;
- no secret calculator-key trigger.

### AutoTap

- keep Accessibility use deterministic and user-defined;
- no AI autonomous click planning;
- disclose Accessibility purpose clearly;
- request/enable only when the user chooses AutoTap.

## 10. Navigation proposal

Keep the calculator as the default home.

Suggested top-level product navigation:

1. Calculator
2. AI Math
3. AutoTap
4. Tools

`Tools` can contain:

- Underwater Camera
- unit/conversion utilities
- future small calculators

Do not open the app into a generic AI chat screen.

## 11. Acquisition strategy — global

### A. Google Play organic search first

Main keyword families:

- calculator
- scientific calculator
- AI calculator
- math solver
- step by step math
- percentage calculator
- equation solver

Google Play supports custom store listings targeted by search keywords. Use this to test different screenshots/names/descriptions for users arriving from different search intents while keeping one APK/product.

Recommended listing strategy:

- default listing: `Nova Calculator AI` — fast calculator + AI explanation;
- AI-calculator keyword listing: emphasize natural-language math and step explanations;
- scientific-calculator keyword listing: emphasize exact scientific engine, graph/history/features;
- AutoTap keyword listing only after its commercial UX and policy disclosure are mature.

Do not market Underwater Camera as the main reason to install the calculator app.

### B. Store listing A/B experiments

Continuously test one variable at a time:

- icon;
- first screenshot;
- short description;
- "AI explains your calculation" vs "Ask math in plain language".

Primary conversion metric: unique install clicks / opens.

### C. Programmatic web SEO

Build lightweight public calculator pages that answer long-tail searches and deep-link into Nova.

Examples:

- percentage increase calculator
- discount then tax calculator
- tip split calculator
- margin vs markup calculator
- compound interest calculator
- loan payment calculator
- fraction calculator
- unit converters

Each page should give useful free output on the web and offer:

`Continue / explain this calculation in Nova`

This channel is highly automatable and compounds over time.

### D. Shareable AI answer cards

Let users share a clean result/explanation card with a small Nova attribution and store/deep link. This creates product-led acquisition without requiring a social network.

### E. Short-form content

Use repeatable templates rather than manual creator work:

- "Can your calculator understand this sentence?"
- percentage/tip/tax tricks;
- common calculator mistakes;
- before/after AI explanation;
- useful formula of the day.

Prioritize YouTube Shorts/TikTok/Instagram Reels globally. Avoid making paid ads the primary validation channel.

### F. Localization

After English is stable, prioritize languages where Android usage is large and AI math explanations can add value. Candidate order for testing:

- Spanish;
- Portuguese;
- Arabic;
- Indonesian;
- Hindi.

Localize AI explanations and store screenshots, not just the menu strings.

## 12. Acquisition strategy — China

Do not compete head-on with 作业帮/小猿AI/夸克 as a full homework platform. They already have massive question banks, school resources and parent ecosystems.

Position China version as:

> 一个好用的计算器，需要时顺手问 AI 为什么这么算。

Channels:

- major Android app stores after commercial compliance is complete;
- store search around 计算器 / 科学计算器 / AI计算器;
- Douyin/Bilibili/Xiaohongshu short demonstrations using repeatable templates;
- Baidu/search-oriented web calculator pages;
- shareable calculation cards;
- later, cross-promotion from other Nova utility products if they exist.

Because the commercial build includes online AI service, complete required China app/online-service compliance before public mainland distribution.

## 13. Pricing hypotheses to test

Do not hard-code final pricing in product architecture.

Global initial test range:

- Pro Lifetime: USD 5.99–9.99
- AI Plus monthly: USD 1.99–3.99
- AI Plus annual: USD 14.99–24.99

China initial test range:

- Pro Lifetime: CNY 18.8–38.8
- AI Plus monthly: CNY 6.9–12.9
- AI Plus annual: CNY 39–68

Because Agnes is assumed free, the goal is not to maximize AI ARPU immediately. The first goal is to discover whether integrated AI meaningfully increases retention and purchase conversion versus the calculator alone.

## 14. MVP success metrics

Measure separately:

- install -> first calculation completion;
- users who tap AI after a normal calculation;
- AI query success rate;
- AI follow-up rate;
- day-1/day-7 retention;
- calculator-only vs AI-user retention;
- Free -> Pro conversion;
- Free -> AI Plus conversion;
- AutoTap activation rate;
- crash/ANR rate;
- Accessibility failure rate by OEM/Android version;
- support contacts per 1,000 active users.

A key operating metric for this product should be support burden: the commercial design should prefer features that can be diagnosed automatically and do not require device-specific manual support.

## 15. Development phases

### Phase 0 — freeze and separate

Done:

- create `self-use/full-feature-v1`;
- create `commercial/nova-ai-v1` from it.

### Phase 1 — commercial safety cleanup

- physically remove secret photo/video/audio code from commercial build;
- keep explicit Underwater Camera only;
- remove camera initialization from calculator startup;
- clean permissions and settings language;
- preserve AutoTap fixes;
- produce a clean commercial debug APK for regression testing.

### Phase 2 — calculator product cleanup

- new package/application ID and commercial branding;
- modern release signing path;
- current Android target API;
- modern billing abstraction;
- analytics/crash reporting owned by Nova;
- clean privacy/data-safety declarations.

### Phase 3 — AI MVP

Implement only:

1. Explain current result;
2. Natural-language calculation;
3. Follow-up about current calculation;
4. Error explanation;
5. Verified exact-result pipeline.

### Phase 4 — monetization

- Free / Pro Lifetime / AI Plus entitlement model;
- remote-configurable quotas and prices;
- Play billing for global build;
- China channel/payment strategy separately.

### Phase 5 — acquisition engine

- Play listing variants;
- store experiments;
- programmatic calculator landing pages;
- share cards;
- automated short-form content templates;
- localization.

## 16. V1 non-goals

Do not add before evidence requires them:

- full homework database;
- live human tutors;
- AI video tutor;
- classroom features;
- cloud-sync-heavy collaboration;
- autonomous screen agents;
- complex macro recorder;
- broad social features;
- hidden capture functionality in commercial builds.

This document is the product baseline. Changes should be deliberate and justified by user demand, store compliance, measurable retention/monetization benefit, or support-cost reduction.
