package com.bethibande.repository.repository.maven;

import com.bethibande.repository.repository.mirror.MirrorAuthType;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record MirrorConnectionSettings(
        String url,
        MirrorAuthType authType,
        String username,
        String password
) {
}
