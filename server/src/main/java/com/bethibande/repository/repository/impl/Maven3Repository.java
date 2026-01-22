package com.bethibande.repository.repository.impl;

import com.bethibande.repository.jpa.Repository;
import com.bethibande.repository.repository.ArtifactDescriptor;
import com.bethibande.repository.repository.IRepository;
import com.bethibande.repository.repository.backend.IRepositoryBackend;
import com.bethibande.repository.repository.backend.S3Backend;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public class Maven3Repository implements IRepository {

    public static final String POM_FILE_NAME = "pom.xml";

    protected final Repository info;
    protected final Maven3RepositoryConfig config;
    protected final IRepositoryBackend backend;

    public Maven3Repository(final Repository info, final ObjectMapper objectMapper) throws JsonProcessingException {
        this.info = info;
        this.config = objectMapper.readValue(info.settings, Maven3RepositoryConfig.class);
        this.backend = switch (info.backend.type) {
            case S3 -> new S3Backend(info.backend, objectMapper);
            case PROXY -> throw new UnsupportedOperationException("Not yet implemented");
        };
    }

    public CompletableFuture<Void> uploadFile(final String path,
                                              final InputStream data,
                                              final long contentLength,
                                              final String contentType) {
        final String[] pathSegments = path.split("/");
        final String fileName = pathSegments[pathSegments.length - 1];

        final CompletableFuture<Void> future = backend.put(new ArtifactDescriptor(
                path,
                contentLength,
                contentType,
                data
        ));

        if (fileName.equalsIgnoreCase(POM_FILE_NAME)) {

        }

        return future;
    }

    @Override
    public Repository getRepositoryInfo() {
        return this.info;
    }

    @Override
    public IRepositoryBackend getBackend() {
        return this.backend;
    }
}
