package com.bethibande.arcae.jpa.system;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;

@RegisterForReflection
public record SystemReference(
        @NotNull String label,
        @NotNull SystemReferenceType type,
        @NotNull String value
) {
}
