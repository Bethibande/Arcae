package com.bethibande.repository.web.api;

import com.bethibande.repository.k8s.KubernetesSupport;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@RolesAllowed("ADMIN")
@Path("/api/v1/system")
public class SystemEndpoint {

    @Inject
    protected KubernetesSupport kubernetesSupport;

    public record KubernetesCapabilities(
            @NotNull boolean enabled,
            @NotNull boolean routing
    ) {
    }

    @GET
    @Path("/k8s/capabilities")
    public @NotNull KubernetesCapabilities hasKubernetesRoutingSupport() {
        return new KubernetesCapabilities(
                kubernetesSupport.isEnabled(),
                kubernetesSupport.hasHttpRouteSupport()
        );
    }

}
