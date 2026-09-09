import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const root = process.cwd();
const script = path.join(root, 'tools', 'build-macro-release.mjs');
const extensionId = 'abcdefghijklmnopabcdefghijklmnop';
const gatewayOrigin = 'https://nova-gateway-preview.example.com';
const oauthClientId = 'nova-macro-release.apps.googleusercontent.com';

function run(extraEnv = {}, outDir = fs.mkdtempSync(path.join(os.tmpdir(), 'nova-macro-release-'))) {
  const result = spawnSync(process.execPath, [script, '--out', outDir], {
    cwd: root,
    env: {
      ...process.env,
      NOVA_MACRO_EXTENSION_ID: extensionId,
      NOVA_MACRO_GATEWAY_ORIGIN: gatewayOrigin,
      NOVA_MACRO_GOOGLE_OAUTH_CLIENT_ID: oauthClientId,
      ...extraEnv,
    },
    encoding: 'utf8',
  });
  return { ...result, outDir };
}

{
  const result = run();
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const manifest = JSON.parse(fs.readFileSync(path.join(result.outDir, 'manifest.json'), 'utf8'));
  assert.equal(manifest.oauth2.client_id, oauthClientId);
  assert.deepEqual(manifest.oauth2.scopes, ['openid']);
  assert.deepEqual(manifest.host_permissions, [`${gatewayOrigin}/*`]);
  assert.equal(manifest.permissions.includes('identity'), true);
  assert.equal(manifest.permissions.includes('identity.email'), false);

  const aiClient = fs.readFileSync(path.join(result.outDir, 'ai-review-client.js'), 'utf8');
  assert.equal(aiClient.includes(`const PUBLIC_GATEWAY_ORIGIN = ${JSON.stringify(gatewayOrigin)};`), true);
  assert.equal(aiClient.includes("const PUBLIC_GATEWAY_ORIGIN = '';"), false);

  const metadata = JSON.parse(fs.readFileSync(path.join(result.outDir, 'release-metadata.json'), 'utf8'));
  assert.deepEqual(metadata, {
    configVersion: 1,
    extensionId,
    gatewayOrigin,
    googleOauthClientId: oauthClientId,
    oauthScopes: ['openid'],
  });
}

for (const [name, env] of [
  ['wildcard Gateway', { NOVA_MACRO_GATEWAY_ORIGIN: 'https://*.example.com' }],
  ['Gateway path', { NOVA_MACRO_GATEWAY_ORIGIN: 'https://example.com/api' }],
  ['HTTP Gateway', { NOVA_MACRO_GATEWAY_ORIGIN: 'http://example.com' }],
  ['invalid extension id', { NOVA_MACRO_EXTENSION_ID: 'not-a-chrome-extension-id' }],
  ['invalid OAuth client', { NOVA_MACRO_GOOGLE_OAUTH_CLIENT_ID: 'not-google-oauth' }],
]) {
  const result = run(env);
  assert.notEqual(result.status, 0, `${name} unexpectedly succeeded`);
}

console.log('Macro release config gate passed: one public config generates exact OAuth + host + Gateway routing and invalid configs fail closed.');
