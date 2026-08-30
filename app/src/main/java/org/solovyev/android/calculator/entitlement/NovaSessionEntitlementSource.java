package org.solovyev.android.calculator.entitlement;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.solovyev.android.calculator.ai.AiSessionTokenStore;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

/**
 * Reads local UI/local-feature entitlements from Nova's last server-issued session token.
 *
 * The APK intentionally does not contain the HMAC signing secret, so this source cannot and does
 * not authenticate the token cryptographically. Remote AI access remains protected because the
 * gateway verifies the signature server-side on every request. This local parser only restores
 * convenience feature flags from app-private storage; a patched APK can always bypass local UI
 * gates regardless of token format.
 */
public final class NovaSessionEntitlementSource implements EntitlementSource {

    interface Clock {
        long now();
    }

    private static final int MAX_TOKEN_CHARS = 8192;
    private static final int MAX_PAYLOAD_BYTES = 4096;

    private final AiSessionTokenStore tokenStore;
    private final Clock clock;

    public NovaSessionEntitlementSource(AiSessionTokenStore tokenStore) {
        this(tokenStore, System::currentTimeMillis);
    }

    NovaSessionEntitlementSource(AiSessionTokenStore tokenStore, Clock clock) {
        if (tokenStore == null) throw new IllegalArgumentException("tokenStore must not be null");
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        this.tokenStore = tokenStore;
        this.clock = clock;
    }

    @Override
    public EntitlementSnapshot load() {
        final long now = safeNow();
        final AiSessionTokenStore.Snapshot cached;
        try {
            cached = tokenStore.load();
        } catch (RuntimeException e) {
            return EntitlementSnapshot.free("session-cache-read-failed", now);
        }
        if (cached == null || cached.getExpiresAtEpochMs() <= now) {
            return EntitlementSnapshot.free("session-cache-empty", now);
        }

        final Parsed parsed = parse(cached.getToken());
        if (parsed == null || parsed.expiresAtEpochMs <= now
                || parsed.expiresAtEpochMs != cached.getExpiresAtEpochMs()) {
            return EntitlementSnapshot.free("session-cache-invalid", now);
        }
        if (parsed.anonymous) {
            if (!parsed.entitlements.isEmpty()) {
                return EntitlementSnapshot.free("session-cache-invalid", now);
            }
            return EntitlementSnapshot.free("anonymous-session", now);
        }
        return parsed.entitlements.isEmpty()
                ? EntitlementSnapshot.free("verified-play-session", now)
                : EntitlementSnapshot.of(parsed.entitlements, "verified-play-session", now);
    }

    private Parsed parse(String token) {
        if (token == null) return null;
        final String text = token.trim();
        if (text.isEmpty() || text.length() > MAX_TOKEN_CHARS) return null;
        final String[] parts = text.split("\\.", -1);
        if (parts.length != 3 || !"nova1".equals(parts[0])
                || parts[1].isEmpty() || parts[2].isEmpty()) {
            return null;
        }

        try {
            final byte[] payloadBytes = Base64.decode(
                    parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            if (payloadBytes.length == 0 || payloadBytes.length > MAX_PAYLOAD_BYTES) return null;
            final JSONObject payload = new JSONObject(
                    new String(payloadBytes, StandardCharsets.UTF_8));
            if (payload.optInt("v", -1) != 1) return null;
            final String kind = payload.optString("kind", "");
            final boolean anonymous;
            if ("anonymous".equals(kind)) {
                anonymous = true;
            } else if ("account".equals(kind)) {
                anonymous = false;
            } else {
                return null;
            }
            final long expSeconds = payload.optLong("exp", 0L);
            if (expSeconds <= 0L || expSeconds > Long.MAX_VALUE / 1000L) return null;

            final JSONArray values = payload.optJSONArray("ent");
            if (values == null) return null;
            final EnumSet<Entitlement> entitlements = EnumSet.noneOf(Entitlement.class);
            for (int i = 0; i < values.length(); i++) {
                final String value = values.optString(i, "");
                if (Entitlement.PRO_LIFETIME.name().equals(value)) {
                    entitlements.add(Entitlement.PRO_LIFETIME);
                } else if (Entitlement.AI_PLUS.name().equals(value)) {
                    entitlements.add(Entitlement.AI_PLUS);
                } else {
                    return null;
                }
            }
            return new Parsed(anonymous, entitlements, expSeconds * 1000L);
        } catch (Exception e) {
            return null;
        }
    }

    private long safeNow() {
        try {
            final long value = clock.now();
            return value >= 0L ? value : Long.MAX_VALUE;
        } catch (RuntimeException e) {
            return Long.MAX_VALUE;
        }
    }

    private static final class Parsed {
        final boolean anonymous;
        final EnumSet<Entitlement> entitlements;
        final long expiresAtEpochMs;

        Parsed(boolean anonymous, EnumSet<Entitlement> entitlements, long expiresAtEpochMs) {
            this.anonymous = anonymous;
            this.entitlements = entitlements;
            this.expiresAtEpochMs = expiresAtEpochMs;
        }
    }
}
