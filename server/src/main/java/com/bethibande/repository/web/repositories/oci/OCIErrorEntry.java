package com.bethibande.repository.web.repositories.oci;

public record OCIErrorEntry(
        String code,
        String message,
        String details
) {
}
