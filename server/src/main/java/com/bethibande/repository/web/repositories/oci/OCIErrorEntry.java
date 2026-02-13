package com.bethibande.repository.web.repositories.oci;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record OCIErrorEntry(
        String code,
        String message,
        String details
) {
}
