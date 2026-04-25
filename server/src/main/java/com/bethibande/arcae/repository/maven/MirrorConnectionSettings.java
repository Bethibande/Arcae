package com.bethibande.arcae.repository.maven;

import com.bethibande.arcae.repository.mirror.MirrorAuthType;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record MirrorConnectionSettings(
        boolean internal,
        long repositoryId,
        String url,
        MirrorAuthType authType,
        String username,
        String password
) {
}
