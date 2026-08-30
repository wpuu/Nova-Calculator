package org.solovyev.android.calculator.ai;

/**
 * Transport abstraction for Nova AI Gateway.
 *
 * Implementations may use any HTTP stack, but product code must not depend on an upstream
 * model/provider SDK. Authentication, billing verification, quotas and provider routing are
 * server responsibilities.
 */
public interface AiGatewayClient {

    void execute(AiGatewayRequest request, Callback callback);

    interface Callback {
        void onComplete(AiGatewayResponse response);
    }
}
