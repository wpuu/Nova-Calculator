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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class HttpAiGatewayClientTest {

    private static final Executor DIRECT = Runnable::run;

    @Test
    public void postsOnlyNovaContractFieldsAndSessionTokenWithoutFollowingRedirects() throws Exception {
        FakeConnection connection = successConnection();
        AtomicInteger opens = new AtomicInteger();
        HttpAiGatewayClient client = client(connection, opens, () -> " session-token ", 32 * 1024);
        AtomicReference<AiGatewayResponse> result = new AtomicReference<>();

        client.execute(request("req-1"), result::set);

        assertEquals(1, opens.get());
        assertEquals("POST", connection.methodSeen);
        assertFalse(connection.getInstanceFollowRedirects());
        assertEquals("Bearer session-token", connection.getRequestProperty("Authorization"));
        assertEquals("application/json", connection.getRequestProperty("Accept"));
        assertEquals("identity", connection.getRequestProperty("Accept-Encoding"));
        JSONObject json = new JSONObject(connection.requestBody.toString("UTF-8"));
        assertEquals(5, json.length());
        assertEquals("req-1", json.getString("requestId"));
        assertEquals("EXPLAIN_CALCULATION", json.getString("operation"));
        assertEquals("2+2", json.getString("expression"));
        assertEquals("4", json.getString("deterministicResult"));
        assertEquals("en-US", json.getString("localeTag"));
        assertFalse(json.has("provider"));
        assertFalse(json.has("model"));
        assertFalse(json.has("apiKey"));
        assertFalse(json.has("tier"));
        assertEquals(AiGatewayStatus.SUCCESS, result.get().getStatus());
        assertEquals("ok", result.get().getAnswer());
    }

    @Test
    public void anonymousRequestDoesNotSendAuthorizationHeader() throws Exception {
        FakeConnection connection = successConnection();
        AtomicReference<AiGatewayResponse> result = new AtomicReference<>();

        client(connection, new AtomicInteger(), () -> "   ", 32 * 1024)
                .execute(request("req-anon"), result::set);

        assertNull(connection.getRequestProperty("Authorization"));
        assertEquals(AiGatewayStatus.SUCCESS, result.get().getStatus());
    }

    @Test
    public void malformedSessionTokenFailsBeforeOpeningNetwork() throws Exception {
        FakeConnection connection = successConnection();
        AtomicInteger opens = new AtomicInteger();
        AtomicReference<AiGatewayResponse> result = new AtomicReference<>();

        client(connection, opens, () -> "good\r\nX-Evil: yes", 1024)
                .execute(request("req-token"), result::set);

        assertEquals(0, opens.get());
        assertEquals(AiGatewayStatus.TEMPORARILY_UNAVAILABLE, result.get().getStatus());
    }

    @Test
    public void redirectIsNeverFollowedAndReturnsUnavailable() throws Exception {
        FakeConnection connection = new FakeConnection(new URL("https://nova.example/ai"));
        connection.responseCode = 307;
        connection.responseBody = "{\"status\":\"SUCCESS\",\"answer\":\"wrong\"}";
        AtomicReference<AiGatewayResponse> result = new AtomicReference<>();

        client(connection, new AtomicInteger(), () -> "token", 32 * 1024)
                .execute(request("req-redirect"), result::set);

        assertFalse(connection.getInstanceFollowRedirects());
        assertFalse(connection.inputStreamOpened);
        assertEquals(AiGatewayStatus.TEMPORARILY_UNAVAILABLE, result.get().getStatus());
    }

    @Test
    public void oversizedDeclaredResponseIsRejectedBeforeOpeningBodyStream() throws Exception {
        FakeConnection connection = successConnection();
        connection.contentLength = 1025;
        AtomicReference<AiGatewayResponse> result = new AtomicReference<>();

        client(connection, new AtomicInteger(), () -> null, 1024)
                .execute(request("req-large"), result::set);

        assertFalse(connection.inputStreamOpened);
        assertEquals(AiGatewayStatus.TEMPORARILY_UNAVAILABLE, result.get().getStatus());
    }

    @Test
    public void oversizedStreamingResponseIsRejected() throws Exception {
        FakeConnection connection = successConnection();
        connection.responseBody = repeat('x', 1025);
        connection.contentLength = -1;
        AtomicReference<AiGatewayResponse> result = new AtomicReference<>();

        client(connection, new AtomicInteger(), () -> null, 1024)
                .execute(request("req-stream-large"), result::set);

        assertTrue(connection.inputStreamOpened);
        assertEquals(AiGatewayStatus.TEMPORARILY_UNAVAILABLE, result.get().getStatus());
    }

    @Test
    public void rateLimitBodyCanPreserveQuotaOrRpmDetails() throws Exception {
        FakeConnection connection = new FakeConnection(new URL("https://nova.example/ai"));
        connection.responseCode = 429;
        connection.responseBody = "{\"requestId\":\"req-rate\",\"status\":\"QUOTA_EXHAUSTED\",\"retryAfterSeconds\":12}";
        AtomicReference<AiGatewayResponse> result = new AtomicReference<>();

        client(connection, new AtomicInteger(), () -> null, 32 * 1024)
                .execute(request("req-rate"), result::set);

        assertEquals(AiGatewayStatus.QUOTA_EXHAUSTED, result.get().getStatus());
        assertEquals(12L, result.get().getRetryAfterSeconds());
    }

    @Test
    public void authHttpStatusCannotBeOverriddenByResponseBody() throws Exception {
        FakeConnection connection = new FakeConnection(new URL("https://nova.example/ai"));
        connection.responseCode = 401;
        connection.responseBody = "{\"requestId\":\"req-auth\",\"status\":\"QUOTA_EXHAUSTED\",\"retryAfterSeconds\":999}";
        AtomicReference<AiGatewayResponse> result = new AtomicReference<>();

        client(connection, new AtomicInteger(), () -> "token", 32 * 1024)
                .execute(request("req-auth"), result::set);

        assertEquals(AiGatewayStatus.AUTH_REQUIRED, result.get().getStatus());
        assertEquals(0L, result.get().getRetryAfterSeconds());
    }

    @Test
    public void genericClientErrorCannotInventAnotherBusinessState() throws Exception {
        FakeConnection connection = new FakeConnection(new URL("https://nova.example/ai"));
        connection.responseCode = 400;
        connection.responseBody = "{\"requestId\":\"req-bad\",\"status\":\"RATE_LIMITED\"}";
        AtomicReference<AiGatewayResponse> result = new AtomicReference<>();

        client(connection, new AtomicInteger(), () -> null, 32 * 1024)
                .execute(request("req-bad"), result::set);

        assertEquals(AiGatewayStatus.INVALID_REQUEST, result.get().getStatus());
    }

    @Test
    public void transportOrExecutorFailureBecomesUnavailableAndCallbackRunsOnce() throws Exception {
        AtomicInteger callbacks = new AtomicInteger();
        HttpAiGatewayClient transportFailure = new HttpAiGatewayClient(
                new AiGatewayEndpoint("https://nova.example/ai"),
                () -> null,
                DIRECT,
                DIRECT,
                url -> { throw new IOException("boom"); },
                1000,
                1000,
                1024);
        AtomicReference<AiGatewayResponse> first = new AtomicReference<>();
        transportFailure.execute(request("req-fail"), response -> {
            callbacks.incrementAndGet();
            first.set(response);
        });
        assertEquals(1, callbacks.get());
        assertEquals(AiGatewayStatus.TEMPORARILY_UNAVAILABLE, first.get().getStatus());

        Executor rejecting = command -> { throw new RejectedExecutionException("shutdown"); };
        HttpAiGatewayClient executorFailure = new HttpAiGatewayClient(
                new AiGatewayEndpoint("https://nova.example/ai"),
                () -> null,
                rejecting,
                DIRECT);
        AtomicReference<AiGatewayResponse> second = new AtomicReference<>();
        executorFailure.execute(request("req-rejected"), second::set);
        assertEquals(AiGatewayStatus.TEMPORARILY_UNAVAILABLE, second.get().getStatus());
    }

    @Test
    public void tokenProviderFailureDoesNotOpenNetwork() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        FakeConnection connection = successConnection();
        AtomicReference<AiGatewayResponse> result = new AtomicReference<>();

        client(connection, opens, () -> { throw new IllegalStateException("session unavailable"); }, 1024)
                .execute(request("req-token-provider"), result::set);

        assertEquals(0, opens.get());
        assertEquals(AiGatewayStatus.TEMPORARILY_UNAVAILABLE, result.get().getStatus());
    }

    private static HttpAiGatewayClient client(FakeConnection connection,
                                              AtomicInteger opens,
                                              AiSessionTokenProvider tokenProvider,
                                              int maxResponseBytes) {
        return new HttpAiGatewayClient(
                new AiGatewayEndpoint("https://nova.example/ai"),
                tokenProvider,
                DIRECT,
                DIRECT,
                url -> {
                    opens.incrementAndGet();
                    return connection;
                },
                1000,
                1000,
                maxResponseBytes);
    }

    private static FakeConnection successConnection() throws Exception {
        FakeConnection connection = new FakeConnection(new URL("https://nova.example/ai"));
        connection.responseCode = 200;
        connection.responseBody = "{\"status\":\"SUCCESS\",\"answer\":\"ok\"}";
        return connection;
    }

    private static AiGatewayRequest request(String id) {
        return new AiGatewayRequest(id, AiOperation.EXPLAIN_CALCULATION, "2+2", "4", "en-US");
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) builder.append(value);
        return builder.toString();
    }

    private static final class FakeConnection extends HttpURLConnection {
        final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
        int responseCode = 200;
        String responseBody = "";
        int contentLength = -1;
        boolean inputStreamOpened;
        String methodSeen;

        FakeConnection(URL url) {
            super(url);
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public void setRequestMethod(String method) throws java.net.ProtocolException {
            super.setRequestMethod(method);
            methodSeen = method;
        }

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
            return contentLength;
        }

        @Override
        public InputStream getInputStream() {
            inputStreamOpened = true;
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            inputStreamOpened = true;
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }
    }
}
