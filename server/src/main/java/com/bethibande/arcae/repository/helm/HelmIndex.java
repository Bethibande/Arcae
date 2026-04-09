package com.bethibande.arcae.repository.helm;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record HelmIndex(
        String apiVersion,
        Map<String, List<HelmIndexEntry>> entries,
        Instant generated
) {
}
