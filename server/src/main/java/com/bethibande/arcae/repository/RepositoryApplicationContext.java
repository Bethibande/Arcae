package com.bethibande.arcae.repository;

import com.bethibande.arcae.jpa.repository.RepositoryManager;
import com.bethibande.arcae.k8s.KubernetesSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.Executor;

public record RepositoryApplicationContext(
        ObjectMapper objectMapper,
        KubernetesSupport kubernetesSupport,
        Executor executor,
        RepositoryManager repositoryManager
) {
}
