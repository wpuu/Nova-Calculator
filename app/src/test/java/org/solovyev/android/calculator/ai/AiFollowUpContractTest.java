package org.solovyev.android.calculator.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.JSONObject;
import org.junit.Test;

public class AiFollowUpContractTest {

    @Test
    public void followUpFactoryRequiresCurrentVerifiedCalculationAndQuestion() throws Exception {
        AiGatewayRequest request = AiRequests.followUpCalculation(
                "8536*0.85*1.13",
                "8200.328",
                "为什么税是在折扣之后计算？",
                "zh-CN");

        assertEquals(AiOperation.FOLLOW_UP_CALCULATION, request.getOperation());
        assertEquals("8536*0.85*1.13", request.getExpression());
        assertEquals("8200.328", request.getDeterministicResult());
        assertEquals("为什么税是在折扣之后计算？", request.getFollowUpQuestion());
        assertEquals("", request.getNaturalLanguageQuery());

        JSONObject encoded = new JSONObject(AiGatewayJsonCodec.encodeRequest(request));
        assertEquals("FOLLOW_UP_CALCULATION", encoded.getString("operation"));
        assertEquals("8536*0.85*1.13", encoded.getString("expression"));
        assertEquals("8200.328", encoded.getString("deterministicResult"));
        assertEquals("为什么税是在折扣之后计算？", encoded.getString("followUpQuestion"));
        assertFalse(encoded.has("naturalLanguageQuery"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void followUpRejectsBlankQuestion() {
        AiRequests.followUpCalculation("2+2", "4", "   ", "en-US");
    }

    @Test(expected = IllegalArgumentException.class)
    public void followUpRejectsMissingVerifiedResult() {
        AiRequests.followUpCalculation("2+2", "", "why?", "en-US");
    }
}
