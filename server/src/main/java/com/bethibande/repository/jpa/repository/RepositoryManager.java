package com.bethibande.repository.jpa.repository;

import com.bethibande.repository.k8s.KubernetesSupport;
import com.bethibande.repository.repository.ManagedRepository;
import com.bethibande.repository.repository.RepositoryApplicationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RepositoryManager {

    @Inject
    protected ObjectMapper mapper;

    @Inject
    protected KubernetesSupport kubernetesSupport;

    private RepositoryApplicationContext ctx;

    @PostConstruct
    protected void init() {
        ctx = new RepositoryApplicationContext(mapper, kubernetesSupport);
    }

    public <T extends ManagedRepository> T findRepository(final String name, final PackageManager packageManager) {
        final Repository repo = Repository.find("name = ?1 and packageManager = ?2", name, packageManager).firstResult();
        if (repo == null) return null;

        return manage(repo);
    }

    @SuppressWarnings("unchecked")
    public <T extends ManagedRepository> T manage(final Repository repo) {
        return (T) repo.packageManager.createRepository(repo, this.ctx);
    }

}
