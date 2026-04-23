package com.bethibande.arcae.jobs.scheduler;

import com.bethibande.arcae.jobs.ScheduledJob;
import com.bethibande.arcae.jobs.runner.JobRunner;
import com.bethibande.arcae.jobs.runner.RunnerQueueCapacityReached;
import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractJobScheduler implements JobScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJobScheduler.class);

    private final AtomicInteger workerOffset = new AtomicInteger();

    protected final CronDefinition cronDefinition = CronDefinitionBuilder.defineCron()
            .withMinutes().and()
            .withHours().and()
            .withDayOfMonth().and()
            .withDayOfWeek().and()
            .withMonth().and()
            .instance();

    protected final CronParser cronParser = new CronParser(this.cronDefinition);

    protected final Instant startedAt = Instant.now();

    @Override
    public abstract boolean isActive();

    protected final Set<JobRunner> taintedRunners = new HashSet<>();

    protected void loop() {
        if (!isActive()) return;

        this.taintedRunners.clear();

        checkStatusOfRunningJobs();
        schedulePendingJobs();
    }

    @Override
    public Instant getStartupTime() {
        return this.startedAt;
    }

    @Override
    public void schedule(final ScheduledJob job) {
        final Instant now = Instant.now();

        QuarkusTransaction.requiringNew().run(() -> {
            final ScheduledJob existing = ScheduledJob.findById(job.id, LockModeType.PESSIMISTIC_WRITE);

            final Cron cron = this.cronParser.parse(existing.cronSchedule);
            if (existing.nextRunAt == null) {
                existing.nextRunAt = ExecutionTime.forCron(cron)
                        .nextExecution(now.atZone(ZoneOffset.UTC))
                        .map(ZonedDateTime::toInstant)
                        .orElse(null);
                job.nextRunAt = existing.nextRunAt;
                existing.persist();
            }
        });

        if (!job.nextRunAt.isAfter(now)) runNow(job);
    }

    @Override
    public void complete(final ScheduledJob job) {
        if (job.deleteAfterRun) {
            ScheduledJob.deleteById(job.id);
            return;
        }

        final Instant now = Instant.now();

        job.runner = null;
        job.assignedAt = null;
        job.lastSuccessfulRun = now;

        final Cron cron = this.cronParser.parse(job.cronSchedule);
        job.nextRunAt = ExecutionTime.forCron(cron)
                .nextExecution(now.atZone(ZoneOffset.UTC))
                .map(ZonedDateTime::toInstant)
                .orElse(null);
    }

    @Override
    public void fail(final long jobId) {
        ScheduledJob.update("runner = NULL, assignedAt = NULL where id = ?1", jobId);
    }

    protected void schedulePendingJobs() {
        final Instant now = Instant.now();

        final AtomicBoolean running = new AtomicBoolean(true);
        do {
            QuarkusTransaction.requiringNew().run(() -> {
                final List<ScheduledJob> pendingJobs = ScheduledJob.find("nextRunAt <= ?1 AND runner IS NULL", now)
                        .page(0, 50)
                        .list();
                running.set(!pendingJobs.isEmpty());

                pendingJobs.forEach(this::runNow);

                if (this.taintedRunners.size() == this.getWorkers().size()) {
                    running.set(false);
                    LOGGER.warn("All workers are busy, the maximum queue capacity has been reached for all workers.");
                }
            });
        } while (running.get());
    }

    // Not ideal to just synchronize this, but good enough to avoid unwanted issues for now
    @Override
    public synchronized void runNow(final ScheduledJob job) {
        final int startOffset = this.workerOffset.getAndIncrement();

        final List<JobRunner> workers = getWorkers();
        for (int i = startOffset; i < startOffset + workers.size(); i++) {
            final JobRunner worker = workers.get(i % workers.size());
            if (this.taintedRunners.contains(worker)) continue;

            try {
                QuarkusTransaction.requiringNew().run(() -> ScheduledJob.update("runner = ?1, assignedAt = ?2 where id = ?3", worker.getName(), Instant.now(), job.id));

                worker.run(job.id);
                return;
            } catch (final RunnerQueueCapacityReached _) {
                this.taintedRunners.add(worker);
            } catch (final Throwable th) {
                this.taintedRunners.add(worker);
                LOGGER.warn("Failed to assign job to runner: {}", worker.getName(), th);
            }
        }
    }

    protected void checkStatusOfRunningJobs() {
        final AtomicBoolean running = new AtomicBoolean(true);

        do {
            QuarkusTransaction.requiringNew().run(() -> {
                final List<ScheduledJob> runningJobs = ScheduledJob.find("runner IS NOT NULL")
                        .page(0, 50)
                        .list();
                running.set(!runningJobs.isEmpty());

                runningJobs.forEach(this::checkJobStatus);
            });

        } while (running.get());
    }

    protected void checkJobStatus(final ScheduledJob job) {
        final JobRunner worker = getWorkerByName(job.runner);

        if (worker == null || worker.getStartedAt().isAfter(job.assignedAt)) {
            fail(job.id); // Job is dead, either the worker no longer exists or it restarted before the job was completed
        }
    }

    protected abstract JobRunner getWorkerByName(final String name);

    protected abstract List<JobRunner> getWorkers();

}
