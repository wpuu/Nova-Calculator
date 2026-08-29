# Nova Commercial Identity / Monetization Audit V1

Date: 2026-08-30
Branch: `commercial/nova-ai-v1`
Status: implementation checklist

## Executive conclusion

The commercial branch still contains legacy Calculator++ commercial identity and infrastructure. Before public distribution, Nova must separate all app-store identity, advertising, analytics, billing, signing and version metadata from the inherited project.

The inherited Apache-2.0 copyright/license notices must remain where required; commercial identity separation does **not** mean removing third-party copyright/license attribution.

## 1. Package / version identity — P0

Current state in `app/build.gradle`:

- `applicationId "org.solovyev.android.calculator"`
- `namespace 'org.solovyev.android.calculator'`
- `versionCode 185`
- `versionName '2.3.27'`

Commercial action:

- choose a new, permanent Nova `applicationId` before Play publication;
- keep source/namespace migration as a separate refactor if necessary; application ID may be separated first;
- reset Nova commercial versioning to a new scheme (recommended first commercial series: `1.0.0` with a fresh monotonically increasing versionCode);
- never publish Nova under the original Calculator++ Play package.

Do not finalize a permanent package ID until availability/brand choice is checked.

## 2. AdMob identity — P0

Current Manifest contains a real legacy AdMob application ID and `res/values/admob.xml` contains a real legacy banner ad-unit ID.

Current ad implementation:

- legacy custom `AdView` wrapper;
- `AdSize.SMART_BANNER`;
- automatic banner load when legacy `ad_free` entitlement is not found;
- ad entitlement is tied to the old billing implementation.

Commercial action:

1. Do not send any Nova test/commercial traffic to the legacy AdMob IDs.
2. Before Nova-owned AdMob credentials exist, commercial development builds should use Google test IDs or have ads disabled by a build-time flag.
3. Replace `SMART_BANNER` with current adaptive banner behavior if banner ads are retained.
4. Prefer no persistent banner on the primary calculator screen.
5. Consider rewarded ads as the primary Free-tier AI upsell: one voluntary rewarded ad -> one extra AI request, capped per day.
6. Pro Lifetime and AI Plus should remove ads.
7. Separate CN and Global ad implementations; do not force Google Mobile Ads into the China flavor if it is not useful there.

## 3. Firebase / analytics identity — P0

Current `app/google-services.json` belongs to legacy project:

- project id: `calculatorpp-86d8a`
- Android package: `org.solovyev.android.calculator`

Current `Ga.java` initializes Firebase Analytics and logs calculator button text, theme/layout selections and floating-calculator opens.

Commercial action:

- remove the legacy `google-services.json` from the commercial product line before distribution;
- create a Nova-owned Firebase project later and inject its config through controlled build/release setup;
- until Nova Firebase exists, analytics/crash reporting may be disabled rather than silently using the inherited project;
- redesign event schema around product decisions instead of logging every raw calculator button press.

Recommended V1 analytics events:

- `first_calculation_completed`
- `ai_explain_opened`
- `ai_request_success` / `ai_request_failure`
- `ai_verification_class` (A/B/C/D only, not raw question text)
- `ai_quota_paywall_shown`
- `pro_purchase_started/success`
- `ai_plus_purchase_started/success`
- `autotap_enabled`
- `autotap_first_success`
- `underwater_camera_opened`

Avoid collecting raw expressions/questions unless explicitly needed and disclosed; aggregate metadata is enough for most product analytics.

## 4. Billing — P0

Current billing stack:

- dependency `org.solovyev.android:checkout:1.3.2`;
- old `PurchaseDialogActivity`;
- only product id `ad_free`;
- legacy public billing key embedded through `CalculatorSecurity.getPK()`;
- `AdUi` queries the old `ad_free` purchase to decide whether banners appear.

Commercial action:

