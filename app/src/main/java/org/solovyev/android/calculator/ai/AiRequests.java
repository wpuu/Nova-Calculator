package org.solovyev.android.calculator.ai;

import java.util.UUID;

/** Small factories for the currently approved client-side AI operations. */
public final class AiRequests {

    private AiRequests() {
        throw new AssertionError();
    }

    public static AiGatewayRequest explainCalculation(String expression,
                                                      String deterministicResult,
                                                      String localeTag) {
        return new AiGatewayRequest(
                UUID.randomUUID().toString(),
                AiOperation.EXPLAIN_CALCULATION,
                expression,
                deterministicResult,
                localeTag);
    }

    public static AiGatewayRequest parseNaturalLanguageCalculation(String naturalLanguageQuery,
                                                                   String localeTag) {
        return new AiGatewayRequest(
                UUID.randomUUID().toString(),
                AiOperation.PARSE_NATURAL_LANGUAGE_CALCULATION,
                null,
                null,
                naturalLanguageQuery,
                localeTag);
    }
}
