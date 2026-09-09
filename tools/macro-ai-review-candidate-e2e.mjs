import assert from 'node:assert/strict';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const extensionPath = path.join(root, 'prototypes', 'nova-macro-mv3');

const recordHtml = `<!doctype html>
<html><head><meta charset="utf-8"><title>Orders Record</title></head>
<body>
  <main data-section="Orders"><h1>Orders</h1>
    <button id="record-export" data-action="export">Export orders</button>
  </main>
  <script>
    document.getElementById('record-export').addEventListener('click', () => {
      document.body.dataset.clicked = 'record';
    });
  </script>
</body></html>`;

const ambiguousHtml = `<!doctype html>
<html><head><meta charset="utf-8"><title>Orders Ambiguous</title></head>
<body>
  <main data-section="Orders"><h1>Orders</h1>
    <button id="first" data-action="export">Export orders</button>
    <button id="second" data-action="export">Export orders</button>
  </main>
  <script>
    document.getElementById('first').addEventListener('click', () => {
      document.body.dataset.clicked = 'first';
    });
    document.getElementById('second').addEventListener('click', () => {
      document.body.dataset.clicked = 'second';
    });
  </script>
</body></html>`;

function startServer() {
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      res.setHeader('content-type', 'text/html; charset=utf-8');
      res.end(req.url === '/record' ? recordHtml : ambiguousHtml);
    });
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      resolve({ server, origin: `http://127.0.0.1:${server.address().port}` });
    });
  });
}

async function waitForWorker(browser) {
  const target = await browser.waitForTarget(
    (candidate) =>
      candidate.type() === 'service_worker' &&
      candidate.url().startsWith('chrome-extension://') &&
      candidate.url().endsWith('/background.js'),
    { timeout: 15000 },
  );
  const worker = await target.worker();
  assert(worker, 'extension service worker unavailable');
  return { worker, extensionId: new URL(target.url()).host };
}

async function findExtension(browser, extensionId) {
  const extensions = await browser.extensions();
  const extension = extensions.get(extensionId);
  assert(extension, 'Nova Macro extension missing');
  return extension;
}

async function openPopup(browser, extension, page) {
  const oldTargets = new Set(browser.targets());
  await page.triggerExtensionAction(extension);
  const target = await browser.waitForTarget(
    (candidate) =>
      !oldTargets.has(candidate) &&
      candidate.type() === 'page' &&
      candidate.url().includes(extension.id) &&
      candidate.url().endsWith('/popup.html'),
    { timeout: 10000 },
  );
  const popup = await target.asPage();
  assert(popup, 'popup page unavailable');
  await popup.waitForSelector('#output');
  return popup;
}

async function clickPopupAndWait(popup, selector) {
  await popup.click(selector);
  await popup.waitForFunction(() => {
    const text = document.querySelector('#output')?.textContent || '';
    return text && text !== 'Working…';
  }, { timeout: 10000 });
  return popup.$eval('#output', (node) => node.textContent);
}

async function getSession(worker) {
  return worker.evaluate(async () => {
    const stored = await chrome.storage.session.get('novaMacroPocSession');
    return stored.novaMacroPocSession || null;
  });
}

async function waitForSession(worker, predicate, timeoutMs = 12000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const session = await getSession(worker);
    if (session && predicate(session)) return session;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error('Timed out waiting for extension session');
}

async function startReplayToReview(browser, extension, page, worker, origin) {
  await page.goto(`${origin}/ambiguous`, { waitUntil: 'domcontentloaded' });
  const popup = await openPopup(browser, extension, page);
  const replayOutput = await clickPopupAndWait(popup, '#replay');
  assert.match(replayOutput, /"mode":\s*"REPLAYING"/);
  return waitForSession(worker, (session) => session.mode === 'AI_REVIEW');
}

function assertCandidateOnlyReview(review) {
  assert(review, 'AI_REVIEW payload missing');
  assert.equal(review.policy, 'SELECT_LISTED_CANDIDATE_OR_ABSTAIN');
  assert(Array.isArray(review.candidates) && review.candidates.length >= 2, 'review must contain local candidates');
  assert.deepEqual(
    review.allowedCandidateIds,
    review.candidates.map((candidate) => candidate.id),
    'allowed IDs must exactly match locally emitted candidates',
  );
  const serialized = JSON.stringify(review).toLowerCase();
  assert(!serialized.includes('selector'), 'AI review payload must not expose executable selectors');
  for (const candidate of review.candidates) {
    assert(/^candidate_\d+$/.test(candidate.id), `invalid local candidate id: ${candidate.id}`);
    assert(!Object.prototype.hasOwnProperty.call(candidate, 'el'), 'DOM element must never cross AI boundary');
  }
}

