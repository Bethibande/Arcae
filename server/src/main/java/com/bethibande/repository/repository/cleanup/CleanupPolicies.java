package com.bethibande.repository.repository.cleanup;

public record CleanupPolicies(
    MaxAgeCleanupPolicy maxAgePolicy,
    MaxVersionCountPolicy maxVersionCountPolicy
) {
}
