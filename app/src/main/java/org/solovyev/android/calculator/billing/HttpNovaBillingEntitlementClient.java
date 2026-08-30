package org.solovyev.android.calculator.billing;

import com.android.billingclient.api.Purchase;

import org.json.JSONArray;
import org.json.JSONObject;
import org.solovyev.android.calculator.ai.AiSessionTokenProvider;
import org.solovyev.android.calculator.ai.AiSessionTokenStore;
import org.solovyev.android.calculator.entitlement.Entitlement;
import org.solovyev.android.calculator.entitlement.EntitlementSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Blocking server-verification client. Call only from a background executor. */
public final class HttpNovaBillingEntitlementClient implements NovaBillingEntitlementClient {

    static final int DEFAULT_CONNECT_TIMEOUT_MS = 8_000;
    static final int DEFAULT_READ_TIMEOUT_MS = 12_000;
    static final int DEFAULT_MAX_RESPONSE_BYTES = 24 * 1024;
    private static final int MAX_SESSION_TOKEN_CHARS = 8192;
    private static final int MAX_PURCHASE_TOKEN_CHARS = 8192;

    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    interface Clock {
        long now();
    }

    private final NovaBillingEndpoint endpoint;
    private final AiSessionTokenProvider sessionTokenProvider;
    private final AiSessionTokenStore tokenStore;
    private final ConnectionFactory connectionFactory;
    private final Clock clock;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxResponseBytes;

    public HttpNovaBillingEntitlementClient(NovaBillingEndpoint endpoint,
                                            AiSessionTokenProvider sessionTokenProvider,
                                            AiSessionTokenStore tokenStore) {
        this(endpoint,
                sessionTokenProvider,
                tokenStore,
                url -> (HttpURLConnection) url.openConnection(),
                System::currentTimeMillis,
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_READ_TIMEOUT_MS,
                DEFAULT_MAX_RESPONSE_BYTES);
    }

    HttpNovaBillingEntitlementClient(NovaBillingEndpoint endpoint,
                                     AiSessionTokenProvider sessionTokenProvider,
                                     AiSessionTokenStore tokenStore,
                                     ConnectionFactory connectionFactory,
                                     Clock clock,
                                     int connectTimeoutMs,
                                     int readTimeoutMs,
                                     int maxResponseBytes) {
        if (endpoint == null) throw new IllegalArgumentException("endpoint must not be null");
        if (sessionTokenProvider == null) {
            throw new IllegalArgumentException("sessionTokenProvider must not be null");
        }
        if (tokenStore == null) throw new IllegalArgumentException("tokenStore must not be null");
        if (connectionFactory == null) throw new IllegalArgumentException("connectionFactory must not be null");
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0 || maxResponseBytes <= 0) {
            throw new IllegalArgumentException("invalid billing client limits");
        }
        this.endpoint = endpoint;
        this.sessionTokenProvider = sessionTokenProvider;
        this.tokenStore = tokenStore;
        this.connectionFactory = connectionFactory;
        this.clock = clock;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public synchronized NovaBillingEntitlementResult refresh(List<Purchase> purchases) {
        final String body;
        try {
            body = requestBody(purchases);
        } catch (Exception e) {
            return NovaBillingEntitlementResult.failure(
                    NovaBillingEntitlementResult.Status.INVALID_REQUEST);
        }

        String sessionToken = normalizeSessionToken(sessionTokenProvider.getSessionToken());
        if (sessionToken == null) {
            return NovaBillingEntitlementResult.failure(
                    NovaBillingEntitlementResult.Status.AUTH_REQUIRED);
        }

        ServerResponse response = execute(body, sessionToken);
        if (response.httpStatus == 401) {
            // The cached Nova session may have expired or been invalidated by key rotation.
            // Clear it, obtain a fresh proof-gated anonymous session, and retry exactly once.
            safeClearToken();
            sessionToken = normalizeSessionToken(sessionTokenProvider.getSessionToken());
            if (sessionToken == null) {
                return NovaBillingEntitlementResult.failure(
                        NovaBillingEntitlementResult.Status.AUTH_REQUIRED);
            }
            response = execute(body, sessionToken);
        }
        return parseResponse(response);
    }

