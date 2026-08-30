import { GatewayDispatcher } from './gateway-dispatcher.mjs';
import { OpenAiCompatibleChatProvider } from './openai-compatible-chat-provider.mjs';
import { parseProviderKeys } from './provider-key-config.mjs';
import { ProviderKeyPool } from './provider-key-pool.mjs';

/**
 * Builds the server-only Nova AI runtime from deployment environment variables.
 * Concrete provider identity remains outside Android and outside committed configuration.
 */
export function createGatewayRuntime(env = process.env, options = {}) {
  const config = readConfig(env);
  const keys = parseProviderKeys(config.providerKeys, config.rpmPerKey);
  const keyPool = new ProviderKeyPool(keys, {
    paidReserveFraction: config.paidReserveFraction,
    cooldownOnFailureMs: config.cooldownOnFailureMs,
    maxFailuresBeforeCooldown: config.maxFailuresBeforeCooldown,
    now: options.now,
  });
  const provider = new OpenAiCompatibleChatProvider({
    baseUrl: config.providerBaseUrl,
    model: config.providerModel,
    timeoutMs: config.providerTimeoutMs,
    maxTokens: config.maxTokens,
    fetchImpl: options.fetchImpl,
  });
  const dispatcher = new GatewayDispatcher({ keyPool, provider });

  return Object.freeze({
    dispatcher,
    keyPool,
    safeSummary: Object.freeze({
      providerKeyCount: keys.length,
      rpmPerKey: config.rpmPerKey,
      paidReserveFraction: config.paidReserveFraction,
      providerTimeoutMs: config.providerTimeoutMs,
      maxTokens: config.maxTokens,
    }),
  });
}

function readConfig(env) {
  return Object.freeze({
    providerBaseUrl: requireEnv(env, 'NOVA_PROVIDER_BASE_URL'),
    providerModel: requireEnv(env, 'NOVA_PROVIDER_MODEL'),
    providerKeys: requireEnv(env, 'NOVA_PROVIDER_KEYS'),
    rpmPerKey: positiveInt(env.NOVA_PROVIDER_RPM_PER_KEY ?? 20, 'NOVA_PROVIDER_RPM_PER_KEY'),
    paidReserveFraction: fraction(env.NOVA_PAID_RESERVE_FRACTION ?? 0.2, 'NOVA_PAID_RESERVE_FRACTION'),
    providerTimeoutMs: positiveInt(env.NOVA_PROVIDER_TIMEOUT_MS ?? 15_000, 'NOVA_PROVIDER_TIMEOUT_MS'),
    maxTokens: positiveInt(env.NOVA_PROVIDER_MAX_TOKENS ?? 800, 'NOVA_PROVIDER_MAX_TOKENS'),
    cooldownOnFailureMs: positiveInt(env.NOVA_PROVIDER_FAILURE_COOLDOWN_MS ?? 30_000, 'NOVA_PROVIDER_FAILURE_COOLDOWN_MS'),
    maxFailuresBeforeCooldown: positiveInt(env.NOVA_PROVIDER_FAILURE_THRESHOLD ?? 3, 'NOVA_PROVIDER_FAILURE_THRESHOLD'),
  });
}

function requireEnv(env, name) {
  const value = String(env?.[name] ?? '').trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function positiveInt(value, name) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return number;
}

function fraction(value, name) {
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0 || number >= 1) {
    throw new Error(`${name} must be >= 0 and < 1`);
  }
  return number;
}
