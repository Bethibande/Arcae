package com.bethibande.repository.jobs;

import com.bethibande.repository.jobs.runner.JobRunner;
import com.bethibande.repository.jobs.runner.LocalJobRunner;
import com.bethibande.repository.jobs.runner.RemoteJobRunner;
import com.bethibande.repository.k8s.KubernetesLeaderService;
import com.bethibande.repository.k8s.KubernetesSupport;
import com.bethibande.repository.web.api.JobEndpoint;
import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class JobScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobScheduler.class);

    private final KubernetesLeaderService kubernetesLeaderService;

    private final boolean distributed;

    private final CronDefinition cronDefinition = CronDefinitionBuilder.defineCron()
            .withMinutes().and()
            .withHours().and()
            .withDayOfMonth().and()
            .withDayOfWeek().and()
            .withMonth().and()
            .instance();

    private final CronParser cronParser = new CronParser(this.cronDefinition);

    @Inject
    protected RemoteWorkerScheduler remoteWorkerScheduler;

    @Inject
    protected JobEndpoint jobEndpoint;

    @Inject
    protected LocalJobRunner localJobRunner;

    private final long startupTime = System.currentTimeMillis();

    @Inject
    public JobScheduler(final KubernetesLeaderService kubernetesLeaderService,
                        final KubernetesSupport kubernetesSupport,
                        final @ConfigProperty(name = "repository.distributed") boolean distributedAllowed) {
        this.kubernetesLeaderService = kubernetesLeaderService;

        this.distributed = distributedAllowed
                && kubernetesSupport.isEnabled()
                && kubernetesSupport.hasLeaderElectionSupport()
                && kubernetesSupport.canListPods()
                && kubernetesSupport.canInspectServices();

        if (distributedAllowed
                && kubernetesSupport.isEnabled()
                && !this.distributed) {
            LOGGER.warn("Leader election disabled due to missing permissions, replication not supported.");
        }

        if (this.distributed) {
            this.kubernetesLeaderService.subscribe(new LeaderCallbacks(
                    this::updateJobs,
                    () -> {
                    },
                    (_) -> {
                    }
            ));
        }
    }

    public boolean isDistributed() {
        return this.distributed;
    }

    @Scheduled(cron = "0 * * * * ?")
    protected void updateJobs() {
        if (this.distributed && !this.kubernetesLeaderService.isLeader()) {
            return;
        }

        final List<ScheduledJob> runningJobs = QuarkusTransaction.requiringNew()
                .call(() -> ScheduledJob.list("runner IS NOT NULL"));

        checkRunnerStatus(runningJobs);

        QuarkusTransaction.requiringNew().run(() -> {
            final Instant now = Instant.now();
            final List<ScheduledJob> toRun = ScheduledJob.find("runner IS NULL AND nextRunAt <= ?1", now)
                    .withLock(LockModeType.PESSIMISTIC_WRITE)
                    .list();

            for (int i = 0; i < toRun.size(); i++) {
                scheduleNow(toRun.get(i));
            }
        });
    }

    public void schedule(final ScheduledJob job, final Instant now) {
        QuarkusTransaction.requiringNew().run(() -> {
            final Cron cron = this.cronParser.parse(job.cronSchedule);
            if (job.nextRunAt == null) {
                job.nextRunAt = ExecutionTime.forCron(cron)
                        .nextExecution(now.atZone(ZoneOffset.UTC))
                        .map(ZonedDateTime::toInstant)
                        .orElse(null);
            }

            job.persist();

            if (!job.nextRunAt.isAfter(now)) scheduleNow(job);
        });
    }

    private void scheduleNow(final ScheduledJob job) {
        if (job.runner != null) return;

        final JobRunner runner;
        if (this.distributed) {
            final String hostname = this.remoteWorkerScheduler.getHostname();
            runner = new RemoteJobRunner(this.jobEndpoint, hostname);
            job.runner = hostname;
        } else {
            runner = this.localJobRunner;
            job.runner = String.valueOf(this.startupTime);
        }

        runner.run(job);
    }

    private void checkRunnerStatus(final List<ScheduledJob> runningJobs) {
        final Set<String> availableRunners = this.distributed
                ? new HashSet<>(this.remoteWorkerScheduler.getAllHostNames())
                : Set.of(String.valueOf(this.startupTime));

        final Map<String, List<ScheduledJob>> runningJobsByRunners = runningJobs.stream()
                .collect(Collectors.groupingBy(job -> job.runner));

        runningJobsByRunners.forEach((runner, jobs) -> {
            if (!availableRunners.contains(runner)) {
                QuarkusTransaction.requiringNew().run(() -> jobs.forEach(this::failJob));
            }
        });
    }

    public void failJob(final ScheduledJob job) {
        ScheduledJob.update("runner = NULL where id = ?1", job.id);
    }

    public void completeJob(final ScheduledJob job, final Instant now) {
        if (job.deleteAfterRun) {
            ScheduledJob.deleteById(job.id);
            return;
        }

        job.runner = null;
        job.lastSuccessfulRun = now;

        final Cron cron = this.cronParser.parse(job.cronSchedule);
        job.nextRunAt = ExecutionTime.forCron(cron)
                .nextExecution(now.atZone(ZoneOffset.UTC))
                .map(ZonedDateTime::toInstant)
                .orElse(null);
    }

}
