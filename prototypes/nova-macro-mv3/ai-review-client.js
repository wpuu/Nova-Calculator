(function (root) {
  'use strict';

  if (root.NovaMacroAiReview) return;

  const CONFIG_KEY = 'novaMacroGatewaySession';
  // Shared name is deliberate: future Agnes-backed extension modules must reuse this
  // chrome.storage.session lock instead of inventing per-feature concurrency.
  const LOCK_KEY = 'novaAgnesSingleFlight';
  const LOCK_TTL_MS = 30_000;

  function createController(options) {
    const chrome = options?.chrome;
    const getSession = options?.getSession;
    const reviewFromSession = options?.reviewFromSession;
    const selectRepairCandidate = options?.selectRepairCandidate;
    const abstainRepair = options?.abstainRepair;
    const fetchImpl = options?.fetchImpl || root.fetch;
    const now = options?.now || (() => Date.now());
    const newRequestId = options?.newRequestId || (() => {
      const uuid = root.crypto?.randomUUID?.();
      return uuid ? `macro_${uuid}` : `macro_${now()}_${Math.random().toString(36).slice(2)}`;
    });

    if (!chrome?.storage?.session) throw new Error('Macro AI review requires chrome.storage.session');
    if (typeof getSession !== 'function' || typeof reviewFromSession !== 'function') {
      throw new Error('Macro AI review requires session callbacks');
    }
    if (typeof selectRepairCandidate !== 'function' || typeof abstainRepair !== 'function') {
      throw new Error('Macro AI review requires bounded decision callbacks');
    }
    if (typeof fetchImpl !== 'function') throw new Error('Macro AI review requires fetch');

    let lockQueue = Promise.resolve();

    async function setGatewaySession(message, sender) {
      if (!isTrustedExtensionUiSender(chrome, sender)) {
        return { ok: false, error: 'UNTRUSTED_GATEWAY_CONFIG_SENDER' };
      }
      let config;
      try {
        config = normalizeGatewayConfig(message);
      } catch (error) {
        return { ok: false, error: error?.message || 'INVALID_GATEWAY_CONFIG' };
      }
      await chrome.storage.session.set({ [CONFIG_KEY]: config });
      return {
        ok: true,
        configured: true,
        endpoint: config.endpoint,
        expiresAtEpochMs: config.expiresAtEpochMs,
      };
    }

    async function clearGatewaySession(_message, sender) {
      if (!isTrustedExtensionUiSender(chrome, sender)) {
        return { ok: false, error: 'UNTRUSTED_GATEWAY_CONFIG_SENDER' };
      }
      await chrome.storage.session.set({ [CONFIG_KEY]: null });
      return { ok: true, configured: false };
    }

    async function gatewayPublicState(_message, sender) {
      if (!isTrustedExtensionUiSender(chrome, sender)) {
        return { ok: false, error: 'UNTRUSTED_GATEWAY_CONFIG_SENDER' };
      }
      const config = await readGatewayConfig(chrome, now);
      const lock = await readLock(chrome);
      return {
        ok: true,
        configured: Boolean(config),
        endpoint: config?.endpoint || null,
        expiresAtEpochMs: config?.expiresAtEpochMs || 0,
        aiReviewInFlight: isLiveLock(lock, now()),
      };
    }

    async function runAiReview(message, sender) {
      if (!isTrustedExtensionUiSender(chrome, sender)) {
        return { ok: false, error: 'UNTRUSTED_AI_REVIEW_SENDER' };
      }

      const session = await getSession();
      const review = reviewFromSession(session);
      if (session?.mode !== 'AI_REVIEW' || !review) {
        return { ok: false, error: 'NO_PENDING_AI_REVIEW' };
      }
      if (review.policy !== 'SELECT_LISTED_CANDIDATE_OR_ABSTAIN') {
        return { ok: false, error: 'INVALID_AI_REVIEW_POLICY' };
      }
      if (!Array.isArray(review.allowedCandidateIds) || review.allowedCandidateIds.length < 1 || review.allowedCandidateIds.length > 5) {
        return { ok: false, error: 'INVALID_AI_REVIEW_CANDIDATES' };
      }
      if (message?.reviewId && message.reviewId !== review.reviewId) {
        return { ok: false, error: 'STALE_AI_REVIEW' };
      }

      const lock = await claimLock(review.reviewId);
      if (!lock.ok) return lock;

      try {
        const config = await readGatewayConfig(chrome, now);
        if (!config) return { ok: false, error: 'GATEWAY_SESSION_REQUIRED' };

        const requestId = newRequestId();
        let response;
        try {
          response = await fetchImpl(config.endpoint, {
            method: 'POST',
            headers: {
              authorization: `Bearer ${config.sessionToken}`,
              'content-type': 'application/json',
            },
            body: JSON.stringify({
              requestId,
              operation: 'MACRO_CANDIDATE_REVIEW',
              review,
            }),
            cache: 'no-store',
          });
        } catch {
          return { ok: false, error: 'GATEWAY_TRANSPORT_FAILED' };
        }

        let payload;
        try {
          payload = await response.json();
        } catch {
          return { ok: false, error: 'GATEWAY_INVALID_RESPONSE' };
        }

        if (!response.ok || payload?.status !== 'SUCCESS') {
          return {
            ok: false,
            error: gatewayStatusError(payload?.status, response.status),
            retryAfterSeconds: nonNegative(payload?.retryAfterSeconds),
          };
        }

        if (payload.decision === 'ABSTAIN') {
          const next = await abstainRepair({
            reviewId: review.reviewId,
            reason: safeReason(payload.reason),
          });
          return { ok: true, gatewayDecision: 'ABSTAIN', session: next };
        }

        const candidateId = typeof payload.candidateId === 'string' ? payload.candidateId : '';
        if (payload.decision !== 'SELECT' || !review.allowedCandidateIds.includes(candidateId)) {
          const next = await abstainRepair({
            reviewId: review.reviewId,
            reason: 'CLIENT_CANDIDATE_REJECTED',
          });
          return { ok: true, gatewayDecision: 'ABSTAIN', session: next };
        }

        const next = await selectRepairCandidate({ reviewId: review.reviewId, candidateId });
        return { ok: true, gatewayDecision: 'SELECT', candidateId, session: next };
      } finally {
        await releaseLock(lock.token);
      }
    }

    async function claimLock(reviewId) {
      const operation = lockQueue.then(async () => {
        const current = await readLock(chrome);
        const timestamp = now();
        if (isLiveLock(current, timestamp)) return { ok: false, error: 'AI_REVIEW_IN_FLIGHT' };
        const token = newRequestId();
        await chrome.storage.session.set({
          [LOCK_KEY]: { token, reviewId, startedAtEpochMs: timestamp },
        });
        return { ok: true, token };
      });
      lockQueue = operation.catch(() => {});
      return operation;
    }

    async function releaseLock(token) {
      const operation = lockQueue.then(async () => {
        const current = await readLock(chrome);
        if (current?.token === token) await chrome.storage.session.set({ [LOCK_KEY]: null });
      });
      lockQueue = operation.catch(() => {});
      return operation;
    }

    return Object.freeze({ setGatewaySession, clearGatewaySession, gatewayPublicState, runAiReview });
  }

  function normalizeGatewayConfig(message) {
    const rawEndpoint = String(message?.endpoint || '').trim();
    let endpoint;
    try { endpoint = new URL(rawEndpoint); } catch { throw new Error('INVALID_GATEWAY_ENDPOINT'); }
    if (endpoint.protocol !== 'https:') throw new Error('GATEWAY_REQUIRES_HTTPS');
    if (endpoint.username || endpoint.password || endpoint.hash) throw new Error('INVALID_GATEWAY_ENDPOINT');
    if (!endpoint.pathname.endsWith('/api/macro-candidate-review')) throw new Error('INVALID_GATEWAY_ENDPOINT_PATH');

    const sessionToken = String(message?.sessionToken || '').trim();
    if (sessionToken.length < 16 || sessionToken.length > 8192) throw new Error('INVALID_GATEWAY_SESSION_TOKEN');
    const expiresAtEpochMs = Number(message?.expiresAtEpochMs || 0);
    if (!Number.isFinite(expiresAtEpochMs) || expiresAtEpochMs < 0) throw new Error('INVALID_GATEWAY_SESSION_EXPIRY');

    endpoint.search = '';
    endpoint.hash = '';
    return Object.freeze({ endpoint: endpoint.toString(), sessionToken, expiresAtEpochMs });
  }

  async function readGatewayConfig(chrome, now) {
    const stored = await chrome.storage.session.get(CONFIG_KEY);
    const value = stored?.[CONFIG_KEY];
    if (!value || typeof value !== 'object') return null;
    if (value.expiresAtEpochMs > 0 && value.expiresAtEpochMs <= now()) return null;
    return value;
  }

  async function readLock(chrome) {
    const stored = await chrome.storage.session.get(LOCK_KEY);
    return stored?.[LOCK_KEY] || null;
  }

  function isLiveLock(lock, nowMs) {
    return Boolean(
      lock && typeof lock.token === 'string' && Number.isFinite(lock.startedAtEpochMs) &&
      nowMs - lock.startedAtEpochMs >= 0 && nowMs - lock.startedAtEpochMs < LOCK_TTL_MS
    );
  }

  function isTrustedExtensionUiSender(chrome, sender) {
    if (sender?.tab) return false;
    const runtimeId = String(chrome?.runtime?.id || '').trim();
    const senderUrl = String(sender?.url || '').trim();
    return Boolean(runtimeId && senderUrl.startsWith(`chrome-extension://${runtimeId}/`));
  }

  function gatewayStatusError(status, httpStatus) {
    const known = new Set(['AUTH_REQUIRED','QUOTA_EXHAUSTED','RATE_LIMITED','INVALID_REQUEST','TEMPORARILY_UNAVAILABLE']);
    return known.has(status) ? `GATEWAY_${status}` : `GATEWAY_HTTP_${Number(httpStatus) || 0}`;
  }

  function safeReason(value) {
    return typeof value === 'string' ? value.trim().slice(0, 300) : '';
  }

  function nonNegative(value) {
    const number = Number(value);
    return Number.isFinite(number) ? Math.max(0, number) : 0;
  }

  root.NovaMacroAiReview = Object.freeze({ createController });
})(globalThis);
