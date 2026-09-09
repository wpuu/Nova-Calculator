import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const extDir = path.join(root, 'prototypes', 'nova-macro-mv3');
const fixturePath = path.join(root, 'docs', 'growth', 'fixtures', 'macro-multi-action-variants-v2.json');
const agnesFixturePath = path.join(root, 'docs', 'growth', 'fixtures', 'agnes-candidate-review-v1.json');
const agnesRuntimeEvalPath = path.join(root, 'tools', 'agnes-candidate-review-eval.mjs');

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
  fail('optional host permission pool must remain available for a future explicit persistent-access feature');
}

const requiredFiles = [
  'manifest.json', 'background.js', 'semantic-matcher.js', 'content.js', 'ai-review-client.js',
  'popup.html', 'popup.js', 'ui-contract.json',
];
for (const file of requiredFiles) {
  if (!fs.existsSync(path.join(extDir, file))) fail(`required extension file missing: ${file}`);
}

for (const file of ['background.js', 'semantic-matcher.js', 'content.js', 'ai-review-client.js', 'popup.js']) {
  const source = read(file);
  try { new Function(source); } catch (error) { fail(`${file} has invalid JavaScript: ${error.message}`); }
  for (const pattern of [/\beval\s*\(/, /\bnew\s+Function\s*\(/, /importScripts\s*\(\s*['"]https?:/i]) {
    if (pattern.test(source)) fail(`${file} contains forbidden dynamic/remote execution pattern: ${pattern}`);
  }
}

const matcher = read('semantic-matcher.js');
const requiredActions = [
  'shopify.open_orders',
  'shopify.open_order',
  'shopify.search_orders',
  'shopify.filter_orders',
  'shopify.export_orders',
];
for (const action of requiredActions) {
  if (!matcher.includes(`'${action}'`)) fail(`semantic action missing from matcher: ${action}`);
}

for (const state of ['IDLE','RECORDING','SITE_ACCESS_REQUIRED','REPLAYING','AI_REVIEW','REQUIRES_CONFIRMATION','BLOCKED_SENSITIVE_INPUT']) {
  if (!contract.states?.includes(state)) fail(`UI contract state missing: ${state}`);
}
if (!contract.commands?.includes('RESUME_CURRENT_SITE_ONCE')) fail('one-time cross-origin resume command missing');
if (!contract.commands?.includes('SELECT_REPAIR_CANDIDATE')) fail('candidate-only repair command missing');
if (!contract.commands?.includes('ABSTAIN_REPAIR')) fail('repair abstain command missing');

const requiredUiRules = {
  requestSiteAccessOnlyFromExplicitUserGesture: true,
  doNotRequestAllUrlsAtInstall: true,
  defaultCrossOriginResumeUsesActiveTabOnly: true,
  persistentHostPermissionIsDeferred: true,
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
if ((fixture.actions || []).length !== 5) fail(`Shopify gate must cover exactly five actions; got ${(fixture.actions || []).length}`);
const holdoutCount = (fixture.actions || []).flatMap((action) => action.variants || []).filter((variant) => variant.holdout).length;
if (holdoutCount < 8) fail(`holdout coverage too small: ${holdoutCount}`);

const content = read('content.js');
for (const marker of [
  'BLOCKED_SENSITIVE_INPUT','REQUIRES_CONFIRMATION','NOVA_RECORD_STEP','resolveWithWait',
  'setNativeValue','HTMLInputElement.prototype','urlBefore','NOVA_CONTENT_STATE',
  'NOVA_APPLY_REVIEW_CHOICE','allowedCandidateIds','SELECT_LISTED_CANDIDATE_OR_ABSTAIN',
  'INVALID_AI_CANDIDATE','STALE_AI_REVIEW',
]) {
  if (!content.includes(marker)) fail(`content safety/orchestration marker missing: ${marker}`);
}

const background = read('background.js');
for (const marker of [
  "importScripts('ai-review-client.js')",
  'chrome.storage.session','chrome.tabs.onUpdated','stepWriteQueue','NOVA_RESUME_CURRENT','needsSiteAccess',
  'replayWaitingForDocument','waitingFromUrl','probeNavigationProgress',
  "if (!session.replayWaitingForDocument && !session.needsSiteAccess) return session;",
  'Delivery here is deliberately at-most-once','NOVA_SELECT_REPAIR_CANDIDATE','NOVA_ABSTAIN_REPAIR',
  'INVALID_AI_CANDIDATE','NOVA_APPLY_REVIEW_CHOICE','NOVA_RUN_AI_REVIEW','NOVA_SET_GATEWAY_SESSION',
]) {
  if (!background.includes(marker)) fail(`background navigation/orchestration marker missing: ${marker}`);
}

const aiClient = read('ai-review-client.js');
for (const marker of [
  "operation: 'MACRO_CANDIDATE_REVIEW'",
  'SELECT_LISTED_CANDIDATE_OR_ABSTAIN',
  'allowedCandidateIds',
  'CLIENT_CANDIDATE_REJECTED',
  'AI_REVIEW_IN_FLIGHT',
  'chrome.storage.session',
  "authorization: `Bearer ${config.sessionToken}`",
  "endpoint.protocol !== 'https:'",
  "senderUrl.startsWith(`chrome-extension://${runtimeId}/`)",
]) {
  if (!aiClient.includes(marker)) fail(`AI review client safety marker missing: ${marker}`);
}
for (const forbidden of [
  /selector\s*:/i,
  /xpath\s*:/i,
  /NOVA_PROVIDER_KEYS/,
  /apiKey\s*:/,
]) {
  if (forbidden.test(aiClient)) fail(`AI review client contains forbidden execution/secret marker: ${forbidden}`);
}

const popupHtml = read('popup.html');
if (!popupHtml.includes('id="ai-review"')) fail('explicit AI review button missing from popup');
const popup = read('popup.js');
if (popup.includes('chrome.permissions.request')) fail('default popup must not request persistent host permission');
if (!popup.includes("type: 'NOVA_RESUME_CURRENT'")) fail('popup one-time resume must route through background');
if (!popup.includes("type: 'NOVA_RUN_AI_REVIEW'")) fail('popup AI review must route through bounded background client');
if (!popup.includes('buttons.forEach((button) => { button.disabled = true; })')) {
  fail('popup must disable all controls during Agnes-backed requests');
}

for (const testFile of [
  'macro-background-state-eval.mjs',
  'macro-cross-origin-e2e.mjs',
  'macro-shopify-action-pack-e2e.mjs',
  'macro-ai-review-candidate-e2e.mjs',
  'agnes-candidate-review-eval.mjs',
]) {
  if (!fs.existsSync(path.join(root, 'tools', testFile))) fail(`required Macro gate missing: ${testFile}`);
}

if (!fs.existsSync(agnesFixturePath)) fail('bounded Agnes candidate-review fixture missing');
if (fs.existsSync(agnesRuntimeEvalPath)) {
  const agnesRuntimeEval = fs.readFileSync(agnesRuntimeEvalPath, 'utf8');
  for (const marker of [
    'SELECT_LOCAL_CANDIDATE_OR_ABSTAIN',
    'candidate_not_allowed',
    'forbidden_output_key',
    'max_in_flight_per_session: 1',
    '--contract-only',
  ]) {
    if (!agnesRuntimeEval.includes(marker)) fail(`bounded Agnes runtime evaluator marker missing: ${marker}`);
  }
}

if (!process.exitCode) {
  console.log(`Macro POC guard passed: ${requiredActions.length} actions, ${holdoutCount} holdouts, activeTab-first + Gateway-backed candidate-only AI review + global single-flight architecture intact.`);
}
