package org.solovyev.android.calculator.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class AiNaturalLanguageContractTest {

    @Test
    public void naturalLanguageFactoryCarriesOnlyUserTextAndLocale() throws Exception {
        AiGatewayRequest request = AiRequests.parseNaturalLanguageCalculation(
                "8536 打 85 折以后再加 13% 税",
                "zh-CN");

        assertEquals(AiOperation.PARSE_NATURAL_LANGUAGE_CALCULATION, request.getOperation());
        assertEquals("8536 打 85 折以后再加 13% 税", request.getNaturalLanguageQuery());
        assertEquals("", request.getExpression());
        assertEquals("", request.getDeterministicResult());

        JSONObject encoded = new JSONObject(AiGatewayJsonCodec.encodeRequest(request));
        assertEquals("PARSE_NATURAL_LANGUAGE_CALCULATION", encoded.getString("operation"));
        assertEquals("8536 打 85 折以后再加 13% 税", encoded.getString("naturalLanguageQuery"));
        assertFalse(encoded.has("expression"));
        assertFalse(encoded.has("deterministicResult"));
    }

    @Test
    public void codecReadsCandidateExpressionWithoutTreatingItAsExplanation() {
        AiGatewayResponse response = AiGatewayJsonCodec.decodeResponse(
                "{\"requestId\":\"req-natural\",\"status\":\"SUCCESS\","
                        + "\"answer\":\"\",\"candidateExpression\":\"8536*0.85*1.13\"}",
                "fallback");

        assertTrue(response.isSuccess());
        assertEquals("", response.getAnswer());
        assertEquals("8536*0.85*1.13", response.getCandidateExpression());
    }

    @Test
    public void clientCandidateGuardAllowsArithmeticButRejectsInstructionsAndControlCharacters() {
        assertTrue(AiNaturalLanguageCoordinator.isSafeCalculatorExpression("8536*0.85*1.13"));
        assertTrue(AiNaturalLanguageCoordinator.isSafeCalculatorExpression("(186.4/5)*1.2"));
        assertFalse(AiNaturalLanguageCoordinator.isSafeCalculatorExpression("price*0.9"));
        assertFalse(AiNaturalLanguageCoordinator.isSafeCalculatorExpression("2+2;3+3"));
        assertFalse(AiNaturalLanguageCoordinator.isSafeCalculatorExpression("2+2\n3+3"));
        assertFalse(AiNaturalLanguageCoordinator.isSafeCalculatorExpression("2+2=4"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void naturalLanguageFactoryRejectsBlankUserText() {
        AiRequests.parseNaturalLanguageCalculation("   ", "en-US");
    }
}
