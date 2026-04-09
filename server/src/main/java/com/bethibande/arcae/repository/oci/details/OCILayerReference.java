package com.bethibande.arcae.repository.oci.details;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record OCILayerReference(
        String digest
) {
}
