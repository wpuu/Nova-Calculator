# Nova commercial release readiness — current

Date: 2026-08-31
Branch: `commercial/nova-ai-v1`
Status: **repository implementation is substantially complete; public release remains blocked on production/Play/policy setup and final validation.**

This file is the current operational checklist. `COMMERCIAL_IDENTITY_AUDIT_V1.md` is the 2026-08-30 historical audit that drove the cleanup and must not be treated as current source state.

## 1. Repository-side work already completed

The commercial branch now has these foundations in source and CI:

- production Android application id: `com.wpuu.novacalculator`;
- isolated debug application id: `com.wpuu.novacalculator.dev`;
- compile/target SDK 36;
- legacy commercial Firebase configuration and legacy AdMob resource removed from the commercial line;
- covert calculator-triggered photo/audio/video paths removed; visible Underwater Camera retained;
- deterministic user-directed AutoTap with prominent Accessibility disclosure/consent and `canRetrieveWindowContent=false`;
- calculator-first Nova AI V1 flows: contextual explanation, natural-language calculation, contextual follow-up, error explanation and formula builder;
- Android Standard Play Integrity proof provider plus server-side `decodeIntegrityToken` verification path;
- signed/time-limited Nova sessions and pseudonymous quota subjects;
- shared atomic Redis user quota/rate limiting and shared provider-key RPM/cooldown state;
- current Google Play Billing client and Nova server entitlement verification;
- launch billing ids frozen in source:
  - `nova_pro_lifetime`;
  - `nova_ai_plus` with `monthly` and `annual` base plans;
- public `/api/privacy`, in-app privacy entry and bundled Apache-2.0 LICENSE/NOTICE access;
- signed production AAB workflow with production identity/config/signing guards;
- production privacy-readiness gate that intentionally blocks AAB release until the public privacy contact is externally verified and the live policy contains no unresolved release markers;
- ordinary commercial CI builds both Debug APK and unsigned Release AAB preflight;
- release lint Error/Fatal findings are blocking in ordinary commercial CI; the 2026-08-31 baseline was reduced from 19 Error/Fatal findings to 0 before enabling the gate.

These completed items should not be reopened merely because the older V1 audit still describes their pre-cleanup state.

## 2. P0 external production blockers

These require real production accounts/configuration or human verification. They cannot be truthfully completed by source code alone.

### A. Production publisher and privacy identity

- [ ] Choose/confirm the public publisher/legal identity shown by `/api/privacy`.
- [ ] Verify actual control of the production privacy domain and monitored mailbox.
- [ ] Configure `NOVA_PRIVACY_PUBLISHER_NAME`, `NOVA_PRIVACY_CONTACT_EMAIL` and final effective date in production Gateway.
- [ ] Review the live HTTPS privacy page against the exact production processors and behavior.
- [ ] Only after real verification, set GitHub production variable `NOVA_PRIVACY_CONTACT_VERIFIED=true`.

Do not set the verification variable merely to make CI pass. It is an operator sign-off, not ownership proof.

### B. Production Nova Gateway

- [ ] Deploy `gateway/` to the chosen production Vercel project/domain.
- [ ] Configure server-only AI provider credentials/model routing.
- [ ] Configure shared Redis REST URL/token and production TTL/capacity policy.
- [ ] Configure independent Nova session signing and subject secrets.
- [ ] Configure Google service-account credentials for Play Integrity decode and Google Play billing verification.
- [ ] Confirm `/api/health` and `/api/privacy` are public as intended and expose no server secrets/provider identity.
- [ ] Confirm `/api/session`, `/api/ai` and `/api/billing` work on the production origin.

Never put provider, Redis, Nova signing or Google private credentials in the APK.

### C. Google Play application and Play Integrity

- [ ] Create/confirm the Google Play app using exactly `com.wpuu.novacalculator`.
- [ ] Link the production Play app to the intended Google Cloud project and Standard Play Integrity configuration.
- [ ] Configure the public Play Integrity Cloud project number used by the release build.
- [ ] Grant the server service account only the required Google Play/Integrity permissions.
- [ ] Build a signed production AAB through `Android Production AAB` and upload it to a controlled Play internal testing track.
- [ ] On a Play-distributed production-package build, verify the full chain:
  `Play Integrity -> /api/session -> server decode -> signed Nova session -> /api/ai`.

