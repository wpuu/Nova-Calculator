import { randomUUID } from 'node:crypto';

import { QUOTA_DECISION } from './nova-ai-service.mjs';
import { DEFAULT_AI_QUOTA_LIMITS, quotaLimitsForPriority } from './quota-policy.mjs';

const DAY_MS = 86_400_000;
const MINUTE_MS = 60_000;

/**
 * Quota ledger facade backed by an atomic persistent store.
 *
 * Store contract:
 * - reserve(input) must atomically consume one minute-attempt slot and reserve one daily slot.
 * - commit(reservationId) finalizes the reserved daily slot after an upstream success.
 * - release(reservationId) returns the reserved daily slot after an upstream failure, while the
 *   minute-attempt remains spent because provider RPM capacity was already consumed.
 *
 * Production deployments must use a shared transactional/atomic store (for example a database
 * transaction or Redis script). An in-memory per-instance counter is intentionally not supplied
 * here because it would over-issue quota when the gateway scales horizontally.
 */
export class DailyQuotaLedger {
  constructor({
    store,
    policy = DEFAULT_AI_QUOTA_LIMITS,
    now = () => Date.now(),
    newReservationId = () => randomUUID(),
  } = {}) {
    if (!store || typeof store.reserve !== 'function'
        || typeof store.commit !== 'function'
        || typeof store.release !== 'function') {
      throw new Error('DailyQuotaLedger requires atomic store reserve/commit/release');
    }
    if (typeof now !== 'function') throw new Error('now must be a function');
    if (typeof newReservationId !== 'function') throw new Error('newReservationId must be a function');
    this.store = store;
    this.policy = policy;
    this.now = now;
    this.newReservationId = newReservationId;
  }

  async reserve(context) {
    const subjectId = requiredText(context?.subjectId, 'subjectId');
    const priority = context?.priority;
    const limits = quotaLimitsForPriority(this.policy, priority);
    const nowMs = nonNegativeInteger(this.now(), 'now');
    const dayStartMs = Math.floor(nowMs / DAY_MS) * DAY_MS;
    const minuteStartMs = Math.floor(nowMs / MINUTE_MS) * MINUTE_MS;
    const reservationId = requiredText(this.newReservationId(), 'reservationId');

    const outcome = normalizeStoreOutcome(await this.store.reserve(Object.freeze({
      reservationId,
      subjectId,
      priority,
      operation: optionalText(context?.operation),
      requestId: optionalText(context?.requestId),
      nowMs,
      dailyBucket: String(dayStartMs),
      minuteBucket: String(minuteStartMs),
      dailyLimit: limits.dailyLimit,
      rpmLimit: limits.rpmLimit,
      quotaResetAtEpochMs: dayStartMs + DAY_MS,
      minuteResetAtEpochMs: minuteStartMs + MINUTE_MS,
    })));

    if (outcome.status === QUOTA_DECISION.ALLOWED) {
      return Object.freeze({
        status: QUOTA_DECISION.ALLOWED,
        reservationId,
        remainingRequestHint: outcome.remainingRequestHint,
        quotaResetAtEpochMs: dayStartMs + DAY_MS,
        retryAfterSeconds: 0,
      });
    }
    if (outcome.status === QUOTA_DECISION.QUOTA_EXHAUSTED) {
      return Object.freeze({
        status: QUOTA_DECISION.QUOTA_EXHAUSTED,
        remainingRequestHint: 0,
        quotaResetAtEpochMs: dayStartMs + DAY_MS,
        retryAfterSeconds: 0,
      });
    }
    return Object.freeze({
      status: QUOTA_DECISION.RATE_LIMITED,
      remainingRequestHint: outcome.remainingRequestHint,
      quotaResetAtEpochMs: dayStartMs + DAY_MS,
      retryAfterSeconds: Math.max(1, Math.ceil((minuteStartMs + MINUTE_MS - nowMs) / 1000)),
    });
  }

  async commit(reservationId) {
    return this.store.commit(requiredText(reservationId, 'reservationId'));
  }

  async release(reservationId) {
    return this.store.release(requiredText(reservationId, 'reservationId'));
  }
}

function normalizeStoreOutcome(value) {
  const status = value?.status;
  if (!Object.values(QUOTA_DECISION).includes(status)) {
    throw new Error('atomic quota store returned invalid status');
  }
  return Object.freeze({
    status,
    remainingRequestHint: normalizeRemaining(value?.remainingRequestHint),
  });
}

function normalizeRemaining(value) {
  const number = Number(value);
  return Number.isInteger(number) && number >= -1 ? number : -1;
}

function requiredText(value, name) {
  const text = String(value ?? '').trim();
  if (!text) throw new Error(`${name} must not be blank`);
  return text;
}

function optionalText(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function nonNegativeInteger(value, name) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number < 0) {
    throw new Error(`${name} must be a non-negative safe integer`);
  }
  return number;
}
