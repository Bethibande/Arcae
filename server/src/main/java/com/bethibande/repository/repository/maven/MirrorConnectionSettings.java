package com.bethibande.repository.repository.maven;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record MirrorConnectionSettings(
        String url,
        MavenMirrorAuthType authType,
        String username,
        String password
) {
}
