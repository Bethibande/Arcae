package com.bethibande.arcae.repository.oci.config;

import com.bethibande.arcae.repository.mirror.StandardMirrorConfig;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;

@RegisterForReflection
public record OCIRepositoryConfig(
        @NotNull
        OCIRoutingConfig routingConfig,
        Boolean allowRedeployments,
        StandardMirrorConfig mirrorConfig,
        @NotNull String externalHostname
) {
}
