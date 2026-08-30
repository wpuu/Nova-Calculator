const DEFAULT_PREFIX = 'nova:product:v1';
const DEFAULT_RETENTION_DAYS = 120;

const RECORD_SCRIPT = `
local dedupe_key = KEYS[1]
local count_key = KEYS[2]
local unique_key = KEYS[3]
local subject = ARGV[1]
local field = ARGV[2]
local dedupe_ttl = tonumber(ARGV[3])
local aggregate_ttl = tonumber(ARGV[4])

local created = redis.call('SET', dedupe_key, '1', 'NX', 'EX', dedupe_ttl)
if not created then
  return 0
end
redis.call('HINCRBY', count_key, field, 1)
redis.call('EXPIRE', count_key, aggregate_ttl)
redis.call('PFADD', unique_key, subject)
redis.call('EXPIRE', unique_key, aggregate_ttl)
return 1
`;

/**
 * Stores only aggregate counters and HyperLogLog anonymous-unique counts. It deliberately does not
 * persist raw event payloads, tap coordinates, calculator input, screenshots or target-app data.
 */
export class RedisProductEventStore {
  constructor({
    evalClient,
    keyPrefix = DEFAULT_PREFIX,
    retentionDays = DEFAULT_RETENTION_DAYS,
  } = {}) {
    if (!evalClient || typeof evalClient.eval !== 'function') {
      throw new Error('RedisProductEventStore requires evalClient.eval');
    }
    this.evalClient = evalClient;
    this.keyPrefix = normalizePrefix(keyPrefix);
    this.retentionDays = positiveInteger(retentionDays, 'retentionDays');
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

    const result = await this.evalClient.eval(
      RECORD_SCRIPT,
      [
        `${this.keyPrefix}:dedupe:${eventId}`,
        `${this.keyPrefix}:count:${date}`,
        `${this.keyPrefix}:unique:${date}:${eventName}:${entitlement}`,
      ],
      [subject, field, dedupeTtl, aggregateTtl],
    );
    return Number(result) === 1;
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
