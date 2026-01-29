package com.bethibande.repository.jpa.repository;

import com.bethibande.repository.repository.ManagedRepository;
import com.bethibande.repository.repository.maven.MavenRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public enum PackageManager {

    MAVEN_3(MavenRepository::new);

    private final RepositoryFactory factory;

    PackageManager(final RepositoryFactory factory) {
        this.factory = factory;
    }

    public ManagedRepository createRepository(final Repository info, final ObjectMapper mapper) {
        try {
            return factory.createRepository(info, mapper);
        } catch (final JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

}
