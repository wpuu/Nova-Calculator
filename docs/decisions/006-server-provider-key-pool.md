# Nova server provider key pool v1

## Goal

Scale Nova AI Gateway without changing the Android protocol as user traffic grows.

The server owns all upstream provider knowledge. Android never receives upstream keys, model names, provider URLs or pool state.

## Key source

Raw API keys must come from a deployment secret store or protected environment variable. They are not committed to Git. A deployment adapter may parse multiple keys with `parseProviderKeys()` and construct `ProviderKeyPool`.

The pool is provider-neutral. A future provider adapter can use the leased secret for Agnes or another OpenAI-compatible/HTTP provider without exposing that choice to the app.

## Capacity and health

Each key tracks independently:

- configured RPM limit;
- requests consumed in the current one-minute window;
- cooldown deadline;
- consecutive failures;
- last-use time;
- enabled/disabled state.

Lease selection prefers lower utilization, then healthier and less recently used keys.

A reported 429 marks that key's current RPM window exhausted and applies cooldown. Repeated non-rate-limit failures can also place a key into cooldown.

## Paid-first policy

Two mechanisms protect paid traffic:

1. `ProviderKeyPool` reserves a configurable fraction of each key's RPM capacity from Free traffic. Paid traffic may use that reserve.
2. `PriorityRequestQueue` drains `AI_PLUS` first, then `PRO`, then `FREE`, preserving FIFO order inside each class.

This is admission priority, not a promise that one membership has a hard-coded number of AI calls. Quotas remain server policy.

## Legal capacity boundary

Pooling is only for keys that the upstream provider legitimately issues with independently usable capacity. It must not be used to evade an account-wide quota, contractual restriction or provider abuse control.

## Logging boundary

`ProviderKeyPool.snapshot()` deliberately omits raw secrets. Operational dashboards should use only key ids and health/capacity metadata.

## Agnes status

Agnes currently publicly advertises a Free API and says its models are intended to be easy to plug into products/platforms. Production monetization still requires confirming the exact commercial embedding/resale terms applicable to the Nova account before enabling paid end-user traffic through an Agnes adapter.

For that reason this v1 commit implements only the provider-neutral pool and priority core, not a hard-coded Agnes endpoint or model adapter.
