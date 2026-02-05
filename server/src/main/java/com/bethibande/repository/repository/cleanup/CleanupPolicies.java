package com.bethibande.repository.repository.cleanup;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CleanupPolicies(
    MaxAgeCleanupPolicy maxAgePolicy,
    MaxVersionCountPolicy maxVersionCountPolicy
) {
}
