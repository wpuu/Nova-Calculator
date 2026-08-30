/**
 * Convert a deployment-secret string into provider-neutral key-pool entries.
 * The deployment layer decides which environment variable supplies the raw value.
 */
export function parseProviderKeys(raw, rpmLimit = 20) {
  const limit = Number(rpmLimit);
  if (!Number.isInteger(limit) || limit <= 0) {
    throw new Error('rpmLimit must be a positive integer');
  }

  const secrets = String(raw ?? '')
    .split(/[\n,;]+/)
    .map((value) => value.trim())
    .filter(Boolean);

  if (secrets.length === 0) {
    throw new Error('no provider API keys configured');
  }

  const unique = [...new Set(secrets)];
  return unique.map((secret, index) => ({
    id: `key-${index + 1}`,
    secret,
    rpmLimit: limit,
  }));
}
