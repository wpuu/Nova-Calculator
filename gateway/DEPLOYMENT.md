# Nova AI Gateway — Vercel deployment contract

The commercial gateway is designed to be deployed as a standalone Vercel project with **Root Directory = `gateway`**. Vercel automatically exposes files under `api/` as Node.js Functions.

Routes:

- `POST /api/session` — proof-gated anonymous Nova session
- `POST /api/ai` — authenticated Nova AI request
- `GET /api/health` — coarse configuration health only; never returns secrets or provider identity

The project pins Node.js 22.x to match CI.

## Required server-only environment variables

These values belong only in the Vercel project environment. Never place them in Android resources, BuildConfig, the public repository, screenshots or client logs.

### Upstream AI provider

- `NOVA_PROVIDER_BASE_URL` — server-side OpenAI-compatible base URL
- `NOVA_PROVIDER_MODEL` — server-side model id
- `NOVA_PROVIDER_KEYS` — one or more independent API keys, separated by comma, semicolon or newline

Optional capacity policy:

- `NOVA_PROVIDER_RPM_PER_KEY` — default `20`
- `NOVA_PAID_RESERVE_FRACTION` — default `0.2`
- `NOVA_PROVIDER_TIMEOUT_MS` — default `15000`
- `NOVA_PROVIDER_MAX_TOKENS` — default `800`
- `NOVA_PROVIDER_FAILURE_COOLDOWN_MS` — default `30000`
- `NOVA_PROVIDER_FAILURE_THRESHOLD` — default `3`
- `NOVA_PROVIDER_CREDENTIAL_DISABLE_MS` — default `21600000` (6 hours); shared quarantine after a provider rejects a credential. Use a bounded value so a future key rotation is automatically re-evaluated instead of inheriting a permanent Redis disable flag.

A provider HTTP 429 also saturates that credential's shared current-minute RPM counter in Redis. This prevents another horizontally scaled gateway instance from reusing the same credential in the same minute merely because the upstream `Retry-After` is shorter than Nova's one-minute accounting window.

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

The same Redis service can safely back both namespaces. Provider API key **secrets are never written to Redis**; provider capacity state contains only opaque ids, counters, cooldown/failure state and temporary disable state.

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

## Android-side public configuration

The Android app needs only public/routable configuration, not server credentials:

- production AI URL → `https://<gateway-domain>/api/ai`
- anonymous-session URL → `https://<gateway-domain>/api/session`
- Play Integrity Cloud project number → public numeric project configuration used by the Android Play Integrity SDK

The following must **never** ship in the APK:

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
4. Configure the Vercel server-only variables above.
5. Confirm `/api/health` returns HTTP 200 without exposing internal configuration.
6. Confirm a Play-installed build can obtain `/api/session` and then call `/api/ai`.
7. Keep the debug/development APK fail-closed when no production Play project number is supplied.
