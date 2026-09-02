# Nova — Google Play Economics + China Go-To-Market V2

Date: 2026-09-02
Status: operating plan for PR #6 / `fix/commercial-autotap-market-fit-v2`

## 1. Decision

Nova should pay the Google Play developer registration fee and proceed to controlled testing, but should **not** spend meaningful money on paid acquisition before physical-device acceptance and the organic funnel are proven.

The expected financial downside of the Play registration itself is small. The real risk is developer time and policy/account failure, so launch must use explicit stop/go gates.

## 2. Google Play current economics

Current policy baseline as of 2026-09-02:

- Play Console developer registration: USD 25 one-time.
- New personal developer accounts must satisfy the closed-test production-access requirement: at least 12 testers opted in continuously for the preceding 14 days before applying for production access.
- New personal developer accounts must also complete identity/contact and real Android-device verification.
- China is supported for Google Play developer and merchant registration, but mainland-China user acquisition should not depend on Google Play availability.
- For digital goods/services, Play-distributed apps generally use Google Play Billing unless a specific regional exception/program applies.
- For the first USD 1M of annual earnings, a practical launch-model assumption is roughly 15% platform/billing cost. In the US/EEA/UK from 2026-06-30 the new first-USD-1M schedule is 10% service fee + 5% Play Billing fee; in markets where the new schedule has not yet rolled out, the existing 15% tier remains the useful planning baseline.

Official references:

- https://support.google.com/googleplay/android-developer/answer/6112435
- https://support.google.com/googleplay/android-developer/answer/14151465
- https://support.google.com/googleplay/android-developer/answer/14316361
- https://support.google.com/googleplay/android-developer/answer/112622
- https://support.google.com/googleplay/android-developer/answer/9858738
- https://support.google.com/googleplay/android-developer/answer/9306917

## 3. Recovering the USD 25 registration fee

Using an 85% developer net before tax/FX and ignoring refunds:

| Product | Example price | Approx. developer net | Sales to recover USD 25 |
|---|---:|---:|---:|
| Pro Lifetime | USD 5.99 | USD 5.09 | 5 |
| Pro Lifetime | USD 6.99 | USD 5.94 | 5 |
| Pro Lifetime | USD 9.99 | USD 8.49 | 3 |
| AI Plus monthly | USD 2.99 | USD 2.54 first month | 10 first-month payments |
| AI Plus annual | USD 19.99 | USD 16.99 | 2 |

Therefore the registration fee itself is not a meaningful commercial risk. If Nova cannot produce roughly 3–10 paid transactions over its lifetime, the project has a distribution/product-market problem rather than a Play-fee problem.

## 4. Launch risk model

### Low financial risk

- USD 25 one-time Play registration.
- Existing repository, CI, Gateway/Billing architecture and free/low-cost infrastructure mean there is no need for a large fixed launch budget.

### Medium account/policy risk

- AccessibilityService declaration and review.
- Data Safety/privacy mismatch.
- Play Integrity or Billing production-identity mismatch.
- Identity/contact/device verification.
- Closed-test production-access rejection if testing is superficial.

Mitigation: do real testing, preserve the deterministic/user-controlled AutoTap design, and never buy fake reviews/install/tester activity.

### High market risk

- App is published but gets almost no impressions.
- Search visitors install but do not finish Accessibility setup.
- Users succeed once but do not return.
- Users return but do not value the Pro feature split.

This is the risk PR #6's product funnel is intended to measure.

## 5. Spend gates

### Gate A — before paying for traffic

Must pass:

- physical-device landscape/full-screen AutoTap P0 matrix;
- Play-distributed package identity test;
- first overlay success on representative OEMs;
- Volume Up start / Volume Down immediate stop;
- no touch interception while running;
- Billing test purchase/restore and server verification;
- privacy/Accessibility declarations match shipping behavior.

### Gate B — before more feature work

Need evidence from organic/test traffic that:

- users can reach first successful AutoTap run;
- a meaningful subset returns for a second session;
- saved profiles are actually used;
- paywall is reached after value is demonstrated, not before.

If first-run success is poor, fix reliability/onboarding. If repeat use is poor, do not add more monetization. If repeat use is strong but purchases are weak, test packaging/price.

