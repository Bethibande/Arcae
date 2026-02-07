package com.bethibande.repository.repository.oci;

public record OCIContentInfo(
        String digest,
        long size
) {
}
