package org.solovyev.android.calculator.ai;

/** Supplies a short-lived Nova session token at AI request time. */
public interface AiSessionTokenProvider {

    /**
     * Returns the current signed Nova session token, refreshing it if necessary, or null/blank when
     * a Nova session cannot be obtained. This method may block and is called from the AI network
     * executor. Provider API keys must never be returned here.
     */
    String getSessionToken();
}
