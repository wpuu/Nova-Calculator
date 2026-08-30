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

const READ_DAILY_SCRIPT = `
local counts = redis.call('HGETALL', KEYS[1])
local uniques = {}
for i = 2, #KEYS do
  uniques[i - 1] = redis.call('PFCOUNT', KEYS[i])
end
return {counts, uniques}
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

  /**
   * Returns aggregate count + unique-user data only. `dimensions` must be a server-owned fixed
   * list of event/entitlement pairs; callers cannot use this method to enumerate raw Redis keys.
   */
  async readDaily(date, dimensions) {
    const normalizedDate = safeDate(date);
    if (!Array.isArray(dimensions) || dimensions.length === 0 || dimensions.length > 100) {
      throw new Error('product funnel dimensions are invalid');
    }
    const safeDimensions = dimensions.map((dimension) => {
      if (!dimension || typeof dimension !== 'object') {
        throw new Error('product funnel dimension is invalid');
      }
      return Object.freeze({
        event: safeSegment(dimension.event, 80),
        entitlement: safeSegment(dimension.entitlement, 30),
      });
    });
    const keys = [`${this.keyPrefix}:count:${normalizedDate}`];
    for (const dimension of safeDimensions) {
      keys.push(`${this.keyPrefix}:unique:${normalizedDate}:${dimension.event}:${dimension.entitlement}`);
    }

    const raw = await this.evalClient.eval(READ_DAILY_SCRIPT, keys, []);
    const rawCounts = Array.isArray(raw?.[0]) ? raw[0] : [];
    const rawUniques = Array.isArray(raw?.[1]) ? raw[1] : [];
    const countMap = new Map();
    for (let index = 0; index + 1 < rawCounts.length; index += 2) {
      countMap.set(String(rawCounts[index]), nonNegativeInteger(rawCounts[index + 1]));
    }

    return Object.freeze(safeDimensions.map((dimension, index) => Object.freeze({
      event: dimension.event,
      entitlement: dimension.entitlement,
      count: countMap.get(`${dimension.event}|${dimension.entitlement}`) ?? 0,
      unique: nonNegativeInteger(rawUniques[index]),
    })));
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

function safeDate(value) {
  const text = String(value ?? '').trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) throw new Error('product funnel date is invalid');
  const parsed = new Date(`${text}T00:00:00.000Z`);
  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== text) {
    throw new Error('product funnel date is invalid');
  }
  return text;
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

function nonNegativeInteger(value) {
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= 0 ? number : 0;
}
