package org.solovyev.android.calculator.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpAnonymousSessionTokenProviderTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void validCachedTokenAvoidsProofAndNetwork() throws Exception {
        MemoryStore store = new MemoryStore("cached-token", NOW + 120_000L);
        AtomicInteger proofs = new AtomicInteger();
        AtomicInteger opens = new AtomicInteger();
        HttpAnonymousSessionTokenProvider provider = provider(
                store,
                availableProof(proofs, "proof"),
                successConnection("new-token", NOW + 300_000L),
                opens);

        assertEquals("cached-token", provider.getSessionToken());
        assertEquals(0, proofs.get());
        assertEquals(0, opens.get());
    }

    @Test
    public void nearExpiryTokenRefreshesWithOnlyInstallationIdAndProof() throws Exception {
        MemoryStore store = new MemoryStore("old-token", NOW + 30_000L);
        AtomicInteger proofs = new AtomicInteger();
        AtomicInteger opens = new AtomicInteger();
        FakeConnection connection = successConnection("fresh-token", NOW + 300_000L);
        HttpAnonymousSessionTokenProvider provider = provider(
                store,
                availableProof(proofs, "signed-proof"),
                connection,
                opens);

        assertEquals("fresh-token", provider.getSessionToken());
        assertEquals(1, proofs.get());
        assertEquals(1, opens.get());
        assertFalse(connection.getInstanceFollowRedirects());
        assertEquals("fresh-token", store.token);
        assertEquals(NOW + 300_000L, store.expiresAt);

        JSONObject request = new JSONObject(connection.requestBody.toString("UTF-8"));
        assertEquals(2, request.length());
        assertEquals("installation-123456", request.getString("installationId"));
        assertEquals("signed-proof", request.getString("proof"));
        assertFalse(request.has("entitlements"));
        assertFalse(request.has("priority"));
        assertFalse(request.has("provider"));
        assertFalse(request.has("apiKey"));
    }

    @Test
    public void unavailableOrFailedProofDoesNotOpenSessionNetwork() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        HttpAnonymousSessionTokenProvider unavailable = provider(
                new MemoryStore(null, 0L),
                new InstallationProofProvider() {
                    @Override public boolean isAvailable() { return false; }
                    @Override public String getProof(String installationId) { return "should-not-run"; }
                },
                successConnection("token", NOW + 300_000L),
                opens);
        assertNull(unavailable.getSessionToken());
        assertEquals(0, opens.get());

        HttpAnonymousSessionTokenProvider failed = provider(
                new MemoryStore(null, 0L),
                new InstallationProofProvider() {
                    @Override public boolean isAvailable() { return true; }
                    @Override public String getProof(String installationId) { return null; }
                },
                successConnection("token", NOW + 300_000L),
                opens);
        assertNull(failed.getSessionToken());
        assertEquals(0, opens.get());
    }

    @Test
    public void redirectAndOversizedResponseAreRejected() throws Exception {
        FakeConnection redirect = successConnection("wrong", NOW + 300_000L);
        redirect.responseCode = 307;
        MemoryStore redirectStore = new MemoryStore(null, 0L);
        assertNull(provider(
                redirectStore,
                availableProof(new AtomicInteger(), "proof"),
                redirect,
                new AtomicInteger()).getSessionToken());
        assertFalse(redirect.inputOpened);
        assertNull(redirectStore.token);

        FakeConnection oversized = successConnection("wrong", NOW + 300_000L);
        oversized.contentLength = 2049;
        MemoryStore oversizedStore = new MemoryStore(null, 0L);
        HttpAnonymousSessionTokenProvider provider = new HttpAnonymousSessionTokenProvider(
                new AiSessionEndpoint("https://nova.example/session/anonymous"),
                availableProof(new AtomicInteger(), "proof"),
                () -> "installation-123456",
                oversizedStore,
                url -> oversized,
                () -> NOW,
                1000,
                1000,
                2048,
                60_000L);
        assertNull(provider.getSessionToken());
        assertFalse(oversized.inputOpened);
        assertNull(oversizedStore.token);
    }

    @Test
    public void malformedSuccessPayloadOrUnsafeTokenIsNeverCached() throws Exception {
        FakeConnection malformed = new FakeConnection(new URL("https://nova.example/session/anonymous"));
        malformed.responseBody = "{\"status\":\"SUCCESS\",\"sessionToken\":\"bad\\r\\nheader\",\"expiresAtEpochMs\":"
                + (NOW + 300_000L) + "}";
        MemoryStore store = new MemoryStore(null, 0L);
        assertNull(provider(
                store,
                availableProof(new AtomicInteger(), "proof"),
                malformed,
                new AtomicInteger()).getSessionToken());
        assertNull(store.token);
    }

    @Test
    public void persistenceFailureDoesNotDiscardFreshTokenForCurrentAiRequest() throws Exception {
        MemoryStore store = new MemoryStore(null, 0L);
        store.failSave = true;
        HttpAnonymousSessionTokenProvider provider = provider(
                store,
                availableProof(new AtomicInteger(), "proof"),
                successConnection("fresh-token", NOW + 300_000L),
                new AtomicInteger());

        assertEquals("fresh-token", provider.getSessionToken());
        assertNull(store.token);
    }

    private static HttpAnonymousSessionTokenProvider provider(MemoryStore store,
                                                              InstallationProofProvider proof,
                                                              FakeConnection connection,
                                                              AtomicInteger opens) {
        return new HttpAnonymousSessionTokenProvider(
                new AiSessionEndpoint("https://nova.example/session/anonymous"),
                proof,
                () -> "installation-123456",
                store,
                url -> {
                    opens.incrementAndGet();
                    return connection;
                },
                () -> NOW,
                1000,
                1000,
                16 * 1024,
                60_000L);
    }

    private static InstallationProofProvider availableProof(AtomicInteger calls, String proof) {
        return new InstallationProofProvider() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String getProof(String installationId) {
                calls.incrementAndGet();
                assertEquals("installation-123456", installationId);
                return proof;
            }
        };
    }

    private static FakeConnection successConnection(String token, long expiresAt) throws Exception {
        FakeConnection connection = new FakeConnection(new URL("https://nova.example/session/anonymous"));
        connection.responseCode = 200;
        connection.responseBody = "{\"status\":\"SUCCESS\",\"sessionToken\":\"" + token
                + "\",\"expiresAtEpochMs\":" + expiresAt + "}";
        return connection;
    }

    private static final class MemoryStore implements AiSessionTokenStore {
        String token;
        long expiresAt;
        boolean failSave;

        MemoryStore(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        @Override
        public Snapshot load() {
            return new Snapshot(token, expiresAt);
        }

        @Override
        public void save(String token, long expiresAtEpochMs) {
            if (failSave) throw new IllegalStateException("disk unavailable");
            this.token = token;
            this.expiresAt = expiresAtEpochMs;
        }

        @Override
        public void clear() {
            token = null;
            expiresAt = 0L;
        }
    }

    private static final class FakeConnection extends HttpURLConnection {
        final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
        int responseCode = 200;
        String responseBody = "";
        int contentLength = -1;
        boolean inputOpened;

        FakeConnection(URL url) {
            super(url);
        }

        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }

        @Override
        public OutputStream getOutputStream() {
            return requestBody;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public int getContentLength() {
            return contentLength >= 0 ? contentLength : responseBody.getBytes().length;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            inputOpened = true;
            return new ByteArrayInputStream(responseBody.getBytes("UTF-8"));
        }
    }
}
