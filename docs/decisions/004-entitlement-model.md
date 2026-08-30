# Nova commercial entitlement model v1

## Purpose

Keep product feature code independent from any payment SDK and model paid rights correctly from day one.

## Durable rights

- `PRO_LIFETIME`: permanent local/offline Pro ownership.
- `AI_PLUS`: recurring AI membership.

These rights are independent and may coexist. `Free` is the baseline state and is represented by no durable paid rights.

## What is not stored as an Android entitlement

Dynamic AI quotas, request-per-minute capacity, provider routing, promotional trial counts and abuse limits are server policy. They must remain authoritative in Nova AI Gateway so they can change without an APK release.

A Free user may therefore receive a limited AI trial even though the local entitlement snapshot contains neither `PRO_LIFETIME` nor `AI_PLUS`.

## Integration rule

Product UI and feature code depend on `EntitlementManager`, not Google Play Billing or another store SDK directly. A future billing/account adapter implements `EntitlementSource` and supplies a cached snapshot.

If a source is unavailable or returns an invalid empty result, the client fails closed to the Free baseline rather than accidentally granting paid rights.

## Security boundary

The Android snapshot is for UX and local feature gating. Server-paid AI service access must still verify account/subscription state at Nova AI Gateway. The client must never be trusted as the authority for AI membership, quota or billing status.
