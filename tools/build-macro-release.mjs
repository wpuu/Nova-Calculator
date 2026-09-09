import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const sourceDir = path.join(root, 'prototypes', 'nova-macro-mv3');
const args = process.argv.slice(2);
const outIndex = args.indexOf('--out');
const outputDir = path.resolve(root, outIndex >= 0 ? String(args[outIndex + 1] || '') : 'dist/nova-macro-mv3');

const extensionId = normalizeExtensionId(process.env.NOVA_MACRO_EXTENSION_ID);
const gatewayOrigin = normalizeGatewayOrigin(process.env.NOVA_MACRO_GATEWAY_ORIGIN);
const oauthClientId = normalizeOauthClientId(process.env.NOVA_MACRO_GOOGLE_OAUTH_CLIENT_ID);

if (!fs.existsSync(sourceDir)) throw new Error(`Macro source directory missing: ${sourceDir}`);
if (!outputDir || outputDir === root || outputDir === sourceDir) throw new Error('release output directory is unsafe');

fs.rmSync(outputDir, { recursive: true, force: true });
fs.mkdirSync(outputDir, { recursive: true });
for (const entry of fs.readdirSync(sourceDir, { withFileTypes: true })) {
  if (!entry.isFile()) continue;
  fs.copyFileSync(path.join(sourceDir, entry.name), path.join(outputDir, entry.name));
}

const manifestPath = path.join(outputDir, 'manifest.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
manifest.oauth2 = {
  client_id: oauthClientId,
  scopes: ['openid'],
};
manifest.host_permissions = [`${gatewayOrigin}/*`];
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');

const aiClientPath = path.join(outputDir, 'ai-review-client.js');
const source = fs.readFileSync(aiClientPath, 'utf8');
const marker = "const PUBLIC_GATEWAY_ORIGIN = '';";
const count = source.split(marker).length - 1;
if (count !== 1) throw new Error(`expected exactly one public Gateway marker, found ${count}`);
const configuredSource = source.replace(
  marker,
  `const PUBLIC_GATEWAY_ORIGIN = ${JSON.stringify(gatewayOrigin)};`,
);
fs.writeFileSync(aiClientPath, configuredSource, 'utf8');

const metadata = {
  configVersion: 1,
  extensionId,
  gatewayOrigin,
  googleOauthClientId: oauthClientId,
  oauthScopes: ['openid'],
};
fs.writeFileSync(
  path.join(outputDir, 'release-metadata.json'),
  `${JSON.stringify(metadata, null, 2)}\n`,
  'utf8',
);

verifyBuiltRelease({ outputDir, extensionId, gatewayOrigin, oauthClientId });
console.log(`Macro release configured: ${outputDir}`);

function verifyBuiltRelease({ outputDir, extensionId, gatewayOrigin, oauthClientId }) {
  const manifest = JSON.parse(fs.readFileSync(path.join(outputDir, 'manifest.json'), 'utf8'));
  if (!manifest.permissions?.includes('identity')) throw new Error('release manifest lost identity permission');
  if (manifest.permissions?.includes('identity.email')) throw new Error('release manifest must not request identity.email');
  if (manifest.oauth2?.client_id !== oauthClientId) throw new Error('OAuth client id mismatch');
  if (JSON.stringify(manifest.oauth2?.scopes) !== JSON.stringify(['openid'])) {
    throw new Error('OAuth scopes must remain exactly openid');
  }
  if (JSON.stringify(manifest.host_permissions) !== JSON.stringify([`${gatewayOrigin}/*`])) {
    throw new Error('release manifest must contain exactly one Gateway host permission');
  }
  const aiClient = fs.readFileSync(path.join(outputDir, 'ai-review-client.js'), 'utf8');
  if (!aiClient.includes(`const PUBLIC_GATEWAY_ORIGIN = ${JSON.stringify(gatewayOrigin)};`)) {
    throw new Error('AI client Gateway origin mismatch');
  }
  if (aiClient.includes("const PUBLIC_GATEWAY_ORIGIN = '';")) {
    throw new Error('release AI client remained fail-closed/unconfigured');
  }
  const metadata = JSON.parse(fs.readFileSync(path.join(outputDir, 'release-metadata.json'), 'utf8'));
  if (metadata.extensionId !== extensionId || metadata.gatewayOrigin !== gatewayOrigin) {
    throw new Error('release metadata mismatch');
  }
}

function normalizeExtensionId(value) {
  const text = String(value ?? '').trim();
  if (!/^[a-p]{32}$/.test(text)) {
    throw new Error('NOVA_MACRO_EXTENSION_ID must be a 32-character Chrome extension id (a-p only)');
  }
  return text;
}

function normalizeGatewayOrigin(value) {
  const text = String(value ?? '').trim();
  let url;
  try { url = new URL(text); } catch { throw new Error('NOVA_MACRO_GATEWAY_ORIGIN must be one exact HTTPS origin'); }
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) {
    throw new Error('NOVA_MACRO_GATEWAY_ORIGIN must be one exact HTTPS origin');
  }
  if (url.pathname !== '/' && url.pathname !== '') {
    throw new Error('NOVA_MACRO_GATEWAY_ORIGIN must not contain a path');
  }
  if (!isExactDnsHostname(url.hostname)) {
    throw new Error('NOVA_MACRO_GATEWAY_ORIGIN must use one exact routable DNS hostname');
  }
  return url.origin;
}

function isExactDnsHostname(hostname) {
  const host = String(hostname ?? '').toLowerCase();
  if (!host || host.length > 253 || host === 'localhost' || host.endsWith('.localhost')) return false;
  if (host.includes('*') || !/^[a-z0-9.-]+$/.test(host)) return false;
  const labels = host.split('.');
  if (labels.length < 2) return false;
  return labels.every((label) => (
    label.length >= 1
    && label.length <= 63
    && /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/.test(label)
  ));
}

function normalizeOauthClientId(value) {
  const text = String(value ?? '').trim();
  if (!/^[A-Za-z0-9._-]+\.apps\.googleusercontent\.com$/.test(text) || text.length > 300) {
    throw new Error('NOVA_MACRO_GOOGLE_OAUTH_CLIENT_ID must be a Google OAuth client id');
  }
  return text;
}
