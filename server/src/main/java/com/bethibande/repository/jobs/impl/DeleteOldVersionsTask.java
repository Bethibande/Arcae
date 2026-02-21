package com.bethibande.repository.jobs.impl;

import com.bethibande.repository.jobs.JobType;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.repository.RepositoryManager;
import com.bethibande.repository.repository.ManagedRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionSemantics;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
        QuarkusTransaction.runner(TransactionSemantics.REQUIRE_NEW).run(() -> {
            Repository.<Repository>streamAll()
                    .forEach(repository -> {
                        if (repository.cleanupPolicies != null
                                && repository.cleanupPolicies.maxAgePolicy() != null
                                && repository.cleanupPolicies.maxAgePolicy().enabled()) {
                            final ManagedRepository managedRepository = this.repositoryManager.manage(repository);
                            repository.cleanupPolicies.maxAgePolicy().cleanup(repository, managedRepository);
                        }
                    });
        });
    }
}
