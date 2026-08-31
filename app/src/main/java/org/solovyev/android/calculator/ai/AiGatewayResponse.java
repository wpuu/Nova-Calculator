package org.solovyev.android.calculator.ai;

/** Provider-neutral response from Nova AI Gateway. */
public final class AiGatewayResponse {

    private final String requestId;
    private final AiGatewayStatus status;
    private final String answer;
    private final String candidateExpression;
    private final long retryAfterSeconds;
    private final int remainingRequestHint;
    private final long quotaResetAtEpochMs;

    public AiGatewayResponse(String requestId,
                             AiGatewayStatus status,
                             String answer,
                             long retryAfterSeconds,
                             int remainingRequestHint,
                             long quotaResetAtEpochMs) {
        this(requestId, status, answer, "", retryAfterSeconds, remainingRequestHint, quotaResetAtEpochMs);
    }

    public AiGatewayResponse(String requestId,
                             AiGatewayStatus status,
                             String answer,
                             String candidateExpression,
                             long retryAfterSeconds,
                             int remainingRequestHint,
                             long quotaResetAtEpochMs) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.requestId = requestId.trim();
        this.status = status;
        this.answer = answer == null ? "" : answer;
        this.candidateExpression = status == AiGatewayStatus.SUCCESS && candidateExpression != null
                ? candidateExpression.trim()
                : "";
        this.retryAfterSeconds = Math.max(0L, retryAfterSeconds);
        this.remainingRequestHint = Math.max(-1, remainingRequestHint);
        this.quotaResetAtEpochMs = Math.max(0L, quotaResetAtEpochMs);
    }

    public String getRequestId() {
        return requestId;
    }

    public AiGatewayStatus getStatus() {
        return status;
    }

    public String getAnswer() {
        return answer;
    }

    /** Candidate calculator expression returned only for natural-language parsing. */
    public String getCandidateExpression() {
        return candidateExpression;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** -1 means the server did not expose a remaining-request hint. */
    public int getRemainingRequestHint() {
        return remainingRequestHint;
    }

    public long getQuotaResetAtEpochMs() {
        return quotaResetAtEpochMs;
    }

    public boolean isSuccess() {
        return status == AiGatewayStatus.SUCCESS;
    }
}
