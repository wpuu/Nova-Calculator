package org.solovyev.android.calculator.ai;

/** Safe no-op transport used when a Nova gateway is not configured in the build. */
public final class DisabledAiGatewayClient implements AiGatewayClient {

    @Override
    public void execute(AiGatewayRequest request, Callback callback) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        if (callback == null) throw new IllegalArgumentException("callback must not be null");
        callback.onComplete(new AiGatewayResponse(
                request.getRequestId(),
                AiGatewayStatus.TEMPORARILY_UNAVAILABLE,
                "",
                0L,
                -1,
                0L));
    }
}
