package com.bethibande.repository.repository.oci.details;

public record OCIManifestReference(
        String digest,
        String architecture,
        String os
) {
}
