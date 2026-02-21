package com.bethibande.repository.jobs.runner;

import com.bethibande.repository.jobs.JobScheduler;
import com.bethibande.repository.jobs.JobStatus;
import com.bethibande.repository.jobs.JobType;
import com.bethibande.repository.jobs.ScheduledJob;
import com.bethibande.repository.jobs.impl.JobTask;
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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

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

    public JobTask<?> getTask(final JobType type) {
        for (int i = 0; i < this.tasks.size(); i++) {
            final JobTask<?> task = this.tasks.get(i);
            if (Objects.equals(task.getJobType(), type)) return task;
        }
        return null;
    }

    @Override
    public void run(final ScheduledJob job) {
        // TODO: Queue jobs if one is currently running
        this.executor.execute(() -> {
            try {
                run0(job);
            } catch (final JsonProcessingException ex) {
                LOGGER.error("Failed to parse job settings for {}: {}", job.id, ex.getMessage(), ex);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <T> void run0(final ScheduledJob job) throws JsonProcessingException {
        final JobTask<T> task = (JobTask<T>) this.getTask(job.type);
        final T config = job.settings != null
                ? this.objectMapper.readValue(job.settings, task.getConfigType())
                : null;

        try {
            task.run(config);
            updateJobStatus(job, JobStatus.SUCCEEDED);
        } catch (final Throwable th) {
            LOGGER.error("Error while running job: {}-{}", job.id, job.type, th);
            updateJobStatus(job, JobStatus.FAILED);
        }
    }

    protected void updateJobStatus(final ScheduledJob job, JobStatus result) {
        QuarkusTransaction.runner(TransactionSemantics.REQUIRE_NEW).run(() -> {
            final ScheduledJob entity = ScheduledJob.findById(job.id, LockModeType.PESSIMISTIC_WRITE);
            if (result == JobStatus.SUCCEEDED) {
                this.scheduler.completeJob(entity, Instant.now());
            } else {
                this.scheduler.failJob(entity);
            }
        });
    }

}
