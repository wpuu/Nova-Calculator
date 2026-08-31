# Google Play Data Safety baseline — Nova Calculator AI

Status: **release baseline / must be re-checked against the exact production build and production service configuration before Play submission.**

This document maps the current commercial source behavior to the questions that will need to be answered in Google Play Console. It is not a substitute for completing the Play Console form.

## Current data-flow summary

| Feature | Data involved | Leaves device? | Current purpose | Play/Data Safety handling baseline |
| --- | --- | --- | --- | --- |
| Local calculator | Expressions, results, history, variables, saved formulas | No, unless the user invokes an AI action | App functionality | Local-only data is not declared as collected merely because Nova processes it on device. |
| Nova AI | Current expression/result and the user text needed for the chosen AI action (for example a natural-language calculation, follow-up, error question or formula description) | **Yes** | App functionality: generate the requested math explanation/translation/formula assistance | Treat as off-device user-provided/app-activity data. Do **not** claim ephemeral processing unless the final Nova Gateway, hosting and AI-provider logging/retention configuration all satisfy Google's ephemeral definition. |
| Installation/session security | App-local installation id, Play Integrity token/verdict, signed Nova session and pseudonymous quota subject | **Yes** | Security, fraud/abuse prevention, rate limiting, app functionality | Treat pseudonymous identifiers as data that must be considered in Data Safety. Current code does not create a permanent hardware identifier. |
| Google Play purchases | Product id/type, Play purchase token, Google purchase/subscription state, derived Nova entitlement | **Yes** | Purchase verification, account/entitlement management, fraud prevention | Treat as purchase-related data. Nova does not receive payment-card details; payment processing is handled by Google Play. |
| Underwater Camera photos/video/audio | Camera frames, saved photo/video and optional microphone audio | No in the current camera feature | User-requested local media capture | Current code writes media to Android MediaStore and contains no camera-feature upload path. Local-only processing is not declared as off-device collection, but CAMERA/RECORD_AUDIO access must still be disclosed in the privacy policy and permission UX. |
| AutoTap AccessibilityService | Window-change events, hardware volume-key events and the two user-positioned screen coordinates used for gestures | No in the current AutoTap feature | User-requested deterministic two-point automation | `canRetrieveWindowContent=false`; no screen text/content collection through AccessibilityService. Accessibility still requires its separate Play declaration and prominent in-app disclosure/affirmative consent. |

## AI processing

When a user explicitly invokes an AI feature, Nova sends only the math context required for that action through the Nova Gateway. The Gateway can forward that request to a configured third-party AI inference provider.

Production submission rules:

1. The Play Data Safety form must account for this off-device processing even if the AI response is generated in real time.
2. Third-party AI use remains Nova's responsibility under Google Play User Data policy.
3. Do not select an "ephemeral" treatment until the exact production hosting logs, Gateway logs and upstream AI-provider retention terms/configuration have been verified.
4. Do not state publicly that AI data is never retained unless that statement is true for every production processor involved.
5. Provider/model names and API credentials may remain abstracted in the product, but the privacy policy must still explain that a service provider processes AI requests when AI is used.

## Security and identifiers

Current design:

- Android creates an app-local installation identifier.
- Play Integrity binds anonymous-session issuance to a fresh request hash and verifies package/app/device/licensing verdicts server-side.
- The Gateway derives a pseudonymous quota subject with HMAC instead of storing the raw installation id as the quota key.
- Anonymous Nova sessions are signed and time-limited.
- Redis capacity/quota data uses pseudonymous subjects, opaque provider-key ids and time buckets; provider API-key secrets are not stored in Redis.

For Data Safety, pseudonymous identifiers must still be evaluated as collected data when transmitted off device.

## Purchases

Nova's Android billing client receives Play purchase tokens and sends the current purchase snapshot to Nova's billing endpoint. The server verifies the purchase directly with the Google Play Developer API before issuing a signed entitlement session.

Current launch products:

- `nova_pro_lifetime` — one-time product
- `nova_ai_plus` — subscription (`monthly` / `annual` base plans)

Nova does not receive or process the user's payment-card number, bank account or Google Play payment credentials.

## Camera and microphone

The commercial product line exposes camera/microphone only through the visible **Underwater Camera** tool:

- camera disclosure appears after the user enters the tool and before the Android permission request;
- microphone is optional and requested only when the user asks to record video with sound;
- silent video remains available;
- photos are saved to `Pictures/UnderwaterCamera` and videos to `Movies/UnderwaterCamera` through MediaStore;
- current source has no network upload path for those captures.

If a later version adds cloud backup, AI image analysis, sharing automation or any off-device media processing, this document and the Play declaration must be updated before release.

## AccessibilityService / AutoTap

Current commercial AutoTap is not declared as an accessibility tool. It therefore requires the non-accessibility-tool declaration and prominent disclosure/consent flow.

Current safeguards:

- Android 7.0 / API 24+ only;
- user explicitly enables the feature;
- a dedicated disclosure explains the exact AccessibilityService access and use before Android Accessibility settings are opened;
- affirmative "同意并继续" action is required;
- `canRetrieveWindowContent=false`;
- event scope is limited to window state/windows changed;
- gestures are deterministic at the two user-positioned target coordinates;
- Volume Up starts and Volume Down stops;
- AI does not choose targets or autonomously execute Accessibility actions.

Before Play submission, record the required Accessibility declaration demonstration video showing both consent and decline flows plus AutoTap operation.

## Data Safety submission checklist

Before answering the Play Console form, re-check the final production APK/AAB and every production processor:

- [ ] Confirm exact AI Gateway host and upstream AI provider processing/retention terms.
- [ ] Confirm whether hosting/provider request logs contain AI request bodies and their retention period.
- [ ] Confirm Redis TTL/retention configuration used in production.
- [ ] Confirm no analytics, crash-reporting or advertising SDK has been added since this baseline.
- [ ] Confirm Underwater Camera still has no upload/cloud-sync path.
- [ ] Confirm AccessibilityService still has `canRetrieveWindowContent=false` and no AI-autonomous execution.
- [ ] Confirm Google Play Billing product ids/base plans match the Play Console products.
- [ ] Complete the AccessibilityService declaration and demonstration video.
- [ ] Publish the final privacy policy at a stable public HTTPS URL and expose it inside the app.
- [ ] Make the Play Console Data Safety answers match the final privacy policy and actual production behavior.

## Current unresolved release items

This baseline intentionally does **not** make final claims about AI/log retention or a privacy contact because those production decisions are not yet encoded in the repository. They must be resolved before the privacy-policy draft is promoted to the public production policy.
