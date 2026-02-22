package com.bethibande.repository.jobs.impl;

import com.bethibande.repository.jobs.JobType;
import com.bethibande.repository.jpa.files.StoredFile;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.repository.RepositoryManager;
import com.bethibande.repository.repository.ManagedRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class CleanupOrphanedFilesTask implements JobTask<Object> {

    public static final Duration MIN_FILE_AGE = Duration.ofHours(1);

    @Inject
    protected RepositoryManager repositoryManager;

    @Override
    public Class<Object> getConfigType() {
        return Object.class;
    }

    @Override
    public JobType getJobType() {
        return JobType.CLEAN_UP_ORPHANED_FILES;
    }

    @Override
    public void run(final Object config) {
        final Instant now = Instant.now();
        final Instant minAge = now.minus(MIN_FILE_AGE);

        final List<ManagedRepository> repositories = QuarkusTransaction.requiringNew().call(
                () -> Repository.<Repository>listAll()
                        .stream()
                        .<ManagedRepository>map(this.repositoryManager::manage)
                        .toList()
        );

        for (int i = 0; i < repositories.size(); i++) {
            final ManagedRepository repository = repositories.get(i);

            final AtomicBoolean running = new AtomicBoolean(true);
            while (running.get()) {
                QuarkusTransaction.requiringNew().run(() -> {
                    final List<StoredFile> files = StoredFile.findOrphanedFiles(repository.getInfo(), minAge, 500);
                    running.set(!files.isEmpty());

                    for (int i1 = 0; i1 < files.size(); i1++) {
                        final StoredFile file = files.get(i1);
                        repository.delete(file);
                    }
                });
            }
        }
    }
}
