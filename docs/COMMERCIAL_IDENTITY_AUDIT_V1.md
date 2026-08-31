# Nova Commercial Identity / Monetization Audit V1 — HISTORICAL

Date: 2026-08-30
Branch: `commercial/nova-ai-v1`
Status: **SUPERSEDED historical audit**

This audit captured the commercial branch before the subsequent identity, billing, AI, Gateway, privacy and release work was implemented. Its original detailed checklist is preserved in Git history, but it is intentionally no longer repeated here because several of those statements became false within one day and could cause duplicate or regressive work.

Do **not** use this V1 file to determine current package identity, billing architecture, Firebase/AdMob state, AI implementation status or release blockers.

Use the current operational source of truth instead:

- `docs/COMMERCIAL_RELEASE_READINESS_CURRENT.md` — current completed work, P0 external production blockers, P1 repository quality work and final acceptance criteria;
- `docs/NOVA_ANDROID_RELEASE.md` — production Android identity/signing/release workflow;
- `gateway/DEPLOYMENT.md` — production Gateway/server environment contract;
- `docs/PLAY_DATA_SAFETY_BASELINE.md` — current Google Play data-flow/declaration baseline;
- `docs/PRIVACY_POLICY_DRAFT.md` — intentionally non-production privacy drafting baseline until external release facts are verified.

## Historical purpose

The original V1 audit identified, among other things:

- inherited Calculator++ application/store identity;
- inherited Firebase/AdMob configuration;
- legacy billing coupling;
- commercial branding/release metadata cleanup;
- need for server-side AI quota/rate limiting;
- need to remove covert recording from the commercial product while retaining explicit Underwater Camera;
- release signing, policy and Play-readiness work.

Those findings drove the cleanup. Current CI and current source, not this historical snapshot, determine whether any item remains open.
