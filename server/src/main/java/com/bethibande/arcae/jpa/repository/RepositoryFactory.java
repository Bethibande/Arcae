package com.bethibande.arcae.jpa.repository;

import com.bethibande.arcae.repository.ManagedRepository;
import com.bethibande.arcae.repository.RepositoryApplicationContext;
import com.fasterxml.jackson.core.JsonProcessingException;

@FunctionalInterface
public interface RepositoryFactory {

    ManagedRepository createRepository(final Repository info,
                                       final RepositoryApplicationContext ctx) throws JsonProcessingException;

}
