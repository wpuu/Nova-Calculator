# Test Status

## Passed

- Semantic matcher executed against real Chromium 144 DOM.
- Generic recorder baseline: 9 / 18 known synthetic variants.
- Shopify Site Adapter: 18 / 18 known synthetic variants after role-family / alias / menu-path fixes.
- Negative cases stop at `ABSTAIN` or `AI_REVIEW` instead of force-clicking.
- Dangerous labels are not auto-selected.

## Not yet passed / not yet testable here

- Real Shopify Admin.
- Real Chrome unpacked extension load and popup interaction.
- Navigation-spanning recording.
- iframe / shadow DOM.
- Agnes candidate selection.

The current container can run Chromium DOM via CDP but does not reliably register unpacked `--load-extension` packages, so extension-load status remains unproven rather than failed.
