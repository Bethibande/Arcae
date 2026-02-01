package com.bethibande.repository.repository.maven;

public record MirrorConnectionSettings(
        String url,
        MavenMirrorAuthType authType,
        String username,
        String password
) {
}
