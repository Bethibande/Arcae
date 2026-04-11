package com.bethibande.arcae.repository.helm;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RegisterForReflection
public record HelmIndex(
        String apiVersion,
        Map<String, List<HelmIndexEntry>> entries,
        Instant generated
) {
}
