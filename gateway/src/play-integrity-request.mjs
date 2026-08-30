import { createHash } from 'node:crypto';

const REQUEST_DOMAIN = 'nova-anonymous-session-v1';

/**
 * Content binding shared conceptually with the Android Play Integrity adapter.
 * Raw installation ids are never placed in requestHash; only this SHA-256 digest is sent to Play.
 */
export function anonymousSessionRequestHash(installationId) {
  const install = boundedText(installationId, 'installationId', 200);
  return createHash('sha256')
    .update(REQUEST_DOMAIN, 'utf8')
    .update('\n', 'utf8')
    .update(install, 'utf8')
    .digest('base64url');
}

function boundedText(value, name, maxLength) {
  const text = typeof value === 'string' ? value.trim() : '';
  if (!text || text.length > maxLength) {
    throw new Error(`${name} must be a non-blank string no longer than ${maxLength} characters`);
  }
  return text;
}
