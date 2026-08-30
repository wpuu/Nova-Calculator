package org.solovyev.android.calculator.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AiGatewayContractTest {

    @Test
    public void explainCalculationUsesProviderNeutralOperation() {
        AiGatewayRequest request = AiRequests.explainCalculation("2+2", "4", "zh-CN");

        assertEquals(AiOperation.EXPLAIN_CALCULATION, request.getOperation());
        assertEquals("2+2", request.getExpression());
        assertEquals("4", request.getDeterministicResult());
        assertEquals("zh-CN", request.getLocaleTag());
        assertFalse(request.getRequestId().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void requestRejectsBlankExpression() {
        AiRequests.explainCalculation("   ", "4", "zh-CN");
    }

    @Test
    public void responseNormalizesServerHintsSafely() {
        AiGatewayResponse response = new AiGatewayResponse(
                "req-1",
                AiGatewayStatus.RATE_LIMITED,
                null,
                -5,
                -8,
                -1);

        assertFalse(response.isSuccess());
        assertEquals(0L, response.getRetryAfterSeconds());
        assertEquals(-1, response.getRemainingRequestHint());
        assertEquals(0L, response.getQuotaResetAtEpochMs());
        assertEquals("", response.getAnswer());
    }

    @Test
    public void successResponseCarriesExplanationOnly() {
        AiGatewayResponse response = new AiGatewayResponse(
                "req-2",
                AiGatewayStatus.SUCCESS,
                "2 加 2 等于 4。",
                0,
                2,
                123L);

        assertTrue(response.isSuccess());
        assertEquals("2 加 2 等于 4。", response.getAnswer());
    }
}
