package com.bethibande.repository.jpa.repository;

import com.bethibande.repository.repository.ManagedRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RepositoryManager {

    @Inject
    protected ObjectMapper mapper;

    public <T extends ManagedRepository> T findRepository(final String name, final PackageManager packageManager) {
        final Repository repo = Repository.find("name = ?1 and packageManager = ?2", name, packageManager).firstResult();
        if (repo == null) return null;

        return manage(repo);
    }

    @SuppressWarnings("unchecked")
    public <T extends ManagedRepository> T manage(final Repository repo) {
        return (T) repo.packageManager.createRepository(repo, mapper);
    }

}
