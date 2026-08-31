# Nova Calculator AI — Privacy Policy Draft

Status: **DRAFT — not yet approved for publication.**

Before this text is used as the production privacy policy, Nova must add the final privacy contact/legal publisher identity and confirm the exact production retention/logging settings for the Nova Gateway, hosting platform and AI inference provider.

## 1. Scope

This policy draft describes the current commercial design of Nova Calculator AI for Android.

Nova is primarily an on-device scientific calculator. Optional features can use Nova's server services, Google Play services, camera/microphone permissions or Android AccessibilityService as described below.

## 2. Calculator data stored on the device

Ordinary calculator expressions, results, history, variables, functions and saved formulas are processed locally unless you explicitly invoke a feature that needs network processing, such as Nova AI or purchase verification.

## 3. Nova AI

When you explicitly use an AI feature, Nova may send the math context needed to answer your request to the Nova Gateway. Depending on the feature, this can include:

- the current mathematical expression and deterministic calculator result;
- a natural-language calculation request;
- a follow-up question about the current calculation;
- an invalid-expression/error question; or
- a description of a formula you asked Nova to build.

The Nova Gateway may use a third-party AI inference service to process this request and return the requested explanation or math assistance.

Nova's Android application does not contain the upstream AI provider's API keys. Provider credentials and routing are controlled server-side.

**Release decision still required:** before publication, verify and document the production retention/logging behavior of the Gateway host and AI processor. Do not replace this paragraph with a "never retained" statement unless that has been technically and contractually verified.

## 4. Installation security, Play Integrity and abuse prevention

Nova can create an app-local installation identifier and use Google Play Integrity to confirm that an anonymous-session request comes from the recognized Nova application/device environment.

The server may process:

- the app-local installation identifier;
- Play Integrity tokens and decoded verdicts;
- a signed Nova session token;
- a pseudonymous subject derived for quotas and abuse prevention; and
- request-count/rate-limit state.

These signals are used for application security, fraud/abuse prevention, entitlement enforcement and service capacity management. Nova's current design does not invent or expose a permanent hardware identifier from Play Integrity data.

## 5. Google Play purchases

Nova uses Google Play Billing for paid digital features. The app may send the following to the Nova billing service for server-side verification:

- Google Play product id and product type;
- Google Play purchase token; and
- the current purchase snapshot needed to restore or update Nova entitlements.

The Nova server verifies purchases using Google Play Developer APIs before issuing a signed entitlement session.

Nova does not receive your payment-card number, bank account details or Google Play payment credentials. Google handles payment processing under Google's own terms and privacy practices.

## 6. Underwater Camera

Underwater Camera is an explicit tool. Nova requests camera permission only after you enter the tool and see its in-app explanation.

If you record video with sound, Nova separately offers a microphone permission flow. You may choose silent video instead.

In the current commercial implementation:

- photos are written to your device through Android MediaStore under `Pictures/UnderwaterCamera`;
- videos are written under `Movies/UnderwaterCamera`;
- optional microphone audio is stored as part of the video you choose to record; and
- the Underwater Camera feature does not upload those photos, videos or audio to Nova servers.

If a future version introduces cloud backup, AI media analysis or another off-device media feature, this policy must be updated before that behavior is released.

## 7. AutoTap and Android AccessibilityService

AutoTap is an optional deterministic two-point click helper for Android 7.0 and newer. It is not intended to be an accessibility tool for people with disabilities.

Before Nova opens Android Accessibility settings for AutoTap, the app presents a separate in-app disclosure and requires an affirmative consent action.

The current AutoTap AccessibilityService:

- receives limited window-change events so it can restore the user-visible target overlays after display/full-screen changes;
- listens for Volume Up and Volume Down while AutoTap is armed, using them as start/stop controls;
- sends click gestures only to the two target positions chosen by the user;
- has `canRetrieveWindowContent=false` and therefore does not retrieve window/screen text through AccessibilityService; and
- does not let AI choose targets or autonomously operate AccessibilityService.

Nova does not use AutoTap AccessibilityService data for advertising or user profiling.

## 8. Data sharing and service providers

Nova may transmit data to service providers only as needed to deliver the user-requested or security/payment feature, including:

- Google Play / Google Play Integrity for application integrity and purchase verification;
- infrastructure used to operate the Nova Gateway and shared quota/capacity state; and
- the AI inference processor used when you explicitly invoke Nova AI.

Production processor details and retention terms must be verified before this draft becomes the final public policy.

Nova does not sell personal or sensitive user data.

## 9. Security

Nova separates client and server responsibilities so that upstream AI credentials, Redis credentials, Google service-account private keys and Nova session-signing secrets are not included in the Android application.

Server-issued Nova sessions are signed and time-limited. Quota/security state uses pseudonymous identifiers rather than exposing provider API-key secrets to shared Redis storage.

No security measure can guarantee absolute protection, but Nova is designed to minimize the credentials and sensitive server configuration exposed to the client.

## 10. Retention and deletion

Local calculator data and locally captured media remain under Android/device storage controls and can be removed using the app/device controls that apply to those items.

Nova server sessions and quota/capacity records are designed to be time-limited or bucketed, but the final public retention statement must also account for production hosting, operational logs, Google services and AI-processor retention.

**Release decision still required:** document the final retention periods and the process for privacy/deletion requests before publication.

## 11. Children's privacy

Nova Calculator AI is a general calculator/utility product and is not designed as a child-directed social or chat service. Final Play target-audience selections and any child-specific compliance requirements must match the actual store listing and marketing before release.

## 12. Changes

If Nova materially changes how it collects, processes or shares data, the privacy policy and relevant in-app disclosures must be updated before or with that release.

## 13. Contact

**Required before publication:** add the production publisher/legal entity name and a monitored privacy/support email or equivalent privacy contact channel.
