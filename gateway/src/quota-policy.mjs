import { REQUEST_PRIORITY } from './provider-key-pool.mjs';

export const DEFAULT_AI_QUOTA_LIMITS = Object.freeze({
  [REQUEST_PRIORITY.FREE]: Object.freeze({ dailyLimit: 3, rpmLimit: 1 }),
  [REQUEST_PRIORITY.PRO]: Object.freeze({ dailyLimit: 10, rpmLimit: 3 }),
  [REQUEST_PRIORITY.AI_PLUS]: Object.freeze({ dailyLimit: 200, rpmLimit: 10 }),
});

/**
 * Commercial AI allowance policy.
 *
 * Defaults intentionally keep the free trial small because provider capacity is RPM-limited.
 * Every value is deploy-time configurable; changing an allowance never requires a new APK.
 */
export function quotaPolicyFromEnv(env = process.env) {
  return Object.freeze({
    [REQUEST_PRIORITY.FREE]: Object.freeze({
      dailyLimit: positiveInt(env.NOVA_AI_FREE_DAILY_LIMIT ?? 3, 'NOVA_AI_FREE_DAILY_LIMIT'),
      rpmLimit: positiveInt(env.NOVA_AI_FREE_RPM_LIMIT ?? 1, 'NOVA_AI_FREE_RPM_LIMIT'),
    }),
    [REQUEST_PRIORITY.PRO]: Object.freeze({
      dailyLimit: positiveInt(env.NOVA_AI_PRO_DAILY_LIMIT ?? 10, 'NOVA_AI_PRO_DAILY_LIMIT'),
      rpmLimit: positiveInt(env.NOVA_AI_PRO_RPM_LIMIT ?? 3, 'NOVA_AI_PRO_RPM_LIMIT'),
    }),
    [REQUEST_PRIORITY.AI_PLUS]: Object.freeze({
      dailyLimit: positiveInt(env.NOVA_AI_PLUS_DAILY_LIMIT ?? 200, 'NOVA_AI_PLUS_DAILY_LIMIT'),
      rpmLimit: positiveInt(env.NOVA_AI_PLUS_RPM_LIMIT ?? 10, 'NOVA_AI_PLUS_RPM_LIMIT'),
    }),
  });
}

export function quotaLimitsForPriority(policy, priority) {
  if (!Object.values(REQUEST_PRIORITY).includes(priority)) {
    throw new Error(`unknown request priority: ${priority}`);
  }
  const limits = policy?.[priority];
  if (!limits) throw new Error(`quota policy missing priority: ${priority}`);
  return Object.freeze({
    dailyLimit: positiveInt(limits.dailyLimit, `dailyLimit(${priority})`),
    rpmLimit: positiveInt(limits.rpmLimit, `rpmLimit(${priority})`),
  });
}

function positiveInt(value, name) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return number;
}
