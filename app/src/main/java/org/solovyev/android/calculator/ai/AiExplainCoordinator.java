package org.solovyev.android.calculator.ai;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates the calculator's explicit "explain this calculation" action.
 *
 * The coordinator is deliberately UI- and transport-agnostic. It keeps only the newest request
 * active so a slow response for an older calculation can never overwrite a newer explanation.
 */
public final class AiExplainCoordinator {

    private final AiGatewayClient client;
    private final AtomicLong generation = new AtomicLong();

    public AiExplainCoordinator(AiGatewayClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client must not be null");
        }
        this.client = client;
    }

    public void explain(String expression,
                        String deterministicResult,
                        String localeTag,
                        Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }

        final long token = generation.incrementAndGet();
        final AiGatewayRequest request = AiRequests.explainCalculation(
                expression,
                deterministicResult,
                localeTag);

        listener.onStarted(request);

        try {
            client.execute(request, response -> {
                if (generation.get() != token) {
                    return;
                }
                listener.onFinished(normalizeResponse(request, response));
            });
        } catch (RuntimeException e) {
            if (generation.get() == token) {
                listener.onFinished(unavailable(request.getRequestId()));
            }
        }
    }

    /** Invalidates the active callback without requiring the transport to support cancellation. */
    public void cancelCurrent() {
        generation.incrementAndGet();
    }

    private static AiGatewayResponse normalizeResponse(AiGatewayRequest request,
                                                       AiGatewayResponse response) {
        if (response == null || !request.getRequestId().equals(response.getRequestId())) {
            return unavailable(request.getRequestId());
        }
        return response;
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

    public interface Listener {
        void onStarted(AiGatewayRequest request);
        void onFinished(AiGatewayResponse response);
    }
}
