import { GatewayDispatcher } from './gateway-dispatcher.mjs';
import { MacroCandidateReviewProvider } from './macro-candidate-review-provider.mjs';
import { OpenAiCompatibleChatProvider } from './openai-compatible-chat-provider.mjs';
import { parseProviderKeys } from './provider-key-config.mjs';
import { ProviderKeyPool } from './provider-key-pool.mjs';

/**
 * Builds the server-only Nova AI runtime from deployment environment variables.
 * Concrete provider identity remains outside Android/extension clients and outside committed config.
 *
 * Calculator explanations and Macro candidate review use separate provider adapters/dispatchers,
 * but deliberately share one provider key pool so RPM, cooldown and multi-key capacity accounting
 * remain global across all Agnes-backed Nova modules.
 *
 * Production deployments may inject keyPoolFactory to replace the single-process pool with a
 * shared atomic implementation while keeping provider configuration parsing in one place.
 */
export function createGatewayRuntime(env = process.env, options = {}) {
  const config = readConfig(env);
  const keys = parseProviderKeys(config.providerKeys, config.rpmPerKey);
  const poolOptions = Object.freeze({
    paidReserveFraction: config.paidReserveFraction,
    cooldownOnFailureMs: config.cooldownOnFailureMs,
    maxFailuresBeforeCooldown: config.maxFailuresBeforeCooldown,
    now: options.now,
  });
  const keyPool = typeof options.keyPoolFactory === 'function'
    ? options.keyPoolFactory(Object.freeze({ keys, ...poolOptions }))
    : new ProviderKeyPool(keys, poolOptions);
  if (!keyPool || typeof keyPool.lease !== 'function') {
    throw new Error('keyPoolFactory returned an invalid provider key pool');
  }

  const calculationProvider = new OpenAiCompatibleChatProvider({
    baseUrl: config.providerBaseUrl,
    model: config.providerModel,
    timeoutMs: config.providerTimeoutMs,
    maxTokens: config.maxTokens,
    fetchImpl: options.fetchImpl,
  });
  const macroCandidateReviewProvider = new MacroCandidateReviewProvider({
    baseUrl: config.providerBaseUrl,
    model: config.providerModel,
    timeoutMs: config.providerTimeoutMs,
    maxTokens: config.macroMaxTokens,
    fetchImpl: options.fetchImpl,
  });

  const dispatcher = new GatewayDispatcher({ keyPool, provider: calculationProvider });
  const macroCandidateReviewDispatcher = new GatewayDispatcher({
    keyPool,
    provider: macroCandidateReviewProvider,
  });

  return Object.freeze({
    dispatcher,
    macroCandidateReviewDispatcher,
    keyPool,
    safeSummary: Object.freeze({
      providerKeyCount: keys.length,
      rpmPerKey: config.rpmPerKey,
      paidReserveFraction: config.paidReserveFraction,
      providerTimeoutMs: config.providerTimeoutMs,
      maxTokens: config.maxTokens,
      macroMaxTokens: config.macroMaxTokens,
      sharedProviderCapacity: typeof options.keyPoolFactory === 'function',
      macroCandidateReviewSharesProviderCapacity: true,
    }),
  });
}

function readConfig(env) {
  return Object.freeze({
    providerBaseUrl: requireEnv(env, 'NOVA_PROVIDER_BASE_URL'),
    providerModel: requireEnv(env, 'NOVA_PROVIDER_MODEL'),
    providerKeys: requireEnv(env, 'NOVA_PROVIDER_KEYS'),
    // Agnes deployment policy: steady-state 12 RPM/key, hard fail above 15.
    // parseProviderKeys enforces the hard ceiling so a bad deployment value cannot silently overrun it.
    rpmPerKey: positiveInt(env.NOVA_PROVIDER_RPM_PER_KEY ?? 12, 'NOVA_PROVIDER_RPM_PER_KEY'),
    paidReserveFraction: fraction(env.NOVA_PAID_RESERVE_FRACTION ?? 0.2, 'NOVA_PAID_RESERVE_FRACTION'),
    providerTimeoutMs: positiveInt(env.NOVA_PROVIDER_TIMEOUT_MS ?? 15_000, 'NOVA_PROVIDER_TIMEOUT_MS'),
    maxTokens: positiveInt(env.NOVA_PROVIDER_MAX_TOKENS ?? 800, 'NOVA_PROVIDER_MAX_TOKENS'),
    macroMaxTokens: positiveInt(env.NOVA_MACRO_PROVIDER_MAX_TOKENS ?? 400, 'NOVA_MACRO_PROVIDER_MAX_TOKENS'),
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
