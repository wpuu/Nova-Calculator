# Nova AI Gateway client contract v1

## Scope

The Android commercial app talks only to Nova AI Gateway. The first approved operation is `EXPLAIN_CALCULATION`.

The app supplies:

- a client request id;
- the business operation;
- the expression the user explicitly asks AI to explain;
- the deterministic result already produced by the local calculator engine;
- the user's locale tag.

The Android request deliberately has no upstream provider name, model name, provider API key, key-pool identifier or upstream URL.

## Why the local result is included

Exact arithmetic remains the responsibility of the deterministic calculator engine. AI receives the already-computed result and explains it. The explanation may add reasoning or context, but the app must not replace a verified local result merely because an LLM produced a different number.

## Server authority

Nova AI Gateway is authoritative for:

- account/session authentication;
- AI membership verification;
- Free trial and promotional quotas;
- request-per-minute limits;
- paid-first scheduling;
- provider selection and failover;
- provider key-pool health, cooldown and capacity;
- abuse controls;
- retry policy and usage accounting.

Client entitlement state is never sufficient proof that a paid AI request is authorized.

## Provider key pool

Multiple provider keys may be pooled only when they are legitimately issued with independently usable capacity. The gateway tracks health and capacity per key and does not use rotation to evade a provider's account-wide limits.

The Android protocol remains unchanged when the server adds, removes or replaces providers or keys.

## Generic response states

The client understands only Nova-level states:

- `SUCCESS`
- `AUTH_REQUIRED`
- `QUOTA_EXHAUSTED`
- `RATE_LIMITED`
- `INVALID_REQUEST`
- `TEMPORARILY_UNAVAILABLE`

A response may include non-authoritative display hints for remaining requests and quota reset time. The server remains the source of truth.

## Accessibility boundary

AI may explain or recommend a static AutoTap configuration in future versions, but the model must not autonomously observe another app, decide actions and drive Accessibility gestures on its own. Execution remains deterministic and user-confirmed.

## Privacy boundary

Ordinary calculator use is local and sends no calculation to the AI service. A calculation is sent only after an explicit AI action by the user. Image solving, if added later, must use an explicit user-selected image flow and separate disclosure.
