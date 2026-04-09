package com.bethibande.arcae.jobs.runner;

import com.bethibande.arcae.jobs.JobScheduler;
import com.bethibande.arcae.jobs.JobType;
import com.bethibande.arcae.jobs.ScheduledJob;
import com.bethibande.arcae.jobs.impl.JobTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.All;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionSemantics;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class LocalJobRunner implements JobRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalJobRunner.class);

    @Inject
    protected JobScheduler scheduler;

    @Inject
    protected ObjectMapper objectMapper;

    @All
    @Inject
    protected List<JobTask<?>> tasks;

    @Inject
    @VirtualThreads
    protected Executor executor;

    protected volatile ScheduledJob running;

    protected final Deque<ScheduledJob> queue = new ArrayDeque<>();

    protected final ReentrantLock lock = new ReentrantLock();

    public JobTask<?> getTask(final JobType type) {
        for (int i = 0; i < this.tasks.size(); i++) {
            final JobTask<?> task = this.tasks.get(i);
            if (Objects.equals(task.getJobType(), type)) return task;
        }
        return null;
    }

    protected void onAfterRun() {
        this.lock.lock();
        try {
            this.running = null;

            final ScheduledJob next = this.queue.poll();
            if (next != null) {
                run(next);
            }
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void run(final ScheduledJob job) {
        this.lock.lock();
        try {
            if (this.running != null) {
                this.queue.add(job);
                return;
            }

            this.running = job;

            this.executor.execute(() -> {
                try {
                    run0(job);
                } catch (final JsonProcessingException ex) {
                    LOGGER.error("Failed to parse job settings for {}: {}", job.id, ex.getMessage(), ex);
                } finally {
                    onAfterRun();
                }
            });
        } finally {
            lock.unlock();
        }
    }

    private enum JobExecutionStatus {

        SUCCEEDED,
        FAILED

    }


    @SuppressWarnings("unchecked")
    private <T> void run0(final ScheduledJob job) throws JsonProcessingException {
        final JobTask<T> task = (JobTask<T>) this.getTask(job.type);
        final T config = job.settings != null
                ? this.objectMapper.readValue(job.settings, task.getConfigType())
                : null;

        QuarkusTransaction.requiringNew().run(() -> {
            final ScheduledJob entity = ScheduledJob.findById(job.id, LockModeType.PESSIMISTIC_WRITE);
            entity.executionStartedAt = Instant.now();
        });

        try {
            task.run(config);
            updateJobStatus(job, JobExecutionStatus.SUCCEEDED);
        } catch (final Throwable th) {
            LOGGER.error("Error while running job: {}-{}", job.id, job.type, th);
            updateJobStatus(job, JobExecutionStatus.FAILED);
        }
    }

    protected void updateJobStatus(final ScheduledJob job, JobExecutionStatus result) {
        QuarkusTransaction.runner(TransactionSemantics.REQUIRE_NEW).run(() -> {
            final ScheduledJob entity = ScheduledJob.findById(job.id, LockModeType.PESSIMISTIC_WRITE);
            if (result == JobExecutionStatus.SUCCEEDED) {
                this.scheduler.completeJob(entity, Instant.now());
            } else {
                this.scheduler.failJob(entity);
            }
        });
    }

}
