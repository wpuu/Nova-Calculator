import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const matcherPath = path.join(root, 'prototypes', 'nova-macro-mv3', 'semantic-matcher.js');
const fixture = JSON.parse(fs.readFileSync(
  path.join(root, 'docs', 'growth', 'fixtures', 'macro-multi-action-variants-v2.json'),
  'utf8',
));

const recordedPages = {
  'shopify.open_orders': {
    target: 'record-target',
    html: "<nav aria-label='Main navigation'><a href='/home'>Home</a><a id='record-target' href='/orders'>Orders</a></nav>",
  },
  'shopify.open_order': {
    target: 'record-target',
    html: "<main data-section='Orders'><h1>Orders</h1><a id='record-target' href='/store/demo/orders/987654321'>#1042</a></main>",
  },
  'shopify.search_orders': {
    target: 'record-target',
    html: "<main data-section='Orders'><h1>Orders</h1><input id='record-target' type='search' aria-label='Search orders'></main>",
  },
  'shopify.filter_orders': {
    target: 'record-target',
    html: "<main data-section='Orders'><h1>Orders</h1><button id='record-target' aria-haspopup='menu'>Filter</button></main>",
  },
  'shopify.export_orders': {
    target: 'record-target',
    html: "<main data-section='Orders'><h1>Orders</h1><button id='record-target' data-action='export'>Export orders</button></main>",
  },
};

async function loadMatcher(page, html) {
  await page.setContent(`<!doctype html><html><body>${html}</body></html>`, { waitUntil: 'domcontentloaded' });
  await page.addScriptTag({ path: matcherPath });
}

async function recordFingerprint(page, actionId) {
  const setup = recordedPages[actionId];
  assert(setup, `missing recorded page for ${actionId}`);
  await loadMatcher(page, setup.html);
  return page.evaluate(({ targetId, expectedAction }) => {
    const el = document.getElementById(targetId);
    if (!el) throw new Error(`record target missing: ${targetId}`);
    const fp = NovaSemanticMatcher.fingerprint(el, null);
    fp.semanticActionId = NovaSemanticMatcher.recognizeSemanticAction('admin.shopify.com', fp);
    if (fp.semanticActionId !== expectedAction) {
      throw new Error(`recognition mismatch: expected=${expectedAction} actual=${fp.semanticActionId}`);
    }
    return fp;
  }, { targetId: setup.target, expectedAction: actionId });
}

async function evaluateVariant(page, fp, variant) {
  await loadMatcher(page, variant.html);
  return page.evaluate(async ({ fingerprint }) => {
    const result = await NovaSemanticMatcher.resolveWithSafeMenu(document, fingerprint, { useAdapter: true });
    return {
      decision: result.decision,
      targetId: result.target?.id || null,
      menuExpanded: !!result.menuExpanded,
      scores: (result.ranked || []).slice(0, 3).map((candidate) => ({
        id: candidate.el?.id || null,
        score: candidate.score,
      })),
    };
  }, { fingerprint: fp });
}

function assertVariant(actionId, variant, result) {
  const prefix = `${actionId}/${variant.id}`;
  switch (variant.expected) {
    case 'AUTO':
      assert.equal(result.decision, 'AUTO', `${prefix} expected AUTO; got ${result.decision}`);
      assert.equal(result.targetId, variant.target, `${prefix} AUTO selected wrong target`);
      return;
    case 'AUTO_AFTER_MENU':
      assert.equal(result.decision, 'AUTO', `${prefix} expected AUTO after menu; got ${result.decision}`);
      assert.equal(result.targetId, variant.target, `${prefix} selected wrong menu target`);
      assert.equal(result.menuExpanded, true, `${prefix} should expand safe menu`);
      return;
    case 'AUTO_OR_REVIEW': {
      // Holdouts exist to prove fail-safe behavior on unseen UI. ABSTAIN is
      // acceptable for a holdout because refusing to guess is safer than a
      // wrong AUTO. Known non-holdout variants still must resolve or escalate.
      const allowed = variant.holdout
        ? ['AUTO', 'AI_REVIEW', 'ABSTAIN']
        : ['AUTO', 'AI_REVIEW'];
      assert(allowed.includes(result.decision), `${prefix} expected ${allowed.join('/')}; got ${result.decision}`);
      if (result.decision === 'AUTO') {
        assert.equal(result.targetId, variant.target, `${prefix} wrong AUTO is forbidden`);
      }
      return;
    }
    case 'AI_REVIEW_OR_ABSTAIN':
      assert(['AI_REVIEW', 'ABSTAIN'].includes(result.decision), `${prefix} must fail closed; got ${result.decision}`);
      return;
    default:
      throw new Error(`${prefix} unknown expected mode: ${variant.expected}`);
  }
}

let browser;
try {
  browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  });
  const page = await browser.newPage();

  let total = 0;
  let holdouts = 0;
  let wrongAuto = 0;

  for (const action of fixture.actions) {
    const fp = await recordFingerprint(page, action.id);
    console.log(`PASS recognize ${action.id}`);

    for (const variant of action.variants || []) {
      const result = await evaluateVariant(page, fp, variant);
      try {
        assertVariant(action.id, variant, result);
      } catch (error) {
        if (result.decision === 'AUTO' && result.targetId !== variant.target) wrongAuto += 1;
        console.error('DETAIL', JSON.stringify({ action: action.id, variant: variant.id, expected: variant.expected, target: variant.target, result }));
        throw error;
      }
      if (variant.holdout) holdouts += 1;
      total += 1;
      console.log(`PASS ${action.id}/${variant.id} -> ${result.decision}${result.targetId ? `:${result.targetId}` : ''}`);
    }
  }

  assert.equal(fixture.actions.length, 5, 'Shopify gate must cover five semantic actions');
  assert(holdouts >= 8, `expected at least 8 holdouts; got ${holdouts}`);
  assert.equal(wrongAuto, 0, 'Wrong AUTO must remain zero');
  console.log(`RESULT actions=${fixture.actions.length} variants=${total} holdouts=${holdouts} wrongAuto=${wrongAuto}`);
} finally {
  if (browser) await browser.close();
}
