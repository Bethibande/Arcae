package com.bethibande.repository.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class TaskLockService {

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public TaskLock acquire(final String taskId, final long timeout, final ChronoUnit unit) {
        return TaskLock.acquire(taskId, Instant.now(), timeout, unit);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void release(final String taskId, final String lock) {
        TaskLock.release(taskId, lock, Instant.now());
    }

}