A GitHub Debug APK uses the `.dev` package and is not evidence that production Play Integrity identity is correct.

### D. Google Play Billing products

- [ ] Create one-time product `nova_pro_lifetime`.
- [ ] Create subscription `nova_ai_plus`.
- [ ] Create/activate `monthly` and `annual` base plans with intended regional prices/offers.
- [ ] Confirm production service-account permissions allow server purchase verification.
- [ ] Test purchase, acknowledgement/verification, restore, renewal, cancellation/expiry and reinstall flows with Play test accounts.
- [ ] Confirm client UI, server entitlement and Play Console product ids/base plans match exactly.

### E. Google Play policy declarations

- [ ] Re-check `PLAY_DATA_SAFETY_BASELINE.md` against the exact production Gateway, hosting logs, Redis retention and AI-provider retention configuration.
- [ ] Complete Play Console Data Safety answers to match actual production behavior and the live privacy policy.
- [ ] Complete the non-accessibility-tool AccessibilityService declaration for AutoTap.
- [ ] Record the required Accessibility demonstration video showing disclosure/consent, decline path and deterministic AutoTap operation.
- [ ] Ensure store listing clearly describes AutoTap/Accessibility usage and does not imply autonomous AI control.

### F. Production signing/release environment

- [ ] Create/confirm the Play upload key and protect the keystore outside Git history.
- [ ] Configure GitHub `production` environment signing secrets and public release variables.
- [ ] Prefer a required reviewer for the production environment where the repository plan supports it.
- [ ] Verify each Play upload uses a monotonically increasing `versionCode`.

## 3. Repository-side next quality work (P1)

These do not block continued external setup, but should be completed before broad public rollout:

- [x] Turn Android release lint into a real gate. `lintRelease` was audited on 2026-08-31, 19 Error/Fatal findings were resolved or narrowly isolated where the inherited AppCompat bridge intentionally depends on restricted internals, and ordinary commercial CI now blocks on release Error/Fatal findings. The current 534 Warning baseline remains visible/non-blocking for incremental cleanup.
- [ ] Run a physical-device/OEM matrix for AutoTap overlay recovery, fullscreen coordinate handling and Accessibility lifecycle.
- [ ] Run camera/microphone permission and save-path regression on several Android versions for Underwater Camera.
- [ ] Perform final English and Simplified Chinese copy/brand review across onboarding, billing, AI states, privacy and Accessibility disclosure.
- [ ] Decide whether crash reporting/aggregate analytics are needed for launch. If added, update Data Safety/privacy before enabling them; do not reintroduce the inherited Firebase project.

## 4. Final release acceptance

Nova should not be considered public-release ready until all P0 boxes above are complete and a Play-distributed production build has passed at least:

1. normal/scientific calculator smoke tests;
2. all five Nova AI V1 flows through the real Gateway;
3. Play Integrity anonymous-session issuance and rejection behavior;
4. Free/Pro/AI Plus quota and entitlement behavior;
5. Play purchase/restore/expiry behavior;
6. AutoTap consent, arm/start/stop, overlay recovery and unsupported-Android behavior;
7. Underwater Camera photo, silent video and optional-audio video permission/save flows;
8. in-app privacy/open-source notices;
9. crash/ANR and network-failure behavior;
10. final Play listing, Data Safety and Accessibility declarations matching the shipped AAB.

## 5. Non-blockers / intentionally deferred

The following are not required to reopen V1 implementation before production integration:

- generic AI chat;
- autonomous AI Accessibility actions;
- homework-camera solver/question-bank platform;
- social/community/classroom features;
- persistent banner advertising on the calculator screen;
- China-store payment/flavor architecture.

They may be evaluated later from retention, conversion, acquisition and support data rather than delaying the first controlled commercial test.
