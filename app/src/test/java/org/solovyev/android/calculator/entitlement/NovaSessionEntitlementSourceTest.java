package org.solovyev.android.calculator.entitlement;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.solovyev.android.calculator.ai.AiSessionTokenStore;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NovaSessionEntitlementSourceTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void restoresPaidEntitlementsFromUnexpiredAccountSession() throws Exception {
        long expiresAt = NOW + 3_600_000L;
        FakeStore store = new FakeStore(token("account", expiresAt,
                Entitlement.PRO_LIFETIME.name(), Entitlement.AI_PLUS.name()), expiresAt);
        NovaSessionEntitlementSource source = new NovaSessionEntitlementSource(store, () -> NOW);

        EntitlementSnapshot snapshot = source.load();

        assertTrue(snapshot.hasProLifetime());
        assertTrue(snapshot.hasAiPlus());
    }

    @Test
    public void anonymousSessionNeverGrantsPaidRights() throws Exception {
        long expiresAt = NOW + 3_600_000L;
        FakeStore store = new FakeStore(token("anonymous", expiresAt), expiresAt);
        NovaSessionEntitlementSource source = new NovaSessionEntitlementSource(store, () -> NOW);

        EntitlementSnapshot snapshot = source.load();

        assertFalse(snapshot.hasProLifetime());
        assertFalse(snapshot.hasAiPlus());
    }

    @Test
    public void rejectsExpiredOrStoreExpiryMismatch() throws Exception {
        long tokenExpiry = NOW + 3_600_000L;
        NovaSessionEntitlementSource mismatch = new NovaSessionEntitlementSource(
                new FakeStore(token("account", tokenExpiry, Entitlement.PRO_LIFETIME.name()),
                        tokenExpiry + 1_000L),
                () -> NOW);
        NovaSessionEntitlementSource expired = new NovaSessionEntitlementSource(
                new FakeStore(token("account", NOW - 1_000L, Entitlement.PRO_LIFETIME.name()),
                        NOW - 1_000L),
                () -> NOW);

        assertFalse(mismatch.load().hasProLifetime());
        assertFalse(expired.load().hasProLifetime());
    }

    @Test
    public void rejectsUnknownEntitlementAndAnonymousPaidClaim() throws Exception {
        long expiresAt = NOW + 3_600_000L;
        NovaSessionEntitlementSource unknown = new NovaSessionEntitlementSource(
                new FakeStore(token("account", expiresAt, "UNKNOWN"), expiresAt),
                () -> NOW);
        NovaSessionEntitlementSource anonymousPaid = new NovaSessionEntitlementSource(
                new FakeStore(token("anonymous", expiresAt, Entitlement.PRO_LIFETIME.name()), expiresAt),
                () -> NOW);

        assertFalse(unknown.load().hasProLifetime());
        assertFalse(anonymousPaid.load().hasProLifetime());
    }

    private static String token(String kind, long expiresAtEpochMs, String... entitlements)
            throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("v", 1);
        payload.put("iss", "nova-calculator");
        payload.put("kind", kind);
        payload.put("sub", "anon_test");
        payload.put("iat", (expiresAtEpochMs / 1000L) - 3600L);
        payload.put("exp", expiresAtEpochMs / 1000L);
        JSONArray ent = new JSONArray();
        for (String entitlement : entitlements) ent.put(entitlement);
        payload.put("ent", ent);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.toString().getBytes(StandardCharsets.UTF_8));
        return "nova1." + encoded + ".test-signature";
    }

    private static final class FakeStore implements AiSessionTokenStore {
        private Snapshot snapshot;

        FakeStore(String token, long expiresAtEpochMs) {
            snapshot = new Snapshot(token, expiresAtEpochMs);
        }

        @Override
        public Snapshot load() {
            return snapshot;
        }

        @Override
        public void save(String token, long expiresAtEpochMs) {
            snapshot = new Snapshot(token, expiresAtEpochMs);
        }

        @Override
        public void clear() {
            snapshot = null;
        }
    }
}
