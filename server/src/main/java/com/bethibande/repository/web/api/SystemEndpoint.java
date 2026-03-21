package com.bethibande.repository.web.api;

import com.bethibande.repository.jobs.JobScheduler;
import com.bethibande.repository.jpa.system.SystemProperty;
import com.bethibande.repository.jpa.system.SystemReference;
import com.bethibande.repository.jpa.system.SystemReferenceType;
import com.bethibande.repository.k8s.KubernetesLeaderService;
import com.bethibande.repository.k8s.KubernetesSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@RolesAllowed("ADMIN")
@Path("/api/v1/system")
public class SystemEndpoint {

    public static final String PROPERTY_SYSTEM_REFERENCES = "header.references";

    @Inject
    protected KubernetesSupport kubernetesSupport;

    @Inject
    protected KubernetesLeaderService kubernetesLeaderService;

    @Inject
    protected JobScheduler jobScheduler;

    @Inject
    protected ObjectMapper objectMapper;

    public record AppStatus(
            Instant startupTime
    ) {
    }

    @GET
    @Path("/status")
    public @NotNull AppStatus getAppStatus() {
        return new AppStatus(
                this.jobScheduler.getStartupTime()
        );
    }

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
    @Path("/header/refs")
    public @NotNull List<@NotNull SystemReference> getHeaderReferences() {
        final List<SystemReference> references = SystemProperty.get(
                PROPERTY_SYSTEM_REFERENCES,
                new TypeReference<>() {
                },
                this.objectMapper
        );

        return references == null ? Collections.emptyList() : references;
    }

    @PUT
    @Transactional
    @Path("/header/refs")
    public void setHeaderReferences(final @NotNull List<@NotNull SystemReference> references) {
        for (int i = 0; i < references.size(); i++) {
            final SystemReference reference = references.get(i);
            if (reference.type() == SystemReferenceType.TEXT) continue;

            final String url = reference.value().toLowerCase();
            if (url.startsWith("http://")
                    || url.startsWith("https://")
                    || url.startsWith("/")
                    || url.startsWith("mailto:")) continue;

            throw new BadRequestException("Invalid URL: " + reference.value());
        }

        SystemProperty.set(PROPERTY_SYSTEM_REFERENCES, references, this.objectMapper);
    }

}
