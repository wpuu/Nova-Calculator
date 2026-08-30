package org.solovyev.android.calculator.ai;

/** Supplies the Nova account/session token at request time. */
public interface AiSessionTokenProvider {

    /**
     * Returns the current Nova session token, or {@code null}/blank for an anonymous request.
     * Provider API keys must never be returned here.
     */
    String getSessionToken();
}
