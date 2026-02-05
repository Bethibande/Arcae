package com.bethibande.repository.repository.maven;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record MavenMirrorConfig(
        List<MirrorConnectionSettings> connections,
        boolean enabled,
        boolean storeArtifacts
) {
}
