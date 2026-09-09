(function (root) {
  'use strict';

  if (root.NovaMacroAiReview) return;

  const CONFIG_KEY = 'novaMacroGatewaySession';
  // Shared name is deliberate: every Agnes-backed extension module must reuse
  // this chrome.storage.session lock instead of inventing per-feature concurrency.
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
    const runtimeConfig = options?.runtimeConfig || root.NovaMacroRuntimeConfig || {};
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

    async function connectGoogle(_message, sender) {
      if (!isTrustedExtensionUiSender(chrome, sender)) {
        return { ok: false, error: 'UNTRUSTED_BROWSER_SESSION_SENDER' };
      }

      const origin = configuredGatewayOrigin(runtimeConfig);
      if (!origin) return { ok: false, error: 'GATEWAY_ORIGIN_NOT_CONFIGURED' };

      const oauth = chrome.runtime?.getManifest?.()?.oauth2;
      const clientId = String(oauth?.client_id || '').trim();
      const manifestScopes = Array.isArray(oauth?.scopes) ? oauth.scopes.map(String) : [];
      if (!clientId || manifestScopes.length !== 1 || manifestScopes[0] !== 'openid') {
        return { ok: false, error: 'GOOGLE_OAUTH_NOT_CONFIGURED' };
      }
      if (!chrome.identity || typeof chrome.identity.getAuthToken !== 'function') {
        return { ok: false, error: 'CHROME_IDENTITY_UNAVAILABLE' };
      }

      const lock = await claimLock('browser-session');
      if (!lock.ok) return lock;
      let googleToken = '';
      try {
        try {
          googleToken = await getGoogleAccessToken(chrome);
        } catch {
          return { ok: false, error: 'GOOGLE_OAUTH_FAILED' };
        }
        if (!googleToken) return { ok: false, error: 'GOOGLE_OAUTH_FAILED' };

        let response;
        try {
          response = await fetchImpl(`${origin}/api/browser-session`, {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ accessToken: googleToken }),
            cache: 'no-store',
          });
        } catch {
          return { ok: false, error: 'BROWSER_SESSION_TRANSPORT_FAILED' };
        }

        let payload;
        try { payload = await response.json(); } catch {
          return { ok: false, error: 'BROWSER_SESSION_INVALID_RESPONSE' };
        }
        if (!response.ok || payload?.status !== 'SUCCESS') {
          if ([401, 403].includes(Number(response.status))) {
            await removeCachedGoogleToken(chrome, googleToken);
          }
          return {
            ok: false,
            error: browserSessionStatusError(payload?.status, response.status),
          };
        }

        const novaToken = String(payload.sessionToken || '').trim();
        const expiresAtEpochMs = Number(payload.expiresAtEpochMs || 0);
        if (novaToken.length < 16 || novaToken.length > 8192 || !Number.isFinite(expiresAtEpochMs) || expiresAtEpochMs <= now()) {
          return { ok: false, error: 'BROWSER_SESSION_INVALID_RESPONSE' };
        }

        const config = Object.freeze({
          endpoint: `${origin}/api/macro-candidate-review`,
          sessionToken: novaToken,
          expiresAtEpochMs,
        });
        await chrome.storage.session.set({ [CONFIG_KEY]: config });
        return {
          ok: true,
          configured: true,
          endpoint: config.endpoint,
          expiresAtEpochMs: config.expiresAtEpochMs,
        };
      } finally {
        // The Google access token exists only in this stack frame. It is never
        // persisted to chrome.storage or copied into the Nova session.
        googleToken = '';
        await releaseLock(lock.token);
      }
    }

    async function setGatewaySession(message, sender) {
      if (!isTrustedExtensionUiSender(chrome, sender)) {
        return { ok: false, error: 'UNTRUSTED_GATEWAY_CONFIG_SENDER' };
      }
      let config;
      try {
        config = normalizeGatewayConfig(message, configuredGatewayOrigin(runtimeConfig));
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
        try { payload = await response.json(); } catch {
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

    return Object.freeze({
      connectGoogle,
      setGatewaySession,
      clearGatewaySession,
      gatewayPublicState,
      runAiReview,
    });
  }

  async function getGoogleAccessToken(chrome) {
    const result = await chrome.identity.getAuthToken({ interactive: true, scopes: ['openid'] });
    if (typeof result === 'string') return result.trim();
    return typeof result?.token === 'string' ? result.token.trim() : '';
  }

  async function removeCachedGoogleToken(chrome, token) {
    if (!token || typeof chrome.identity?.removeCachedAuthToken !== 'function') return;
    try { await chrome.identity.removeCachedAuthToken({ token }); } catch { /* best effort */ }
  }

  function configuredGatewayOrigin(runtimeConfig) {
    const raw = String(runtimeConfig?.gatewayOrigin || '').trim();
    if (!raw) return '';
    let url;
    try { url = new URL(raw); } catch { return ''; }
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) return '';
    if (url.pathname !== '/' && url.pathname !== '') return '';
    return url.origin;
  }

  function normalizeGatewayConfig(message, expectedOrigin = '') {
    const rawEndpoint = String(message?.endpoint || '').trim();
    let endpoint;
    try { endpoint = new URL(rawEndpoint); } catch { throw new Error('INVALID_GATEWAY_ENDPOINT'); }
    if (endpoint.protocol !== 'https:') throw new Error('GATEWAY_REQUIRES_HTTPS');
    if (endpoint.username || endpoint.password || endpoint.hash) throw new Error('INVALID_GATEWAY_ENDPOINT');
    if (!endpoint.pathname.endsWith('/api/macro-candidate-review')) throw new Error('INVALID_GATEWAY_ENDPOINT_PATH');
    if (expectedOrigin && endpoint.origin !== expectedOrigin) throw new Error('GATEWAY_ORIGIN_MISMATCH');

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

  function browserSessionStatusError(status, httpStatus) {
    const known = new Set(['INVALID_REQUEST','IDENTITY_REJECTED','TEMPORARILY_UNAVAILABLE']);
    return known.has(status) ? `BROWSER_SESSION_${status}` : `BROWSER_SESSION_HTTP_${Number(httpStatus) || 0}`;
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
