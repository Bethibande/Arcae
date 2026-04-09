package com.bethibande.arcae.jpa.repository;

import com.bethibande.arcae.repository.ManagedRepository;
import com.bethibande.arcae.repository.RepositoryApplicationContext;
import com.bethibande.arcae.repository.helm.HelmRepository;
import com.bethibande.arcae.repository.maven.MavenRepository;
import com.bethibande.arcae.repository.oci.OCIRepository;
import com.fasterxml.jackson.core.JsonProcessingException;

public enum PackageManager {

    OCI(OCIRepository::new),
    MAVEN(MavenRepository::new),
    HELM(HelmRepository::new);

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
