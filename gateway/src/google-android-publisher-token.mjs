import { GoogleServiceAccountAccessTokenProvider } from './google-service-account-token.mjs';

export const GOOGLE_ANDROID_PUBLISHER_SCOPE =
  'https://www.googleapis.com/auth/androidpublisher';

/**
 * Server-only OAuth provider for Google Play Developer API purchase verification.
 *
 * Billing credentials may be isolated from Play Integrity credentials. For small deployments the
 * same service account can be reused by omitting the NOVA_PLAY_BILLING_* variables, provided that
 * account is linked in Play Console and has the required purchase/subscription API permissions.
 */
export function googleAndroidPublisherAccessTokenProviderFromEnv(
  env = process.env,
  options = {},
) {
  const encodedPrivateKey = firstNonBlank(
    env.NOVA_PLAY_BILLING_SERVICE_ACCOUNT_PRIVATE_KEY_B64,
    env.NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_PRIVATE_KEY_B64,
  );
  const privateKey = decodePrivateKey(encodedPrivateKey);
  return new GoogleServiceAccountAccessTokenProvider({
    clientEmail: firstNonBlank(
      env.NOVA_PLAY_BILLING_SERVICE_ACCOUNT_EMAIL,
      env.NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_EMAIL,
    ),
    privateKey,
    privateKeyId: firstNonBlank(
      env.NOVA_PLAY_BILLING_SERVICE_ACCOUNT_KEY_ID,
      env.NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_KEY_ID,
    ),
    fetchImpl: options.fetchImpl,
    now: options.now,
    timeoutMs: env.NOVA_GOOGLE_OAUTH_TIMEOUT_MS ?? options.timeoutMs,
    scope: GOOGLE_ANDROID_PUBLISHER_SCOPE,
  });
}

function decodePrivateKey(value) {
  const encoded = String(value ?? '').trim();
  if (!encoded || encoded.length > 64 * 1024 || !/^[A-Za-z0-9+/=]+$/.test(encoded)) {
    throw new Error('Nova Play billing service-account private key is invalid');
  }
  let decoded;
  try {
    decoded = Buffer.from(encoded, 'base64').toString('utf8').trim();
  } catch {
    throw new Error('Nova Play billing service-account private key is invalid');
  }
  if (!/^-----BEGIN (?:RSA )?PRIVATE KEY-----/.test(decoded)) {
    throw new Error('Nova Play billing service-account private key must be PEM');
  }
  return decoded;
}

function firstNonBlank(...values) {
  for (const value of values) {
    const text = String(value ?? '').trim();
    if (text) return text;
  }
  return '';
}
