# Nova AI Gateway — Vercel deployment contract

The commercial gateway is designed to be deployed as a standalone Vercel project with **Root Directory = `gateway`**. Vercel automatically exposes files under `api/` as Node.js Functions.

Routes:

- `POST /api/session` — proof-gated anonymous Nova session for the Android client
- `POST /api/ai` — authenticated Nova calculation-explanation request
- `POST /api/macro-candidate-review` — authenticated, candidate-only Macro AI review; response can only select one locally supplied candidate ID or abstain
- `GET /api/health` — coarse configuration health only; never returns secrets or provider identity

The project pins Node.js 22.x to match CI.

## Required server-only environment variables

These values belong only in the Vercel project environment. Never place them in Android resources, extension code, BuildConfig, the public repository, screenshots or client logs.

### Upstream AI provider

- `NOVA_PROVIDER_BASE_URL` — server-side OpenAI-compatible base URL
- `NOVA_PROVIDER_MODEL` — server-side model id
- `NOVA_PROVIDER_KEYS` — one or more independent API keys, separated by comma, semicolon or newline

Optional capacity policy:

- `NOVA_PROVIDER_RPM_PER_KEY` — default `12`; **hard maximum `15`**. This deliberately stays below the Agnes account/key limit and is shared by all Nova Agnes-backed modules.
- `NOVA_PAID_RESERVE_FRACTION` — default `0.2`
- `NOVA_PROVIDER_TIMEOUT_MS` — default `15000`
- `NOVA_PROVIDER_MAX_TOKENS` — calculation explanation default `800`
- `NOVA_MACRO_PROVIDER_MAX_TOKENS` — candidate review default `400`
- `NOVA_PROVIDER_FAILURE_COOLDOWN_MS` — default `30000`
- `NOVA_PROVIDER_FAILURE_THRESHOLD` — default `3`

Calculator explanation and Macro candidate review use different provider adapters but the **same provider key pool**. Therefore adding a second feature does not create a second hidden RPM budget. A deployment value above 15 RPM/key fails closed during runtime construction.

### Nova session signing

- `NOVA_SESSION_SIGNING_SECRETS` — current signing secret first; old secrets may remain after it during rotation
- `NOVA_SESSION_SUBJECT_SECRET` — stable independent secret used to derive pseudonymous quota subject ids

These secrets must be independent. Rotating the signing secret must not implicitly change quota identity.

### Shared Redis

- `NOVA_QUOTA_REDIS_REST_URL` — HTTPS Redis REST endpoint
- `NOVA_QUOTA_REDIS_REST_TOKEN` — Redis REST bearer token

Optional namespacing:

- `NOVA_QUOTA_REDIS_KEY_PREFIX` — default `nova:quota:v1`
- `NOVA_PROVIDER_REDIS_KEY_PREFIX` — default `nova:provider:v1`
- `NOVA_QUOTA_REDIS_TIMEOUT_MS` — Redis REST timeout override

The same Redis service can safely back both namespaces. Provider API key **secrets are never written to Redis**; provider capacity state contains only opaque ids, counters, cooldown/failure state and disable state.

### Google Play Integrity server decode

- `NOVA_ANDROID_PACKAGE_NAME` — exact production Android application id
- `NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_EMAIL` — service-account email from the Cloud project linked to Play Integrity
- `NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_PRIVATE_KEY_B64` — base64 of the PEM private key; server only
- `NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_KEY_ID` — optional service-account key id

Optional timeouts/policy:

- `NOVA_GOOGLE_OAUTH_TIMEOUT_MS`
- `NOVA_PLAY_INTEGRITY_DECODE_TIMEOUT_MS`
- `NOVA_PLAY_INTEGRITY_REQUIRE_LICENSED` — production default is enabled; do not set `false` without an explicit product decision
- `NOVA_PLAY_INTEGRITY_REQUIRE_DEVICE_INTEGRITY` — production default is enabled; do not set `false` without an explicit product decision

A Vercel production deployment refuses `NOVA_ANDROID_PACKAGE_NAME` values ending in `.dev`.

## Deploy-time AI allowance policy

Defaults can be changed without releasing a new APK:

- `NOVA_AI_FREE_DAILY_LIMIT` / `NOVA_AI_FREE_RPM_LIMIT`
- `NOVA_AI_PRO_DAILY_LIMIT` / `NOVA_AI_PRO_RPM_LIMIT`
- `NOVA_AI_PLUS_DAILY_LIMIT` / `NOVA_AI_PLUS_RPM_LIMIT`

These client/principal quotas are separate from the global upstream per-key ceiling. They must never be configured with the assumption that each product feature has an independent provider capacity pool.

## Android-side public configuration

The Android app needs only public/routable configuration, not server credentials:

- production AI URL → `https://<gateway-domain>/api/ai`
- anonymous-session URL → `https://<gateway-domain>/api/session`
- Play Integrity Cloud project number → public numeric project configuration used by the Android Play Integrity SDK

## Chrome Macro public configuration

The Macro extension must receive only:

- the public HTTPS route → `https://<gateway-domain>/api/macro-candidate-review`
- a short-lived/revocable **Nova session token**, stored only in `chrome.storage.session`

The extension must never receive an upstream provider key, model id, provider URL, Redis secret or Nova signing secret. Current Android `/api/session` issuance is Play-Integrity-specific and is **not** a valid Chrome bootstrap mechanism; Chrome production authentication/session issuance remains a separate release gate.

The Macro runtime also maintains a shared `chrome.storage.session` Agnes single-flight lock. During a live AI request, other Agnes-backed extension entrypoints must remain disabled/fail-closed rather than start another provider request.

The following must **never** ship in the APK or extension:

- provider API keys
- provider model/base URL if product policy requires hiding them
- Redis URL/token
- Nova session signing/subject secrets
- Google service-account private key
- Google OAuth access tokens

## Release gates

Before enabling production AI in an APK:

1. Freeze the real production application id; `.dev` is forbidden.
2. Link that exact app/package to the correct Google Cloud project in Play Console.
3. Configure the Android Play Integrity Cloud project number.
4. Configure the Vercel server-only variables above, with provider RPM/key at 12 normally and never above 15.
5. Confirm `/api/health` returns HTTP 200 without exposing internal configuration.
6. Confirm a Play-installed build can obtain `/api/session` and then call `/api/ai`.
7. Keep the debug/development APK fail-closed when no production Play project number is supplied.

Before enabling production Macro AI review:

1. Keep local Core candidate generation as the only source of executable DOM targets.
2. Keep Gateway/provider output limited to listed candidate ID or ABSTAIN.
3. Provide a browser-appropriate Nova session bootstrap; do not reuse the Android Play-Integrity issuance path as if Chrome could satisfy it.
4. Pin the public Gateway origin/permission deliberately for the production extension build.
5. Confirm the extension stores only the Nova session token in `chrome.storage.session` and does not persist upstream credentials.
6. Confirm global Agnes single-flight and all-controls-disabled behavior under a real network request.
7. Run the full owner-only ChatOps gate before release.
