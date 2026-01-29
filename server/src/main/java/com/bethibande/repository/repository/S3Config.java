package com.bethibande.repository.repository;

public record S3Config(
        String url,
        String region,
        String bucket,
        String accessKey,
        String secretKey
) {
}
