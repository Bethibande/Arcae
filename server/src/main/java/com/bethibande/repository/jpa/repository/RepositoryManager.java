package com.bethibande.repository.jpa.repository;

import com.bethibande.repository.k8s.KubernetesSupport;
import com.bethibande.repository.repository.ManagedRepository;
import com.bethibande.repository.repository.RepositoryApplicationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;

@ApplicationScoped
public class RepositoryManager {

    @Inject
    protected ObjectMapper mapper;

    @Inject
    protected KubernetesSupport kubernetesSupport;

    private RepositoryApplicationContext ctx;

    protected final Cache<String, ManagedRepository> repositoryCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(10))
            .<String, ManagedRepository>removalListener((_, val, _) -> {
                if (val != null) val.close();
            })
            .build();

    @PostConstruct
    protected void init() {
        ctx = new RepositoryApplicationContext(mapper, kubernetesSupport);
    }

    @SuppressWarnings("unchecked")
    public <T extends ManagedRepository> T findRepository(final String name, final PackageManager packageManager) {
        final ManagedRepository cached = this.repositoryCache.getIfPresent(name);
        if (cached != null) return (T) cached;

        final Repository repo = Repository.find("name = ?1 and packageManager = ?2", name, packageManager).firstResult();
        if (repo == null) return null;

        return manage(repo);
    }

    @SuppressWarnings("unchecked")
    public <T extends ManagedRepository> T manage(final Repository repo) {
        return (T) this.repositoryCache.get(repo.name, (_) -> repo.packageManager.createRepository(repo, this.ctx));
    }

    public void cacheInvalidate(final String name) {
        this.repositoryCache.invalidate(name);
    }

}
