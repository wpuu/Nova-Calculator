package org.solovyev.android.calculator.ai;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Blocking token provider used only from HttpAiGatewayClient's background executor.
 *
 * It reuses a still-valid Nova session and refreshes through the proof-gated anonymous session
 * endpoint only when necessary. It never handles an upstream provider credential.
 */
public final class HttpAnonymousSessionTokenProvider implements AiSessionTokenProvider {

    static final int DEFAULT_CONNECT_TIMEOUT_MS = 8_000;
    static final int DEFAULT_READ_TIMEOUT_MS = 12_000;
    static final int DEFAULT_MAX_RESPONSE_BYTES = 16 * 1024;
    static final long DEFAULT_REFRESH_SKEW_MS = 60_000L;
    private static final int MAX_SESSION_TOKEN_CHARS = 4096;
    private static final int MAX_INSTALLATION_ID_CHARS = 200;
    private static final int MAX_PROOF_CHARS = 6000;

    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    interface Clock {
        long now();
    }

    private final AiSessionEndpoint endpoint;
    private final InstallationProofProvider proofProvider;
    private final InstallationIdProvider installationIdProvider;
    private final AiSessionTokenStore tokenStore;
    private final ConnectionFactory connectionFactory;
    private final Clock clock;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxResponseBytes;
    private final long refreshSkewMs;

    public HttpAnonymousSessionTokenProvider(AiSessionEndpoint endpoint,
                                             InstallationProofProvider proofProvider,
                                             InstallationIdProvider installationIdProvider,
                                             AiSessionTokenStore tokenStore) {
        this(endpoint,
                proofProvider,
                installationIdProvider,
                tokenStore,
                url -> (HttpURLConnection) url.openConnection(),
                System::currentTimeMillis,
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_READ_TIMEOUT_MS,
                DEFAULT_MAX_RESPONSE_BYTES,
                DEFAULT_REFRESH_SKEW_MS);
    }

    HttpAnonymousSessionTokenProvider(AiSessionEndpoint endpoint,
                                      InstallationProofProvider proofProvider,
                                      InstallationIdProvider installationIdProvider,
                                      AiSessionTokenStore tokenStore,
                                      ConnectionFactory connectionFactory,
                                      Clock clock,
                                      int connectTimeoutMs,
                                      int readTimeoutMs,
                                      int maxResponseBytes,
                                      long refreshSkewMs) {
        if (endpoint == null) throw new IllegalArgumentException("endpoint must not be null");
        if (proofProvider == null) throw new IllegalArgumentException("proofProvider must not be null");
        if (installationIdProvider == null) throw new IllegalArgumentException("installationIdProvider must not be null");
        if (tokenStore == null) throw new IllegalArgumentException("tokenStore must not be null");
        if (connectionFactory == null) throw new IllegalArgumentException("connectionFactory must not be null");
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0 || maxResponseBytes <= 0 || refreshSkewMs < 0L) {
            throw new IllegalArgumentException("invalid Nova session client limits");
        }
        this.endpoint = endpoint;
        this.proofProvider = proofProvider;
        this.installationIdProvider = installationIdProvider;
        this.tokenStore = tokenStore;
        this.connectionFactory = connectionFactory;
        this.clock = clock;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.maxResponseBytes = maxResponseBytes;
        this.refreshSkewMs = refreshSkewMs;
    }

    @Override
    public synchronized String getSessionToken() {
        final long now = safeNow();
        final AiSessionTokenStore.Snapshot cached = safeLoad();
        final String cachedToken = cached == null ? null : normalizeToken(cached.getToken());
        if (cachedToken != null && cached.getExpiresAtEpochMs() > now + refreshSkewMs) {
            return cachedToken;
        }
        safeClear();

        if (!proofProvider.isAvailable()) return null;

        final String installationId;
        final String proof;
        try {
            installationId = boundedText(installationIdProvider.getInstallationId(), MAX_INSTALLATION_ID_CHARS);
            if (installationId == null) return null;
            proof = boundedText(proofProvider.getProof(installationId), MAX_PROOF_CHARS);
            if (proof == null) return null;
        } catch (RuntimeException e) {
            return null;
        }

        HttpURLConnection connection = null;
        try {
            final byte[] body = requestBody(installationId, proof).getBytes(StandardCharsets.UTF_8);
            connection = connectionFactory.open(endpoint.getUrl());
            configure(connection, body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            final int status = connection.getResponseCode();
            if (status >= 300 && status < 400) return null;
            if (status != 200) return null;

            final int declaredLength = connection.getContentLength();
            if (declaredLength > maxResponseBytes) return null;
            final String responseBody = readBody(connection.getInputStream(), maxResponseBytes);
            final JSONObject json = new JSONObject(responseBody);
            if (!"SUCCESS".equals(json.optString("status", ""))) return null;

            final String token = normalizeToken(json.optString("sessionToken", null));
            final long expiresAt = json.optLong("expiresAtEpochMs", 0L);
            final long refreshedNow = safeNow();
            if (token == null || expiresAt <= refreshedNow + Math.min(refreshSkewMs, 5_000L)) {
                return null;
            }
            safeSave(token, expiresAt);
            return token;
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void configure(HttpURLConnection connection, int requestBytes) throws IOException {
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setFixedLengthStreamingMode(requestBytes);
    }

    private static String requestBody(String installationId, String proof) throws Exception {
        final JSONObject json = new JSONObject();
        json.put("installationId", installationId);
        json.put("proof", proof);
        return json.toString();
    }

    private AiSessionTokenStore.Snapshot safeLoad() {
        try {
            return tokenStore.load();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void safeSave(String token, long expiresAt) {
        try {
            tokenStore.save(token, expiresAt);
        } catch (RuntimeException ignored) {
            // The token remains valid for the current AI call; a later call may refresh again.
        }
    }

    private void safeClear() {
        try {
            tokenStore.clear();
        } catch (RuntimeException ignored) {
        }
    }

    private long safeNow() {
        final long value;
        try {
            value = clock.now();
        } catch (RuntimeException e) {
            return Long.MAX_VALUE;
        }
        return value >= 0L ? value : Long.MAX_VALUE;
    }

    private static String normalizeToken(String value) {
        if (value == null) return null;
        final String text = value.trim();
        if (text.isEmpty() || text.length() > MAX_SESSION_TOKEN_CHARS
                || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
            return null;
        }
        return text;
    }

    private static String boundedText(String value, int maxLength) {
        if (value == null) return null;
        final String text = value.trim();
        if (text.isEmpty() || text.length() > maxLength
                || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
            return null;
        }
        return text;
    }

    private static String readBody(InputStream stream, int maxBytes) throws IOException {
        if (stream == null) return "";
        try (InputStream input = stream;
             ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 4096))) {
            final byte[] buffer = new byte[2048];
            int total = 0;
            while (true) {
                final int read = input.read(buffer);
                if (read == -1) break;
                total += read;
                if (total > maxBytes) throw new IOException("Nova session response exceeds size limit");
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
