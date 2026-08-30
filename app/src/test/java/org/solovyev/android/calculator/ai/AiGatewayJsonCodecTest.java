package org.solovyev.android.calculator.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class AiGatewayJsonCodecTest {

    @Test
    public void requestJsonContainsOnlyNovaContractFields() throws Exception {
        AiGatewayRequest request = new AiGatewayRequest(
                "req-1",
                AiOperation.EXPLAIN_CALCULATION,
                "8536*0.85*1.13",
                "8200.328",
                "zh-CN");

        JSONObject json = new JSONObject(AiGatewayJsonCodec.encodeRequest(request));
        assertEquals("req-1", json.getString("requestId"));
        assertEquals("EXPLAIN_CALCULATION", json.getString("operation"));
        assertEquals("8536*0.85*1.13", json.getString("expression"));
        assertEquals("8200.328", json.getString("deterministicResult"));
        assertEquals("zh-CN", json.getString("localeTag"));
        assertEquals(5, json.length());

        String encoded = json.toString().toLowerCase();
        assertFalse(encoded.contains("provider"));
        assertFalse(encoded.contains("model"));
        assertFalse(encoded.contains("apikey"));
        assertFalse(encoded.contains("entitlement"));
    }

    @Test
    public void successfulResponseParsesNovaFieldsAndIgnoresInternalExtras() {
        String body = "{"
                + "\"requestId\":\"req-1\","
                + "\"status\":\"SUCCESS\","
                + "\"answer\":\"解释完成\","
                + "\"retryAfterSeconds\":0,"
                + "\"remainingRequestHint\":4,"
                + "\"quotaResetAtEpochMs\":12345,"
                + "\"provider\":\"must-be-ignored\","
                + "\"keyId\":\"must-be-ignored\""
                + "}";

        AiGatewayResponse response = AiGatewayJsonCodec.decodeResponse(body, "fallback");
        assertEquals("req-1", response.getRequestId());
        assertEquals(AiGatewayStatus.SUCCESS, response.getStatus());
        assertEquals("解释完成", response.getAnswer());
        assertEquals(4, response.getRemainingRequestHint());
        assertEquals(12345L, response.getQuotaResetAtEpochMs());
    }

    @Test
    public void nonSuccessResponseCannotSmuggleAnswerText() {
        AiGatewayResponse response = AiGatewayJsonCodec.decodeResponse(
                "{\"requestId\":\"req-1\",\"status\":\"AUTH_REQUIRED\",\"answer\":\"private detail\"}",
                "fallback");
        assertEquals(AiGatewayStatus.AUTH_REQUIRED, response.getStatus());
        assertEquals("", response.getAnswer());
    }

    @Test
    public void unknownOrMalformedResponseFailsClosed() {
        AiGatewayResponse unknown = AiGatewayJsonCodec.decodeResponse(
                "{\"requestId\":\"req-1\",\"status\":\"INTERNAL_PROVIDER_ERROR\",\"answer\":\"secret\"}",
                "fallback");
        assertEquals(AiGatewayStatus.TEMPORARILY_UNAVAILABLE, unknown.getStatus());
        assertEquals("", unknown.getAnswer());

        AiGatewayResponse malformed = AiGatewayJsonCodec.decodeResponse("not-json", "fallback");
        assertEquals("fallback", malformed.getRequestId());
        assertEquals(AiGatewayStatus.TEMPORARILY_UNAVAILABLE, malformed.getStatus());
    }

    @Test
    public void httpFallbackMapsOnlyNovaLevelStates() {
        assertEquals(AiGatewayStatus.AUTH_REQUIRED,
                AiGatewayJsonCodec.fallbackForHttpStatus(401, "r").getStatus());
        assertEquals(AiGatewayStatus.AUTH_REQUIRED,
                AiGatewayJsonCodec.fallbackForHttpStatus(403, "r").getStatus());
        assertEquals(AiGatewayStatus.RATE_LIMITED,
                AiGatewayJsonCodec.fallbackForHttpStatus(429, "r").getStatus());
        assertEquals(AiGatewayStatus.INVALID_REQUEST,
                AiGatewayJsonCodec.fallbackForHttpStatus(422, "r").getStatus());
        assertEquals(AiGatewayStatus.TEMPORARILY_UNAVAILABLE,
                AiGatewayJsonCodec.fallbackForHttpStatus(503, "r").getStatus());
        assertTrue(AiGatewayJsonCodec.fallbackForHttpStatus(503, "r").getAnswer().isEmpty());
    }
}
