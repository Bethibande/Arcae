package com.bethibande.arcae.jobs;

import com.bethibande.arcae.jobs.runner.JobRunner;
import com.bethibande.arcae.jobs.runner.LocalJobRunner;
import com.bethibande.arcae.jobs.runner.RemoteJobRunner;
import com.bethibande.arcae.k8s.KubernetesLeaderService;
import com.bethibande.arcae.k8s.KubernetesSupport;
import com.bethibande.arcae.web.api.JobEndpoint;
import com.bethibande.arcae.web.api.SystemEndpoint;
import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import io.vertx.core.Future;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

@ApplicationScoped
public class JobScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobScheduler.class);

    private final KubernetesLeaderService kubernetesLeaderService;
    private final KubernetesSupport kubernetesSupport;

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
                        final @ConfigProperty(name = "arcae.distributed") boolean distributedAllowed) {
        this.kubernetesLeaderService = kubernetesLeaderService;
        this.kubernetesSupport = kubernetesSupport;

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

    public Instant getStartupTime() {
        return Instant.ofEpochMilli(this.startupTime);
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
            final ScheduledJob existing = ScheduledJob.findById(job.id, LockModeType.PESSIMISTIC_WRITE);

            final Cron cron = this.cronParser.parse(existing.cronSchedule);
            if (existing.nextRunAt == null) {
                existing.nextRunAt = ExecutionTime.forCron(cron)
                        .nextExecution(now.atZone(ZoneOffset.UTC))
                        .map(ZonedDateTime::toInstant)
                        .orElse(null);
                job.nextRunAt = existing.nextRunAt;
            }

            existing.persist();
        });

        if (!job.nextRunAt.isAfter(now)) scheduleNow(job);
    }

    private void scheduleNow(final ScheduledJob job) {
        if (job.runner != null) return;

        final JobRunner runner = QuarkusTransaction.requiringNew().call(() -> {
            job.assignedAt = Instant.now();
            if (this.distributed) {
                final String hostname = this.remoteWorkerScheduler.getHostname();
                job.runner = hostname;
                return new RemoteJobRunner(this.jobEndpoint, hostname);
            } else {
                job.runner = String.valueOf(this.startupTime);
                return this.localJobRunner;
            }
        });

        runner.run(job);
    }

    private void checkRunnerStatus(final List<ScheduledJob> runningJobs) {
        if (!this.distributed) {
            checkRunnerStatusLocal(runningJobs);
        } else {
            checkRunnerStatusRemote(runningJobs);
        }
    }

    private void checkRunnerStatusRemote(final List<ScheduledJob> runningJobs) {
        final List<String> availableRunners = this.kubernetesSupport.getAllReplicaPodNames();
        final Map<String, Instant> runnerStartTimes = new HashMap<>();

        final List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < availableRunners.size(); i++) {
            final String runner = availableRunners.get(i);
            final InetAddress address = this.kubernetesSupport.podNameToClusterIP(runner);

            final Future<?> future = this.kubernetesSupport.sendRequest(
                    address,
                    (baseUrl, webClient) -> webClient.getAbs(baseUrl + "/api/v1/system/status").send()
            ).onSuccess(response -> {
                if (response.statusCode() == 200) {
                    final SystemEndpoint.AppStatus status = response.bodyAsJson(SystemEndpoint.AppStatus.class);
                    runnerStartTimes.put(runner, status.startupTime());
                } else {
                    LOGGER.warn("Failed to fetch status of runner {}: {}", runner, response.statusCode());
                }
            }).onFailure(ex -> LOGGER.warn("Failed to fetch status of runner {}", runner, ex));

            futures.add(future);
        }

        Future.all(futures).onComplete(_ -> {
            collectDeadJob(runningJobs, runnerStartTimes);
        });
    }

    private void collectDeadJob(final List<ScheduledJob> jobs, final Map<String, Instant> runnerStartTimes) {
        final List<ScheduledJob> deadJobs = new ArrayList<>();
        for (int i = 0; i < jobs.size(); i++) {
            final ScheduledJob job = jobs.get(i);
            final Instant runnerStartTime = runnerStartTimes.get(job.runner);
            if (runnerStartTime == null || job.assignedAt.isBefore(runnerStartTime)) {
                deadJobs.add(job);
            }
        }

        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 0; i < deadJobs.size(); i++) {
                failJob(deadJobs.get(i));
            }
        });
    }

    private void checkRunnerStatusLocal(final List<ScheduledJob> runningJobs) {
        final Instant startedAt = this.getStartupTime();
        final List<ScheduledJob> failedJobs = new ArrayList<>();

        for (int i = 0; i < runningJobs.size(); i++) {
            final ScheduledJob job = runningJobs.get(i);
            if (job.assignedAt.isBefore(startedAt)) {
                failedJobs.add(job);
            }
        }

        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 0; i < failedJobs.size(); i++) {
                final ScheduledJob job = failedJobs.get(i);
                failJob(job);
            }
        });
    }

    public void failJob(final ScheduledJob job) {
        ScheduledJob.update("runner = NULL AND assignedAt = NULL where id = ?1", job.id);
    }

    public void completeJob(final ScheduledJob job, final Instant now) {
        if (job.deleteAfterRun) {
            ScheduledJob.deleteById(job.id);
            return;
        }

        job.runner = null;
        job.assignedAt = null;
        job.lastSuccessfulRun = now;

        final Cron cron = this.cronParser.parse(job.cronSchedule);
        job.nextRunAt = ExecutionTime.forCron(cron)
                .nextExecution(now.atZone(ZoneOffset.UTC))
                .map(ZonedDateTime::toInstant)
                .orElse(null);
    }

}
