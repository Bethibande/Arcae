package com.bethibande.repository.jpa.repository;

import com.bethibande.repository.repository.ManagedRepository;
import com.bethibande.repository.repository.RepositoryApplicationContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@FunctionalInterface
public interface RepositoryFactory {

    ManagedRepository createRepository(final Repository info,
                                       final RepositoryApplicationContext ctx) throws JsonProcessingException;

}
