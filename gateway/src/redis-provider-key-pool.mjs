import { REQUEST_PRIORITY } from './provider-key-pool.mjs';

const WINDOW_MS = 60_000;
const EXTRA_WINDOW_RETENTION_MS = 60_000;
const FAILURE_RETENTION_MS = 7 * 24 * 60 * 60 * 1000;

const LEASE_SCRIPT = `
local keyCount = tonumber(ARGV[1])
local isFree = ARGV[2] == '1'
local reserveFraction = tonumber(ARGV[3])
local minuteExpireAt = tonumber(ARGV[4])
local bestIndex = 0
local bestId = ''
local bestUsage = 0
local bestRpm = 1
local bestFailures = 0
local bestCeiling = 0

for i = 1, keyCount do
  local keyOffset = (i - 1) * 4
  local argOffset = 4 + (i - 1) * 4
  local id = ARGV[argOffset + 1]
  local rpm = tonumber(ARGV[argOffset + 2])
  local configuredEnabled = ARGV[argOffset + 3] == '1'
  local excluded = ARGV[argOffset + 4] == '1'
  local sharedDisabled = redis.call('EXISTS', KEYS[keyOffset + 4]) == 1
  local coolingDown = redis.call('EXISTS', KEYS[keyOffset + 2]) == 1
  local usage = tonumber(redis.call('GET', KEYS[keyOffset + 1]) or '0')
  local failures = tonumber(redis.call('GET', KEYS[keyOffset + 3]) or '0')
  local ceiling = rpm
  if isFree then
    ceiling = math.max(0, math.floor(rpm * (1 - reserveFraction)))
  end

  if configuredEnabled and not excluded and not sharedDisabled and not coolingDown and usage < ceiling then
    local choose = false
    if bestIndex == 0 then
      choose = true
    else
      local left = usage * bestRpm
      local right = bestUsage * rpm
      if left < right then
        choose = true
      elseif left == right then
        if failures < bestFailures then
          choose = true
        elseif failures == bestFailures and id < bestId then
          choose = true
        end
      end
    end

    if choose then
      bestIndex = i
      bestId = id
      bestUsage = usage
      bestRpm = rpm
      bestFailures = failures
      bestCeiling = ceiling
    end
  end
end

if bestIndex == 0 then
  return {'', -1}
end

local minuteKey = KEYS[(bestIndex - 1) * 4 + 1]
local after = redis.call('INCR', minuteKey)
redis.call('PEXPIREAT', minuteKey, minuteExpireAt)
return {bestId, math.max(0, bestCeiling - after)}
`;

const REPORT_SUCCESS_SCRIPT = `
return redis.call('DEL', KEYS[1])
`;

const REPORT_RATE_LIMIT_SCRIPT = `
local retryMs = tonumber(ARGV[1])
local currentTtl = redis.call('PTTL', KEYS[1])
if currentTtl < retryMs then
  redis.call('SET', KEYS[1], '1', 'PX', retryMs)
end
local failures = redis.call('INCR', KEYS[2])
redis.call('PEXPIRE', KEYS[2], tonumber(ARGV[2]))
return failures
`;

const REPORT_FAILURE_SCRIPT = `
local threshold = tonumber(ARGV[1])
local cooldownMs = tonumber(ARGV[2])
local retentionMs = tonumber(ARGV[3])
local failures = redis.call('INCR', KEYS[1])
redis.call('PEXPIRE', KEYS[1], retentionMs)
if failures >= threshold then
  local currentTtl = redis.call('PTTL', KEYS[2])
  if currentTtl < cooldownMs then
    redis.call('SET', KEYS[2], '1', 'PX', cooldownMs)
  end
end
return failures
`;

const SET_ENABLED_SCRIPT = `
if ARGV[1] == '1' then
  return redis.call('DEL', KEYS[1])
end
redis.call('SET', KEYS[1], '1')
return 1
`;

/**
 * Multi-instance provider-key capacity pool backed by atomic Redis Lua scripts.
 *
 * Raw provider secrets never enter Redis. Redis stores only opaque key ids, counters, cooldowns,
 * failure counts and disabled flags. The selected id is mapped back to its secret only inside this
 * server process immediately before the upstream request.
 */
export class RedisProviderKeyPool {
  constructor(keys, options = {}) {
    if (!Array.isArray(keys) || keys.length === 0) {
      throw new Error('RedisProviderKeyPool requires at least one key');
    }
    if (!options.evalClient || typeof options.evalClient.eval !== 'function') {
      throw new Error('RedisProviderKeyPool requires evalClient.eval');
    }
    this.evalClient = options.evalClient;
    this.now = typeof options.now === 'function' ? options.now : () => Date.now();
    this.paidReserveFraction = fraction(options.paidReserveFraction ?? 0.2);
    this.cooldownOnFailureMs = positiveInteger(
      options.cooldownOnFailureMs ?? 30_000,
      'cooldownOnFailureMs',
    );
    this.maxFailuresBeforeCooldown = positiveInteger(
      options.maxFailuresBeforeCooldown ?? 3,
      'maxFailuresBeforeCooldown',
    );
    this.keyPrefix = normalizePrefix(options.keyPrefix ?? 'nova:provider:v1');

    const ids = new Set();
    this.keys = keys.map((key, index) => {
      const id = safeSegment(key?.id ?? `key-${index + 1}`, 'key id', 128);
      const secret = requiredSecret(key?.secret, id);
      const rpmLimit = positiveInteger(key?.rpmLimit ?? 20, `rpmLimit(${id})`);
      if (ids.has(id)) throw new Error(`duplicate key id: ${id}`);
      ids.add(id);
      return Object.freeze({ id, secret, rpmLimit, enabled: key?.enabled !== false });
    });
    this.byId = new Map(this.keys.map((key) => [key.id, key]));
  }

