import { anonymousSessionRequestHash } from './play-integrity-request.mjs';

const DEFAULT_MAX_VERDICT_AGE_MS = 2 * 60 * 1000;
const DEFAULT_CLOCK_SKEW_MS = 30 * 1000;

/**
 * Provider adapter for Google Play Integrity Standard verdicts.
 *
 * decodeIntegrityToken is an injected server-only function that sends the encrypted token to
 * Google's decodeIntegrityToken endpoint and returns the decoded payload. Google credentials stay
 * in the deployment adapter and never enter the Android app or this provider-neutral core.
 */
export class PlayIntegrityInstallationProofVerifier {
  constructor(options = {}) {
    if (typeof options.decodeIntegrityToken !== 'function') {
      throw new Error('PlayIntegrityInstallationProofVerifier requires decodeIntegrityToken');
    }
    this.decodeIntegrityToken = options.decodeIntegrityToken;
    this.expectedPackageName = requireText(options.expectedPackageName, 'expectedPackageName');
    this.now = typeof options.now === 'function' ? options.now : () => Date.now();
    this.maxVerdictAgeMs = positiveInt(options.maxVerdictAgeMs ?? DEFAULT_MAX_VERDICT_AGE_MS, 'maxVerdictAgeMs');
    this.clockSkewMs = nonNegativeInt(options.clockSkewMs ?? DEFAULT_CLOCK_SKEW_MS, 'clockSkewMs');
    this.requireLicensed = options.requireLicensed !== false;
    this.requireDeviceIntegrity = options.requireDeviceIntegrity !== false;
  }

  async verify({ installationId, proof }) {
    const install = boundedText(installationId, 'installationId', 200);
    const token = boundedText(proof, 'proof', 12_000);
    let decoded;
    try {
      decoded = await this.decodeIntegrityToken(token);
    } catch {
      return Object.freeze({ accepted: false });
    }

    const payload = decoded?.tokenPayloadExternal ?? decoded;
    if (!payload || typeof payload !== 'object') return Object.freeze({ accepted: false });

    const requestDetails = payload.requestDetails;
    if (!requestDetails || requestDetails.requestPackageName !== this.expectedPackageName) {
      return Object.freeze({ accepted: false });
    }
    if (requestDetails.requestHash !== anonymousSessionRequestHash(install)) {
      return Object.freeze({ accepted: false });
    }
    if (!freshTimestamp(requestDetails.timestampMillis, this.now(), this.maxVerdictAgeMs, this.clockSkewMs)) {
      return Object.freeze({ accepted: false });
    }

    if (payload.appIntegrity?.appRecognitionVerdict !== 'PLAY_RECOGNIZED') {
      return Object.freeze({ accepted: false });
    }
    if (this.requireLicensed && payload.accountDetails?.appLicensingVerdict !== 'LICENSED') {
      return Object.freeze({ accepted: false });
    }
    if (this.requireDeviceIntegrity) {
      const verdicts = Array.isArray(payload.deviceIntegrity?.deviceRecognitionVerdict)
        ? payload.deviceIntegrity.deviceRecognitionVerdict
        : [];
      if (!verdicts.includes('MEETS_DEVICE_INTEGRITY')) {
        return Object.freeze({ accepted: false });
      }
    }

    // Play Integrity proves this request came from the recognized app/device, but it intentionally
    // does not expose a permanent hardware identifier. Use the verified app-local installation id
    // as the session binding. Reinstall abuse is handled separately by policy/signals, not by
    // inventing a device id from integrity data.
    return Object.freeze({
      accepted: true,
      bindingId: `play:${install}`,
    });
  }
}

function freshTimestamp(raw, nowValue, maxAgeMs, clockSkewMs) {
  const timestamp = Number(raw);
  const now = Number(nowValue);
  if (!Number.isFinite(timestamp) || !Number.isFinite(now) || timestamp < 0 || now < 0) return false;
  if (timestamp > now + clockSkewMs) return false;
  return now - timestamp <= maxAgeMs;
}

function requireText(value, name) {
  const text = typeof value === 'string' ? value.trim() : '';
  if (!text) throw new Error(`${name} is required`);
  return text;
}

function boundedText(value, name, maxLength) {
  const text = typeof value === 'string' ? value.trim() : '';
  if (!text || text.length > maxLength) throw new Error(`${name} is invalid`);
  return text;
}

function positiveInt(value, name) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0) throw new Error(`${name} must be a positive integer`);
  return number;
}

function nonNegativeInt(value, name) {
  const number = Number(value);
  if (!Number.isInteger(number) || number < 0) throw new Error(`${name} must be a non-negative integer`);
  return number;
}
