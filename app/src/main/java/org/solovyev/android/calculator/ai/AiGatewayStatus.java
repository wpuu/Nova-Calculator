package org.solovyev.android.calculator.ai;

/** Generic result states returned by Nova AI Gateway. */
public enum AiGatewayStatus {
    SUCCESS,
    AUTH_REQUIRED,
    QUOTA_EXHAUSTED,
    RATE_LIMITED,
    INVALID_REQUEST,
    TEMPORARILY_UNAVAILABLE
}
