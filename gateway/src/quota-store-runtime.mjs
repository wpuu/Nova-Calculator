import { RedisQuotaStore } from './redis-quota-store.mjs';
import { upstashRedisEvalClientFromEnv } from './upstash-redis-eval-client.mjs';

/** Build the production Redis-backed quota store entirely from server-side deployment config. */
export function redisQuotaStoreFromEnv(env = process.env, options = {}) {
  const evalClient = options.evalClient ?? upstashRedisEvalClientFromEnv(env, {
    fetchImpl: options.fetchImpl,
    timeoutMs: options.timeoutMs,
  });
  return new RedisQuotaStore({
    evalClient,
    keyPrefix: env.NOVA_QUOTA_REDIS_KEY_PREFIX || 'nova:quota:v1',
  });
}
