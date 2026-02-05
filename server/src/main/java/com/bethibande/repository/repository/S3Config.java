package com.bethibande.repository.repository;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record S3Config(
        String url,
        String region,
        String bucket,
        String accessKey,
        String secretKey
) {
}
