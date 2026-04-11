package com.bethibande.arcae.jobs;

import com.bethibande.arcae.jobs.impl.JobTask;
import com.bethibande.arcae.k8s.KubernetesLeaderService;
import com.bethibande.arcae.k8s.KubernetesSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

@ApplicationScoped
public class BuiltinJobScheduler {

    @Inject
    protected KubernetesLeaderService kubernetesLeaderService;

    @Inject
    protected KubernetesSupport kubernetesSupport;

    @Inject
    protected JobScheduler scheduler;

    @Inject
    protected ObjectMapper objectMapper;

    @Inject
    @VirtualThreads
    protected Executor executor;

    public <C> CompletionStage<Void> runOnce(final JobTask<C> task, final C config) {
        return this.schedule(task, config, "* * * * *", true, true);
    }

    public <C> CompletionStage<Void> schedule(final JobTask<C> task,
                                              final C config,
                                              final String cron,
                                              final boolean immediate,
                                              final boolean deleteAfterRun) {
        final String configJson;
        try {
            configJson = this.objectMapper.writeValueAsString(config);
        } catch (final JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize job config", ex);
        }

        if (this.kubernetesSupport.isEnabled() && this.kubernetesSupport.isServiceDiscoveryEnabled()) {
            return scheduleRemote(configJson, task, cron, immediate, deleteAfterRun);
        } else {
            return scheduleLocal(configJson, task, cron, immediate, deleteAfterRun);
        }
    }

    private CompletionStage<Void> scheduleRemote(final String configJson,
                                                 final JobTask<?> task,
                                                 final String cron,
                                                 final boolean immediate,
                                                 final boolean deleteAfterRun) {
        final ScheduledJobDTOWithoutId body = new ScheduledJobDTOWithoutId(
                task.getJobType(),
                configJson,
                cron,
                deleteAfterRun
        );

        return this.kubernetesLeaderService.sendHTTPRequestToLeader(
                (baseUrl, webClient) -> webClient.postAbs(baseUrl + "/api/v1/job/schedule?now=%s".formatted(immediate))
                        .putHeader("Content-Type", "application/json")
                        .sendJson(body)
        ).<Void>map(response -> {
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to schedule job on leader node, status: " + response.statusCode() + " - " + response.bodyAsString());
            }

            return null;
        }).toCompletionStage();
    }

    private CompletableFuture<Void> scheduleLocal(final String configJson,
                                                  final JobTask<?> task,
                                                  final String cron,
                                                  final boolean immediate,
                                                  final boolean deleteAfterRun) {
        final ScheduledJob job = new ScheduledJob();
        job.type = task.getJobType();
        job.settings = configJson;
        job.cronSchedule = cron;
        job.deleteAfterRun = deleteAfterRun;
        job.nextRunAt = immediate ? Instant.now() : null;

        return CompletableFuture.runAsync(() -> {
            QuarkusTransaction.requiringNew().run(job::persist);

            scheduler.schedule(job, Instant.now());
        }, this.executor);
    }

}
