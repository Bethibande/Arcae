package com.bethibande.repository.repository;

import com.bethibande.repository.k8s.KubernetesSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.Executor;

public record RepositoryApplicationContext(
        ObjectMapper objectMapper,
        KubernetesSupport kubernetesSupport,
        Executor executor
) {
}
