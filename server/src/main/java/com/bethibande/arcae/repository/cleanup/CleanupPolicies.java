package com.bethibande.arcae.repository.cleanup;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;

import java.time.temporal.ChronoUnit;

@RegisterForReflection
public record CleanupPolicies(
        @NotNull
        MaxAgeCleanupPolicy maxAgePolicy,
        @NotNull
        MaxVersionCountPolicy maxVersionCountPolicy
) {

        public static CleanupPolicies standard() {
                return new CleanupPolicies(
                        new MaxAgeCleanupPolicy(
                                false,
                                10,
                                ChronoUnit.DAYS
                        ),
                        new MaxVersionCountPolicy(
                                false,
                                4
                        )
                );
        }

}