- remove legacy billing public key and legacy Checkout integration from Nova commercial architecture;
- implement a Nova-owned entitlement layer independent of UI and independent of a specific store;
- Global flavor: current supported Google Play Billing Library;
- CN flavor: separate store/payment adapters as needed;
- entitlements should be product concepts, not billing-SKU checks scattered in UI code.

Recommended entitlements:

- `FREE`
- `PRO_LIFETIME`
- `AI_PLUS`

Recommended capability queries:

- `adsEnabled`
- `dailyAiQuota`
- `aiPriority`
- `advancedAutoTap`
- `unlimitedLocalTemplates`

Do not hard-wire ad visibility directly to a SKU such as `ad_free`.

## 5. Branding / strings — P0/P1

Current resources still contain mixed legacy identity:

- launcher name currently Chinese `高级计算器`;
- floating calculator text still says `Calculator++ (Window mode)`;
- first-run copy says `Thank you for choosing Calculator++!`;
- share link is `https://example.com`;
- old purchase copy describes "supporting the project" and redirects to old Google purchase flow;
- mixed Chinese/English product strings.

Commercial action:

- define Nova product naming once and centralize brand strings;
- remove Calculator++ user-facing branding from Nova-owned screens while preserving required open-source attribution in About/Licenses;
- replace example.com with a disabled/owned Nova destination only when it exists;
- rewrite onboarding, purchase, privacy and feature disclosure text;
- keep localization files structurally compatible but make English and Simplified Chinese the first fully audited locales.

## 6. Release/build metadata — P0/P1

Current project still includes legacy build assumptions and old comments, including historical Maven upload metadata in a commented block.

Commercial action:

- use a Nova release signing configuration outside Git history;
- add release CI gates;
- build AAB + mapping + checksums;
- make release lint fail on critical errors instead of `abortOnError false` forever;
- maintain a deterministic dependency lock/audit path;
- upgrade target/compile SDK to current Play requirement before release;
- audit deprecated APIs and Android 15/16 behavior.

## 7. Advertising product strategy

Do not treat inherited banner advertising as the business model.

Recommended monetization order:

1. **AI Plus subscription** — main recurring paid product.
2. **Pro Lifetime** — local features, no ads, modest recurring AI convenience allowance.
3. **Rewarded AI unlocks** — monetizes Free users while limiting scarce RPM capacity.
4. Optional non-intrusive adaptive banner/native placements in secondary screens only after retention is measured.

Avoid interstitial ads during calculation flow. The calculator must feel faster and cleaner than competitors.

## 8. Required implementation sequence

### Commercial Safety Cleanup

- remove calculator secret photo/video/audio paths;
- keep explicit Underwater Camera;
- remove stealth/evidence wording and hidden camera initialization.

### Commercial Identity Cleanup

- neutralize legacy AdMob IDs;
- neutralize legacy Firebase project;
- remove legacy billing public key/Checkout coupling;
- define Nova app ID/version/brand resources;
- replace old purchase and first-run strings.

### Monetization Foundation

- `EntitlementManager` interface;
- `AiQuotaManager` / gateway entitlement response;
- Free / Pro Lifetime / AI Plus capabilities;
- rewarded-ad integration behind an `AdProvider` abstraction;
- Google billing implementation for Global flavor only.

### Release Quality

- current Android target;
- release signing;
- CI AAB build;
- lint/test gates;
- privacy/Data Safety review;
- Play pre-review checklist.

## 9. Release blockers

Nova commercial release is blocked if any of the following remains:

- original Calculator++ applicationId used for Play submission;
- legacy AdMob IDs receive traffic;
- legacy Firebase project receives Nova telemetry;
- legacy billing public key/product ownership controls Nova purchases;
- Calculator++ branding appears as Nova product identity outside required attribution;
- secret photo/audio/video recording is compiled into the commercial build;
- no server-side AI quota/rate limiting;
- AI is marketed as guaranteed advanced-math correctness without deterministic verification.
