package com.bethibande.repository.repository.oci.details;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record OCIManifestDetails(
        String configDigest,
        List<OCIManifestReference> manifests,
        List<OCILayerReference> layers
) {
}
