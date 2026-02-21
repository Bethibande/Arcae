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
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class JobScheduler {

    private final KubernetesLeaderService kubernetesLeaderService;

    private final boolean distributed;

    @ConfigProperty(name = "repository.scheduler.distributed")
    protected boolean distributedAllowed;

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

    @Inject
    public JobScheduler(final KubernetesLeaderService kubernetesLeaderService, final KubernetesSupport kubernetesSupport) {
        this.kubernetesLeaderService = kubernetesLeaderService;

        this.distributed = this.distributedAllowed
                && kubernetesSupport.isEnabled()
                && kubernetesSupport.hasLeaderElectionSupport();

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

        if (this.distributed) checkRunnerStatus(runningJobs);

        QuarkusTransaction.requiringNew().run(() -> {
            final Instant now = Instant.now();
            final List<ScheduledJob> toRun = ScheduledJob.list("runner IS NULL AND nextRunAt <= ?1", now);
            for (int i = 0; i < toRun.size(); i++) {
                scheduleNow(toRun.get(i));
            }
        });
    }

    public void schedule(final ScheduledJob job, final Instant now) {
        final Cron cron = this.cronParser.parse(job.cronSchedule);
        job.nextRunAt = ExecutionTime.forCron(cron)
                .nextExecution(now.atZone(ZoneOffset.UTC))
                .map(ZonedDateTime::toInstant)
                .orElse(null);

        job.persist();

        if (!now.isAfter(job.nextRunAt)) scheduleNow(job);
    }

    private void scheduleNow(final ScheduledJob job) {
        final JobRunner runner;
        if (this.distributed) {
            final String hostname = this.remoteWorkerScheduler.getHostname();
            runner = new RemoteJobRunner(this.jobEndpoint, hostname);
        } else {
            runner = this.localJobRunner;
        }

        runner.run(job);
    }

    private void checkRunnerStatus(final List<ScheduledJob> runningJobs) {
        final Set<String> hostnames = this.remoteWorkerScheduler.getAllHostNames();

        final Map<String, List<ScheduledJob>> runningJobsByRunners = runningJobs.stream()
                .collect(Collectors.groupingBy(job -> job.runner));

        runningJobsByRunners.forEach((runner, jobs) -> {
            if (!hostnames.contains(runner)) {
                QuarkusTransaction.requiringNew().run(() -> jobs.forEach(this::failJob));
            }
        });
    }

    public void failJob(final ScheduledJob job) {
        job.runner = null;
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
