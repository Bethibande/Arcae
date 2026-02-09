package com.bethibande.repository.repository.oci.details;

import java.util.List;

public record OCIManifestDetails(
        String configDigest,
        List<OCIManifestReference> manifests,
        List<OCILayerReference> layers
) {
}
