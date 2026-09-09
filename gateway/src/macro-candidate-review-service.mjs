import { GatewayDispatchError, ProviderInvocationError, PROVIDER_FAILURE_KIND } from './gateway-dispatcher.mjs';
import { normalizeCandidateReviewRequest } from './macro-candidate-review-provider.mjs';
import {
  NOVA_GATEWAY_STATUS,
  QUOTA_DECISION,
  priorityForPrincipal,
} from './nova-ai-service.mjs';

/**
 * Server-authoritative orchestration for one bounded Macro AI review.
 * The model can never return an executable selector to the client; the only
 * successful action is one locally minted candidate ID or ABSTAIN.
 */
export class MacroCandidateReviewService {
  constructor({ authVerifier, quotaLedger, dispatcher }) {
    if (!authVerifier || typeof authVerifier.verify !== 'function') throw new Error('MacroCandidateReviewService requires authVerifier.verify');
    if (!quotaLedger || typeof quotaLedger.reserve !== 'function') throw new Error('MacroCandidateReviewService requires quotaLedger.reserve');
    if (typeof quotaLedger.commit !== 'function' || typeof quotaLedger.release !== 'function') {
      throw new Error('MacroCandidateReviewService requires quotaLedger commit/release');
    }
    if (!dispatcher || typeof dispatcher.dispatch !== 'function') throw new Error('MacroCandidateReviewService requires dispatcher.dispatch');
    this.authVerifier = authVerifier;
    this.quotaLedger = quotaLedger;
    this.dispatcher = dispatcher;
  }

  async execute({ authorization, request }) {
    let normalized;
    try {
      normalized = normalizeCandidateReviewRequest(request);
    } catch (error) {
      if (error instanceof ProviderInvocationError && error.kind === PROVIDER_FAILURE_KIND.REQUEST) {
        return response(safeRequestId(request?.requestId), NOVA_GATEWAY_STATUS.INVALID_REQUEST);
      }
      return response(safeRequestId(request?.requestId), NOVA_GATEWAY_STATUS.INVALID_REQUEST);
    }

    let principal;
    try {
      principal = normalizePrincipal(await this.authVerifier.verify(authorization));
    } catch {
      principal = null;
    }
    if (!principal) return response(normalized.requestId, NOVA_GATEWAY_STATUS.AUTH_REQUIRED);

    const priority = priorityForPrincipal(principal);
    let quota;
    try {
      quota = normalizeQuota(await this.quotaLedger.reserve({
        subjectId: principal.subjectId,
        priority,
        operation: normalized.operation,
        requestId: normalized.requestId,
      }));
    } catch {
      return response(normalized.requestId, NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE);
    }

    if (quota.status === QUOTA_DECISION.QUOTA_EXHAUSTED) {
      return response(normalized.requestId, NOVA_GATEWAY_STATUS.QUOTA_EXHAUSTED, quota);
    }
    if (quota.status === QUOTA_DECISION.RATE_LIMITED) {
      return response(normalized.requestId, NOVA_GATEWAY_STATUS.RATE_LIMITED, quota);
    }

    try {
      const result = await this.dispatcher.dispatch(normalized, priority);
      await settleQuietly(this.quotaLedger, 'commit', quota.reservationId);

      const allowed = new Set(normalized.review.allowedCandidateIds);
      const candidateAllowed = result?.decision === 'SELECT'
        && typeof result?.candidateId === 'string'
        && allowed.has(result.candidateId);
      const safeResult = result?.decision === 'SELECT' && !candidateAllowed
        ? { decision: 'ABSTAIN', candidateId: null, confidence: 0, reason: 'SERVER_CANDIDATE_REJECTED' }
        : result;

      return response(normalized.requestId, NOVA_GATEWAY_STATUS.SUCCESS, {
        ...quota,
        decision: safeResult?.decision,
        candidateId: safeResult?.candidateId,
        confidence: safeResult?.confidence,
        reason: safeResult?.reason,
      });
    } catch (error) {
      await settleQuietly(this.quotaLedger, 'release', quota.reservationId);
      if (error instanceof GatewayDispatchError && error.code === 'PROVIDER_REQUEST_REJECTED') {
        return response(normalized.requestId, NOVA_GATEWAY_STATUS.INVALID_REQUEST);
      }
      return response(normalized.requestId, NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE);
    }
  }
}

function response(requestId, status, options = {}) {
  const success = status === NOVA_GATEWAY_STATUS.SUCCESS;
  const decision = success && options.decision === 'SELECT' ? 'SELECT' : success ? 'ABSTAIN' : null;
  const candidateId = decision === 'SELECT' ? safeCandidateId(options.candidateId) : null;
  const safeDecision = decision === 'SELECT' && !candidateId ? 'ABSTAIN' : decision;
  return Object.freeze({
    requestId: safeRequestId(requestId),
    status,
    decision: success ? safeDecision : null,
    candidateId: success && safeDecision === 'SELECT' ? candidateId : null,
    confidence: success ? confidence(options.confidence) : 0,
    reason: success ? safeReason(options.reason) : '',
    retryAfterSeconds: nonNegative(options.retryAfterSeconds),
    remainingRequestHint: integerHint(options.remainingRequestHint),
    quotaResetAtEpochMs: nonNegative(options.quotaResetAtEpochMs),
  });
}

function normalizePrincipal(principal) {
  const subjectId = typeof principal?.subjectId === 'string' ? principal.subjectId.trim() : '';
  if (!subjectId) return null;
  const entitlements = principal.entitlements instanceof Set
    ? [...principal.entitlements]
    : Array.isArray(principal.entitlements) ? principal.entitlements : [];
  return Object.freeze({ subjectId, entitlements: Object.freeze(entitlements.map(String)) });
}

function normalizeQuota(value) {
  if (!value || !Object.values(QUOTA_DECISION).includes(value.status)) throw new Error('invalid quota decision');
  const common = {
    remainingRequestHint: integerHint(value.remainingRequestHint),
    quotaResetAtEpochMs: nonNegative(value.quotaResetAtEpochMs),
    retryAfterSeconds: nonNegative(value.retryAfterSeconds),
  };
  if (value.status === QUOTA_DECISION.ALLOWED) {
    const reservationId = typeof value.reservationId === 'string' ? value.reservationId.trim() : '';
    if (!reservationId) throw new Error('allowed quota decision requires reservationId');
    return Object.freeze({ ...common, status: value.status, reservationId });
  }
  return Object.freeze({ ...common, status: value.status });
}

async function settleQuietly(ledger, method, reservationId) {
  try { await ledger[method](reservationId); } catch { /* accounting reconciliation belongs to ledger */ }
}

function safeRequestId(value) {
  const text = typeof value === 'string' ? value.trim() : '';
  return text ? text.slice(0, 200) : 'invalid';
}

function safeCandidateId(value) {
  const text = typeof value === 'string' ? value.trim() : '';
  return /^candidate_\d+$/.test(text) ? text : null;
}

function safeReason(value) {
  return typeof value === 'string' ? value.trim().slice(0, 600) : '';
}

function confidence(value) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.max(0, Math.min(1, number)) : 0;
}

function nonNegative(value) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.max(0, number) : 0;
}

function integerHint(value) {
  const number = Number(value);
  return Number.isInteger(number) && number >= -1 ? number : -1;
}
