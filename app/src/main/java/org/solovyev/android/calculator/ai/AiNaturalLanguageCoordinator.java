package org.solovyev.android.calculator.ai;

import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Coordinates explicit natural-language-to-calculator-expression requests. */
@Singleton
public final class AiNaturalLanguageCoordinator {

    private static final int MAX_CANDIDATE_LENGTH = 1024;

    private final AiGatewayClient client;
    private final AtomicLong generation = new AtomicLong();

    @Inject
    public AiNaturalLanguageCoordinator(AiGatewayClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client must not be null");
        }
        this.client = client;
    }

    public void parse(String naturalLanguageQuery,
                      String localeTag,
                      Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        final long token = generation.incrementAndGet();
        final AiGatewayRequest request = AiRequests.parseNaturalLanguageCalculation(
                naturalLanguageQuery,
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

    public void cancelCurrent() {
        generation.incrementAndGet();
    }

    private static AiGatewayResponse normalizeResponse(AiGatewayRequest request,
                                                       AiGatewayResponse response) {
        if (response == null || !request.getRequestId().equals(response.getRequestId())) {
            return unavailable(request.getRequestId());
        }
        if (response.getStatus() != AiGatewayStatus.SUCCESS) {
            return response;
        }
        final String candidate = response.getCandidateExpression();
        if (!isSafeCalculatorExpression(candidate)) {
            return new AiGatewayResponse(
                    request.getRequestId(),
                    AiGatewayStatus.INVALID_REQUEST,
                    "",
                    0L,
                    response.getRemainingRequestHint(),
                    response.getQuotaResetAtEpochMs());
        }
        return response;
    }

    static boolean isSafeCalculatorExpression(String expression) {
        if (expression == null) return false;
        final String text = expression.trim();
        if (text.isEmpty() || text.length() > MAX_CANDIDATE_LENGTH) return false;
        boolean hasDigit = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t' || Character.isISOControl(c)) {
                return false;
            }
            if (Character.isDigit(c)) {
                hasDigit = true;
                continue;
            }
            if (c == ' ') {
                continue;
            }
            switch (c) {
                case '.':
                case '+':
                case '-':
                case '*':
                case '/':
                case '^':
                case '(':
                case ')':
                    break;
                default:
                    return false;
            }
        }
        return hasDigit;
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
