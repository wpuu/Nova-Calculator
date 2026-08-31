package org.solovyev.android.calculator.ai;

/** Provider-neutral request sent to Nova AI Gateway. */
public final class AiGatewayRequest {

    private final String requestId;
    private final AiOperation operation;
    private final String expression;
    private final String deterministicResult;
    private final String naturalLanguageQuery;
    private final String followUpQuestion;
    private final String localeTag;

    public AiGatewayRequest(String requestId,
                            AiOperation operation,
                            String expression,
                            String deterministicResult,
                            String localeTag) {
        this(requestId, operation, expression, deterministicResult, null, null, localeTag);
    }

    public AiGatewayRequest(String requestId,
                            AiOperation operation,
                            String expression,
                            String deterministicResult,
                            String naturalLanguageQuery,
                            String localeTag) {
        this(requestId, operation, expression, deterministicResult, naturalLanguageQuery, null, localeTag);
    }

    public AiGatewayRequest(String requestId,
                            AiOperation operation,
                            String expression,
                            String deterministicResult,
                            String naturalLanguageQuery,
                            String followUpQuestion,
                            String localeTag) {
        this.requestId = requireText(requestId, "requestId");
        if (operation == null) throw new IllegalArgumentException("operation must not be null");
        this.operation = operation;

        if (operation == AiOperation.EXPLAIN_CALCULATION) {
            this.expression = requireText(expression, "expression");
            this.deterministicResult = requireText(deterministicResult, "deterministicResult");
            this.naturalLanguageQuery = "";
            this.followUpQuestion = "";
        } else if (operation == AiOperation.PARSE_NATURAL_LANGUAGE_CALCULATION) {
            this.expression = "";
            this.deterministicResult = "";
            this.naturalLanguageQuery = requireText(naturalLanguageQuery, "naturalLanguageQuery");
            this.followUpQuestion = "";
        } else if (operation == AiOperation.FOLLOW_UP_CALCULATION) {
            this.expression = requireText(expression, "expression");
            this.deterministicResult = requireText(deterministicResult, "deterministicResult");
            this.naturalLanguageQuery = "";
            this.followUpQuestion = requireText(followUpQuestion, "followUpQuestion");
        } else {
            throw new IllegalArgumentException("unsupported AI operation");
        }

        this.localeTag = localeTag == null || localeTag.trim().isEmpty()
                ? "und"
                : localeTag.trim();
    }

    public String getRequestId() { return requestId; }
    public AiOperation getOperation() { return operation; }
    public String getExpression() { return expression; }
    public String getDeterministicResult() { return deterministicResult; }
    public String getNaturalLanguageQuery() { return naturalLanguageQuery; }
    public String getFollowUpQuestion() { return followUpQuestion; }
    public String getLocaleTag() { return localeTag; }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
