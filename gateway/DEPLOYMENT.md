# Nova AI Gateway — Vercel deployment contract

The commercial gateway is designed to be deployed as a standalone Vercel project with **Root Directory = `gateway`**. Vercel automatically exposes files under `api/` as Node.js Functions.

Routes:

- `POST /api/session` — proof-gated anonymous Nova session for the Android client
- `POST /api/browser-session` — optional Chrome Google identity → short-lived Nova browser session exchange
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

Optional browser-session TTL:

- `NOVA_BROWSER_SESSION_TTL_MS` — default `3600000` (1 hour). Actual issued TTL is additionally capped below the verified Google token expiry with a safety margin.

### Chrome Google identity verification — optional

Chrome Macro authentication is deliberately optional at deployment composition time. If the following variable is absent, `/api/browser-session` remains unavailable while Android `/api/session`, `/api/ai` and Macro's authenticated candidate-review service continue to construct normally.

- `NOVA_CHROME_OAUTH_CLIENT_IDS` — one or more allowed Google OAuth client IDs, separated by comma, semicolon or newline
- `NOVA_CHROME_OAUTH_VERIFY_TIMEOUT_MS` — optional Google token-verification timeout; default `5000`

Browser identity rules:

1. The extension obtains a Google OAuth access token only from an explicit extension-UI user gesture.
2. The extension sends that access token once to `/api/browser-session`; it never persists the Google token.
3. Gateway verifies the token against Google's fixed token-information endpoint and requires an allowed `aud`, allowed `azp` when present, stable `sub`, `openid` scope and sufficient remaining expiry.
4. Gateway ignores Google email/profile fields even if returned.
5. Gateway derives Nova's pseudonymous account subject from `google:<sub>` using the server-only Nova subject secret.
6. Gateway returns only a short-lived Nova session token and expiry. No Google token, email, raw Google subject or OAuth client ID is returned or written to Redis.
7. Browser sessions start with an empty server-issued entitlement set. Client claims cannot upgrade them.

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

The Chrome extension needs only public configuration:

- one exact HTTPS Gateway origin
- one Google Chrome-extension OAuth client ID
- OAuth scope **exactly** `openid`
- one exact HTTPS Gateway host permission after that origin is frozen

The extension must not request `identity.email`; it does not need Google email/profile scopes. The Google access token is ephemeral and is used only for the one-time `/api/browser-session` exchange. After exchange, the extension stores only:

- the public `https://<gateway-domain>/api/macro-candidate-review` endpoint
- the short-lived Nova session token
- its expiry

Those values live only in `chrome.storage.session`.

The extension must never receive an upstream provider key, model id, provider URL, Redis secret or Nova signing secret. Android `/api/session` remains Play-Integrity-specific and is **not** reused for Chrome.

The Macro runtime also maintains a shared `chrome.storage.session` Agnes single-flight lock. During Google/Nova bootstrap or a live AI request, other Agnes/API-backed extension entrypoints must remain disabled/fail-closed rather than start another request.

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
3. Freeze the Chrome extension ID and create the corresponding Google Chrome-extension OAuth client.
4. Put only that client ID plus `openid` into the production extension manifest; do not request email/profile scopes.
5. Freeze the public Gateway HTTPS origin and grant only that exact origin as the extension's Gateway host permission.
6. Configure `NOVA_CHROME_OAUTH_CLIENT_IDS` on the Gateway with the same allowed client ID.
7. Confirm an explicit popup click obtains Google identity, exchanges it for a Nova browser session, and never writes the Google access token to Chrome storage.
8. Confirm the Nova session can call `/api/macro-candidate-review`, while forged candidate IDs and stale reviews still fail closed.
9. Confirm global Agnes single-flight and all-controls-disabled behavior under a real network request.
10. Run the full owner-only ChatOps gate before release.
