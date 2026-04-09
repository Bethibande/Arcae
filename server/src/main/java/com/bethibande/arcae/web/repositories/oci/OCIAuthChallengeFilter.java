package com.bethibande.arcae.web.repositories.oci;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

@Provider
public class OCIAuthChallengeFilter {

    @Inject
    protected UriInfo uriInfo;

    @ServerResponseFilter
    public void authResponseInterceptor(final ContainerResponseContext context) {
        if (!uriInfo.getPath().matches("/repositories/(helm|oci)/.*")) return;

        if (context.getStatus() == 401) {
            String baseUri = uriInfo.getBaseUri().toString();
            if (baseUri.endsWith("/")) {
                baseUri = baseUri.substring(0, baseUri.length() - 1);
            }

            final String realm = "%s/v2/auth".formatted(baseUri);
            final String service = uriInfo.getBaseUri().getHost();

            context.getHeaders().add("WWW-Authenticate", "Bearer realm=\"%s\",service=\"%s\"".formatted(realm, service));
            context.setEntity(OCIError.of(OCIErrorCodes.UNAUTHORIZED, "Not authenticated", "You are not authenticated or not permitted to perform the requested action"));
        }
        if (context.getStatus() == 403) {
            context.setEntity(OCIError.of(OCIErrorCodes.DENIED, "Access denied", "You are not permitted to perform the requested action"));
        }
    }

}
