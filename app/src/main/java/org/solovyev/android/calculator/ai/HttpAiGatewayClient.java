package org.solovyev.android.calculator.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

/**
 * HTTPS client for Nova's own AI gateway.
 *
 * The client never knows an upstream provider, model, provider API key or upstream URL.
 * Redirects are intentionally disabled so a Nova session token cannot be forwarded to a
 * different origin by an HTTP redirect.
 */
public final class HttpAiGatewayClient implements AiGatewayClient {

    static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    static final int DEFAULT_READ_TIMEOUT_MS = 20_000;
    static final int DEFAULT_MAX_RESPONSE_BYTES = 32 * 1024;

    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    private final AiGatewayEndpoint endpoint;
    private final AiSessionTokenProvider tokenProvider;
    private final Executor networkExecutor;
    private final Executor callbackExecutor;
    private final ConnectionFactory connectionFactory;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxResponseBytes;

    public HttpAiGatewayClient(AiGatewayEndpoint endpoint,
                               AiSessionTokenProvider tokenProvider,
                               Executor networkExecutor,
                               Executor callbackExecutor) {
        this(endpoint,
                tokenProvider,
                networkExecutor,
                callbackExecutor,
                url -> (HttpURLConnection) url.openConnection(),
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_READ_TIMEOUT_MS,
                DEFAULT_MAX_RESPONSE_BYTES);
    }

    HttpAiGatewayClient(AiGatewayEndpoint endpoint,
                        AiSessionTokenProvider tokenProvider,
                        Executor networkExecutor,
                        Executor callbackExecutor,
                        ConnectionFactory connectionFactory,
                        int connectTimeoutMs,
                        int readTimeoutMs,
                        int maxResponseBytes) {
        if (endpoint == null) throw new IllegalArgumentException("endpoint must not be null");
        if (tokenProvider == null) throw new IllegalArgumentException("tokenProvider must not be null");
        if (networkExecutor == null) throw new IllegalArgumentException("networkExecutor must not be null");
        if (callbackExecutor == null) throw new IllegalArgumentException("callbackExecutor must not be null");
        if (connectionFactory == null) throw new IllegalArgumentException("connectionFactory must not be null");
        if (connectTimeoutMs <= 0) throw new IllegalArgumentException("connectTimeoutMs must be positive");
        if (readTimeoutMs <= 0) throw new IllegalArgumentException("readTimeoutMs must be positive");
        if (maxResponseBytes <= 0) throw new IllegalArgumentException("maxResponseBytes must be positive");
        this.endpoint = endpoint;
        this.tokenProvider = tokenProvider;
        this.networkExecutor = networkExecutor;
        this.callbackExecutor = callbackExecutor;
        this.connectionFactory = connectionFactory;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public void execute(AiGatewayRequest request, Callback callback) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        if (callback == null) throw new IllegalArgumentException("callback must not be null");
        networkExecutor.execute(() -> {
            final AiGatewayResponse response = executeBlocking(request);
            callbackExecutor.execute(() -> callback.onComplete(response));
        });
    }

    private AiGatewayResponse executeBlocking(AiGatewayRequest request) {
        HttpURLConnection connection = null;
        try {
            final String token = normalizeToken(tokenProvider.getSessionToken());
            final byte[] requestBody = AiGatewayJsonCodec.encodeRequest(request)
                    .getBytes(StandardCharsets.UTF_8);

            connection = connectionFactory.open(endpoint.getUrl());
            configure(connection, requestBody.length, token);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBody);
            }

            final int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                return unavailable(request.getRequestId());
            }

            final long declaredLength = connection.getContentLengthLong();
            if (declaredLength > maxResponseBytes) {
                return unavailable(request.getRequestId());
            }

            final InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            final String responseBody = readBody(stream, maxResponseBytes);

            if (status >= 200 && status < 300) {
                return AiGatewayJsonCodec.decodeResponse(responseBody, request.getRequestId());
            }

            if (responseBody != null && !responseBody.trim().isEmpty()) {
                final AiGatewayResponse decoded = AiGatewayJsonCodec.decodeResponse(
                        responseBody,
                        request.getRequestId());
                if (!decoded.isSuccess() && decoded.getStatus() != AiGatewayStatus.TEMPORARILY_UNAVAILABLE) {
                    return decoded;
                }
            }
            return AiGatewayJsonCodec.fallbackForHttpStatus(status, request.getRequestId());
        } catch (IOException | RuntimeException e) {
            return unavailable(request.getRequestId());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void configure(HttpURLConnection connection, int requestBytes, String token)
            throws IOException {
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept-Encoding", "identity");
        if (token != null) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        connection.setFixedLengthStreamingMode(requestBytes);
    }

    private static String normalizeToken(String token) {
        if (token == null) return null;
        final String trimmed = token.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
                if (total > maxBytes) {
                    throw new IOException("Nova AI response exceeds size limit");
                }
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static AiGatewayResponse unavailable(String requestId) {
        return new AiGatewayResponse(
                requestId,
                AiGatewayStatus.TEMPORARILY_UNAVAILABLE,
                "",
                0L,
                -1,
                0L);
    }
}
