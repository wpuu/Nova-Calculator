package org.solovyev.android.calculator.ai;

/** Business-level AI operations understood by the Nova gateway contract. */
public enum AiOperation {
    EXPLAIN_CALCULATION,
    PARSE_NATURAL_LANGUAGE_CALCULATION,
    FOLLOW_UP_CALCULATION,
    EXPLAIN_CALCULATION_ERROR,
    BUILD_FORMULA
}