### Gate C — paid acquisition

Do not buy broad ads first. Only test paid traffic after an organic keyword/listing or external content source already shows conversion.

Initial paid acquisition budget cap: small experiment only; no scaling until verified purchase CAC is below expected net lifetime value.

## 6. Mainland China strategy: treat it as a separate distribution product

Google Play can be used by a China-based developer, but it should not be the primary channel for mainland Android users. China distribution should be treated as a separate flavor/launch track with its own compliance, store metadata and payment layer.

### Current mainland store friction

Mainland app distribution now requires APP filing for public Internet information services. Major Android stores also request copyright/ownership evidence.

Examples:

- Huawei's current publishing checklist requires filing information for mainland releases and lists electronic/app copyright certificates among preparation items.
- Xiaomi states that all apps submitted to its store need an accepted software/electronic copyright certificate and APP filing.

Official references:

- https://developer.huawei.com/consumer/cn/doc/app/agc-help-releasebundle-0000001100316672
- https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2251
- https://beian.miit.gov.cn/

This means the mainland launch has more administrative friction than the USD 25 Google Play launch.

## 7. Mainland AI compliance changes the product decision

Do **not** simply expose the current overseas Agnes-backed AI Gateway to mainland public users and assume that hiding the upstream model is compatible with local launch requirements.

China's `生成式人工智能服务管理暂行办法` applies to generative-AI services provided to the public in mainland China. Current CAC notices also state that apps/features directly calling already-filed model capabilities may complete local registration, and launched generative-AI apps/features should display the filed/registered service information, including model/service identification and filing/launch number where applicable.

References:

- https://www.cac.gov.cn/2023-07/13/c_1690898327029107.htm
- https://www.cac.gov.cn/2026-05/13/c_1780413225190669.htm

Therefore Nova should use one of two China paths:

### China Path A — recommended first

`Calculator + AutoTap + local tools`, with public AI disabled in the mainland flavor until a compliant model/service route is selected.

Advantages:

- fastest compliance path;
- preserves the strongest domestic acquisition keyword: `连点器 / 自动点击器`;
- avoids making China launch depend on Agnes/model disclosure and registration.

### China Path B — later

Use an already-filed mainland model/API and complete the application/function registration requirements applicable to Nova. Treat provider/model disclosure rules as authoritative for the mainland flavor; do not promise hidden upstream identity if law/policy requires disclosure.

## 8. China product positioning

Do not lead with `AI计算器` initially. Domestic competition for generic AI/homework math is strong, while current store/search evidence confirms ongoing demand for `自动点击器 / 连点器`.

Recommended China working identity:

`Nova 连点器 + 科学计算器`

Primary promise:

> 两点连点、横屏稳定、音量键一键停、无需 Root、不读取屏幕内容。

Secondary promise:

> 自带科学计算器和常用工具。

Do not market hidden capture, unattended fraud, order-snatching, review/like manipulation or anti-cheat bypass.

## 9. Domestic demand evidence and differentiation

Current Chinese Android-store/search results show active products under keywords such as:

- 自动点击器
- 连点器
- 自动连点器
- 无需 Root 连点器
- 多点点击
- 横屏/全屏点击

Competitors commonly advertise multiple points, swipe/recording and scripts. Some current listings also openly acknowledge Accessibility failures that sometimes require restarting the app, re-granting permission or even rebooting the device.

Nova should not initially win by feature count. It should win one narrow job:

**reliable, simple, repeatable two-point AutoTap with a hardware stop.**

Differentiators already aligned with PR #6:

- saved setup/profile;
- landscape/full-screen recovery;
- Volume Up start / Volume Down stop;
- running overlay does not consume target-app touches;
- no root;
- deterministic user-defined targets;
- Accessibility service does not read screen content;
- no autonomous AI click planning in V1.

## 10. China acquisition engine

### Channel 1 — Android app-store search

Priority order for testing should roughly follow mainland Android installed-base relevance, beginning with Huawei and then OPPO/vivo/Xiaomi/others as account/compliance work permits.

Store keyword families:

