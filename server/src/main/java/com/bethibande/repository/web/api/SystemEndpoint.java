package com.bethibande.repository.web.api;

import com.bethibande.repository.jobs.JobScheduler;
import com.bethibande.repository.jpa.system.SystemReference;
import com.bethibande.repository.jpa.system.SystemProperty;
import com.bethibande.repository.k8s.KubernetesLeaderService;
import com.bethibande.repository.k8s.KubernetesSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;

import java.util.Collections;
import java.util.List;

@RolesAllowed("ADMIN")
@Path("/api/v1/system")
public class SystemEndpoint {

    public static final String PROPERTY_FOOTER_REFERENCES = "footer.references";

    @Inject
    protected KubernetesSupport kubernetesSupport;

    @Inject
    protected KubernetesLeaderService kubernetesLeaderService;

    @Inject
    protected JobScheduler jobScheduler;

    @Inject
    protected ObjectMapper objectMapper;

    public record KubernetesCapabilities(
            @NotNull boolean enabled,
            @NotNull boolean routing,
            @NotNull boolean leaderElection,
            @NotNull boolean distributedScheduler,
            @NotNull boolean serviceDiscovery
    ) {
    }

    @GET
    @Path("/k8s/capabilities")
    public @NotNull KubernetesCapabilities hasKubernetesRoutingSupport() {
        return new KubernetesCapabilities(
                kubernetesSupport.isEnabled(),
                kubernetesSupport.hasHttpRouteSupport(),
                kubernetesSupport.hasLeaderElectionSupport(),
                jobScheduler.isDistributed(),
                kubernetesSupport.isServiceDiscoveryEnabled()
        );
    }

    public record LeaderResponse(
            @NotNull String hostname
    ) {
    }

    @GET
    @Path("/k8s/leader")
    public @NotNull LeaderResponse getLeader() {
        return new LeaderResponse(
                this.kubernetesLeaderService.getLeader()
        );
    }

    @GET
    @PermitAll
    @Transactional
    @Path("/footer/refs")
    public @NotNull List<@NotNull SystemReference> getFooterReferences() {
        final List<SystemReference> references = SystemProperty.get(
                PROPERTY_FOOTER_REFERENCES,
                new TypeReference<>() {},
                this.objectMapper
        );

        return references == null ? Collections.emptyList() : references;
    }

    @PUT
    @Transactional
    @RolesAllowed("ADMIN")
    @Path("/footer/refs")
    public void setFooterReferences(final @NotNull List<@NotNull SystemReference> references) {
        SystemProperty.set(PROPERTY_FOOTER_REFERENCES, references, this.objectMapper);
    }

}
