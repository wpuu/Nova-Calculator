package org.solovyev.android.calculator.ai;

/**
 * Provider-neutral request sent to Nova AI Gateway.
 *
 * It intentionally contains no provider name, model name, API key or upstream address.
 * The deterministic calculator result is supplied so the AI explains a verified value
 * instead of being trusted to perform exact arithmetic itself.
 */
public final class AiGatewayRequest {

    private final String requestId;
    private final AiOperation operation;
    private final String expression;
    private final String deterministicResult;
    private final String localeTag;

    public AiGatewayRequest(String requestId,
                            AiOperation operation,
                            String expression,
                            String deterministicResult,
                            String localeTag) {
        this.requestId = requireText(requestId, "requestId");
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        this.operation = operation;
        this.expression = requireText(expression, "expression");
        this.deterministicResult = requireText(deterministicResult, "deterministicResult");
        this.localeTag = localeTag == null || localeTag.trim().isEmpty()
                ? "und"
                : localeTag.trim();
    }

    public String getRequestId() {
        return requestId;
    }

    public AiOperation getOperation() {
        return operation;
    }

    public String getExpression() {
        return expression;
    }

    public String getDeterministicResult() {
        return deterministicResult;
    }

    public String getLocaleTag() {
        return localeTag;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
