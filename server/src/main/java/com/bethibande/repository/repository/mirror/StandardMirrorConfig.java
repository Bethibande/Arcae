package com.bethibande.repository.repository.mirror;

import com.bethibande.repository.repository.maven.MirrorConnectionSettings;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record StandardMirrorConfig(
        List<MirrorConnectionSettings> connections,
        boolean enabled,
        boolean storeArtifacts
) {
}
