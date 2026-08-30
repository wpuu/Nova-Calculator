import test from 'node:test';
import assert from 'node:assert/strict';

import { anonymousSessionRequestHash } from '../src/play-integrity-request.mjs';
import { PlayIntegrityInstallationProofVerifier } from '../src/play-integrity-proof-verifier.mjs';

const NOW = 1_800_000_000_000;
const PACKAGE = 'com.example.nova';

function verdict(overrides = {}) {
  return {
    requestDetails: {
      requestPackageName: PACKAGE,
      requestHash: anonymousSessionRequestHash('install-123'),
      timestampMillis: String(NOW - 1_000),
      ...(overrides.requestDetails ?? {}),
    },
    accountDetails: {
      appLicensingVerdict: 'LICENSED',
      ...(overrides.accountDetails ?? {}),
    },
    appIntegrity: {
      appRecognitionVerdict: 'PLAY_RECOGNIZED',
      ...(overrides.appIntegrity ?? {}),
    },
    deviceIntegrity: {
      deviceRecognitionVerdict: ['MEETS_DEVICE_INTEGRITY'],
      ...(overrides.deviceIntegrity ?? {}),
    },
  };
}

function verifier(payload, options = {}) {
  return new PlayIntegrityInstallationProofVerifier({
    expectedPackageName: PACKAGE,
    now: () => NOW,
    decodeIntegrityToken: async (token) => {
      assert.equal(token, 'encrypted-play-token');
      if (options.decodeError) throw new Error('google unavailable');
      return { tokenPayloadExternal: payload };
    },
    ...options,
  });
}

test('accepts recognized licensed device verdict bound to installation request hash', async () => {
  const result = await verifier(verdict()).verify({
    installationId: 'install-123',
    proof: 'encrypted-play-token',
  });
  assert.deepEqual(result, { accepted: true, bindingId: 'play:install-123' });
});

test('rejects mismatched package, request hash, stale or future request details', async () => {
  const cases = [
    verdict({ requestDetails: { requestPackageName: 'com.evil.copy' } }),
    verdict({ requestDetails: { requestHash: anonymousSessionRequestHash('different-install') } }),
    verdict({ requestDetails: { timestampMillis: String(NOW - 121_000) } }),
    verdict({ requestDetails: { timestampMillis: String(NOW + 31_000) } }),
  ];

  for (const payload of cases) {
    const result = await verifier(payload).verify({
      installationId: 'install-123',
      proof: 'encrypted-play-token',
    });
    assert.deepEqual(result, { accepted: false });
  }
});

test('rejects unrecognized app, unlicensed account or missing device integrity', async () => {
  const cases = [
    verdict({ appIntegrity: { appRecognitionVerdict: 'UNRECOGNIZED_VERSION' } }),
    verdict({ accountDetails: { appLicensingVerdict: 'UNLICENSED' } }),
    verdict({ deviceIntegrity: { deviceRecognitionVerdict: [] } }),
    verdict({ deviceIntegrity: { deviceRecognitionVerdict: ['MEETS_BASIC_INTEGRITY'] } }),
  ];

  for (const payload of cases) {
    const result = await verifier(payload).verify({
      installationId: 'install-123',
      proof: 'encrypted-play-token',
    });
    assert.deepEqual(result, { accepted: false });
  }
});

test('decode service outage fails closed without leaking decoder error', async () => {
  const result = await verifier(verdict(), { decodeError: true }).verify({
    installationId: 'install-123',
    proof: 'encrypted-play-token',
  });
  assert.deepEqual(result, { accepted: false });
  assert.equal(JSON.stringify(result).includes('google unavailable'), false);
});

test('optional licensing/device checks can be relaxed only by explicit server policy', async () => {
  const payload = verdict({
    accountDetails: { appLicensingVerdict: 'UNLICENSED' },
    deviceIntegrity: { deviceRecognitionVerdict: [] },
  });
  const result = await verifier(payload, {
    requireLicensed: false,
    requireDeviceIntegrity: false,
  }).verify({ installationId: 'install-123', proof: 'encrypted-play-token' });
  assert.equal(result.accepted, true);
});

test('request hash is stable, hides raw installation id and changes with binding input', () => {
  const first = anonymousSessionRequestHash('install-123');
  const same = anonymousSessionRequestHash('install-123');
  const other = anonymousSessionRequestHash('install-456');
  assert.equal(first, same);
  assert.notEqual(first, other);
  assert.equal(first.includes('install-123'), false);
  assert.match(first, /^[A-Za-z0-9_-]{43}$/);
});
