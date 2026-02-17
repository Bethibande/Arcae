package com.bethibande.repository.jobs;

import com.bethibande.repository.k8s.KubernetesLeaderService;
import com.bethibande.repository.k8s.KubernetesSupport;
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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class JobScheduler {

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
    public JobScheduler(final KubernetesLeaderService kubernetesLeaderService, final KubernetesSupport kubernetesSupport) {
        this.kubernetesLeaderService = kubernetesLeaderService;
        this.kubernetesSupport = kubernetesSupport;

        this.distributed = this.kubernetesSupport.isEnabled() && this.kubernetesSupport.hasLeaderElectionSupport();
        if (this.distributed) {
            this.kubernetesLeaderService.subscribe(new LeaderCallbacks(this::updateJobs, () -> {}, (_) -> {}));
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

    private void scheduleNow(final ScheduledJob job) {
        // Will assign the job to the queue of a worker
    }

    private void checkRunnerStatus(final List<ScheduledJob> runningJobs) {
        final Map<String, List<ScheduledJob>> runningJobsByRunners = runningJobs.stream()
                .collect(Collectors.groupingBy(job -> job.runner));

        // Will ensure each actively used runner is still alive
    }

    public void failJob(final ScheduledJob job) {
        job.runner = null;
    }

    public void completeJob(final ScheduledJob job, final Instant now) {
        job.runner = null;

        final Cron cron = this.cronParser.parse(job.cronSchedule);
        job.nextRunAt = ExecutionTime.forCron(cron)
                .nextExecution(now.atZone(ZoneOffset.UTC))
                .map(ZonedDateTime::toInstant)
                .orElse(null);
    }

}
