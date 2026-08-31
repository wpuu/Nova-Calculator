package org.solovyev.android.calculator.ai;

import org.json.JSONException;
import org.json.JSONObject;

/** JSON codec for the public Nova AI contract. Unknown server fields are ignored. */
public final class AiGatewayJsonCodec {

    private AiGatewayJsonCodec() {
        throw new AssertionError();
    }

    public static String encodeRequest(AiGatewayRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        try {
            final JSONObject json = new JSONObject();
            json.put("requestId", request.getRequestId());
            json.put("operation", request.getOperation().name());
            if (request.getOperation() == AiOperation.EXPLAIN_CALCULATION) {
                json.put("expression", request.getExpression());
                json.put("deterministicResult", request.getDeterministicResult());
            } else if (request.getOperation() == AiOperation.PARSE_NATURAL_LANGUAGE_CALCULATION) {
                json.put("naturalLanguageQuery", request.getNaturalLanguageQuery());
            } else {
                throw new IllegalArgumentException("unsupported AI operation");
            }
            json.put("localeTag", request.getLocaleTag());
            return json.toString();
        } catch (JSONException e) {
            throw new IllegalStateException("Could not encode Nova AI request", e);
        }
    }

    public static AiGatewayResponse decodeResponse(String body, String fallbackRequestId) {
        final String fallback = requireFallbackId(fallbackRequestId);
        if (body == null || body.trim().isEmpty()) {
            return unavailable(fallback);
        }
        try {
            final JSONObject json = new JSONObject(body);
            final String requestId = nonBlank(json.optString("requestId", ""), fallback);
            final AiGatewayStatus status = parseStatus(json.optString("status", ""));
            final String answer = status == AiGatewayStatus.SUCCESS
                    ? json.optString("answer", "")
                    : "";
            final String candidateExpression = status == AiGatewayStatus.SUCCESS
                    ? json.optString("candidateExpression", "")
                    : "";
            return new AiGatewayResponse(
                    requestId,
                    status,
                    answer,
                    candidateExpression,
                    json.optLong("retryAfterSeconds", 0L),
                    json.optInt("remainingRequestHint", -1),
                    json.optLong("quotaResetAtEpochMs", 0L));
        } catch (JSONException | RuntimeException e) {
            return unavailable(fallback);
        }
    }

    public static AiGatewayResponse fallbackForHttpStatus(int httpStatus, String requestId) {
        final AiGatewayStatus status;
        if (httpStatus == 401 || httpStatus == 403) {
            status = AiGatewayStatus.AUTH_REQUIRED;
        } else if (httpStatus == 429) {
            status = AiGatewayStatus.RATE_LIMITED;
        } else if (httpStatus >= 400 && httpStatus < 500) {
            status = AiGatewayStatus.INVALID_REQUEST;
        } else {
            status = AiGatewayStatus.TEMPORARILY_UNAVAILABLE;
        }
        return new AiGatewayResponse(requireFallbackId(requestId), status, "", 0L, -1, 0L);
    }

    private static AiGatewayStatus parseStatus(String raw) {
        try {
            return AiGatewayStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return AiGatewayStatus.TEMPORARILY_UNAVAILABLE;
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

    private static String requireFallbackId(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("fallbackRequestId must not be blank");
        }
        return requestId.trim();
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
