import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import puppeteer from 'puppeteer';

const root = process.cwd();
const sourceDir = path.join(root, 'prototypes', 'nova-macro-mv3');
const identity = JSON.parse(fs.readFileSync(
  path.join(root, 'tools', 'fixtures', 'macro-dev-extension-identity-v1.json'),
  'utf8',
));
const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'nova-macro-stable-id-'));
const extensionDir = path.join(tempRoot, 'extension');
fs.cpSync(sourceDir, extensionDir, { recursive: true });

const manifestPath = path.join(extensionDir, 'manifest.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
manifest.key = identity.publicKey;
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');

let browser;
try {
  browser = await puppeteer.launch({
    headless: 'new',
    pipe: true,
    enableExtensions: [extensionDir],
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  });

  const target = await browser.waitForTarget(
    (candidate) => (
      candidate.type() === 'service_worker'
      && candidate.url().startsWith('chrome-extension://')
      && candidate.url().endsWith('/background.js')
    ),
    { timeout: 15000 },
  );
  const chromeAssignedId = new URL(target.url()).host;
  assert.equal(
    chromeAssignedId,
    identity.extensionId,
    `Chrome assigned ${chromeAssignedId}; expected stable dev id ${identity.extensionId}`,
  );

  const extension = (await browser.extensions()).get(identity.extensionId);
  assert(extension, `browser.extensions() did not expose ${identity.extensionId}`);
  assert.equal(extension.name, 'Nova Macro Semantic POC');
  assert.equal(extension.enabled, true);

  console.log(`PASS Chrome assigned stable development Extension ID ${chromeAssignedId}`);
  console.log('PASS manifest public key controls unpacked MV3 identity');
  console.log('RESULT 2/2');
} finally {
  if (browser) await browser.close();
  fs.rmSync(tempRoot, { recursive: true, force: true });
}