  async lease(priority = REQUEST_PRIORITY.FREE, options = {}) {
    assertPriority(priority);
    const excluded = normalizeExcludedIds(options.excludeIds);
    const nowMs = nonNegativeInteger(this.now(), 'now');
    const minuteStartMs = Math.floor(nowMs / WINDOW_MS) * WINDOW_MS;
    const minuteExpireAt = minuteStartMs + WINDOW_MS + EXTRA_WINDOW_RETENTION_MS;
    const redisKeys = [];
    const args = [
      this.keys.length,
      priority === REQUEST_PRIORITY.FREE ? 1 : 0,
      this.paidReserveFraction,
      minuteExpireAt,
    ];

    for (const key of this.keys) {
      redisKeys.push(
        this.minuteKey(key.id, minuteStartMs),
        this.cooldownKey(key.id),
        this.failureKey(key.id),
        this.disabledKey(key.id),
      );
      args.push(key.id, key.rpmLimit, key.enabled ? 1 : 0, excluded.has(key.id) ? 1 : 0);
    }

    const result = await this.evalClient.eval(LEASE_SCRIPT, redisKeys, args);
    if (!Array.isArray(result) || result.length < 1) {
      throw new Error('Redis provider lease returned malformed result');
    }
    const id = String(result[0] ?? '').trim();
    if (!id) return null;
    const selected = this.byId.get(id);
    if (!selected || excluded.has(id) || !selected.enabled) {
      throw new Error('Redis provider lease returned unknown or ineligible key id');
    }
    return Object.freeze({ id: selected.id, secret: selected.secret });
  }

  async reportSuccess(id) {
    const key = this.requireKey(id);
    await this.evalClient.eval(REPORT_SUCCESS_SCRIPT, [this.failureKey(key.id)], []);
  }

  async reportRateLimit(id, retryAfterMs = WINDOW_MS) {
    const key = this.requireKey(id);
    const retryMs = Math.max(1_000, finitePositive(retryAfterMs, WINDOW_MS));
    await this.evalClient.eval(
      REPORT_RATE_LIMIT_SCRIPT,
      [this.cooldownKey(key.id), this.failureKey(key.id)],
      [retryMs, FAILURE_RETENTION_MS],
    );
  }

  async reportFailure(id) {
    const key = this.requireKey(id);
    await this.evalClient.eval(
      REPORT_FAILURE_SCRIPT,
      [this.failureKey(key.id), this.cooldownKey(key.id)],
      [this.maxFailuresBeforeCooldown, this.cooldownOnFailureMs, FAILURE_RETENTION_MS],
    );
  }

  async setEnabled(id, enabled) {
    const key = this.requireKey(id);
    await this.evalClient.eval(
      SET_ENABLED_SCRIPT,
      [this.disabledKey(key.id)],
      [enabled ? 1 : 0],
    );
  }

  safeSummary() {
    return Object.freeze({
      providerCapacity: 'redis-atomic',
      keyCount: this.keys.length,
      paidReserveFraction: this.paidReserveFraction,
    });
  }

  requireKey(id) {
    const key = this.byId.get(String(id ?? '').trim());
    if (!key) throw new Error(`unknown key id: ${String(id ?? '')}`);
    return key;
  }

  minuteKey(id, bucket) {
    return `${this.keyPrefix}:m:${id}:${bucket}`;
  }

  cooldownKey(id) {
    return `${this.keyPrefix}:cool:${id}`;
  }

  failureKey(id) {
    return `${this.keyPrefix}:fail:${id}`;
  }

  disabledKey(id) {
    return `${this.keyPrefix}:disabled:${id}`;
  }
}

export const REDIS_PROVIDER_CAPACITY_SCRIPTS = Object.freeze({
  lease: LEASE_SCRIPT,
  reportSuccess: REPORT_SUCCESS_SCRIPT,
  reportRateLimit: REPORT_RATE_LIMIT_SCRIPT,
  reportFailure: REPORT_FAILURE_SCRIPT,
  setEnabled: SET_ENABLED_SCRIPT,
});

function normalizeExcludedIds(value) {
  if (value == null) return new Set();
  if (value instanceof Set) return new Set([...value].map(String));
  if (Array.isArray(value)) return new Set(value.map(String));
  throw new Error('excludeIds must be an array or Set');
}

function assertPriority(priority) {
  if (!Object.values(REQUEST_PRIORITY).includes(priority)) {
    throw new Error(`unknown request priority: ${priority}`);
  }
}

function normalizePrefix(value) {
  const text = String(value ?? '').trim();
  if (!/^[A-Za-z0-9:_-]{1,80}$/.test(text)) throw new Error('provider Redis keyPrefix is invalid');
  return text;
}

function safeSegment(value, name, maxLength) {
  const text = String(value ?? '').trim();
  if (!text || text.length > maxLength || !/^[A-Za-z0-9._:-]+$/.test(text)) {
    throw new Error(`${name} is invalid`);
  }
  return text;
}

function requiredSecret(value, id) {
  const text = String(value ?? '').trim();
  if (!text) throw new Error(`secret(${id}) must not be blank`);
  return text;
}

function positiveInteger(value, name) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0) throw new Error(`${name} must be positive`);
  return number;
}

function nonNegativeInteger(value, name) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number < 0) throw new Error(`${name} must be non-negative`);
  return number;
}

function finitePositive(value, fallback) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? Math.round(number) : fallback;
}

function fraction(value) {
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0 || number >= 1) {
    throw new Error('paidReserveFraction must be >= 0 and < 1');
  }
  return number;
}
