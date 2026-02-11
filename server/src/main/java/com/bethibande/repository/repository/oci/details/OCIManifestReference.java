package com.bethibande.repository.repository.oci.details;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record OCIManifestReference(
        String digest,
        String architecture,
        String os
) {
}
