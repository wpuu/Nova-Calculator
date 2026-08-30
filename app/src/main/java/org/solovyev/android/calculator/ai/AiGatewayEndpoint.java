package org.solovyev.android.calculator.ai;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/** Public Nova-owned AI endpoint configuration. Upstream provider URLs never belong here. */
public final class AiGatewayEndpoint {

    private final URL url;

    public AiGatewayEndpoint(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Nova AI gateway endpoint must not be blank");
        }
        try {
            final URI uri = new URI(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Nova AI gateway endpoint must use HTTPS");
            }
            if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
                throw new IllegalArgumentException("Nova AI gateway endpoint must have a host");
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Nova AI gateway endpoint must not contain user info");
            }
            if (uri.getFragment() != null) {
                throw new IllegalArgumentException("Nova AI gateway endpoint must not contain a fragment");
            }
            this.url = uri.toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalArgumentException("Invalid Nova AI gateway endpoint", e);
        }
    }

    public URL getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return url.toString();
    }
}
