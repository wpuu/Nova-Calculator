import { QUOTA_DECISION } from './nova-ai-service.mjs';

const EXTRA_DAILY_RETENTION_MS = 3_600_000;
const EXTRA_MINUTE_RETENTION_MS = 60_000;

const RESERVE_SCRIPT = `
local dailyKey = KEYS[1]
local minuteKey = KEYS[2]
local reservationKey = KEYS[3]
local dailyLimit = tonumber(ARGV[1])
local rpmLimit = tonumber(ARGV[2])
local nowMs = tonumber(ARGV[3])
local dailyExpireAt = tonumber(ARGV[4])
local minuteExpireAt = tonumber(ARGV[5])

if redis.call('EXISTS', reservationKey) == 1 then
  return {'DUPLICATE', -1}
end

local daily = tonumber(redis.call('GET', dailyKey) or '0')
if daily >= dailyLimit then
  return {'QUOTA_EXHAUSTED', 0}
end

local minute = tonumber(redis.call('GET', minuteKey) or '0')
if minute >= rpmLimit then
  return {'RATE_LIMITED', math.max(0, dailyLimit - daily)}
end

redis.call('SET', reservationKey, dailyKey, 'PXAT', dailyExpireAt)
local minuteAfter = redis.call('INCR', minuteKey)
redis.call('PEXPIREAT', minuteKey, minuteExpireAt)
local dailyAfter = redis.call('INCR', dailyKey)
redis.call('PEXPIREAT', dailyKey, dailyExpireAt)
return {'ALLOWED', math.max(0, dailyLimit - dailyAfter)}
`;

const COMMIT_SCRIPT = `
return redis.call('DEL', KEYS[1])
`;

const RELEASE_SCRIPT = `
local reservationKey = KEYS[1]
local dailyKey = redis.call('GET', reservationKey)
if not dailyKey then
  return 0
end
redis.call('DEL', reservationKey)
local current = tonumber(redis.call('GET', dailyKey) or '0')
if current > 0 then
  redis.call('DECR', dailyKey)
end
return 1
`;

/**
 * Shared atomic quota store implemented with Redis EVAL scripts.
 *
 * The injected evalClient can be backed by any Redis service that offers atomic Lua EVAL.
 * No Redis vendor credential or network implementation is embedded here.
 */
export class RedisQuotaStore {
  constructor({ evalClient, keyPrefix = 'nova:quota:v1' } = {}) {
    if (!evalClient || typeof evalClient.eval !== 'function') {
      throw new Error('RedisQuotaStore requires evalClient.eval');
    }
    this.evalClient = evalClient;
    this.keyPrefix = normalizePrefix(keyPrefix);
  }

  async reserve(input) {
    const reservationId = safeSegment(input?.reservationId, 'reservationId', 128);
    const subjectId = safeSegment(input?.subjectId, 'subjectId', 256);
    const dailyBucket = numericSegment(input?.dailyBucket, 'dailyBucket');
    const minuteBucket = numericSegment(input?.minuteBucket, 'minuteBucket');
    const dailyLimit = positiveInteger(input?.dailyLimit, 'dailyLimit');
    const rpmLimit = positiveInteger(input?.rpmLimit, 'rpmLimit');
    const nowMs = nonNegativeInteger(input?.nowMs, 'nowMs');
    const quotaResetAtEpochMs = positiveInteger(input?.quotaResetAtEpochMs, 'quotaResetAtEpochMs');
    const minuteResetAtEpochMs = positiveInteger(input?.minuteResetAtEpochMs, 'minuteResetAtEpochMs');
    if (quotaResetAtEpochMs <= nowMs || minuteResetAtEpochMs <= nowMs) {
      throw new Error('quota reset times must be in the future');
    }

    const dailyKey = `${this.keyPrefix}:d:${subjectId}:${dailyBucket}`;
    const minuteKey = `${this.keyPrefix}:m:${subjectId}:${minuteBucket}`;
    const reservationKey = `${this.keyPrefix}:r:${reservationId}`;
    const dailyExpireAt = quotaResetAtEpochMs + EXTRA_DAILY_RETENTION_MS;
    const minuteExpireAt = minuteResetAtEpochMs + EXTRA_MINUTE_RETENTION_MS;

    const result = normalizeEvalArray(await this.evalClient.eval(
      RESERVE_SCRIPT,
      [dailyKey, minuteKey, reservationKey],
      [dailyLimit, rpmLimit, nowMs, dailyExpireAt, minuteExpireAt],
    ));

    const status = String(result[0] ?? '');
    if (status === 'DUPLICATE') {
      throw new Error('duplicate quota reservation id');
    }
    if (!Object.values(QUOTA_DECISION).includes(status)) {
      throw new Error('Redis quota reserve returned invalid status');
    }
    return Object.freeze({
      status,
      remainingRequestHint: normalizeRemaining(result[1]),
    });
  }

  async commit(reservationId) {
    const id = safeSegment(reservationId, 'reservationId', 128);
    const result = await this.evalClient.eval(
      COMMIT_SCRIPT,
      [`${this.keyPrefix}:r:${id}`],
      [],
    );
    return Number(result) === 1;
  }

  async release(reservationId) {
    const id = safeSegment(reservationId, 'reservationId', 128);
    const result = await this.evalClient.eval(
      RELEASE_SCRIPT,
      [`${this.keyPrefix}:r:${id}`],
      [],
    );
    return Number(result) === 1;
  }
}

export const REDIS_QUOTA_SCRIPTS = Object.freeze({
  reserve: RESERVE_SCRIPT,
  commit: COMMIT_SCRIPT,
  release: RELEASE_SCRIPT,
});

function normalizePrefix(value) {
  const text = String(value ?? '').trim();
  if (!/^[A-Za-z0-9:_-]{1,80}$/.test(text)) {
    throw new Error('Redis quota keyPrefix is invalid');
  }
  return text;
}

function safeSegment(value, name, maxLength) {
  const text = String(value ?? '').trim();
  if (!text || text.length > maxLength || !/^[A-Za-z0-9._:-]+$/.test(text)) {
    throw new Error(`${name} is invalid`);
  }
  return text;
}

function numericSegment(value, name) {
  const text = String(value ?? '').trim();
  if (!/^\d{1,20}$/.test(text)) throw new Error(`${name} is invalid`);
  return text;
}

function positiveInteger(value, name) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0) {
    throw new Error(`${name} must be a positive safe integer`);
  }
  return number;
}

function nonNegativeInteger(value, name) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number < 0) {
    throw new Error(`${name} must be a non-negative safe integer`);
  }
  return number;
}

function normalizeEvalArray(value) {
  if (!Array.isArray(value) || value.length < 1) {
    throw new Error('Redis quota EVAL returned malformed result');
  }
  return value;
}

function normalizeRemaining(value) {
  const number = Number(value);
  return Number.isInteger(number) && number >= -1 ? number : -1;
}
