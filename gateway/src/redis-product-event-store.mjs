const DEFAULT_PREFIX = 'nova:product:v1';
const DEFAULT_RETENTION_DAYS = 120;
const DEFAULT_DAILY_SUBJECT_LIMIT = 500;

const RECORD_SCRIPT = `
local dedupe_key = KEYS[1]
local count_key = KEYS[2]
local unique_key = KEYS[3]
local rate_key = KEYS[4]
local subject = ARGV[1]
local field = ARGV[2]
local dedupe_ttl = tonumber(ARGV[3])
local aggregate_ttl = tonumber(ARGV[4])
local daily_limit = tonumber(ARGV[5])

-- Idempotent retries must not consume the daily budget.
local created = redis.call('SET', dedupe_key, '1', 'NX', 'EX', dedupe_ttl)
if not created then
  return 0
end

local rate = redis.call('INCR', rate_key)
if rate == 1 then
  redis.call('EXPIRE', rate_key, 172800)
end
if rate > daily_limit then
  return -1
end

redis.call('HINCRBY', count_key, field, 1)
redis.call('EXPIRE', count_key, aggregate_ttl)
redis.call('PFADD', unique_key, subject)
redis.call('EXPIRE', unique_key, aggregate_ttl)
return 1
`;

export class ProductEventRateLimitError extends Error {
  constructor() {
    super('product event daily subject limit exceeded');
    this.name = 'ProductEventRateLimitError';
    this.code = 'PRODUCT_EVENT_RATE_LIMITED';
  }
}

/**
 * Stores only aggregate counters and HyperLogLog anonymous-unique counts. It deliberately does not
 * persist raw event payloads, tap coordinates, calculator input, screenshots or target-app data.
 * Abuse control is also pseudonymous: a per-subject/day counter limits event volume without IP,
 * advertising-id or cross-app tracking.
 */
export class RedisProductEventStore {
  constructor({
    evalClient,
    keyPrefix = DEFAULT_PREFIX,
    retentionDays = DEFAULT_RETENTION_DAYS,
    dailySubjectLimit = DEFAULT_DAILY_SUBJECT_LIMIT,
  } = {}) {
    if (!evalClient || typeof evalClient.eval !== 'function') {
      throw new Error('RedisProductEventStore requires evalClient.eval');
    }
    this.evalClient = evalClient;
    this.keyPrefix = normalizePrefix(keyPrefix);
    this.retentionDays = positiveInteger(retentionDays, 'retentionDays');
    this.dailySubjectLimit = positiveInteger(dailySubjectLimit, 'dailySubjectLimit');
  }

  async record(event) {
    validateEvent(event);
    const date = utcDate(event.receivedAtEpochMs);
    const entitlement = safeSegment(event.entitlement, 30);
    const eventName = safeSegment(event.event, 80);
    const eventId = safeSegment(event.eventId, 80);
    const subject = safeSegment(event.subjectId, 100);
    const aggregateTtl = this.retentionDays * 24 * 60 * 60;
    const dedupeTtl = Math.min(aggregateTtl, 30 * 24 * 60 * 60);
    const field = `${eventName}|${entitlement}`;

    const result = Number(await this.evalClient.eval(
      RECORD_SCRIPT,
      [
        `${this.keyPrefix}:dedupe:${eventId}`,
        `${this.keyPrefix}:count:${date}`,
        `${this.keyPrefix}:unique:${date}:${eventName}:${entitlement}`,
        `${this.keyPrefix}:rate:${date}:${subject}`,
      ],
      [subject, field, dedupeTtl, aggregateTtl, this.dailySubjectLimit],
    ));
    if (result === -1) throw new ProductEventRateLimitError();
    return result === 1;
  }
}

function validateEvent(event) {
  if (!event || typeof event !== 'object') throw new Error('product event is required');
  safeSegment(event.eventId, 80);
  safeSegment(event.event, 80);
  safeSegment(event.subjectId, 100);
  safeSegment(event.entitlement, 30);
  if (!Number.isFinite(event.receivedAtEpochMs) || event.receivedAtEpochMs < 0) {
    throw new Error('receivedAtEpochMs is invalid');
  }
}

function utcDate(epochMs) {
  const date = new Date(epochMs);
  if (!Number.isFinite(date.getTime())) throw new Error('product event date is invalid');
  return date.toISOString().slice(0, 10);
}

function normalizePrefix(value) {
  const text = String(value ?? '').trim();
  if (!text || text.length > 100 || !/^[A-Za-z0-9:_-]+$/.test(text)) {
    throw new Error('product event Redis key prefix is invalid');
  }
  return text;
}

function safeSegment(value, maxLength) {
  const text = String(value ?? '').trim();
  if (!text || text.length > maxLength || !/^[A-Za-z0-9:_|.-]+$/.test(text)) {
    throw new Error('product event Redis key segment is invalid');
  }
  return text;
}

function positiveInteger(value, name) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0) throw new Error(`${name} must be positive`);
  return number;
}
