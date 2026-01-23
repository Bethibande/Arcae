package com.bethibande.repository.jpa.repository;

import com.bethibande.repository.repository.IRepository;
import com.bethibande.repository.repository.IRepositoryFactory;
import com.bethibande.repository.repository.impl.Maven3Repository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public enum RepositoryType {

    MAVEN_3(Maven3Repository::new, RepositoryBackendType.S3);

    private final IRepositoryFactory factory;
    private final RepositoryBackendType[] supportedBackendTypes;

    RepositoryType(final IRepositoryFactory factory, final RepositoryBackendType... supportedBackendTypes) {
        this.factory = factory;
        this.supportedBackendTypes = supportedBackendTypes;
    }

    public IRepository create(final Repository entity, final ObjectMapper mapper) {
        try {
            return this.factory.create(entity, mapper);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public RepositoryBackendType[] getSupportedBackendTypes() {
        return supportedBackendTypes;
    }
}
