package com.bethibande.repository.repository.backend;

import com.bethibande.repository.jpa.RepositoryBackend;
import com.bethibande.repository.repository.ArtifactDescriptor;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface IRepositoryBackend {

    RepositoryBackend getBackendInfo();

    CompletableFuture<Void> put(final ArtifactDescriptor descriptor);

    Optional<ArtifactDescriptor> get(final String path);

    void delete(final String path);

}
