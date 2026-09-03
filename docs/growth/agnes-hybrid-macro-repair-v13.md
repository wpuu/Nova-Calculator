# Agnes Hybrid Macro Repair Architecture V13

Date: 2026-09-04

## Core decision

Do not require Agnes to invent a replacement selector from an arbitrary full page.

The runtime should reduce repair to a constrained candidate-selection problem:

1. deterministic engine fingerprints the original target;
2. deterministic engine searches the changed DOM and creates a short ranked candidate list;
3. if confidence is high, Nova proposes a local deterministic patch without Agnes;
4. if confidence is ambiguous, one Agnes call receives the old fingerprint + user intent + 5–10 verified candidates + optional screenshot crop;
5. Agnes may choose a candidate or return `MANUAL_STEP/ABSTAIN`;
6. Nova uses the candidate's already-verified local selector. Agnes never fabricates an executable selector.

This architecture materially lowers both hallucination risk and Agnes capability requirements.

## Target fingerprint stored during recording

For each interactive target, store a privacy-minimized fingerprint such as:

- tag name;
- semantic role;
- accessible name / aria-label;
- associated label text;
- stable `data-*` attributes;
- stable id/name where present;
- button/link/input type;
- nearby heading/section text;
- parent semantic role;
- previous/next sibling short text fingerprints;
- relative order among same-role elements;
- safe page route pattern;
- optional small visual crop hash/embedding metadata later.

Avoid storing passwords, secrets, full unrelated page text, or sensitive form values.

## Deterministic candidate finder

When a target fails:

1. enumerate interactable elements compatible with original operation;
2. discard hidden/disabled/incompatible elements;
3. calculate similarity using stable attributes;
4. calculate accessible-name similarity;
5. compare nearby section/heading context;
6. compare label and role;
7. compare DOM neighborhood rather than absolute XPath;
8. return top candidates with local verified selectors.

Example candidate packet:

```json
{
  "old_target": {
    "role": "button",
    "accessible_name": "Export orders",
    "data_action": "export",
    "section": "Orders"
  },
  "candidates": [
    {
      "candidate_id": "c1",
      "role": "button",
      "accessible_name": "Export orders",
      "data_action": "export",
      "section": "Orders",
      "local_selector": "button[data-action='export']",
      "heuristic_score": 0.96
    },
    {
      "candidate_id": "c2",
      "role": "button",
      "accessible_name": "Export products",
      "section": "Products",
      "local_selector": "#product-export",
      "heuristic_score": 0.51
    }
  ]
}
```

Agnes never needs to return `button[data-action='export']`; it only returns `c1` or abstains.

## Local auto-repair threshold

Initial hypothesis to test, not a production constant:

- candidate score >= 0.95 and margin to #2 >= 0.20: propose deterministic repair without Agnes;
- 0.65–0.95 or small top-two margin: Agnes candidate selection;
- < 0.65: manual repair / user re-record.

For consequential actions, always require human confirmation even with high similarity.

## Agnes repair request

One call receives:

- macro intent;
- old target fingerprint;
- failed operation;
- failure reason;
- top 5–10 candidate records;
- optionally a small screenshot crop or page screenshot if text/DOM is insufficient;
- explicit sensitivity classification.

Expected response:

```json
{
  "decision": "SELECT | MANUAL_STEP | ABSTAIN",
  "candidate_id": "c1",
  "reason": "Same accessible name and export action in Orders section",
  "confidence": 0.97
}
```

No arbitrary code. No arbitrary selectors.

## Why this is more compatible with Agnes 2.5 Flash

Public evidence shows Agnes 2.5 Flash is aimed at coding, reasoning, tool calling, agent workflows, and image understanding. Public/internal software-engineering benchmarks are promising, but browser-selector self-healing is not directly benchmarked.

Candidate selection is substantially easier than open-ended browser repair because:

- all executable candidates are real DOM elements verified locally;
- the output space is tiny;
- hallucinated selectors become impossible by construction;
- semantic reasoning can focus on intent/context;
- one response is sufficient;
- the runtime remains deterministic.

## New A/B benchmark

V12 should be extended to compare two repair modes on the same 10 DOM mutations:

### Mode A — open-ended repair

Agnes receives old selector + new DOM and proposes a replacement step.

### Mode B — constrained candidate selection

Nova generates 5–10 verified candidate targets; Agnes selects one or abstains.

Metrics:

- first-pass repair success;
- false repair rate;
- dangerous false repair rate;
- abstention quality;
- JSON/schema validity;
- latency;
- one-call completion.

Expected product requirement:

- Mode B >= 90% on simple/medium mutations;
- dangerous false repair = 0;
- ambiguous test cases should abstain rather than guess.

If Mode B materially outperforms Mode A, production should not expose open-ended selector generation at all.

## AI use frequency

Normal execution path:

- macro run: 0 Agnes calls;
- high-confidence local target recovery: 0 Agnes calls;
- ambiguous target repair: 1 Agnes call;
- user requests macro optimization: 1 Agnes call;
- user requests explanation: only on explicit click; global single-flight applies.

This makes the architecture compatible with multiple free Agnes accounts whose main relevant constraint is per-key RPM.

## Marketplace moat implication

The moat should not be “Agnes repairs macros.” Competitors can add AI.

The stronger moat becomes:

- accumulated target fingerprints;
- site adapter/component library;
- repair telemetry;
- version compatibility graph;
- known page mutations;
- creator-maintained macros;
- verified candidate maps per site/version;
- aggregate macro health and execution success history.

Over time a common site mutation can be fixed deterministically for many macros before Agnes is needed.

## Product direction if Agnes is only medium-strength

Even a Yellow V12 result may be enough if constrained Mode B performs well. The product no longer depends on Agnes being a top-tier autonomous browser agent. Agnes only needs to be a competent semantic classifier/compiler inside a tightly bounded runtime.
