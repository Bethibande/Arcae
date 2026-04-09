package com.bethibande.arcae.jobs.impl;

import com.bethibande.arcae.jobs.JobType;
import com.bethibande.arcae.jpa.files.FileUploadSession;
import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.jpa.repository.RepositoryManager;
import com.bethibande.arcae.repository.HasUploadSessions;
import com.bethibande.arcae.repository.ManagedRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.Hibernate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class CleanupExpiredUploadsTask implements JobTask<Object> {

    public static final Duration MAX_UPLOAD_AGE = Duration.ofHours(4);

    @Inject
    protected RepositoryManager repositoryManager;

    @Override
    public Class<Object> getConfigType() {
        return Object.class;
    }

    @Override
    public JobType getJobType() {
        return JobType.CLEAN_UP_EXPIRED_UPLOADS;
    }

    @Override
    public void run(final Object config) {
        final List<Repository> repositories = QuarkusTransaction.requiringNew().call(() -> Repository.<Repository>listAll()
                .stream()
                .peek(repo -> Hibernate.initialize(repo.permissions))
                .toList());

        for (int i = 0; i < repositories.size(); i++) {
            final Repository repository = repositories.get(i);
            this.cleanRepository(repository);
        }
    }

    protected void cleanRepository(final Repository repository) {
        final ManagedRepository managedRepository = this.repositoryManager.manage(repository);
        if (!(managedRepository instanceof HasUploadSessions hasUploadSessions)) return;

        final Instant minAge = Instant.now().minus(MAX_UPLOAD_AGE);

        final AtomicBoolean next = new AtomicBoolean(true);
        while (next.get()) {
            QuarkusTransaction.requiringNew().run(() -> {
                final List<FileUploadSession> sessions = FileUploadSession.find("repository = ?1 AND createdAt < ?2", repository, minAge)
                        .page(0, 100)
                        .list();

                for (int i = 0; i < sessions.size(); i++) {
                    final FileUploadSession session = sessions.get(i);
                    hasUploadSessions.abortUploadSession(session);

                    FileUploadSession.deleteById(session.id);
                }

                next.set(!sessions.isEmpty());
            });
        }
    }

}
