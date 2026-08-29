# Decision 002 — Agnes math boundaries and quota policy

Date: 2026-08-30
Status: accepted product baseline

## Assumptions

For planning, `agnes-2.5-flash` inference is treated as free. Capacity is not treated as unlimited. The current operational assumption is a provider limit of **20 requests per minute (RPM)**.

The product must be designed so a change in model/provider economics can be handled later through the Nova server-side `AiProvider` abstraction without changing the calculator UX.

## Core rule: Agnes is not the mathematical source of truth

Agnes is a general AI model, not a computer algebra system. Nova must not market or implement it as an infallible advanced-math solver.

Use Agnes for:

- understanding natural-language math intent;
- extracting variables and assumptions;
- producing candidate formulas/expressions;
- explaining a verified result;
- explaining syntax/calculation errors;
- simplifying an explanation;
- follow-up questions about the current calculation.

Use deterministic/local math for:

- arithmetic;
- percentages;
- powers/roots;
- supported trigonometric/logarithmic functions;
- unit/conversion calculations;
- supported algebra/symbolic operations;
- any result the existing JSCL engine can compute or validate exactly.

## Difficulty / verification classes

### Class A — deterministic

Examples: arithmetic, percentages, tax/discount, tips, ratios, unit conversion, supported scientific functions.

Flow:

`input -> deterministic engine -> exact result -> optional Agnes explanation`

UI may describe the numeric result as calculated/verified.

### Class B — AI-parsed, deterministic result

Examples: everyday word problems, finance-style calculations, natural-language expressions.

Flow:

`language -> Agnes structured intent/expression -> schema validation -> deterministic engine -> exact result -> Agnes explanation`

The model does not get final authority over the number.

### Class C — partially verifiable

Examples: algebraic transformations or symbolic operations where Nova can verify some but not all steps.

UI must distinguish the verified result/steps from AI-generated explanation.

### Class D — unverified advanced math

Examples: difficult proofs, olympiad-style reasoning, advanced calculus derivations, sophisticated linear algebra/proofs, PDEs and other problems outside the deterministic engine's supported domain.

Nova must not present Agnes output as a guaranteed correct solution. V1 may:

- provide a clearly labeled AI explanation;
- say that the result could not be verified by Nova's deterministic engine;
- suggest simplifying the problem into verifiable sub-calculations;
- decline to claim exact correctness.

Do not use marketing such as "solves every math problem" or "always correct".

## Quota objective

Provider RPM is a scarce shared capacity resource even when inference price is zero. Free users should receive enough AI to understand the feature, but paid users receive the majority of capacity and priority.

### Initial entitlement hypothesis

These values must be remotely configurable and are not hard-coded promises.

- **Free**: 1 AI request/day after initial onboarding trial.
- **Initial trial**: up to 3 total AI requests to demonstrate explain/current-result, natural-language calculation and follow-up.
- **Pro Lifetime**: up to 5 AI requests/day as a convenience allowance.
- **AI Plus**: up to 30 AI requests/day, priority queue, advanced explanation/follow-up features.

Do not advertise "unlimited AI".

## Global 20 RPM capacity policy

Do not drive the provider at the absolute ceiling. Initial gateway target:

- operational ceiling: 16 RPM;
- keep ~4 RPM headroom for retries/provider jitter;
- paid traffic receives priority;
- free traffic may be delayed or rejected first during saturation;
- per-user burst limits prevent one account from consuming the queue.

Suggested starting allocation under load:

- AI Plus / paid priority: up to 12 RPM;
- Free / trial: up to 2 RPM;
- system retry/health/admin reserve: up to 2 RPM;
- remaining provider headroom: ~4 RPM.

These are scheduling targets, not separate physical provider quotas.

## Gateway requirements

The Nova AI Gateway must implement:

- authenticated entitlement lookup;
- per-user daily counters;
- global token-bucket/RPM limiter;
- paid-priority queue;
- retry with bounded backoff for transient errors;
- request deduplication/cache for repeated identical calculations where safe;
- request timeout/cancellation;
- usage telemetry that does not log unnecessary sensitive math/photo contents;
- provider abstraction;
- remote-configurable quota values.

A follow-up AI message counts as another provider request unless served from cache/local logic.

## Optional rewarded-ad quota

For ad-supported global builds, a Free user may optionally watch a rewarded ad to unlock one additional AI request. Keep this strictly opt-in and capped (for example, maximum 1-2 extra AI requests/day).

This is preferable to aggressive always-visible banner advertising on the main calculator screen because it aligns ad revenue with the scarce AI capacity the user is requesting.

## Product language

Preferred promises:

- "Explain this calculation"
- "Turn everyday questions into calculations"
- "AI-assisted, calculator-verified when supported"
- "Ask why this result is correct"

Avoid:

- "Solve any math problem"
- "100% accurate AI"
- "Unlimited AI"
- "Advanced theorem/proof solver" until independently validated.
