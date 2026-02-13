package com.bethibande.repository.repository.oci.config;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record OCIRoutingConfig(
        boolean enabled,
        String targetService,
        int targetPort,
        String gatewayName,
        String gatewayNamespace
) {
}
