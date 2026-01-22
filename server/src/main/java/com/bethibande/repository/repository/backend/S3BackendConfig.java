package com.bethibande.repository.repository.backend;

public record S3BackendConfig(
        String host,
        String region,
        String bucket,
        String accessKey,
        String secretKey
) {
}
