package com.bethibande.repository.repository.oci.config;

import com.bethibande.repository.repository.S3Config;
import com.bethibande.repository.repository.mirror.StandardMirrorConfig;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;

@RegisterForReflection
public record OCIRepositoryConfig(
        @NotNull
        S3Config s3Config,
        @NotNull
        OCIRoutingConfig routingConfig,
        Boolean allowRedeployments,
        StandardMirrorConfig mirrorConfig,
        @NotNull String externalHostname
) {
}
