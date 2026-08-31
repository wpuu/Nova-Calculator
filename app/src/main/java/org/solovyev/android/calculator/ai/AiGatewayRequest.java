package org.solovyev.android.calculator.ai;

/**
 * Provider-neutral request sent to Nova AI Gateway.
 *
 * It intentionally contains no provider name, model name, API key or upstream address.
 * Explain requests carry a deterministic calculator result. Natural-language requests carry
 * only user text; the gateway may return a candidate expression, but Android remains responsible
 * for evaluating that expression with the local calculator engine.
 */
public final class AiGatewayRequest {

    private final String requestId;
    private final AiOperation operation;
    private final String expression;
    private final String deterministicResult;
    private final String naturalLanguageQuery;
    private final String localeTag;

    public AiGatewayRequest(String requestId,
                            AiOperation operation,
                            String expression,
                            String deterministicResult,
                            String localeTag) {
        this(requestId, operation, expression, deterministicResult, null, localeTag);
    }

    public AiGatewayRequest(String requestId,
                            AiOperation operation,
                            String expression,
                            String deterministicResult,
                            String naturalLanguageQuery,
                            String localeTag) {
        this.requestId = requireText(requestId, "requestId");
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        this.operation = operation;
        if (operation == AiOperation.EXPLAIN_CALCULATION) {
            this.expression = requireText(expression, "expression");
            this.deterministicResult = requireText(deterministicResult, "deterministicResult");
            this.naturalLanguageQuery = "";
        } else if (operation == AiOperation.PARSE_NATURAL_LANGUAGE_CALCULATION) {
            this.expression = "";
            this.deterministicResult = "";
            this.naturalLanguageQuery = requireText(naturalLanguageQuery, "naturalLanguageQuery");
        } else {
            throw new IllegalArgumentException("unsupported AI operation");
        }
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

    public String getNaturalLanguageQuery() {
        return naturalLanguageQuery;
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
