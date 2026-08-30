const DEFAULT_MAX_BODY_BYTES = 8 * 1024;
const MAX_INSTALLATION_ID_CHARS = 200;
const MAX_PROOF_CHARS = 6000;

export const ANONYMOUS_SESSION_STATUS = Object.freeze({
  SUCCESS: 'SUCCESS',
  INVALID_REQUEST: 'INVALID_REQUEST',
  PROOF_REJECTED: 'PROOF_REJECTED',
  TEMPORARILY_UNAVAILABLE: 'TEMPORARILY_UNAVAILABLE',
});

/**
 * Server-side anonymous session issuer.
 *
 * The installation proof verifier is deliberately provider-neutral. A production deployment can
 * bind this interface to Play Integrity or another app/device proof mechanism without changing
 * Nova's public session contract.
 */
export class AnonymousSessionService {
  constructor({ tokenService, installationProofVerifier }) {
    if (!tokenService || typeof tokenService.issueAnonymous !== 'function') {
      throw new Error('AnonymousSessionService requires tokenService.issueAnonymous');
    }
    if (!installationProofVerifier || typeof installationProofVerifier.verify !== 'function') {
      throw new Error('AnonymousSessionService requires installationProofVerifier.verify');
    }
    this.tokenService = tokenService;
    this.installationProofVerifier = installationProofVerifier;
  }

  async issue({ installationId, proof }) {
    const install = safeText(installationId, MAX_INSTALLATION_ID_CHARS);
    const proofText = safeText(proof, MAX_PROOF_CHARS);
    if (!install || !proofText) {
      return sessionResult(ANONYMOUS_SESSION_STATUS.INVALID_REQUEST);
    }

    let verification;
    try {
      verification = await this.installationProofVerifier.verify(Object.freeze({
        installationId: install,
        proof: proofText,
      }));
    } catch {
      return sessionResult(ANONYMOUS_SESSION_STATUS.TEMPORARILY_UNAVAILABLE);
    }

    const bindingId = verifiedBindingId(verification);
    if (!bindingId) {
      return sessionResult(ANONYMOUS_SESSION_STATUS.PROOF_REJECTED);
    }

    try {
      const issued = this.tokenService.issueAnonymous({ installationId: bindingId });
      return sessionResult(ANONYMOUS_SESSION_STATUS.SUCCESS, {
        sessionToken: issued?.token,
        expiresAtEpochMs: issued?.expiresAtEpochMs,
      });
    } catch {
      return sessionResult(ANONYMOUS_SESSION_STATUS.TEMPORARILY_UNAVAILABLE);
    }
  }
}

/**
 * Framework-neutral Fetch API handler for anonymous Nova session issuance.
 * Only installationId and proof are accepted. Client entitlement/priority claims are rejected.
 */
