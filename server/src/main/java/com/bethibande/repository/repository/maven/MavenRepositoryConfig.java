package com.bethibande.repository.repository.maven;

import com.bethibande.repository.repository.S3Config;

public record MavenRepositoryConfig(
        boolean allowRedeployments,
        S3Config s3Config
) {
}
