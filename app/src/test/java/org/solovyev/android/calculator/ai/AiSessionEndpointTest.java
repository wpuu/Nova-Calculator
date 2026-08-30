package org.solovyev.android.calculator.ai;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AiSessionEndpointTest {

    @Test
    public void validHttpsSessionEndpointIsAccepted() {
        AiSessionEndpoint endpoint = new AiSessionEndpoint("https://nova.example/session/anonymous");
        assertTrue(endpoint.toString().startsWith("https://nova.example/"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void httpSessionEndpointIsRejected() {
        new AiSessionEndpoint("http://nova.example/session/anonymous");
    }

    @Test(expected = IllegalArgumentException.class)
    public void endpointWithUserInfoIsRejected() {
        new AiSessionEndpoint("https://user:pass@nova.example/session/anonymous");
    }

    @Test(expected = IllegalArgumentException.class)
    public void endpointWithFragmentIsRejected() {
        new AiSessionEndpoint("https://nova.example/session/anonymous#fragment");
    }
}
