/**
 * Convert a deployment-secret string into provider-neutral key-pool entries.
 * The deployment layer decides which environment variable supplies the raw value.
 *
 * Nova's Agnes-backed deployment deliberately stays below the upstream <20 RPM account/key
 * constraint: steady-state default is 12 RPM and deployment configuration is fail-closed above 15.
 */
export function parseProviderKeys(raw, rpmLimit = 12) {
  const limit = Number(rpmLimit);
  if (!Number.isInteger(limit) || limit <= 0 || limit > 15) {
    throw new Error('rpmLimit must be an integer between 1 and 15');
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
