package com.bethibande.repository.repository;

import com.bethibande.repository.k8s.KubernetesSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

public record RepositoryApplicationContext(
        ObjectMapper objectMapper,
        KubernetesSupport kubernetesSupport
) {
}
