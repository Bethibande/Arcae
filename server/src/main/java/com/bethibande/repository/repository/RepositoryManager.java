package com.bethibande.repository.repository;

import com.bethibande.repository.jpa.Repository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
public class RepositoryManager {

    private final ObjectMapper objectMapper;

    @Inject
    public RepositoryManager(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Transactional
    // TODO: Cache result
    public @Nullable IRepository getRepositoryByName(final String name) {
        final Repository entity = Repository.find("name = ?1", name)
                .firstResult();

        if (entity == null) return null;
        return entity.type.create(entity, objectMapper);
    }

}
