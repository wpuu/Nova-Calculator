import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const extDir = path.join(root, 'prototypes', 'nova-macro-mv3');
const fixturePath = path.join(root, 'docs', 'growth', 'fixtures', 'macro-multi-action-variants-v2.json');

const fail = (message) => {
  console.error(`Macro POC guard failed: ${message}`);
  process.exitCode = 1;
};

const read = (name) => fs.readFileSync(path.join(extDir, name), 'utf8');
const json = (name) => JSON.parse(read(name));

const manifest = json('manifest.json');
const contract = json('ui-contract.json');
const fixture = JSON.parse(fs.readFileSync(fixturePath, 'utf8'));

if (manifest.manifest_version !== 3) fail('manifest_version must be 3');
if (manifest.background?.service_worker !== 'background.js') fail('background service worker missing');

const requiredPermissions = ['activeTab', 'scripting', 'storage'];
for (const permission of requiredPermissions) {
  if (!manifest.permissions?.includes(permission)) fail(`required permission missing: ${permission}`);
}

const forbiddenInstallPermissions = ['<all_urls>', 'tabs', 'debugger', 'webRequest', 'cookies'];
for (const permission of forbiddenInstallPermissions) {
  if (manifest.permissions?.includes(permission)) fail(`forbidden install-time permission found: ${permission}`);
}

if (manifest.host_permissions?.length) fail('host_permissions must not be requested at install time');
const optionalHosts = new Set(manifest.optional_host_permissions || []);
if (!optionalHosts.has('https://*/*') || !optionalHosts.has('http://*/*')) {
  fail('optional host permission pool must support explicit current-site grants');
}

const requiredFiles = [
  'manifest.json',
  'background.js',
  'semantic-matcher.js',
  'content.js',
  'popup.html',
  'popup.js',
  'ui-contract.json',
];
for (const file of requiredFiles) {
  if (!fs.existsSync(path.join(extDir, file))) fail(`required extension file missing: ${file}`);
}

const javascriptFiles = ['background.js', 'semantic-matcher.js', 'content.js', 'popup.js'];
for (const file of javascriptFiles) {
  const source = read(file);
  try {
    new Function(source);
  } catch (error) {
    fail(`${file} has invalid JavaScript: ${error.message}`);
  }
  const forbiddenRemoteExecution = [
    /\beval\s*\(/,
    /\bnew\s+Function\s*\(/,
    /importScripts\s*\(\s*['"]https?:/i,
  ];
  for (const pattern of forbiddenRemoteExecution) {
    if (pattern.test(source)) fail(`${file} contains forbidden dynamic/remote execution pattern: ${pattern}`);
  }
}

const matcher = read('semantic-matcher.js');
const requiredActions = [
  'shopify.open_orders',
  'shopify.search_orders',
  'shopify.filter_orders',
  'shopify.export_orders',
];
for (const action of requiredActions) {
  if (!matcher.includes(`'${action}'`)) fail(`semantic action missing from matcher: ${action}`);
}

const requiredStates = [
  'IDLE',
  'RECORDING',
  'SITE_ACCESS_REQUIRED',
  'REPLAYING',
  'AI_REVIEW',
  'REQUIRES_CONFIRMATION',
  'BLOCKED_SENSITIVE_INPUT',
];
for (const state of requiredStates) {
  if (!contract.states?.includes(state)) fail(`UI contract state missing: ${state}`);
}

const requiredUiRules = {
  requestSiteAccessOnlyFromExplicitUserGesture: true,
  doNotRequestAllUrlsAtInstall: true,
  aiCannotGenerateExecutableSelector: true,
  aiCandidatesMustComeFromCore: true,
  dangerousActionsRequireExplicitConfirmation: true,
  passwordAndOtpMustNeverBePersisted: true,
  singleAgnesFlight: true,
  disableAllAgnesEntrypointsDuringRequest: true,
};
for (const [key, expected] of Object.entries(requiredUiRules)) {
  if (contract.uiRules?.[key] !== expected) fail(`UI contract rule changed or missing: ${key}`);
}

const fixtureActions = new Set((fixture.actions || []).map((action) => action.id));
for (const action of requiredActions) {
  if (!fixtureActions.has(action)) fail(`fixture coverage missing semantic action: ${action}`);
}
const holdoutCount = (fixture.actions || [])
  .flatMap((action) => action.variants || [])
  .filter((variant) => variant.holdout).length;
if (holdoutCount < 4) fail(`holdout coverage too small: ${holdoutCount}`);

const content = read('content.js');
for (const marker of [
  'BLOCKED_SENSITIVE_INPUT',
  'REQUIRES_CONFIRMATION',
  'NOVA_RECORD_STEP',
  'resolveWithWait',
  'setNativeValue',
  'HTMLInputElement.prototype',
]) {
  if (!content.includes(marker)) fail(`content safety/orchestration marker missing: ${marker}`);
}

const background = read('background.js');
for (const marker of [
  'chrome.storage.session',
  'chrome.tabs.onUpdated',
  'stepWriteQueue',
  'NOVA_RESUME_CURRENT',
  'needsSiteAccess',
]) {
  if (!background.includes(marker)) fail(`background navigation/orchestration marker missing: ${marker}`);
}

if (!process.exitCode) {
  console.log(`Macro POC guard passed: ${requiredActions.length} actions, ${holdoutCount} holdouts, MV3 optional-site permission architecture intact.`);
}