- 连点器
- 自动点击器
- 自动连点器
- 两点连点器
- 音量键连点器
- 无 Root 连点器
- 横屏连点器
- 全屏自动点击

Create different screenshots/description emphasis per store rather than copying one generic listing everywhere.

### Channel 2 — problem-intent SEO

Create useful pages before generic brand pages:

- 安卓15连点器失效怎么办
- 小米无障碍自动关闭怎么办
- 华为连点器不显示悬浮点
- 横屏游戏连点器只能点左半屏
- 连点器停止后还在点击怎么办
- 不读取屏幕内容的自动点击器
- 音量键停止连点器

Each page should solve the problem first and then show the exact Nova feature that addresses it. Do not impersonate competitor support pages.

### Channel 3 — short video templates

Prioritize Douyin and Bilibili for demonstration, then Xiaohongshu for searchable problem/solution posts.

Repeatable 10–20 second templates:

1. `普通连点器横屏只能点左边？`
2. show Nova target dragged to far-right full-screen coordinate;
3. Volume Up starts;
4. Volume Down stops immediately;
5. CTA: search/download Nova from the supported store.

Other templates:

- `不用 Root，两点自动点`
- `悬浮按钮挡操作？运行时不抢触摸`
- `怕停不下来？音量-硬停止`
- `保存一次，下次直接加载`

The goal is not followers. The KPI is store-search/install intent.

### Channel 4 — community/problem seeding

Participate only where users are already asking genuine setup/compatibility questions: device forums, Android communities, game/device troubleshooting threads. Answer the actual problem and mention Nova only when relevant. Avoid bulk unsolicited promotion.

## 11. China monetization

Do not start with a subscription-heavy China offer.

Recommended first commercial split:

### Free

- calculator;
- basic two-point AutoTap;
- one saved setup/profile;
- enough functionality to prove reliability before any paywall.

### Pro Lifetime

Candidate initial test: CNY 18.8 / 28.8 / 38.8.

Possible Pro value:

- more saved setups;
- advanced timing/local settings;
- import/export if later added;
- advanced convenience features.

Trigger the paywall only after demonstrated value, such as attempting to save a second setup or repeated successful AutoTap use.

### AI Plus

Do not make this a mainland launch dependency. Add later only after a compliant China AI provider/registration path is selected and there is evidence that AI increases retention or purchase conversion.

Payment architecture must be a separate China implementation. Do not reuse Google Play Billing assumptions across manufacturer stores; verify each store's current digital-goods/payment rules before integration.

## 12. 30-day execution order

1. Finish PR #6 physical-device P0 acceptance.
2. If no Play developer account exists, register and pay the USD 25 fee.
3. Complete identity/contact/device verification.
4. Create/confirm the production Play app and internal/closed testing track.
5. Recruit real testers and satisfy the 12-testers/14-days requirement if the account is subject to it.
6. Validate Play Integrity + Nova session + Billing + verified entitlement on a Play-distributed build.
7. Launch the first AutoTap keyword custom listing; do not buy traffic.
8. Publish the first 5–10 problem-intent landing pages and short videos.
9. Use the existing PR #6 product funnel to identify first-run, repeat-use and purchase bottlenecks.
10. In parallel, start China APP filing/copyright preparation if mainland distribution is still desired.
11. Build a mainland flavor with AI disabled by default unless/until the compliant AI route is approved.
12. Prepare Huawei/Xiaomi/OPPO/vivo store-specific listing assets around AutoTap reliability rather than generic AI.

## 13. Stop/go rules

### Continue aggressively if

- organic users reach first successful AutoTap;
- second-session usage exists;
- at least one search/problem-intent source brings repeat users;
- Pro purchases occur without paid traffic or at sustainable CAC.

### Reposition if

- AutoTap gets traffic but not repeat use: reliability/onboarding problem;
- calculator traffic installs but does not engage: do not spend on generic calculator keywords;
- AI users engage but AutoTap users do not: shift listing emphasis toward AI calculator;
- China store traffic is expensive to acquire but global Play converts organically: keep China secondary.

### Stop major investment if

After a meaningful controlled test there is neither organic search traction nor repeat use, and paid acquisition cannot be justified by expected net lifetime value. The correct response is not to add more unrelated features.
