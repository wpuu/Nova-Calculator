import assert from 'node:assert/strict';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const extensionPath = path.join(root, 'prototypes', 'nova-macro-mv3');

const startHtml = `<!doctype html>
<html>
<head><meta charset="utf-8"><title>Macro Start</title></head>
<body>
  <nav aria-label="Main menu">
    <a id="orders" href="/orders">Orders</a>
  </nav>
</body>
</html>`;

const ordersHtml = `<!doctype html>
<html>
<head><meta charset="utf-8"><title>Orders</title></head>
<body>
  <main aria-label="Orders">
    <h1>Orders</h1>
    <label>Search orders <input id="search" type="search" aria-label="Search orders"></label>
    <button id="export" data-action="export">Export orders</button>
    <output id="result">idle</output>
  </main>
  <script>
    document.getElementById('export').addEventListener('click', () => {
      document.body.dataset.exported = 'yes';
      document.getElementById('result').textContent = 'exported';
    });
  </script>
</body>
</html>`;

function startServer() {
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      res.setHeader('content-type', 'text/html; charset=utf-8');
      if (req.url === '/orders') {
        res.end(ordersHtml);
        return;
      }
      res.end(startHtml);
    });
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      resolve({ server, origin: `http://127.0.0.1:${address.port}` });
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
  assert(worker, 'extension service worker target exists but worker is unavailable');
  return { worker, extensionId: new URL(target.url()).host };
}

async function openPopup(browser, worker) {
  const existing = new Set(
    browser.targets()
      .filter((target) => target.type() === 'page' && target.url().endsWith('/popup.html'))
      .map((target) => target.url()),
  );
  await worker.evaluate(() => chrome.action.openPopup());
  const target = await browser.waitForTarget(
    (candidate) =>
      candidate.type() === 'page' &&
      candidate.url().startsWith('chrome-extension://') &&
      candidate.url().endsWith('/popup.html') &&
      (!existing.has(candidate.url()) || candidate.url().endsWith('/popup.html')),
    { timeout: 10000 },
  );
  const popup = await target.asPage();
  assert(popup, 'popup target could not be converted to a page');
  await popup.waitForSelector('#output');
  return popup;
}

async function clickPopupAndWait(popup, selector) {
  await popup.click(selector);
  await popup.waitForFunction(() => {
    const text = document.querySelector('#output')?.textContent || '';
    return text !== 'Working…' && text.length > 0;
  }, { timeout: 10000 });
  return popup.$eval('#output', (node) => node.textContent);
}

async function waitForSession(worker, predicate, timeoutMs = 12000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    const session = await worker.evaluate(async () => {
      const stored = await chrome.storage.session.get('novaMacroPocSession');
      return stored.novaMacroPocSession || null;
    });
    if (session && predicate(session)) return session;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error('Timed out waiting for extension session state');
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
  console.log(`Loaded Nova Macro extension: ${extensionId}`);

  const page = await browser.newPage();
  await page.goto(`${origin}/start`, { waitUntil: 'domcontentloaded' });

  // Opening the popup is an extension action and grants activeTab for the
  // current origin. Same-origin navigation keeps that temporary access.
  let popup = await openPopup(browser, worker);
  const startOutput = await clickPopupAndWait(popup, '#start');
  assert.match(startOutput, /"mode":\s*"RECORDING"/);

  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
    page.click('#orders'),
  ]);
  assert.equal(page.url(), `${origin}/orders`);

  await page.type('#search', '#1042');
  await page.$eval('#search', (input) => input.blur());
  await page.click('#export');
  await page.waitForFunction(() => document.body.dataset.exported === 'yes');

  popup = await openPopup(browser, worker);
  const stopOutput = await clickPopupAndWait(popup, '#stop');
  assert.match(stopOutput, /"mode":\s*"SAVED"/);

  const saved = await worker.evaluate(async () => {
    const stored = await chrome.storage.local.get('novaMacroPocLast');
    return stored.novaMacroPocLast || [];
  });
  assert.equal(saved.length, 3, `expected 3 recorded steps, got ${saved.length}`);
  assert.deepEqual(saved.map((step) => step.type), ['click', 'input', 'click']);
  assert.equal(saved[1].value, '#1042');

  // Replay the saved macro from a clean start page. It must navigate, resume in
  // the new document, restore the controlled input value, and click Export once.
  await page.goto(`${origin}/start`, { waitUntil: 'domcontentloaded' });
  popup = await openPopup(browser, worker);
  const replayOutput = await clickPopupAndWait(popup, '#replay');
  assert.match(replayOutput, /"mode":\s*"REPLAYING"/);

  await page.waitForFunction(
    () => location.pathname === '/orders',
    { timeout: 12000 },
  );
  await page.waitForFunction(
    () => document.querySelector('#search')?.value === '#1042',
    { timeout: 12000 },
  );
  await page.waitForFunction(
    () => document.body.dataset.exported === 'yes',
    { timeout: 12000 },
  );

  const completed = await waitForSession(worker, (session) => session.mode === 'COMPLETED');
  assert.equal(completed.replayIndex, 3);

  console.log('PASS real MV3 load');
  console.log('PASS record -> same-origin navigation -> continue recording');
  console.log('PASS stop -> persist 3 semantic steps');
  console.log('PASS replay -> navigate -> resume -> input -> export');
  console.log('RESULT 4/4');
} finally {
  if (browser) await browser.close();
  await new Promise((resolve) => server.close(resolve));
}
