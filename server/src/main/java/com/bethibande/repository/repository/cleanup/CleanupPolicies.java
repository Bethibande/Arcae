package com.bethibande.repository.repository.cleanup;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;

@RegisterForReflection
public record CleanupPolicies(
        @NotNull
        MaxAgeCleanupPolicy maxAgePolicy,
        @NotNull
        MaxVersionCountPolicy maxVersionCountPolicy
) {
}
