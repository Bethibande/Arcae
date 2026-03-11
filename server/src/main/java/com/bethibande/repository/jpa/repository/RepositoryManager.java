package com.bethibande.repository.jpa.repository;

import com.bethibande.repository.k8s.KubernetesSupport;
import com.bethibande.repository.repository.ManagedRepository;
import com.bethibande.repository.repository.RepositoryApplicationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.Hibernate;

import java.time.Duration;
import java.util.concurrent.Executor;

@ApplicationScoped
@RegisterForReflection(classNames = "com.github.benmanes.caffeine.cache.SSLW")
public class RepositoryManager {

    @Inject
    protected ObjectMapper mapper;

    @Inject
    protected KubernetesSupport kubernetesSupport;

    @Inject
    @VirtualThreads
    protected Executor executor;

    private RepositoryApplicationContext ctx;

    protected final Cache<String, ManagedRepository> repositoryCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(10))
            .<String, ManagedRepository>removalListener((_, val, _) -> {
                if (val != null) val.close();
            })
            .build();

    @PostConstruct
    protected void init() {
        ctx = new RepositoryApplicationContext(this.mapper, this.kubernetesSupport, this.executor);
    }

    @SuppressWarnings("unchecked")
    public <T extends ManagedRepository> T findRepository(final String name, final PackageManager packageManager) {
        final ManagedRepository cached = this.repositoryCache.getIfPresent(name);
        if (cached != null) return (T) cached;

        final Repository repo = Repository.find("name = ?1 and packageManager = ?2", name, packageManager).firstResult();
        if (repo == null) return null;

        Hibernate.initialize(repo.permissions);

        return manage(repo);
    }

    public <T extends ManagedRepository> T manage(final Repository repo) {
        return manage(repo, true);
    }

    @SuppressWarnings("unchecked")
    public <T extends ManagedRepository> T manage(final Repository repo, final boolean useCache) {
        if (!Hibernate.isInitialized(repo.permissions)) Hibernate.initialize(repo.permissions);
        if (!useCache) return (T) repo.packageManager.createRepository(repo, this.ctx);

        return (T) this.repositoryCache.get(repo.name, (_) -> repo.packageManager.createRepository(repo, this.ctx));
    }

    public void cacheInvalidate(final String name) {
        this.repositoryCache.invalidate(name);
    }

}