const { server, origin } = await startServer();
let browser;
try {
  browser = await puppeteer.launch({
    headless: 'new',
    pipe: true,
    enableExtensions: [extensionPath],
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  });

  const { worker, extensionId } = await waitForWorker(browser);
  const extension = await findExtension(browser, extensionId);
  const page = await browser.newPage();

  // Record one unambiguous Export action.
  await page.goto(`${origin}/record`, { waitUntil: 'domcontentloaded' });
  let popup = await openPopup(browser, extension, page);
  await clickPopupAndWait(popup, '#start');
  await page.click('#record-export');
  await page.waitForFunction(() => document.body.dataset.clicked === 'record');
  popup = await openPopup(browser, extension, page);
  const stopOutput = await clickPopupAndWait(popup, '#stop');
  assert.match(stopOutput, /"mode":\s*"SAVED"/);

  const saved = await worker.evaluate(async () => {
    const data = await chrome.storage.local.get('novaMacroPocLast');
    return data.novaMacroPocLast || [];
  });
  assert.equal(saved.length, 1, `expected one saved step, got ${saved.length}`);
  assert.equal(saved[0].fingerprint?.semanticActionId, 'shopify.export_orders');

  // Replay into an intentionally ambiguous DOM. Local core must stop in AI_REVIEW.
  let reviewSession = await startReplayToReview(browser, extension, page, worker, origin);
  const review = reviewSession.lastResult?.review;
  assertCandidateOnlyReview(review);
  assert.equal(await page.evaluate(() => document.body.dataset.clicked || ''), '');
  console.log('PASS local core emits candidate-only AI_REVIEW without executing a target');

  // An invented model output is rejected before content execution.
  const invalid = await worker.evaluate(
    async ({ reviewId }) => selectRepairCandidate({ reviewId, candidateId: 'candidate_999' }),
    { reviewId: review.reviewId },
  );
  assert.equal(invalid.ok, false);
  assert.equal(invalid.error, 'INVALID_AI_CANDIDATE');
  reviewSession = await getSession(worker);
  assert.equal(reviewSession.mode, 'AI_REVIEW');
  assert.equal(await page.evaluate(() => document.body.dataset.clicked || ''), '');
  console.log('PASS invented candidate ID is rejected with no side effect');

  // Choose the locally emitted candidate whose stable local metadata says id=second.
  const second = review.candidates.find((candidate) => candidate.attrs?.id === 'second');
  assert(second, 'expected locally generated candidate for #second');
  const selected = await worker.evaluate(
    async ({ reviewId, candidateId }) => selectRepairCandidate({ reviewId, candidateId }),
    { reviewId: review.reviewId, candidateId: second.id },
  );
  assert.notEqual(selected?.error, 'INVALID_AI_CANDIDATE');
  await page.waitForFunction(() => document.body.dataset.clicked === 'second', { timeout: 10000 });
  const completed = await waitForSession(worker, (session) => session.mode === 'COMPLETED');
  assert.equal(completed.replayIndex, 1);
  assert.equal(completed.lastResult?.status, 'COMPLETED');
  console.log('PASS listed candidate ID executes only its locally mapped DOM element');

  // A new review's IDs become unusable after the document is replaced.
  reviewSession = await startReplayToReview(browser, extension, page, worker, origin);
  const staleReview = reviewSession.lastResult?.review;
  assertCandidateOnlyReview(staleReview);
  const staleChoice = staleReview.candidates.find((candidate) => candidate.attrs?.id === 'second');
  assert(staleChoice, 'expected stale candidate for #second');
  await page.reload({ waitUntil: 'domcontentloaded' });
  const staleResult = await worker.evaluate(
    async ({ reviewId, candidateId }) => selectRepairCandidate({ reviewId, candidateId }),
    { reviewId: staleReview.reviewId, candidateId: staleChoice.id },
  );
  assert.equal(staleResult.mode, 'ABSTAIN');
  assert.equal(await page.evaluate(() => document.body.dataset.clicked || ''), '');
  console.log('PASS document replacement invalidates old candidate mapping and fails closed');

  console.log('RESULT 3/3');
} finally {
  if (browser) await browser.close();
  await new Promise((resolve) => server.close(resolve));
}
