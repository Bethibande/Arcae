package com.bethibande.arcae.repository.maven;

import com.bethibande.arcae.repository.S3Config;
import com.bethibande.arcae.repository.mirror.StandardMirrorConfig;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record MavenRepositoryConfig(
        boolean allowRedeployments,
        S3Config s3Config,
        StandardMirrorConfig mirrorConfig
) {
}
