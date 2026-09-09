import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const root = process.cwd();
const identityPath = path.join(root, 'tools', 'fixtures', 'macro-dev-extension-identity-v1.json');
const releaseBuilder = path.join(root, 'tools', 'build-macro-release.mjs');
const identity = JSON.parse(fs.readFileSync(identityPath, 'utf8'));

const gatewayOrigin = String(process.env.NOVA_MACRO_GATEWAY_ORIGIN ?? '').trim();
const oauthClientId = String(process.env.NOVA_MACRO_GOOGLE_OAUTH_CLIENT_ID ?? '').trim();
if (!gatewayOrigin) throw new Error('NOVA_MACRO_GATEWAY_ORIGIN is required');
if (!oauthClientId) throw new Error('NOVA_MACRO_GOOGLE_OAUTH_CLIENT_ID is required');

const args = process.argv.slice(2);
const outIndex = args.indexOf('--out');
const requestedOutput = outIndex >= 0 ? String(args[outIndex + 1] || '') : 'dist/nova-macro-dev';
const outputDir = path.resolve(root, requestedOutput);

const result = spawnSync(process.execPath, [releaseBuilder, '--out', outputDir], {
  cwd: root,
  env: {
    ...process.env,
    NOVA_MACRO_EXTENSION_ID: identity.extensionId,
    NOVA_MACRO_EXTENSION_PUBLIC_KEY: identity.publicKey,
    NOVA_MACRO_GATEWAY_ORIGIN: gatewayOrigin,
    NOVA_MACRO_GOOGLE_OAUTH_CLIENT_ID: oauthClientId,
  },
  encoding: 'utf8',
  stdio: ['ignore', 'pipe', 'pipe'],
});

if (result.status !== 0) {
  process.stderr.write(result.stderr || result.stdout || 'Macro dev build failed\n');
  process.exit(result.status || 1);
}

const metadataPath = path.join(outputDir, 'release-metadata.json');
const metadata = JSON.parse(fs.readFileSync(metadataPath, 'utf8'));
metadata.identityPurpose = identity.purpose;
metadata.developmentOnly = true;
fs.writeFileSync(metadataPath, `${JSON.stringify(metadata, null, 2)}\n`, 'utf8');

console.log(`Macro dev build configured with stable extension id ${identity.extensionId}: ${outputDir}`);