    private NovaBillingEntitlementResult parseResponse(ServerResponse response) {
        if (response.httpStatus == 401) {
            return NovaBillingEntitlementResult.failure(
                    NovaBillingEntitlementResult.Status.AUTH_REQUIRED);
        }
        if (response.httpStatus == 400 || response.httpStatus == 413 || response.httpStatus == 415) {
            return NovaBillingEntitlementResult.failure(
                    NovaBillingEntitlementResult.Status.INVALID_REQUEST);
        }
        if (response.httpStatus != 200 || response.body == null) {
            return NovaBillingEntitlementResult.failure(
                    NovaBillingEntitlementResult.Status.TEMPORARILY_UNAVAILABLE);
        }

        try {
            final JSONObject json = new JSONObject(response.body);
            if (!"SUCCESS".equals(json.optString("status", ""))) {
                return NovaBillingEntitlementResult.failure(
                        mapStatus(json.optString("status", "")));
            }
            final String token = normalizeSessionToken(json.optString("sessionToken", null));
            final long expiresAt = json.optLong("expiresAtEpochMs", 0L);
            final long now = safeNow();
            if (token == null || expiresAt <= now + 5_000L) {
                return NovaBillingEntitlementResult.failure(
                        NovaBillingEntitlementResult.Status.TEMPORARILY_UNAVAILABLE);
            }

            final EnumSet<Entitlement> entitlements = EnumSet.noneOf(Entitlement.class);
            final JSONArray values = json.optJSONArray("entitlements");
            if (values == null) {
                return NovaBillingEntitlementResult.failure(
                        NovaBillingEntitlementResult.Status.TEMPORARILY_UNAVAILABLE);
            }
            for (int i = 0; i < values.length(); i++) {
                final String value = values.optString(i, "");
                if (Entitlement.PRO_LIFETIME.name().equals(value)) {
                    entitlements.add(Entitlement.PRO_LIFETIME);
                } else if (Entitlement.AI_PLUS.name().equals(value)) {
                    entitlements.add(Entitlement.AI_PLUS);
                } else {
                    // Fail closed if server/client entitlement vocabularies drift.
                    return NovaBillingEntitlementResult.failure(
                            NovaBillingEntitlementResult.Status.TEMPORARILY_UNAVAILABLE);
                }
            }

            try {
                tokenStore.save(token, expiresAt);
            } catch (RuntimeException e) {
                return NovaBillingEntitlementResult.failure(
                        NovaBillingEntitlementResult.Status.TEMPORARILY_UNAVAILABLE);
            }
            final EntitlementSnapshot snapshot = entitlements.isEmpty()
                    ? EntitlementSnapshot.free("play-server", now)
                    : EntitlementSnapshot.of(entitlements, "play-server", now);
            return NovaBillingEntitlementResult.success(snapshot);
        } catch (Exception e) {
            return NovaBillingEntitlementResult.failure(
                    NovaBillingEntitlementResult.Status.TEMPORARILY_UNAVAILABLE);
        }
    }

    private ServerResponse execute(String body, String sessionToken) {
        HttpURLConnection connection = null;
        try {
            final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection = connectionFactory.open(endpoint.getUrl());
            configure(connection, sessionToken, bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            final int status = connection.getResponseCode();
            if (status >= 300 && status < 400) return new ServerResponse(status, null);
            final int declaredLength = connection.getContentLength();
            if (declaredLength > maxResponseBytes) return new ServerResponse(status, null);
            final InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            final String responseBody = input == null ? null : readBody(input, maxResponseBytes);
            return new ServerResponse(status, responseBody);
        } catch (Exception e) {
            return new ServerResponse(503, null);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void configure(HttpURLConnection connection,
                           String sessionToken,
                           int requestBytes) throws IOException {
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Authorization", "Bearer " + sessionToken);
        connection.setFixedLengthStreamingMode(requestBytes);
    }

    static String requestBody(List<Purchase> purchases) throws Exception {
        final JSONArray list = new JSONArray();
        final Set<String> emitted = new HashSet<>();
        if (purchases != null) {
            for (Purchase purchase : purchases) {
                if (purchase == null) continue;
                final String token = boundedToken(purchase.getPurchaseToken());
                if (token == null) continue;
                final List<String> products = purchase.getProducts();
                if (products == null) continue;
                for (String productId : products) {
                    final String type;
                    if (NovaBillingProducts.PRO_LIFETIME.equals(productId)) {
                        type = "inapp";
                    } else if (NovaBillingProducts.AI_PLUS.equals(productId)) {
                        type = "subs";
                    } else {
                        continue;
                    }
                    if (!emitted.add(productId)) continue;
                    final JSONObject item = new JSONObject();
                    item.put("productId", productId);
                    item.put("productType", type);
                    item.put("purchaseToken", token);
                    list.put(item);
                }
            }
        }
        final JSONObject root = new JSONObject();
        root.put("purchases", list);
        return root.toString();
    }

    private void safeClearToken() {
        try {
            tokenStore.clear();
        } catch (RuntimeException ignored) {
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

    private static NovaBillingEntitlementResult.Status mapStatus(String value) {
        if ("AUTH_REQUIRED".equals(value)) {
            return NovaBillingEntitlementResult.Status.AUTH_REQUIRED;
        }
        if ("INVALID_REQUEST".equals(value)) {
            return NovaBillingEntitlementResult.Status.INVALID_REQUEST;
        }
        return NovaBillingEntitlementResult.Status.TEMPORARILY_UNAVAILABLE;
    }

    private static String normalizeSessionToken(String value) {
        if (value == null) return null;
        final String text = value.trim();
        if (text.isEmpty() || text.length() > MAX_SESSION_TOKEN_CHARS
                || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0
                || text.indexOf(' ') >= 0 || text.indexOf('\t') >= 0) {
            return null;
        }
        return text;
    }

    private static String boundedToken(String value) {
        if (value == null) return null;
        final String text = value.trim();
        if (text.isEmpty() || text.length() > MAX_PURCHASE_TOKEN_CHARS) return null;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) return null;
        }
        return text;
    }

    private static String readBody(InputStream stream, int maxBytes) throws IOException {
        try (InputStream input = stream;
             ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 4096))) {
            final byte[] buffer = new byte[2048];
            int total = 0;
            while (true) {
                final int read = input.read(buffer);
                if (read == -1) break;
                total += read;
                if (total > maxBytes) throw new IOException("Nova billing response exceeds size limit");
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class ServerResponse {
        final int httpStatus;
        final String body;

        ServerResponse(int httpStatus, String body) {
            this.httpStatus = httpStatus;
            this.body = body;
        }
    }
}
