# Decision 001 — AI stays inside the calculator workflow

Date: 2026-08-29
Status: accepted

This decision supersedes the top-level navigation proposal in section 10 of `docs/NOVA_COMMERCIAL_PRODUCT_PLAN_V1.md`.

## Decision

Nova commercial V1 remains calculator-first. AI is not a separate default chat product and does not need a dedicated top-level `AI Math` tab in V1.

Default experience:

1. Open app directly into Calculator.
2. Perform calculations normally with the local deterministic engine.
3. From the current expression/result, expose contextual AI actions such as:
   - `解释这一步`
   - `为什么是这个结果`
   - `用更简单的话解释`
   - `继续追问`
   - `用文字描述计算`
4. Natural-language math opens as an input mode attached to Calculator, not a generic chat home.
5. AI answers remain scoped to mathematics/calculation in V1.

## Navigation

Recommended V1 structure:

- **Calculator** — default home, includes contextual Agnes AI actions.
- **Tools** — contains AutoTap, Underwater Camera, converters and other explicit utilities.
- **History / Saved formulas** — reachable from calculator/history UX; no need for a separate AI chat tab.

## Rationale

- Preserves the product's strongest existing habit: users open a calculator to calculate immediately.
- Avoids direct competition with broad AI chat/homework products.
- Reduces onboarding complexity and support burden.
- Makes AI value obvious at the moment a user actually needs explanation.
- Allows exact local calculation to remain the numerical source of truth, with Agnes used for intent extraction and explanation.
- Keeps AutoTap and Underwater Camera visible and explicit without making them the main acquisition message.

## V1 AI scope

Prioritize:

1. Explain current result.
2. Natural-language calculation.
3. Follow-up questions about the current calculation.
4. Explain parser/calculation errors.
5. Convert word problems into verified expressions.

Defer general-purpose chat, full homework tutoring, live camera homework solving and autonomous Accessibility control.
