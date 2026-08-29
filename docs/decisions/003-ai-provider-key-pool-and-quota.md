# Decision 003 — AI provider key pool and paid-first quotas

Date: 2026-08-29
Status: accepted planning baseline

## Assumptions

- Product planning treats `agnes-2.5-flash` inference as free until reality changes.
- Current planning capacity is 20 requests per minute (RPM) per legitimately independent quota/key where Agnes actually grants independent capacity.
- Nova must not assume that creating extra keys automatically creates extra provider capacity. Only keys/accounts/projects that the provider legitimately treats as independent quota pools may increase aggregate capacity.
- Android clients never receive Agnes API keys.

## Architecture

```text
Android app
    -> Nova AI Gateway
        -> Auth / entitlement
        -> User daily quota
        -> Paid-first scheduler
        -> Global abuse control
        -> ProviderKeyPool
            -> Agnes key A
            -> Agnes key B
            -> Agnes key C
            -> future provider/model pool
```

The Android product talks only to Nova AI Gateway. Adding, removing, rotating, suspending or replacing provider keys must not require an APK update.

## ProviderKeyPool

Each configured provider key has server-side state:

- provider
- model
- enabled / disabled
- RPM limit
- rolling 60-second request count
- in-flight count
- last success time
- last error time
- consecutive error count
- rate-limit-until timestamp
- circuit-breaker state
- optional account/project quota group

The pool must understand quota groups. If several keys share one provider/account-level quota, Nova treats them as one capacity group instead of incorrectly multiplying capacity.

## Key selection

Do not use blind round-robin.

For each eligible request:

1. filter disabled, unhealthy and cooling-down keys;
2. filter keys whose quota group has no available RPM;
3. reserve headroom rather than driving a 20 RPM key continuously at 20/20;
4. rank remaining keys by available capacity, in-flight count and recent failure rate;
5. assign the request to the healthiest least-loaded key;
6. on provider 429, mark the key/quota group cooling down and immediately retry through another eligible key only when retry is safe;
7. on repeated 5xx/network failures, open a circuit breaker temporarily.

Initial operating target for a 20 RPM independent key: use approximately 16 RPM as sustained scheduler capacity and keep ~4 RPM headroom for bursts/retries. All values are remote-configurable.

## User priority

Scheduler priority order:

1. AI Plus paid requests
2. Pro Lifetime included AI allowance
3. Free trial requests
4. background/non-interactive work (normally disabled in V1)

A burst of free traffic must never consume all provider capacity while paid users are queued.

Use separate logical queues and reserve paid capacity. When capacity is scarce, free requests fail fast with a friendly busy message rather than making paid requests wait behind them.

## Initial product quotas

All limits are server-side remote configuration, not hard-coded commercial promises.

### Free

- 1 AI request/day after onboarding
- optionally 2–3 total onboarding samples for a brand-new user so the feature can be understood
- lowest queue priority
- no long multi-turn sessions
- stricter maximum input/output size
- optional future rewarded-ad exchange for one extra request, capped per day

### Pro Lifetime

- initial target: 5 AI requests/day
- normal queue priority
- local Pro features remain the primary lifetime value

### AI Plus

- initial target: 30 AI requests/day
- highest interactive priority
- follow-up questions enabled
- richer explanations and natural-language calculation

The UI should say the service is subject to reasonable usage limits, not promise an unlimited API.

## Abuse controls

Gateway enforces at least:

- account/user daily quota
- device/session anomaly signals
- per-user minute burst limit
- maximum prompt/image size
- maximum output tokens
- duplicate/replay suppression where useful
- IP-level anomaly controls without treating household/NAT users as one person
- entitlement verification server-side
- no API-key or raw provider credentials in logs

If account abuse becomes material, add Play Integrity and stronger server-issued device/session credentials.

## Math-specific routing

Agnes is not Nova's numerical source of truth.

Preferred paths:

### Deterministic math

Local JSCL/calculator engine computes directly. No Agnes request is needed just to obtain a number.

### Natural language -> calculation

Agnes extracts intent/variables/expression -> Nova validates -> deterministic engine calculates -> Agnes may explain the verified result.

### Difficult or unverifiable math

If Nova cannot verify the result with a deterministic engine, label the response as AI analysis rather than a verified answer. Do not market Agnes 2.5 Flash as a guaranteed advanced-math solver.

This design deliberately saves RPM by avoiding AI calls for work the local calculator already performs exactly.

## Scaling examples

If Agnes legitimately grants truly independent 20 RPM pools:

- 1 pool: theoretical 20 RPM; Nova sustained target ~16 RPM
- 5 independent pools: theoretical 100 RPM; Nova sustained target roughly ~80 RPM
- 10 independent pools: theoretical 200 RPM; Nova sustained target roughly ~160 RPM

These are capacity illustrations, not promises. Account-level/global limits may make real capacity lower.

## Future provider portability

`AiProvider` and `ProviderKeyPool` must support additional providers/models. If Agnes pricing, capacity or math quality changes, Nova can:

- add another free model;
- route easy explanations to a cheaper/free model;
- route selected hard problems to a stronger paid model;
- alter member quotas;
- use model fallback;
- disable a provider without shipping a new APK.

The business model therefore depends on Nova's entitlement and routing layer, not on one permanent upstream assumption.
