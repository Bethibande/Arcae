package com.bethibande.arcae.repository.maven;

import com.bethibande.arcae.repository.mirror.StandardMirrorConfig;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record MavenRepositoryConfig(
        boolean allowRedeployments,
        StandardMirrorConfig mirrorConfig
) {
}
