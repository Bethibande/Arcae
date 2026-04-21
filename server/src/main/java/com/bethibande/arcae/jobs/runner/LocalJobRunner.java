package com.bethibande.arcae.jobs.runner;

import com.bethibande.arcae.jobs.JobType;
import com.bethibande.arcae.jobs.ScheduledJob;
import com.bethibande.arcae.jobs.impl.JobTask;
import com.bethibande.arcae.jobs.scheduler.JobScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.All;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Startup;
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

@Startup
@ApplicationScoped
public class LocalJobRunner implements JobRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalJobRunner.class);

    public static final int DEFAULT_QUEUE_SIZE = 100;

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

    private final Instant startedAt = Instant.now();

    protected volatile Long running;

    protected final Deque<Long> queue = new ArrayDeque<>();

    protected final ReentrantLock lock = new ReentrantLock();

    @Override
    public String getName() {
        return "local";
    }

    @Override
    public Instant getStartedAt() {
        return this.startedAt;
    }

    public JobTask<?> getTask(final JobType type) {
        for (int i = 0; i < this.tasks.size(); i++) {
            final JobTask<?> task = this.tasks.get(i);
            if (Objects.equals(task.getJobType(), type)) return task;
        }
        return null;
    }

    public boolean isIdle() {
        return this.running == null;
    }

    @Override
    public void run(final long jobId) throws RunnerQueueCapacityReached {
        this.lock.lock();
        try {
            if (isIdle()) {
                run0(jobId);
                return;
            }

            if(this.queue.size() >= DEFAULT_QUEUE_SIZE) {
                throw new RunnerQueueCapacityReached("Out of queue capacity");
            }

            this.queue.add(jobId);
        } catch(final Throwable th) {
            this.afterRun();
            LOGGER.error("Failed to run job, id: {}", jobId, th);
        } finally {
            this.lock.unlock();
        }
    }

    protected void afterRun() {
        this.lock.lock();
        try {
            this.running = null;

            if (!this.queue.isEmpty()) {
                final Long jobId = this.queue.poll();
                if (jobId != null) run0(jobId);
            }
        } finally {
            this.lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> void run0(final long jobId) {
        this.lock.lock();
        try {
            if (!isIdle()) throw new IllegalStateException("Already running a job");
            this.running = jobId;
        } finally {
            this.lock.unlock();
        }

        this.executor.execute(() -> {
            try {
                final ScheduledJob job = QuarkusTransaction.requiringNew().call(() -> {
                    final ScheduledJob entity = ScheduledJob.findById(jobId, LockModeType.PESSIMISTIC_WRITE);
                    ScheduledJob.update("executionStartedAt = ?1 where id = ?2", Instant.now(), jobId);

                    return entity;
                });

                if (job == null) {
                    LOGGER.warn("Scheduled job no longer exists; id {}", jobId);
                    return;
                }

                final JobTask<T> task = (JobTask<T>) this.getTask(job.type);
                if (task == null) throw new IllegalArgumentException("Unknown job type: " + job.type);

                final T config = job.settings != null
                        ? this.objectMapper.readValue(job.settings, task.getConfigType())
                        : null;
                task.run(config);

                QuarkusTransaction.requiringNew().run(() -> {
                    final ScheduledJob currentEntity = ScheduledJob.findById(job.id, LockModeType.PESSIMISTIC_WRITE);
                    this.scheduler.complete(currentEntity);
                });
            } catch (final Throwable th) {
                LOGGER.error("Job execution failed; id {}", jobId, th);
                QuarkusTransaction.requiringNew().run(() -> this.scheduler.fail(jobId));
            } finally {
                afterRun();
            }
        });
    }
}
