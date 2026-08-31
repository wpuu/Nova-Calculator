package org.solovyev.android.calculator.analytics;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class NovaProductAnalyticsTest {

    @Test
    public void eventEndpointStaysOnNovaSessionOrigin() {
        URL endpoint = NovaProductAnalytics.deriveProductEventEndpoint(
                "https://gateway.nova.example/api/session?ignored=1");

        assertEquals("https", endpoint.getProtocol());
        assertEquals("gateway.nova.example", endpoint.getHost());
        assertEquals("/api/product-event", endpoint.getPath());
        assertEquals(null, endpoint.getQuery());
    }

    @Test
    public void insecureOrCredentialedSessionOriginsDisableAnalytics() {
        assertNull(NovaProductAnalytics.deriveProductEventEndpoint(
                "http://gateway.nova.example/api/session"));
        assertNull(NovaProductAnalytics.deriveProductEventEndpoint(
                "https://user:password@gateway.nova.example/api/session"));
        assertNull(NovaProductAnalytics.deriveProductEventEndpoint(
                "https://gateway.nova.example/api/session#fragment"));
    }

    @Test
    public void blankSessionEndpointCreatesNoOpAnalytics() {
        NovaProductAnalytics analytics = NovaProductAnalytics.fromSessionEndpoint(
                "", () -> "token", Runnable::run);

        assertFalse(analytics.isEnabled());
        // Public event methods remain safe no-ops rather than throwing when analytics is disabled.
        analytics.autoTapDisclosureAccepted();
        analytics.autoTapSettingsOpened(NovaProductAnalytics.EntrySource.MAIN_MENU);
    }
}
