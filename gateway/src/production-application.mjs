import { createNovaGatewayApplication } from './application.mjs';
import { googleAndroidPublisherAccessTokenProviderFromEnv } from './google-android-publisher-token.mjs';
import { googlePlayIntegrityDecoderFromEnv } from './google-play-integrity-decoder.mjs';
import { GooglePlayPurchaseVerifier } from './google-play-purchase-verifier.mjs';
import { PlayIntegrityInstallationProofVerifier } from './play-integrity-proof-verifier.mjs';
import { redisQuotaStoreFromEnv } from './quota-store-runtime.mjs';
import { RedisProviderKeyPool } from './redis-provider-key-pool.mjs';
import { upstashRedisEvalClientFromEnv } from './upstash-redis-eval-client.mjs';

/**
 * Production deployment composition for Nova Gateway.
 *
 * This is the only layer that binds the provider-neutral core to concrete server adapters:
 * shared Redis capacity/accounting, Google Play Integrity server decoding, and (when credentials
 * are configured) Google Play purchase verification. All credentials remain server-side.
 */
export function createProductionNovaGatewayApplication(options = {}) {
  const env = options.env ?? process.env;
  const now = typeof options.now === 'function' ? options.now : () => Date.now();
  const packageName = requireAndroidPackage(env.NOVA_ANDROID_PACKAGE_NAME);
  guardProductionIdentity(env, packageName);

  let redisEvalClient = options.redisEvalClient;
  if ((!options.quotaStore || !options.keyPoolFactory) && !redisEvalClient) {
    redisEvalClient = upstashRedisEvalClientFromEnv(env, {
      fetchImpl: options.fetchImpl,
      timeoutMs: options.redisTimeoutMs,
    });
  }

  const quotaStore = options.quotaStore ?? redisQuotaStoreFromEnv(env, {
    evalClient: redisEvalClient,
  });

  const keyPoolFactory = options.keyPoolFactory ?? ((poolOptions) => new RedisProviderKeyPool(
    poolOptions.keys,
    {
      evalClient: redisEvalClient,
      keyPrefix: env.NOVA_PROVIDER_REDIS_KEY_PREFIX || 'nova:provider:v1',
      paidReserveFraction: poolOptions.paidReserveFraction,
      cooldownOnFailureMs: poolOptions.cooldownOnFailureMs,
      maxFailuresBeforeCooldown: poolOptions.maxFailuresBeforeCooldown,
      credentialDisableMs: poolOptions.credentialDisableMs,
      now: poolOptions.now,
    },
  ));

  let integrityDecoder = options.integrityDecoder;
  let installationProofVerifier = options.installationProofVerifier;
  if (!installationProofVerifier) {
    integrityDecoder = integrityDecoder ?? googlePlayIntegrityDecoderFromEnv(env, {
      fetchImpl: options.fetchImpl,
      now,
    });
    installationProofVerifier = new PlayIntegrityInstallationProofVerifier({
      decodeIntegrityToken: (token) => integrityDecoder.decodeIntegrityToken(token),
      expectedPackageName: packageName,
      now,
      requireLicensed: env.NOVA_PLAY_INTEGRITY_REQUIRE_LICENSED !== 'false',
      requireDeviceIntegrity: env.NOVA_PLAY_INTEGRITY_REQUIRE_DEVICE_INTEGRITY !== 'false',
    });
  }

  let purchaseVerifier = options.purchaseVerifier ?? null;
  if (!purchaseVerifier && hasBillingCredentials(env)) {
    const billingAccessTokenProvider = googleAndroidPublisherAccessTokenProviderFromEnv(env, {
      fetchImpl: options.fetchImpl,
      now,
    });
    purchaseVerifier = new GooglePlayPurchaseVerifier({
      packageName,
      accessTokenProvider: billingAccessTokenProvider,
      fetchImpl: options.fetchImpl,
      now,
      timeoutMs: env.NOVA_PLAY_BILLING_API_TIMEOUT_MS ?? options.billingTimeoutMs,
    });
  }

  const core = createNovaGatewayApplication({
    env,
    now,
    fetchImpl: options.fetchImpl,
    newReservationId: options.newReservationId,
    quotaStore,
    keyPoolFactory,
    installationProofVerifier,
    purchaseVerifier,
  });

  return Object.freeze({
    ...core,
    safeSummary: Object.freeze({
      ...core.safeSummary,
      deploymentComposition: 'production-v2-billing',
      androidPackageName: packageName,
      sharedQuotaStore: true,
      sharedProviderCapacity: true,
      playIntegrityServerDecode: !options.installationProofVerifier,
      googlePlayPurchaseVerification: Boolean(purchaseVerifier),
    }),
  });
}

function requireAndroidPackage(value) {
  const text = String(value ?? '').trim();
  if (!/^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/.test(text)
      || text.length > 255) {
    throw new Error('NOVA_ANDROID_PACKAGE_NAME is invalid');
  }
  return text;
}

function guardProductionIdentity(env, packageName) {
  if (String(env.VERCEL_ENV ?? '').trim().toLowerCase() === 'production'
      && packageName.toLowerCase().endsWith('.dev')) {
    throw new Error('production deployment refuses a .dev Android package');
  }
}

function hasBillingCredentials(env) {
  const billingEmail = String(env.NOVA_PLAY_BILLING_SERVICE_ACCOUNT_EMAIL ?? '').trim();
  const billingKey = String(env.NOVA_PLAY_BILLING_SERVICE_ACCOUNT_PRIVATE_KEY_B64 ?? '').trim();
  if (billingEmail || billingKey) return Boolean(billingEmail && billingKey);
  return Boolean(
    String(env.NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_EMAIL ?? '').trim()
    && String(env.NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_PRIVATE_KEY_B64 ?? '').trim(),
  );
}
