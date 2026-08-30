package org.solovyev.android.calculator.ai;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AiGatewayFeatureConfigTest {

    @Test
    public void blankGatewayUrlKeepsAiHidden() {
        assertFalse(AiGatewayFeatureConfig.fromNullableUrl(null).isEnabled());
        assertFalse(AiGatewayFeatureConfig.fromNullableUrl("   ").isEnabled());
    }

    @Test
    public void validHttpsGatewayEnablesAi() {
        AiGatewayFeatureConfig config = AiGatewayFeatureConfig.fromNullableUrl("https://nova.example/ai");
        assertTrue(config.isEnabled());
        assertTrue(config.requireEndpoint().toString().startsWith("https://nova.example/"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void insecureGatewayUrlIsRejectedInsteadOfSilentlyEnablingAi() {
        AiGatewayFeatureConfig.fromNullableUrl("http://nova.example/ai");
    }

    @Test(expected = IllegalStateException.class)
    public void disabledConfigHasNoEndpoint() {
        AiGatewayFeatureConfig.fromNullableUrl("").requireEndpoint();
    }
}
