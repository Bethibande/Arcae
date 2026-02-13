package com.bethibande.repository.jpa.repository;

import com.bethibande.repository.repository.ManagedRepository;
import com.bethibande.repository.repository.RepositoryApplicationContext;
import com.bethibande.repository.repository.maven.MavenRepository;
import com.bethibande.repository.repository.oci.OCIRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public enum PackageManager {

    OCI(OCIRepository::new),
    MAVEN(MavenRepository::new);

    private final RepositoryFactory factory;

    PackageManager(final RepositoryFactory factory) {
        this.factory = factory;
    }

    public ManagedRepository createRepository(final Repository info, final RepositoryApplicationContext ctx) {
        try {
            return factory.createRepository(info, ctx);
        } catch (final JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

}
