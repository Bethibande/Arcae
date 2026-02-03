package com.bethibande.repository.tasks;

import com.bethibande.repository.jpa.TaskLock;
import com.bethibande.repository.jpa.TaskLockService;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.repository.RepositoryManager;
import com.bethibande.repository.repository.ManagedRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;

public class DeleteOldVersionsTask {

    public static final String TASK_ID = "delete_old_versions";

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteOldVersionsTask.class);

    @Inject
    public TaskLockService lockService;

    @Inject
    public RepositoryManager repositoryManager;

    @Transactional
    @Scheduled(cron = "0 * * * * ?")
    public void run() {
        final TaskLock lock = this.lockService.acquire(TASK_ID, 10, ChronoUnit.MINUTES);
        if (lock == null) return;

        try {
            lock.scheduleForCron("0 * * * * *");

            Repository.<Repository>streamAll().forEach(repository -> {
                if (repository.cleanupPolicies != null && repository.cleanupPolicies.maxAgePolicy() != null) {
                    final ManagedRepository managedRepository = repositoryManager.manage(repository);
                    repository.cleanupPolicies.maxAgePolicy().cleanup(repository, managedRepository);
                }
            });
        } finally {
            this.lockService.release(TASK_ID, lock.lock);
        }
    }

}
