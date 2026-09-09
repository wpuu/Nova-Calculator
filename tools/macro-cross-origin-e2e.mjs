import assert from 'node:assert/strict';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const extensionPath = path.join(root, 'prototypes', 'nova-macro-mv3');

function startServer() {
  return new Promise((resolve, reject) => {
    let port;
    const server = http.createServer((req, res) => {
      res.setHeader('content-type', 'text/html; charset=utf-8');
      if (req.url === '/orders') {
        res.end(`<!doctype html><html><head><meta charset="utf-8"><title>Partner Orders</title></head><body>
<main aria-label="Orders"><h1>Orders</h1>
<label>Search orders <input id="search" type="search" aria-label="Search orders"></label>
<button id="export" data-action="export">Export orders</button><output id="result">idle</output></main>
<script>document.getElementById('export').addEventListener('click',()=>{document.body.dataset.exported='yes';document.getElementById('result').textContent='exported';});</script>
</body></html>`);
        return;
      }
      res.end(`<!doctype html><html><head><meta charset="utf-8"><title>Origin A</title></head><body>
<nav aria-label="Main menu"><a id="cross" href="http://localhost:${port}/orders">Open partner orders</a></nav>
</body></html>`);
    });
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      port = server.address().port;
      resolve({ server, originA: `http://127.0.0.1:${port}`, originB: `http://localhost:${port}` });
    });
  });
}

async function waitForWorker(browser) {
  const target = await browser.waitForTarget(
    (candidate) => candidate.type() === 'service_worker' && candidate.url().endsWith('/background.js'),
    { timeout: 15000 },
  );
  const worker = await target.worker();
  assert(worker, 'extension service worker unavailable');
  return { worker, extensionId: new URL(target.url()).host };
}

async function findExtension(browser, extensionId) {
  const extension = (await browser.extensions()).get(extensionId);
  assert(extension?.enabled, 'Nova Macro extension missing or disabled');
  return extension;
}

async function openPopup(browser, extension, page) {
  const oldTargets = new Set(browser.targets());
  await page.triggerExtensionAction(extension);
  const target = await browser.waitForTarget(
    (candidate) => !oldTargets.has(candidate) && candidate.type() === 'page' && candidate.url().includes(extension.id) && candidate.url().endsWith('/popup.html'),
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
  return worker.evaluate(async () => (await chrome.storage.session.get('novaMacroPocSession')).novaMacroPocSession || null);
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

async function waitForUrl(page, expected, timeoutMs = 12000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (page.url() === expected) return;
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  throw new Error(`Timed out waiting for ${expected}; current=${page.url()}`);
}

const { server, originA, originB } = await startServer();
let browser;
try {
  browser = await puppeteer.launch({
    headless: 'new', pipe: true, enableExtensions: [extensionPath],
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  });

  const { worker, extensionId } = await waitForWorker(browser);
  const extension = await findExtension(browser, extensionId);
  const page = await browser.newPage();

  await page.goto(`${originA}/start`, { waitUntil: 'domcontentloaded' });
  let popup = await openPopup(browser, extension, page);
  assert.match(await clickPopupAndWait(popup, '#start'), /"mode":\s*"RECORDING"/);

  await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }), page.click('#cross')]);
  assert.equal(page.url(), `${originB}/orders`);
  const pausedRecord = await waitForSession(worker, (s) => s.mode === 'RECORDING' && s.needsSiteAccess === true);
  assert.equal(pausedRecord.steps.length, 1);
  console.log('PASS recording pauses on origin B before resume');

  // Opening the extension on B grants activeTab for B. Resume uses that
  // temporary grant only; it must not persist a host permission.
  popup = await openPopup(browser, extension, page);
  assert.match(await clickPopupAndWait(popup, '#grant'), /"mode":\s*"RECORDING"/);
  await waitForSession(worker, (s) => s.mode === 'RECORDING' && !s.needsSiteAccess);
  assert.equal(
    await worker.evaluate(async () => chrome.permissions.contains({ origins: ['http://localhost/*'] })),
    false,
    'Resume once must not persist origin B host permission',
  );

  await page.type('#search', '#2048');
  await page.$eval('#search', (input) => input.blur());
  await page.click('#export');
  await page.waitForFunction(() => document.body.dataset.exported === 'yes');
  popup = await openPopup(browser, extension, page);
  assert.match(await clickPopupAndWait(popup, '#stop'), /"mode":\s*"SAVED"/);

  const saved = await worker.evaluate(async () => (await chrome.storage.local.get('novaMacroPocLast')).novaMacroPocLast || []);
  assert.equal(saved.length, 3);
  assert.deepEqual(saved.map((step) => step.type), ['click', 'input', 'click']);
  assert.equal(saved[1].value, '#2048');
  console.log('PASS one-time activeTab resume preserves all 3 recorded steps');

  // Because no persistent host permission was stored, replay must pause on B again.
  await page.goto(`${originA}/start`, { waitUntil: 'domcontentloaded' });
  popup = await openPopup(browser, extension, page);
  assert.match(await clickPopupAndWait(popup, '#replay'), /"mode":\s*"REPLAYING"/);

  await waitForUrl(page, `${originB}/orders`);
  const pausedReplay = await waitForSession(worker, (s) => s.mode === 'REPLAYING' && s.needsSiteAccess === true);
  assert.equal(pausedReplay.replayIndex, 1);
  assert.equal(await page.$eval('#search', (input) => input.value), '');
  console.log('PASS replay pauses before protected B-site steps');

  popup = await openPopup(browser, extension, page);
  assert.match(await clickPopupAndWait(popup, '#grant'), /"mode":\s*"REPLAYING"/);
  await page.waitForFunction(() => document.querySelector('#search')?.value === '#2048', { timeout: 12000 });
  await page.waitForFunction(() => document.body.dataset.exported === 'yes', { timeout: 12000 });
  const completed = await waitForSession(worker, (s) => s.mode === 'COMPLETED');
  assert.equal(completed.replayIndex, 3);
  assert.equal(
    await worker.evaluate(async () => chrome.permissions.contains({ origins: ['http://localhost/*'] })),
    false,
  );
  console.log('PASS one-time resume completes replay without persistent host access');
  console.log('RESULT 4/4');
} finally {
  if (browser) await browser.close();
  await new Promise((resolve) => server.close(resolve));
}
