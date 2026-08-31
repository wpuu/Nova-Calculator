package org.solovyev.android.calculator.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class AiFormulaContractTest {

    @Test
    public void formulaRequestContainsOnlyGoalAndPublicNovaFields() throws Exception {
        AiGatewayRequest request = new AiGatewayRequest("formula-1", "售价和成本计算毛利率", "zh-CN");
        JSONObject json = new JSONObject(AiGatewayJsonCodec.encodeRequest(request));

        assertEquals("formula-1", json.getString("requestId"));
        assertEquals("BUILD_FORMULA", json.getString("operation"));
        assertEquals("售价和成本计算毛利率", json.getString("formulaGoal"));
        assertEquals("zh-CN", json.getString("localeTag"));
        assertEquals(4, json.length());
        String encoded = json.toString().toLowerCase();
        assertFalse(encoded.contains("provider"));
        assertFalse(encoded.contains("model"));
        assertFalse(encoded.contains("apikey"));
        assertFalse(encoded.contains("entitlement"));
    }

    @Test
    public void strictFormulaCandidateParsesSafeReusableFunction() {
        AiFormulaCandidate candidate = AiFormulaCandidate.parse(
                "{\"name\":\"gross_margin\","
                        + "\"parameters\":[\"price\",\"cost\"],"
                        + "\"expression\":\"(price-cost)/price*100\","
                        + "\"description\":\"毛利率百分比\"}");

        assertEquals("gross_margin", candidate.getName());
        assertEquals(Arrays.asList("price", "cost"), candidate.getParameters());
        assertEquals("(price-cost)/price*100", candidate.getExpression());
        assertEquals("毛利率百分比", candidate.getDescription());
    }

    @Test
    public void formulaCandidateRejectsScriptSyntaxDuplicateParamsAndUnusedParams() {
        assertRejected("{\"name\":\"bad\",\"parameters\":[\"x\"],\"expression\":\"x;run()\",\"description\":\"bad\"}");
        assertRejected("{\"name\":\"bad\",\"parameters\":[\"x\",\"x\"],\"expression\":\"x+1\",\"description\":\"bad\"}");
        assertRejected("{\"name\":\"bad\",\"parameters\":[\"x\"],\"expression\":\"1+1\",\"description\":\"bad\"}");
        assertRejected("{\"name\":\"bad\",\"parameters\":[\"x\",\"y\"],\"expression\":\"x+1\",\"description\":\"partially unused parameters\"}");
        assertRejected("{\"name\":\"bad\",\"parameters\":[\"x\",\"tax\"],\"expression\":\"tax+1\",\"description\":\"identifier substring must not count\"}");
        assertRejected("{\"name\":\"bad\",\"parameters\":[\"x\"],\"expression\":\"(x+1\",\"description\":\"bad\"}");
    }

    @Test
    public void formulaFactoryAlwaysBuildsFormulaOperation() {
        AiGatewayRequest request = AiRequests.buildFormula("tip from amount and rate", "en-US");
        assertEquals(AiOperation.BUILD_FORMULA, request.getOperation());
        assertEquals("tip from amount and rate", request.getFormulaGoal());
        assertTrue(request.getExpression().isEmpty());
        assertTrue(request.getDeterministicResult().isEmpty());
    }

    private static void assertRejected(String value) {
        try {
            AiFormulaCandidate.parse(value);
            fail("Expected formula candidate rejection");
        } catch (IllegalArgumentException expected) {
        }
    }
}
