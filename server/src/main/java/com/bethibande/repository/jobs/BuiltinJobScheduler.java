package com.bethibande.repository.jobs;

import com.bethibande.repository.jobs.impl.JobTask;
import com.bethibande.repository.k8s.KubernetesLeaderService;
import com.bethibande.repository.k8s.KubernetesSupport;
import com.bethibande.repository.util.HttpClientUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

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

    public <C> CompletableFuture<Void> runOnce(final JobTask<C> task, final C config) {
        return this.schedule(task, config, "* * * * *", true, true);
    }

    public <C> CompletableFuture<Void> schedule(final JobTask<C> task,
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
            final ScheduledJobDTOWithoutId body = new ScheduledJobDTOWithoutId(
                    task.getJobType(),
                    configJson,
                    cron,
                    deleteAfterRun
            );

            return CompletableFuture.supplyAsync(
                    () -> this.kubernetesLeaderService.sendHTTPRequestToLeader(
                            "/api/v1/job/schedule?now=%s".formatted(immediate),
                            HttpResponse.BodyHandlers.discarding(),
                            builder -> builder.method("POST", HttpClientUtil.jsonBodyPublisher(body))
                                    .header("Content-Type", "application/json")
                    ).<Void>thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new RuntimeException("Failed to schedule job on leader node, status: " + response.statusCode() + " - " + response.body());
                        }
                        return null;
                    }), this.executor).thenCompose(Function.identity());
        } else {
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

}
