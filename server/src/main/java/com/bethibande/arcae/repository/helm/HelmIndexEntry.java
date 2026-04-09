package com.bethibande.arcae.repository.helm;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.List;

@RegisterForReflection
public record HelmIndexEntry(
        Instant created,
        String description,
        String digest,
        String home,
        String name,
        List<String> sources,
        List<String> urls,
        String version
) {
}
