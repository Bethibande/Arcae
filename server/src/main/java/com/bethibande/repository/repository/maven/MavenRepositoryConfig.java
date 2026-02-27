package com.bethibande.repository.repository.maven;

import com.bethibande.repository.repository.S3Config;
import com.bethibande.repository.repository.mirror.StandardMirrorConfig;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record MavenRepositoryConfig(
        boolean allowRedeployments,
        S3Config s3Config,
        StandardMirrorConfig mirrorConfig
) {
}
