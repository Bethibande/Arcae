package com.bethibande.arcae.web.api.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@RegisterForReflection
public record LoginResponse(
        @NotNull LoginResult result,
        Instant sessionExpiry
) {
}
