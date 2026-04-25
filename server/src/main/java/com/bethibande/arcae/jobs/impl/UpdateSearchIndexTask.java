package com.bethibande.arcae.jobs.impl;

import com.bethibande.arcae.jobs.JobType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.hibernate.CacheMode;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UpdateSearchIndexTask implements JobTask<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateSearchIndexTask.class);

    @Inject
    protected Instance<SearchSession> searchSession;

    @Override
    public Class<Object> getConfigType() {
        return Object.class;
    }

    @Override
    public JobType getJobType() {
        return JobType.UPDATE_SEARCH_INDEX;
    }

    @Override
    public void run(final Object config) {
        QuarkusTransaction.requiringNew().run(() -> {
            try {
                if (!this.searchSession.isResolvable()) {
                    LOGGER.warn("Search is disabled, why is this job running?");
                    return;
                }

                this.searchSession.get()
                        .massIndexer()
                        .dropAndCreateSchemaOnStart(true)
                        .idFetchSize(10_000)
                        .batchSizeToLoadObjects(25)
                        .cacheMode(CacheMode.IGNORE)
                        .typesToIndexInParallel(2)
                        .threadsToLoadObjects(Runtime.getRuntime().availableProcessors() / 2)
                        .startAndWait();
            } catch (final InterruptedException ex) {
                LOGGER.error("Failed to update search index", ex);
            }
        });
    }
}
