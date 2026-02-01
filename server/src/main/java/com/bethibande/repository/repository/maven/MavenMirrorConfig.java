package com.bethibande.repository.repository.maven;

import java.util.List;

public record MavenMirrorConfig(
        List<MirrorConnectionSettings> connections,
        boolean enabled,
        boolean storeArtifacts
) {
}