export function createAnonymousSessionFetchHandler({ service, maxBodyBytes = DEFAULT_MAX_BODY_BYTES }) {
  if (!service || typeof service.issue !== 'function') {
    throw new Error('createAnonymousSessionFetchHandler requires service.issue');
  }
  const bodyLimit = positiveInt(maxBodyBytes, 'maxBodyBytes');

  return async function handle(request) {
    if (!request || typeof request.method !== 'string') {
      return jsonResponse(400, sessionResult(ANONYMOUS_SESSION_STATUS.INVALID_REQUEST));
    }
    if (request.method.toUpperCase() !== 'POST') {
      return jsonResponse(405, sessionResult(ANONYMOUS_SESSION_STATUS.INVALID_REQUEST), { allow: 'POST' });
    }

    const contentType = request.headers?.get?.('content-type') ?? '';
    if (!/^application\/json(?:\s*;|$)/i.test(contentType)) {
      return jsonResponse(415, sessionResult(ANONYMOUS_SESSION_STATUS.INVALID_REQUEST));
    }

    const declaredLength = Number(request.headers?.get?.('content-length'));
    if (Number.isFinite(declaredLength) && declaredLength > bodyLimit) {
      return jsonResponse(413, sessionResult(ANONYMOUS_SESSION_STATUS.INVALID_REQUEST));
    }

    let text;
    try {
      text = await request.text();
    } catch {
      return jsonResponse(400, sessionResult(ANONYMOUS_SESSION_STATUS.INVALID_REQUEST));
    }
    if (byteLength(text) > bodyLimit) {
      return jsonResponse(413, sessionResult(ANONYMOUS_SESSION_STATUS.INVALID_REQUEST));
    }

    let body;
    try {
      body = JSON.parse(text);
    } catch {
      return jsonResponse(400, sessionResult(ANONYMOUS_SESSION_STATUS.INVALID_REQUEST));
    }
    if (!isStrictAnonymousBody(body)) {
      return jsonResponse(400, sessionResult(ANONYMOUS_SESSION_STATUS.INVALID_REQUEST));
    }

    let result;
    try {
      result = await service.issue({ installationId: body.installationId, proof: body.proof });
    } catch {
      result = sessionResult(ANONYMOUS_SESSION_STATUS.TEMPORARILY_UNAVAILABLE);
    }
    const sanitized = sanitizeResult(result);
    return jsonResponse(httpStatusFor(sanitized.status), sanitized);
  };
}

function isStrictAnonymousBody(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) return false;
  const keys = Object.keys(body).sort();
  if (keys.length !== 2 || keys[0] !== 'installationId' || keys[1] !== 'proof') return false;
  return Boolean(
    safeText(body.installationId, MAX_INSTALLATION_ID_CHARS)
    && safeText(body.proof, MAX_PROOF_CHARS),
  );
}

function verifiedBindingId(value) {
  if (!value || value.accepted !== true) return '';
  return safeText(value.bindingId, 300);
}

function sessionResult(status, options = {}) {
  const success = status === ANONYMOUS_SESSION_STATUS.SUCCESS;
  return Object.freeze({
    status,
    sessionToken: success && typeof options.sessionToken === 'string' ? options.sessionToken : '',
    expiresAtEpochMs: success ? nonNegative(options.expiresAtEpochMs) : 0,
  });
}

function sanitizeResult(value) {
  const status = Object.values(ANONYMOUS_SESSION_STATUS).includes(value?.status)
    ? value.status
    : ANONYMOUS_SESSION_STATUS.TEMPORARILY_UNAVAILABLE;
  if (status !== ANONYMOUS_SESSION_STATUS.SUCCESS) return sessionResult(status);
  const token = safeText(value?.sessionToken, 8192);
  const expiresAtEpochMs = nonNegative(value?.expiresAtEpochMs);
  if (!token || expiresAtEpochMs <= 0) {
    return sessionResult(ANONYMOUS_SESSION_STATUS.TEMPORARILY_UNAVAILABLE);
  }
  return sessionResult(status, { sessionToken: token, expiresAtEpochMs });
}

function httpStatusFor(status) {
  switch (status) {
    case ANONYMOUS_SESSION_STATUS.SUCCESS:
      return 200;
    case ANONYMOUS_SESSION_STATUS.INVALID_REQUEST:
      return 400;
    case ANONYMOUS_SESSION_STATUS.PROOF_REJECTED:
      return 403;
    case ANONYMOUS_SESSION_STATUS.TEMPORARILY_UNAVAILABLE:
    default:
      return 503;
  }
}

function jsonResponse(status, payload, extraHeaders = {}) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      'x-content-type-options': 'nosniff',
      ...extraHeaders,
    },
  });
}

function safeText(value, maxLength) {
  if (typeof value !== 'string') return '';
  const text = value.trim();
  return text && text.length <= maxLength ? text : '';
}

function nonNegative(value) {
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= 0 ? number : 0;
}

function positiveInt(value, name) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0) throw new Error(`${name} must be a positive integer`);
  return number;
}

function byteLength(value) {
  return new TextEncoder().encode(value).byteLength;
}
