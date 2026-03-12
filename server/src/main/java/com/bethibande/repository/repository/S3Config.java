package com.bethibande.repository.repository;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;

@RegisterForReflection
public record S3Config(
        @NotNull String url,
        @NotNull String region,
        @NotNull String bucket,
        @NotNull String accessKey,
        @NotNull String secretKey
) {
}
