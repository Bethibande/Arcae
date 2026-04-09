package com.bethibande.arcae.repository.oci;

public record OCIContentInfo(
        String digest,
        long size,
        String contentType
) {
}
