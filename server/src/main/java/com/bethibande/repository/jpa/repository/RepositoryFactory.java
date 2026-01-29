package com.bethibande.repository.jpa.repository;

import com.bethibande.repository.repository.ManagedRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@FunctionalInterface
public interface RepositoryFactory {

    ManagedRepository createRepository(final Repository info, final ObjectMapper mapper) throws JsonProcessingException;

}
