package com.bethibande.arcae.jobs.impl;

import com.bethibande.arcae.jobs.JobType;
import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.jpa.repository.RepositoryManager;
import com.bethibande.arcae.repository.maven.MavenRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionSemantics;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class DeleteOldVersionsTask implements JobTask<Object> {

    @Inject
    protected RepositoryManager repositoryManager;

    @Override
    public Class<Object> getConfigType() {
        return Object.class;
    }

    @Override
    public JobType getJobType() {
        return JobType.DELETE_OLD_VERSIONS;
    }

    @Override
    public void run(final Object config) {
        final List<MavenRepository> repositories = QuarkusTransaction.runner(TransactionSemantics.REQUIRE_NEW).call(
                () -> Repository.<Repository>listAll()
                        .stream()
                        .filter(repo -> repo.cleanupPolicies != null)
                        .filter(repo -> repo.cleanupPolicies.maxAgePolicy() != null)
                        .filter(repo -> repo.cleanupPolicies.maxAgePolicy().enabled())
                        .map(repo -> this.repositoryManager.<MavenRepository>manage(repo))
                        .toList()
        );

        for (int i = 0; i < repositories.size(); i++) {
            final MavenRepository repository = repositories.get(i);
            repository.getInfo().cleanupPolicies.maxAgePolicy().cleanup(repository.getInfo(), repository);
        }
    }
}
